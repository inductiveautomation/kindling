package deser;

import static java.io.ObjectStreamConstants.*;

import deser.support.ClassDataDesc;
import deser.support.ClassDetails;
import deser.support.ClassField;
import java.io.ObjectStreamConstants;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.intellij.lang.annotations.MagicConstant;

/**
 * Helper program to dump a hex-ascii encoded serialization stream in a more readable form for debugging purposes.
 * <p>
 * Originally written by Nicky Bloor (@NickstaDB).
 **/
public class SerializationDumper {
    private static final HexFormat HF = HexFormat.of();

    /**
     * The data being parsed
     */
    private final ByteBuffer data;
    /**
     * A string representing the current indentation level for output printing
     */
    private int indent = 0;

    /**
     * The current handle value. Starts from {@link ObjectStreamConstants#baseWireHandle}
     */
    private int handleCursor = baseWireHandle;

    private final List<ClassDataDesc> classDataDescArrayList = new ArrayList<>();

    private final StringBuilder buffer = new StringBuilder();

    /**
     * Construct a SerializationDumper object.
     **/
    public SerializationDumper(byte[] data) {
        this.data = ByteBuffer.wrap(data);
    }

    /**
     * Print the given string using the current indentation.
     *
     * @param s The string to print out.
     **/
    private void print(String s) {
        buffer.append("  ".repeat(indent));
        buffer.append(s).append('\n');
    }

    private static String byteToHex(byte bite) {
        return HF.toHexDigits(bite);
    }

    /**
     * Convert an int to a hex-ascii string.
     *
     * @param i The int to convert to a string.
     * @return The hex-ascii string representation of the int.
     **/
    private static String intToHex(int i) {
        return HF.toHexDigits(i);
    }

    private byte peek() {
        return data.get(data.position());
    }

    private void checkNextByte(@MagicConstant(flagsFromClass = ObjectStreamConstants.class) byte code, String name) {
        byte nextByte = data.get();
        print(name + " - 0x" + byteToHex(nextByte));
        //noinspection MagicConstant
        if (nextByte != code) {
            throw new RuntimeException("Error: Illegal value for %s (should be 0x%s)".formatted(name, byteToHex(code)));
        }
    }

    /**
     * Print a handle value and increment the handle value for the next handle.
     **/
    private int nextHandle() {
        int newHandle = handleCursor;

        // Print the handle value
        print("newHandle 0x" + intToHex(newHandle));

        // Increment the next handle value and return the one we just assigned
        handleCursor++;
        return newHandle;
    }

    /**
     * Parse the given serialization stream and dump the details out as text.
     **/
    public String parseStream() {
        // Magic number, print and validate
        var magic = data.getShort();
        if (magic != STREAM_MAGIC) {
            throw new IllegalArgumentException("Invalid STREAM_MAGIC 0x%s, should be 0xAC ED".formatted(magic));
        }

        // Serialization version
        var version = data.getShort();
        if (version != STREAM_VERSION) {
            throw new IllegalArgumentException("Invalid STREAM_VERSION 0x%s, should be 0x00 05".formatted(version));
        }

        // Remainder of the stream consists of one or more 'content' elements
        indent++;
        while (data.hasRemaining()) {
            readContentElement();
        }
        indent--;
        return buffer.toString();
    }

    /**
     * Read a content element from the data stream.
     **/
    private void readContentElement() {
        // Peek the next byte and delegate to the appropriate method
        switch (peek()) {
            case TC_OBJECT -> readNewObject();
            case TC_CLASS -> readNewClass();
            case TC_ARRAY -> readNewArray();
            case TC_STRING, TC_LONGSTRING -> readNewString();
            case TC_ENUM -> readNewEnum();
            case TC_CLASSDESC, TC_PROXYCLASSDESC -> readNewClassDesc();
            case TC_REFERENCE -> readPrevObject();
            case TC_NULL -> readNullReference();
            case TC_BLOCKDATA -> readBlockData();
            case TC_BLOCKDATALONG -> readLongBlockData();
            default -> {
                print("Invalid content element type 0x" + byteToHex(peek()));
                throw new RuntimeException("Error: Illegal content element type.");
            }
        }
    }

