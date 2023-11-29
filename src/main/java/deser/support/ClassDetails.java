package deser.support;

import java.util.ArrayList;
import java.util.List;

import static java.io.ObjectStreamConstants.*;

/***********************************************************
 * Support class for serialization data parsing that holds
 * details of a single class to enable class data for that
 * class to be read (classDescFlags, field descriptions).
 * <p>
 * Written by Nicky Bloor (@NickstaDB).
 **********************************************************/
public class ClassDetails {
    /**
     * The name of the class
     */
    private final String className;
    /**
     * The reference handle for the class
     */
    private int refHandle;
    /**
     * The classDescFlags value for the class
     */
    private byte classDescFlags;
    /**
     * The class field descriptions
     */
    private final List<ClassField> fieldDescriptions;

    /*******************
     * Construct the ClassDetails object.
     *
     * @param className The name of the class.
     ******************/
    public ClassDetails(String className) {
        this.className = className;
        this.refHandle = -1;
        this.classDescFlags = 0;
        this.fieldDescriptions = new ArrayList<>();
    }

    /*******************
     * Get the class name.
     *
     * @return The class name.
     ******************/
    public String getClassName() {
        return this.className;
    }

    /*******************
     * Set the reference handle of the class.
     *
     * @param handle The reference handle value.
     ******************/
    public void setHandle(int handle) {
        this.refHandle = handle;
    }

    /*******************
     * Get the reference handle.
     *
     * @return The reference handle value for this class.
     ******************/
    public int getHandle() {
        return this.refHandle;
    }

    /*******************
     * Set the classDescFlags property.
     *
     * @param classDescFlags The classDescFlags value.
     ******************/
    public void setClassDescFlags(byte classDescFlags) {
        this.classDescFlags = classDescFlags;
    }

    /*******************
     * Check whether the class is SC_SERIALIZABLE.
     *
     * @return True if the classDescFlags includes SC_SERIALIZABLE.
     ******************/
    public boolean isSC_SERIALIZABLE() {
        return (this.classDescFlags & SC_SERIALIZABLE) == SC_SERIALIZABLE;
    }

    /*******************
     * Check whether the class is SC_EXTERNALIZABLE.
     *
     * @return True if the classDescFlags includes SC_EXTERNALIZABLE.
     ******************/
    public boolean isSC_EXTERNALIZABLE() {
        return (this.classDescFlags & SC_EXTERNALIZABLE) == SC_EXTERNALIZABLE;
    }

    /*******************
     * Check whether the class is SC_WRITE_METHOD.
     *
     * @return True if the classDescFlags includes SC_WRITE_METHOD.
     ******************/
    public boolean isSC_WRITE_METHOD() {
        return (this.classDescFlags & SC_WRITE_METHOD) == SC_WRITE_METHOD;
    }

    /*******************
     * Check whether the class is SC_BLOCKDATA.
     *
     * @return True if the classDescFlags includes SC_BLOCKDATA.
     ******************/
    public boolean isSC_BLOCKDATA() {
        return (this.classDescFlags & SC_BLOCK_DATA) == SC_BLOCK_DATA;
    }

    /*******************
     * Add a field description to the class details object.
     *
     * @param cf The ClassField object describing the field.
     ******************/
    public void addField(ClassField cf) {
        this.fieldDescriptions.add(cf);
    }

    /*******************
     * Get the class field descriptions.
     *
     * @return An array of field descriptions for the class.
     ******************/
    public List<ClassField> getFields() {
        return this.fieldDescriptions;
    }

    /*******************
     * Set the name of the last field to be added to the ClassDetails object.
     *
     * @param name The field name.
     ******************/
    public void setLastFieldName(String name) {
        this.fieldDescriptions.get(this.fieldDescriptions.size() - 1).setName(name);
    }
}
