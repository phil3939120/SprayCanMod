package litematica.util.world;

import java.util.Map;

import malilib.util.data.tag.CompoundData;
import malilib.util.position.BlockPos;
import malilib.util.position.IntBoundingBox;
import malilib.util.position.Vec3i;
import malilib.util.world.BlockState;
import malilib.util.world.ScheduledBlockTickData;

public interface BlockView
{
    /*
    BlockState getBlockState(int x, int y, int z);
    */

    BlockState getBlockState(BlockPos pos);

    /**
     * Reads the block entity at the given position {@link BlockPos pos} to a data tag,
     * using the position {@link Vec3i basePosition} as the origin position.
     * So that means the stored position will be the relative distance between them, i.e.
     * {@link BlockPos pos} - {@link Vec3i basePosition}.
     * @param pos The World position to read from
     * @param basePosition The base position to subtract from the absolute world position
     * @param map The map to put he read data tag (if any) to
     * @return true if reading the data succeeded (including if there was no BlockEntity at that position),
     * false if there was an error reading the data
     */
    boolean readBlockEntityToMap(BlockPos pos, Vec3i basePosition, Map<BlockPos, CompoundData> map);

    boolean readBlockTicksToMap(IntBoundingBox box, Vec3i basePosition, Map<BlockPos, ScheduledBlockTickData> map);
}