    /**
     * Read an enum element from the data stream.
     * <p>
     * TC_ENUM		classDesc	newHandle	enumConstantName
     **/
    private void readNewEnum() {
        checkNextByte(TC_ENUM, "TC_ENUM");

        indent++;
        readClassDesc();
        nextHandle();
        // enumConstantName
        readNewString();
        indent--;
    }

    /**
     * Read an object element from the data stream.
     * <p>
     * TC_OBJECT	classDesc	newHandle	classdata[]
     **/
    private void readNewObject() {
        checkNextByte(TC_OBJECT, "TC_OBJECT");

        indent++;

        // ClassDataDesc describing the format of the objects 'classdata' element
        // Read the class data description
        ClassDataDesc cdd = readClassDesc();

        nextHandle();

        // Read the class data based on the class data description
        // TODO This needs to check if cdd is null before reading anything
        readClassData(cdd);

        indent--;
    }

    /**
     * Read a classDesc from the data stream.
     * <p>
     * Could be: TC_CLASSDESC		(0x72) TC_PROXYCLASSDESC	(0x7d) TC_NULL				(0x70) TC_REFERENCE (0x71)
     **/
    private ClassDataDesc readClassDesc() {
        // Peek the type and delegate to the appropriate method
        switch (peek()) {
            case TC_CLASSDESC, TC_PROXYCLASSDESC -> {
                return readNewClassDesc();
            }
            case TC_NULL -> {
                readNullReference();
                return null;
            }
            case TC_REFERENCE -> {
                // Look up a referenced class data description object and return it
                int refHandle = readPrevObject();
                // Iterate over all class data descriptions
                for (ClassDataDesc cdd : classDataDescArrayList) {
                    // Iterate over all classes in
                    // this class data description
                    for (int classIndex = 0; classIndex < cdd.getClassCount(); ++classIndex) {
                        // Check if the reference handle matches
                        if (cdd.getClassDetails(classIndex).getHandle() == refHandle) {
                            // Generate a ClassDataDesc
                            // starting from the given index and return it
                            return cdd.buildClassDataDescFromIndex(classIndex);
                        }
                    }
                }

                // Invalid classDesc reference handle
                throw new RuntimeException("Error: Invalid classDesc reference (0x" + intToHex(refHandle) + ")");
            }
            default -> {
                print("Invalid classDesc type 0x" + byteToHex(peek()));
                throw new RuntimeException("Error illegal classDesc type.");
            }
        }
    }

    /**
     * Read a newClassDesc from the stream.
     * <p>
     * Could be: TC_CLASSDESC		(0x72) TC_PROXYCLASSDESC	(0x7d)
     **/
    private ClassDataDesc readNewClassDesc() {
        ClassDataDesc cdd;

        // Peek the type and delegate to the appropriate method
        switch (peek()) {
            case TC_CLASSDESC -> {
                cdd = readTC_CLASSDESC();
                classDataDescArrayList.add(cdd);
                return cdd;
            }
            case TC_PROXYCLASSDESC -> {
                cdd = readTC_PROXYCLASSDESC();
                classDataDescArrayList.add(cdd);
                return cdd;
            }
            default -> {
                print("Invalid newClassDesc type 0x" + byteToHex(peek()));
                throw new RuntimeException("Error illegal newClassDesc type.");
            }
        }
    }

    /**
     * Read a TC_CLASSDESC from the stream.
     * <p>
     * TC_CLASSDESC		className	serialVersionUID	newHandle	classDescInfo
     **/
    private ClassDataDesc readTC_CLASSDESC() {
        ClassDataDesc cdd = new ClassDataDesc();

        checkNextByte(TC_CLASSDESC, "TC_CLASSDESC");
        indent++;

        print("className");
        indent++;
        // Add the class name to the class data description
        cdd.addClass(readUtf());
        indent--;

        print("serialVersionUID - 0x" + HF.toHexDigits(data.getLong()));

        // Set the reference handle for the most recently added class
        cdd.setLastClassHandle(nextHandle());

        // Read class desc info, add the super class description to the ClassDataDesc
        // if one is found
        readClassDescInfo(cdd);

        indent--;

        return cdd;
    }

