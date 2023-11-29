package deser.support;

/***********************************************************
 * Support class for serialization data parsing that holds
 * details of a class field to enable the field value to
 * be read from the stream.
 * <p>
 * Written by Nicky Bloor (@NickstaDB).
 **********************************************************/
public class ClassField {
    /**
     * The field type code
     */
    private final byte typeCode;
    /**
     * The field name
     */
    private String name;

    /*******************
     * Construct the ClassField object.
     *
     * @param typeCode The field type code.
     ******************/
    public ClassField(byte typeCode) {
        this.typeCode = typeCode;
        this.name = "";
    }

    /*******************
     * Get the field type code.
     *
     * @return The field type code.
     ******************/
    public byte getTypeCode() {
        return this.typeCode;
    }

    /*******************
     * Set the field name.
     *
     * @param name The field name.
     ******************/
    public void setName(String name) {
        this.name = name;
    }

    /*******************
     * Get the field name.
     *
     * @return The field name.
     ******************/
    public String getName() {
        return this.name;
    }
}
