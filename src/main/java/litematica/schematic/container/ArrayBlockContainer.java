package litematica.schematic.container;

import malilib.util.data.palette.Palette;
import malilib.util.data.palette.PaletteResizeHandler;
import malilib.util.position.Vec3i;
import malilib.util.world.BlockState;

public class ArrayBlockContainer extends BaseBlockContainer implements PaletteResizeHandler<BlockState>
{
    protected PackedIntArray storage;
    protected boolean checkForFreedIds = true;

    public ArrayBlockContainer(Vec3i size, int entryWidthBits)
    {
        super(size, entryWidthBits);

        this.storage = new AlignedLongBackedIntArray(entryWidthBits, this.totalVolume);
    }

    public ArrayBlockContainer(Vec3i size, PackedIntArray storage)
    {
        super(size, storage.getEntryBitWidth());

        this.storage = storage;
    }

    @Override
    protected void setEntryWidthBits(int entryWidthBits)
    {
        entryWidthBits = Math.max(MINIMUM_ENTRY_WIDTH, entryWidthBits);

        if (entryWidthBits != this.entryWidthBits)
        {
            this.entryWidthBits = entryWidthBits;
            this.palette = createPalette(entryWidthBits, this);
        }
    }

    @Override
    public BlockState getBlockState(int x, int y, int z)
    {
        long storageIndex = this.getIndex(x, y, z);
        int valueId = this.storage.getAt(storageIndex);
        BlockState state = this.palette.getValue(valueId);
        return state == null ? AIR_BLOCK_STATE : state;
    }

    @Override
    public void setBlockState(int x, int y, int z, BlockState state)
    {
        long storageIndex = this.getIndex(x, y, z);
        int valueId = this.palette.idFor(state);
        this.storage.setAt(storageIndex, valueId);
        this.hasSetBlockCounts = false; // Force a re-count when next queried
    }

    @Override
    public int onResize(int bits, BlockState state, Palette<BlockState> oldPalette)
    {
        if (this.checkForFreedIds)
        {
            long[] counts = this.storage.getValueCounts();
            final int countsSize = counts.length;

            // Check if there are any IDs that are not in use anymore
            for (int id = 0; id < countsSize; ++id)
            {
                // Found an ID that is not in use anymore, use that instead of increasing the palette size
                if (counts[id] == 0)
                {
                    if (this.palette.overrideMapping(id, state))
                    {
                        return id;
                    }
                }
            }
        }

        PackedIntArray oldArray = this.storage;
        PackedIntArray newArray = this.storage.createNewArray(bits, this.totalVolume);

        // This creates the new palette with the increased size
        this.setEntryWidthBits(bits);
        // Copy over the full old palette mapping
        this.palette.setMapping(oldPalette.getMapping());

        final long size = oldArray.size();

        for (long index = 0; index < size; ++index)
        {
            newArray.setAt(index, oldArray.getAt(index));
        }

        this.storage = newArray;

        return this.palette.idFor(state);
    }

    protected long getIndex(int x, int y, int z)
    {
        return ((long) y * this.sizeLayer) + (long) z * (long) this.sizeX + (long) x;
    }

    public PackedIntArray getIntStorage()
    {
        return this.storage;
    }

    @Override
    protected void calculateBlockCountsIfNeeded()
    {
        if (this.hasSetBlockCounts == false)
        {
            long[] counts = this.storage.getValueCounts();
            this.setBlockCounts(counts);
        }
    }

    @Override
    public ArrayBlockContainer copy()
    {
        ArrayBlockContainer newContainer = new ArrayBlockContainer(this.size, this.storage.copy());
        newContainer.palette = this.palette.copy(newContainer);

        return newContainer;
    }

    public static int getRequiredBitWidth(int paletteSize)
    {
        return Math.max(MINIMUM_ENTRY_WIDTH, Integer.SIZE - Integer.numberOfLeadingZeros(paletteSize - 1));
    }

    public static class BlockStateConverterResults
    {
        public final long[] backingArray;
        public final long[] blockCounts;

        public BlockStateConverterResults(long[] backingArray, long[] blockCounts)
        {
            this.backingArray = backingArray;
            this.blockCounts = blockCounts;
        }
    }
}