    /**
     * Read a TC_PROXYCLASSDESC from the stream.
     * <p>
     * TC_PROXYCLASSDESC	newHandle	proxyClassDescInfo
     **/
    private ClassDataDesc readTC_PROXYCLASSDESC() {
        ClassDataDesc cdd = new ClassDataDesc();

        checkNextByte(TC_PROXYCLASSDESC, "TC_PROXYCLASSDESC");
        indent++;

        // Create the new class descriptor
        cdd.addClass("<Dynamic Proxy Class>");

        // Set the reference handle for the most recently added class
        cdd.setLastClassHandle(nextHandle());

        // Read proxy class desc info, add the super class description to the
        // ClassDataDesc if one is found
        readProxyClassDescInfo(cdd);

        indent--;

        return cdd;
    }

    /**
     * Read a classDescInfo from the stream.
     * <p>
     * classDescFlags	fields	classAnnotation	superClassDesc
     **/
    private void readClassDescInfo(ClassDataDesc cdd) {
        String classDescFlags = "";

        byte flags = data.get();
        if ((flags & SC_WRITE_METHOD) == SC_WRITE_METHOD) {
            classDescFlags += "SC_WRITE_METHOD | ";
        }
        if ((flags & SC_SERIALIZABLE) == SC_SERIALIZABLE) {
            classDescFlags += "SC_SERIALIZABLE | ";
        }
        if ((flags & SC_EXTERNALIZABLE) == SC_EXTERNALIZABLE) {
            classDescFlags += "SC_EXTERNALIZABLE | ";
        }
        if ((flags & SC_BLOCK_DATA) == SC_BLOCK_DATA) {
            classDescFlags += "SC_BLOCKDATA | ";
        }
        if (!classDescFlags.isEmpty()) {
            classDescFlags = classDescFlags.substring(0, classDescFlags.length() - 3);
        }
        print("classDescFlags - 0x" + byteToHex(flags) + " - " + classDescFlags);

        // Set the classDescFlags for the most recently added class
        cdd.setLastClassDescFlags(flags);

        // Validate classDescFlags
        if ((flags & SC_SERIALIZABLE) == SC_SERIALIZABLE) {
            if ((flags & SC_EXTERNALIZABLE) == SC_EXTERNALIZABLE) {
                throw new RuntimeException(
                        "Error: Illegal classDescFlags, SC_SERIALIZABLE is not compatible with SC_EXTERNALIZABLE.");
            }
            if ((flags & SC_BLOCK_DATA) == SC_BLOCK_DATA) {
                throw new RuntimeException(
                        "Error: Illegal classDescFlags, SC_SERIALIZABLE is not compatible with SC_BLOCKDATA.");
            }
        } else if ((flags & SC_EXTERNALIZABLE) == SC_EXTERNALIZABLE) {
            if ((flags & SC_WRITE_METHOD) == SC_WRITE_METHOD) {
                throw new RuntimeException(
                        "Error: Illegal classDescFlags, SC_EXTERNALIZABLE is not compatible with SC_WRITE_METHOD.");
            }
        } else if (flags != 0x00) {
            throw new RuntimeException(
                    "Error: Illegal classDescFlags, must include either SC_SERIALIZABLE or SC_EXTERNALIZABLE.");
        }

        // Read field descriptions and add them to the ClassDataDesc
        readFields(cdd);

        readClassAnnotation();

        cdd.addSuperClassDesc(readSuperClassDesc());
    }

    /**
     * Read a proxyClassDescInfo from the stream.
     * <p>
     * (int)count	(utf)proxyInterfaceName[count]	classAnnotation		superClassDesc
     **/
    private void readProxyClassDescInfo(ClassDataDesc cdd) {
        int count = data.getInt();
        print("Interface count - " + count);

        // proxyInterfaceName[count]
        print("proxyInterfaceNames");
        indent++;
        for (int i = 0; i < count; ++i) {
            print(i + ":");
            indent++;
            readUtf();
            indent--;
        }
        indent--;

        readClassAnnotation();

        cdd.addSuperClassDesc(readSuperClassDesc());
    }

    /**
     * Read a classAnnotation from the stream.
     * <p>
     * Could be either: contents TC_ENDBLOCKDATA		(0x78)
     **/
    private void readClassAnnotation() {
        // Print annotation section and indent
        print("classAnnotations");
        indent++;

        // Loop until we have a TC_ENDBLOCKDATA
        while (peek() != TC_ENDBLOCKDATA) {
            // Read a content element
            readContentElement();
        }

        // Pop and print the TC_ENDBLOCKDATA element
        data.get();
        print("TC_ENDBLOCKDATA - 0x78");

        indent--;
    }

