package litematica.schematic.container;

public interface PackedIntArray
{
    /**
     * @return The size of the array (as number of entries)
     */
    long size();

    /**
     * @return the bit width of each entry
     */
    int getEntryBitWidth();

    /**
     * @param index
     * @return The int value at the given index
     */
    int getAt(long index);

    /**
     * Set the value at the given index to the given value
     * @param index
     */
    void setAt(long index, int value);

    /**
     * @return An array representing the number of occurrences of each value in this array.
     * The length of the returned array depends on the number of unique values currently stored in the array.
     */
    long[] getValueCounts();

    /**
     * @return A copy of this array
     */
    PackedIntArray copy();

    /**
     * @return a new array of the same type with the given entry size and array size
     */
    PackedIntArray createNewArray(int bitsPerEntry, long arraySize);
}
