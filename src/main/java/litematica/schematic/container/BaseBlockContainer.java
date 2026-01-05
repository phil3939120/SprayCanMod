package litematica.schematic.container;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import malilib.util.data.palette.HashMapPalette;
import malilib.util.data.palette.LinearPalette;
import malilib.util.data.palette.Palette;
import malilib.util.data.palette.PaletteResizeHandler;
import malilib.util.position.Vec3i;
import malilib.util.world.BlockState;
import litematica.util.PositionUtils;

public abstract class BaseBlockContainer implements BlockContainer
{
    public static final BlockState AIR_BLOCK_STATE = BlockState.AIR;

    public static final int MINIMUM_ENTRY_WIDTH = 2;
    protected static final int MAX_BITS_LINEAR = 4;

    protected Palette<BlockState> palette;
    protected final Vec3i size;
    protected final int sizeX;
    protected final int sizeY;
    protected final int sizeZ;
    protected final long sizeLayer;
    protected final long totalVolume;
    protected int entryWidthBits;
    protected boolean hasSetBlockCounts;
    protected long[] blockCounts = new long[0];

    public BaseBlockContainer(Vec3i size)
    {
        this(size, MINIMUM_ENTRY_WIDTH);
    }

    protected BaseBlockContainer(Vec3i size, int entryWidthBits)
    {
        this.size = PositionUtils.getAbsoluteSize(size);
        this.sizeX = this.size.getX();
        this.sizeY = this.size.getY();
        this.sizeZ = this.size.getZ();
        this.totalVolume = (long) this.sizeX * (long) this.sizeY * (long) this.sizeZ;
        this.sizeLayer = (long) this.sizeX * (long) this.sizeZ;

        this.setEntryWidthBits(entryWidthBits);
    }

    @Override
    public Vec3i getSize()
    {
        return this.size;
    }

    @Override
    public long getTotalVolume()
    {
        return this.totalVolume;
    }

    @Override
    public Palette<BlockState> getPalette()
    {
        return this.palette;
    }

    @Override
    public long getTotalBlockCount()
    {
        this.calculateBlockCountsIfNeeded();

        final int length = this.blockCounts.length;
        Palette<BlockState> palette = this.getPalette();
        long count = 0;

        for (int id = 0; id < length; ++id)
        {
            BlockState state = palette.getValue(id);

            if (state != null && state.isAirMaterial() == false)
            {
                count += this.blockCounts[id];
            }
        }

        return count;
    }

    @Override
    public Object2LongOpenHashMap<BlockState> getBlockCountsMap()
    {
        this.calculateBlockCountsIfNeeded();

        Object2LongOpenHashMap<BlockState> map = new Object2LongOpenHashMap<>(this.blockCounts.length);
        Palette<BlockState> palette = this.getPalette();
        final int length = Math.min(palette.getSize(), this.blockCounts.length);

        for (int id = 0; id < length; ++id)
        {
            BlockState state = palette.getValue(id);

            if (state != null)
            {
                map.put(state, this.blockCounts[id]);
            }
        }

        return map;
    }

    public void setBlockCounts(long[] blockCounts)
    {
        final int length = blockCounts.length;

        if (this.blockCounts == null || this.blockCounts.length < length)
        {
            this.blockCounts = new long[length];
        }

        System.arraycopy(blockCounts, 0, this.blockCounts, 0, length);
        this.hasSetBlockCounts = true;
    }

    protected void setEntryWidthBits(int bitsIn)
    {
        this.entryWidthBits = bitsIn;
    }

    public static Palette<BlockState> createPalette(int entryWidthBits, PaletteResizeHandler<BlockState> resizeHandler)
    {
        Palette<BlockState> palette;

        if (entryWidthBits <= MAX_BITS_LINEAR)
        {
            palette = new LinearPalette<>(entryWidthBits, resizeHandler);
        }
        else
        {
            palette =  new HashMapPalette<>(entryWidthBits, resizeHandler);
        }

        // Always reserve ID 0 for air, so that the container doesn't need to be filled with air separately
        palette.idFor(AIR_BLOCK_STATE);

        return palette;
    }

    protected abstract void calculateBlockCountsIfNeeded();
}