    /**
     * Read a superClassDesc from the stream.
     * <p>
     * classDesc
     **/
    private ClassDataDesc readSuperClassDesc() {
        print("superClassDesc");
        indent++;
        ClassDataDesc cdd = readClassDesc();
        indent--;

        return cdd;
    }

    /**
     * Read a fields element from the stream.
     * <p>
     * (short)count		fieldDesc[count]
     **/
    private void readFields(ClassDataDesc cdd) {
        var count = data.getShort();
        print("fieldCount - " + count);

        // fieldDesc
        if (count > 0) {
            print("Fields");
            indent++;
            for (int i = 0; i < count; ++i) {
                print(i + ":");
                indent++;
                readFieldDesc(cdd);
                indent--;
            }
            indent--;
        }
    }

    /**
     * Read a fieldDesc from the stream.
     * <p>
     * Could be either: prim_typecode	fieldName obj_typecode	fieldName	className1
     **/
    private void readFieldDesc(ClassDataDesc cdd) {
        // prim_typecode/obj_typecode
        byte typeCode = data.get();
        // Add a field of the type in typeCode to the most recently added class
        cdd.addFieldToLastClass(typeCode);
        switch ((char) typeCode) {
            case 'B' -> print("Byte");
            case 'C' -> print("Char");
            case 'D' -> print("Double");
            case 'F' -> print("Float");
            case 'I' -> print("Int");
            case 'J' -> print("Long");
            case 'S' -> print("Short");
            case 'Z' -> print("Boolean");
            case '[' -> print("Array");
            case 'L' -> print("Object");
            default -> throw new RuntimeException(
                    "Error: Illegal field type code ('%s', 0x%s)".formatted((char) typeCode, byteToHex(typeCode)));
        }

        // fieldName
        print("fieldName");
        indent++;
        // Set the name of the most recently added field
        cdd.setLastFieldName(readUtf());
        indent--;

        // className1 (if non-primitive type)
        if ((char) typeCode == '[' || (char) typeCode == 'L') {
            indent++;
            String cn1 = readNewString();
            print("className1 " + cn1);
            indent--;
        }
    }

    /**
     * Read classdata from the stream.
     * <p>
     * Consists of data for each class making up the object starting with the most super class first. The length and
     * type of data depends on the classDescFlags and field descriptions.
     **/
    private void readClassData(ClassDataDesc cdd) {
        ClassDetails cd;
        int classIndex;

        // Print header and indent
        print("classdata");
        indent++;

        // Print class data if there is any
        if (cdd != null) {
            // Check for SC_EXTERNALIZABLE flags in any of the classes
            for (classIndex = 0; classIndex < cdd.getClassCount(); ++classIndex) {
                if (cdd.getClassDetails(classIndex).isSC_EXTERNALIZABLE()) {
                    throw new RuntimeException("Error: Unable to parse externalContents element.");
                }
            }

            // Iterate backwards through the classes as we need to deal with the most super (last added) class first
            for (classIndex = cdd.getClassCount() - 1; classIndex >= 0; --classIndex) {
                // Get the class details
                cd = cdd.getClassDetails(classIndex);

                // Print the class name and indent
                print(cd.getClassName());
                indent++;

                // Read the field values if the class is SC_SERIALIZABLE
                if (cd.isSC_SERIALIZABLE()) {
                    print("values");
                    indent++;

                    // Iterate over the field and read/print the contents
                    for (ClassField cf : cd.getFields()) {
                        readClassDataField(cf);
                    }

                    indent--;
                }

                // Read object annotations if the right flags are set
                if ((cd.isSC_SERIALIZABLE() && cd.isSC_WRITE_METHOD())
                        || (cd.isSC_EXTERNALIZABLE() && cd.isSC_BLOCKDATA())) {
                    print("objectAnnotation");
                    indent++;

                    // Loop until we have a TC_ENDBLOCKDATA
                    while (peek() != TC_ENDBLOCKDATA) {
                        // Read a content element
                        readContentElement();
                    }

                    // Pop and print the TC_ENDBLOCKDATA element
                    data.get();
                    print("TC_ENDBLOCKDATA - 0x78");

                    indent--;
                }

                indent--;
            }
        } else {
            print("N/A");
        }

        indent--;
    }

