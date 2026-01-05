package litematica.schematic.container;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import malilib.util.position.Vec3i;
import malilib.util.world.BlockState;

public class SparseBlockContainer extends BaseBlockContainer
{
    protected final Long2ObjectOpenHashMap<BlockState> blocks = new Long2ObjectOpenHashMap<>();

    public SparseBlockContainer(Vec3i size)
    {
        super(size);

        this.palette = new NonResizingHashMapPalette<>(1024);
        this.blockCounts = new long[1024];
    }

    @Override
    public BlockState getBlockState(int x, int y, int z)
    {
        long pos = getPosAsLong(x, y, z);
        BlockState state = this.blocks.get(pos);
        return state != null ? state : AIR_BLOCK_STATE;
    }

    @Override
    public void setBlockState(int x, int y, int z, BlockState state)
    {
        long pos = getPosAsLong(x, y, z);
        BlockState oldState = this.blocks.put(pos, state);
        this.updateBlockCounts(state, oldState);
    }

    protected void updateBlockCounts(BlockState newState, BlockState oldState)
    {
        int id = this.palette.idFor(newState);

        if (id >= this.blockCounts.length)
        {
            long[] oldArr = this.blockCounts;
            this.blockCounts = new long[oldArr.length * 2];
            System.arraycopy(oldArr, 0, this.blockCounts, 0, oldArr.length);
        }

        if (oldState != newState)
        {
            if (oldState != null)
            {
                int oldId = this.palette.idFor(oldState);

                if (oldId >= 0)
                {
                    --this.blockCounts[oldId];
                }
            }

            ++this.blockCounts[id];
        }
    }

    @Override
    public BlockContainer copy()
    {
        SparseBlockContainer copy = new SparseBlockContainer(this.size);
        copy.blocks.putAll(this.blocks);
        copy.blockCounts = this.blockCounts.clone();
        copy.palette = this.palette.copy(null);
        return copy;
    }

    @Override
    protected void calculateBlockCountsIfNeeded()
    {
    }

    public Long2ObjectOpenHashMap<BlockState> getBlockMap()
    {
        return this.blocks;
    }

    public static long getPosAsLong(int x, int y, int z)
    {
        return (long) (y & 0xFFFF) << 48 | (long) (z & 0xFFFFFF) << 24 | (long) (x & 0xFFFFFF);
    }

    public static int getXFromLong(long data)
    {
        return (int) (data & 0xFFFFFF);
    }

    public static int getYFromLong(long data)
    {
        return (int) ((data >>> 48) & 0xFFFF);
    }

    public static int getZFromLong(long data)
    {
        return (int) ((data >>> 24) & 0xFFFFFF);
    }
}
