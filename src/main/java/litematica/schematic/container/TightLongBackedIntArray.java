package litematica.schematic.container;

import javax.annotation.Nullable;

import malilib.util.MathUtils;

public class TightLongBackedIntArray extends LongArrayBackedIntArray
{
    public TightLongBackedIntArray(int bitsPerEntry, long arraySize) throws IndexOutOfBoundsException
    {
        this(bitsPerEntry, arraySize, null);
    }

    public TightLongBackedIntArray(int bitsPerEntry, long arraySize, @Nullable long[] array) throws IndexOutOfBoundsException
    {
        super(bitsPerEntry, arraySize, array);
    }

    @Override
    protected long getRequiredArrayLength(int bitsPerEntry, long arraySize)
    {
        return MathUtils.roundUp(arraySize * bitsPerEntry, 64L) / 64L;
    }

    @Override
    public int getAt(long index)
    {
        long startOffset = index * this.bitsPerEntry;
        int startArrIndex = (int) (startOffset >> 6); // startOffset / 64
        int endArrIndex = (int) (((index + 1L) * this.bitsPerEntry - 1L) >> 6);
        int startBitOffset = (int) (startOffset & 0x3F); // startOffset % 64

        if (startArrIndex == endArrIndex)
        {
            return (int) (this.longArray[startArrIndex] >>> startBitOffset & this.maxEntryValue);
        }
        else
        {
            int endOffset = 64 - startBitOffset;
            return (int) ((this.longArray[startArrIndex] >>> startBitOffset | this.longArray[endArrIndex] << endOffset) & this.maxEntryValue);
        }
    }

    @Override
    public void setAt(long index, int value)
    {
        long startOffset = index * (long) this.bitsPerEntry;
        int startArrIndex = (int) (startOffset >> 6); // startOffset / 64
        int endArrIndex = (int) (((index + 1L) * (long) this.bitsPerEntry - 1L) >> 6);
        int startBitOffset = (int) (startOffset & 0x3F); // startOffset % 64
        this.longArray[startArrIndex] = this.longArray[startArrIndex] & ~((long) this.maxEntryValue << startBitOffset) | (long) (value & this.maxEntryValue) << startBitOffset;

        if (startArrIndex != endArrIndex)
        {
            int endOffset = 64 - startBitOffset;
            int j1 = this.bitsPerEntry - endOffset;
            this.longArray[endArrIndex] = this.longArray[endArrIndex] >>> j1 << j1 | (value & this.maxEntryValue) >> endOffset;
        }
    }

    @Override
    public TightLongBackedIntArray copy()
    {
        long[] data = new long[this.longArray.length];
        System.arraycopy(this.longArray, 0, data, 0, data.length);
        return new TightLongBackedIntArray(this.bitsPerEntry, this.arraySize, data);
    }

    @Override
    public PackedIntArray createNewArray(int bitsPerEntry, long arraySize)
    {
        return new TightLongBackedIntArray(bitsPerEntry, arraySize);
    }
}