    /**
     * Read a classdata field from the stream.
     * <p>
     * The data type depends on the given field description.
     *
     * @param cf A description of the field data to read.
     **/
    private void readClassDataField(ClassField cf) {
        print(cf.getName());
        indent++;

        // Read the field data
        readFieldValue(cf.getTypeCode());

        indent--;
    }

    /**
     * Read a field value based on the type code.
     *
     * @param typeCode The field type code.
     **/
    private void readFieldValue(byte typeCode) {
        switch ((char) typeCode) {
            case 'B' -> {
                byte b1 = data.get();
                if (((int) b1) >= 0x20 && ((int) b1) <= 0x7e) {
                    // Print with ASCII
                    print("(byte)%s (ASCII: %s) - 0x%s".formatted(b1, (char) b1, byteToHex(b1)));
                } else {
                    // Just print byte value
                    print("(byte)%s - 0x%s".formatted(b1, byteToHex(b1)));
                }
            }
            case 'C' -> print("(char)" + data.getChar());
            case 'D' -> print("(double)" + data.getDouble());
            case 'F' -> print("(float)" + data.getFloat());
            case 'I' -> print("(int)" + data.getInt());
            case 'J' -> print("(long)" + data.getLong());
            case 'S' -> print("(short)" + data.getShort());
            case 'Z' -> print("(boolean)" + (data.get() != 0));
            case '[' -> {
                // Print field type and increase indent
                print("(array)");
                indent++;

                // Array could be null
                switch (peek()) {
                    case TC_NULL -> readNullReference();
                    case TC_ARRAY -> readNewArray();
                    case TC_REFERENCE -> readPrevObject();
                    default -> throw new RuntimeException(
                            "Error: Unexpected array field value type (0x%s".formatted(byteToHex(peek())));
                }

                indent--;
            }
            case 'L' -> {
                print("(object)");
                indent++;

                // Object fields can have various types of values...
                switch (peek()) {
                    case TC_OBJECT -> readNewObject();
                    case TC_REFERENCE -> readPrevObject();
                    case TC_NULL -> readNullReference();
                    case TC_STRING -> readTC_STRING();
                    case TC_LONGSTRING -> readTC_LONGSTRING();
                    case TC_CLASS -> readNewClass();
                    case TC_ARRAY -> readNewArray();
                    case TC_ENUM -> readNewEnum();
                    default -> throw new RuntimeException(
                            "Error: Unexpected identifier for object field value 0x%s".formatted(byteToHex(peek())));
                }
                indent--;
            }
            default -> throw new RuntimeException(
                    "Error: Illegal field type code ('%s', 0x%s)".formatted(typeCode, byteToHex(typeCode)));
        }
    }

    /**
     * Read a TC_ARRAY from the stream.
     * <p>
     * TC_ARRAY		classDesc	newHandle	(int)size	values[size]
     **/
    private void readNewArray() {
        checkNextByte(TC_ARRAY, "TC_ARRAY");
        indent++;

        // classDesc
        // Read the class data description to enable array elements to be read
        ClassDataDesc cdd = readClassDesc();
        if (cdd == null) {
            throw new RuntimeException("Error: Array class missing class description");
        }
        if (cdd.getClassCount() != 1) {
            throw new RuntimeException("Error: Array class description made up of more than one class.");
        }
        ClassDetails cd = cdd.getClassDetails(0);
        if (cd.getClassName().charAt(0) != '[') {
            throw new RuntimeException("Error: Array class name does not begin with '['.");
        }

        // newHandle
        nextHandle();

        // Array size
        int size = data.getInt();
        print("Array size - " + size);

        // Array data
        print("Values");
        indent++;
        for (int i = 0; i < size; ++i) {
            // Print element index
            print("Index " + i + ":");
            indent++;

            // Read the field values based on the classDesc read above
            readFieldValue((byte) cd.getClassName().charAt(1));

            indent--;
        }
        indent--;
        indent--;
    }

    /**
     * Read a TC_CLASS element from the stream.
     * <p>
     * TC_CLASS		classDesc		newHandle
     **/
    private void readNewClass() {
        checkNextByte(TC_CLASS, "TC_CLASS");
        indent++;
        readClassDesc();
        indent--;
        nextHandle();
    }

