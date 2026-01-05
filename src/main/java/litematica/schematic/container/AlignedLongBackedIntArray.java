package litematica.schematic.container;

import javax.annotation.Nullable;

public class AlignedLongBackedIntArray extends LongArrayBackedIntArray
{
    protected final int valuesPerArrayElement;

    public AlignedLongBackedIntArray(int bitsPerEntry, long arraySize) throws IndexOutOfBoundsException
    {
        this(bitsPerEntry, arraySize, null);
    }

    public AlignedLongBackedIntArray(int bitsPerEntry, long arraySize, @Nullable long[] array) throws IndexOutOfBoundsException
    {
        super(bitsPerEntry, arraySize, array);

        this.valuesPerArrayElement = 64 / bitsPerEntry;
    }

    @Override
    protected long getRequiredArrayLength(int bitsPerEntry, long arraySize)
    {
        int valuesPerArrayElement = 64 / bitsPerEntry;
        return (long) Math.ceil((double) arraySize / (double) valuesPerArrayElement);
    }

    @Override
    public int getAt(long index)
    {
        int arrayIndex = (int) (index / this.valuesPerArrayElement);

        long arrayValue = this.longArray[arrayIndex];
        int indexWithinLongValue = (int) (index - arrayIndex * this.valuesPerArrayElement);
        int value = (int) ((arrayValue >> (indexWithinLongValue * this.bitsPerEntry)) & this.maxEntryValue);

        return value;
    }

    @Override
    public void setAt(long index, int value)
    {
        int arrayIndex = (int) (index / this.valuesPerArrayElement);

        long arrayValue = this.longArray[arrayIndex];
        int indexWithinLongValue = (int) (index - arrayIndex * this.valuesPerArrayElement);
        int shiftAmount = indexWithinLongValue * this.bitsPerEntry;
        arrayValue &= ~((long) this.maxEntryValue << shiftAmount);
        arrayValue |= ((long) (value & this.maxEntryValue) << shiftAmount);

        this.longArray[arrayIndex] = arrayValue;
    }

    @Override
    public AlignedLongBackedIntArray copy()
    {
        long[] data = new long[this.longArray.length];
        System.arraycopy(this.longArray, 0, data, 0, data.length);
        return new AlignedLongBackedIntArray(this.bitsPerEntry, this.arraySize, data);
    }

    @Override
    public PackedIntArray createNewArray(int bitsPerEntry, long arraySize)
    {
        return new AlignedLongBackedIntArray(bitsPerEntry, arraySize);
    }
}
