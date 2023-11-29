package deser.support;

import java.util.ArrayList;
import java.util.List;

/***********************************************************
 * Support class for serialization data parsing that holds
 * class data details that are required to enable object
 * properties and array elements to be read from the
 * stream.
 * <p>
 * Written by Nicky Bloor (@NickstaDB).
 **********************************************************/
public class ClassDataDesc {
    /**
     * List of all classes making up this class data description (i.e. class, super class, etc)
     */
    private final List<ClassDetails> classDetails;

    /*******************
     * Construct the class data description object.
     ******************/
    public ClassDataDesc() {
        this.classDetails = new ArrayList<>();
    }

    /*******************
     * Private constructor which creates a new ClassDataDesc and initialises it
     * with a subset of the ClassDetails objects from another.
     *
     * @param cd The list of ClassDetails objects for the new ClassDataDesc.
     ******************/
    private ClassDataDesc(ArrayList<ClassDetails> cd) {
        this.classDetails = cd;
    }

    /*******************
     * Build a new ClassDataDesc object from the given class index.
     * <p>
     * This is used to enable classdata to be read from the stream in the case
     * where a classDesc element references a super class of another classDesc.
     *
     * @param index The index to start the new ClassDataDesc from.
     * @return A ClassDataDesc describing the classes from the given index.
     ******************/
    public ClassDataDesc buildClassDataDescFromIndex(int index) {
        ArrayList<ClassDetails> cd;

        // Build a list of the ClassDetails objects for the new ClassDataDesc
        cd = new ArrayList<>();
        for (int i = index; i < this.classDetails.size(); ++i) {
            cd.add(this.classDetails.get(i));
        }

        // Return a new ClassDataDesc describing this subset of classes
        return new ClassDataDesc(cd);
    }

    /*******************
     * Add a super class data description to this ClassDataDesc by copying the
     * class details across to this one.
     *
     * @param scdd The ClassDataDesc object describing the super class.
     ******************/
    public void addSuperClassDesc(ClassDataDesc scdd) {
        // Copy the ClassDetails elements to this ClassDataDesc object
        if (scdd != null) {
            for (int i = 0; i < scdd.getClassCount(); ++i) {
                this.classDetails.add(scdd.getClassDetails(i));
            }
        }
    }

    /*******************
     * Add a class to the ClassDataDesc by name.
     *
     * @param className The name of the class to add.
     ******************/
    public void addClass(String className) {
        this.classDetails.add(new ClassDetails(className));
    }

    /*******************
     * Set the reference handle of the last class to be added to the
     * ClassDataDesc.
     *
     * @param handle The handle value.
     ******************/
    public void setLastClassHandle(int handle) {
        this.classDetails.get(this.classDetails.size() - 1).setHandle(handle);
    }

    /*******************
     * Set the classDescFlags of the last class to be added to the
     * ClassDataDesc.
     *
     * @param classDescFlags The classDescFlags value.
     ******************/
    public void setLastClassDescFlags(byte classDescFlags) {
        this.classDetails.get(this.classDetails.size() - 1).setClassDescFlags(classDescFlags);
    }

    /*******************
     * Add a field with the given type code to the last class to be added to
     * the ClassDataDesc.
     *
     * @param typeCode The field type code.
     ******************/
    public void addFieldToLastClass(byte typeCode) {
        this.classDetails.get(this.classDetails.size() - 1).addField(new ClassField(typeCode));
    }

    /*******************
     * Set the name of the last field that was added to the last class to be
     * added to the ClassDataDesc.
     *
     * @param name The field name.
     ******************/
    public void setLastFieldName(String name) {
        this.classDetails.get(this.classDetails.size() - 1).setLastFieldName(name);
    }

    /*******************
     * Get the details of a class by index.
     *
     * @param index The index of the class to retrieve details of.
     * @return The requested ClassDetails object.
     ******************/
    public ClassDetails getClassDetails(int index) {
        return this.classDetails.get(index);
    }

    /*******************
     * Get the number of classes making up this class data description.
     *
     * @return The number of classes making up this class data description.
     ******************/
    public int getClassCount() {
        return this.classDetails.size();
    }
}