    /**
     * Read a TC_REFERENCE from the stream.
     * <p>
     * TC_REFERENCE		(int)handle
     **/
    private int readPrevObject() {
        checkNextByte(TC_REFERENCE, "TC_REFERENCE");
        indent++;

        // Reference handle
        int handle = data.getInt();
        print("Handle - " + handle);

        indent--;

        return handle;
    }

    /**
     * Read a TC_NULL from the stream.
     * <p>
     * TC_NULL
     **/
    private void readNullReference() {
        checkNextByte(TC_NULL, "TC_NULL");
    }

    /**
     * Read a blockdatashort element from the stream.
     * <p>
     * TC_BLOCKDATA		(unsigned byte)size		contents
     **/
    private void readBlockData() {
        StringBuilder contents = new StringBuilder();

        checkNextByte(TC_BLOCKDATA, "TC_BLOCKDATA");
        indent++;

        int len = data.get() & 0xFF;
        print("Length - " + len + " - 0x" + byteToHex((byte) (len & 0xff)));

        // contents
        for (int i = 0; i < len; ++i) {
            contents.append(byteToHex(data.get()));
        }
        print("Contents - 0x" + contents);

        indent--;
    }

    /**
     * Read a blockdatalong element from the stream.
     * <p>
     * TC_BLOCKDATALONG		(int)size	contents
     **/
    private void readLongBlockData() {
        StringBuilder contents = new StringBuilder();

        checkNextByte(TC_BLOCKDATALONG, "TC_BLOCKDATALONG");
        indent++;

        int len = data.getInt();
        print("Length - " + len);

        for (int l = 0; l < len; ++l) {
            contents.append(byteToHex(data.get()));
        }
        print("Contents - 0x" + contents);

        indent--;
    }

    /**
     * Read a newString element from the stream.
     * <p>
     * Could be: TC_STRING		(0x74) TC_LONGSTRING	(0x7c)
     **/
    private String readNewString() {
        // Peek the type and delegate to the appropriate method
        return switch (peek()) {
            case TC_STRING -> readTC_STRING();
            case TC_LONGSTRING -> readTC_LONGSTRING();
            case TC_REFERENCE -> {
                readPrevObject();
                yield "[TC_REF]";
            }
            default -> {
                print("Invalid newString type 0x" + byteToHex(peek()));
                throw new RuntimeException("Error illegal newString type.");
            }
        };
    }

    /**
     * Read a TC_STRING element from the stream.
     * <p>
     * TC_STRING	newHandle	utf
     **/
    private String readTC_STRING() {
        checkNextByte(TC_STRING, "TC_STRING");

        indent++;
        nextHandle();
        String val = readUtf();
        indent--;
        return val;
    }

    /**
     * Read a TC_LONGSTRING element from the stream.
     * <p>
     * TC_LONGSTRING	newHandle	long-utf
     **/
    private String readTC_LONGSTRING() {
        checkNextByte(TC_LONGSTRING, "TC_LONGSTRING");

        indent++;
        nextHandle();
        String val = readLongUtf();
        indent--;
        return val;
    }

    /**
     * Read a UTF string from the stream.
     * <p>
     * (short)length	contents
     **/
    private String readUtf() {
        StringBuilder content = new StringBuilder();

        // length
        int len = data.getShort();
        print("Length - " + len);

        // Contents
        for (int i = 0; i < len; ++i) {
            var nextChar = (char) data.get();
            switch (nextChar) {
                case '>' -> content.append("&gt;");
                case '<' -> content.append("&lt;");
                default -> content.append(nextChar);
            }
        }
        print("Value - " + content);

        return content.toString();
    }

    /**
     * Read a long-UTF string from the stream.
     * <p>
     * (long)length		contents
     **/
    private String readLongUtf() {
        StringBuilder content = new StringBuilder();
        StringBuilder hex = new StringBuilder();

        // Length
        long len = data.getLong();
        print("Length - " + len);

        // Contents
        for (long l = 0; l < len; ++l) {
            var b1 = data.get();
            content.append((char) b1);
            hex.append(byteToHex(b1));
        }
        print("Value - " + content + " - 0x" + hex);

        return content.toString();
    }
}
