package litematica.util.world;

import java.util.Map;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.Chunk.EnumCreateEntityType;

import malilib.util.data.tag.CompoundData;
import malilib.util.position.BlockPos;
import malilib.util.position.IntBoundingBox;
import malilib.util.position.Vec3i;
import malilib.util.world.BlockState;
import malilib.util.world.ScheduledBlockTickData;

/**
 * A BlockView that prefers the source 1, but will fall back to source 2 if source 1 has air in the requested position.
 */
public class VanillaChunkFallbackBlockView implements BlockView
{
    protected final World world1;
    protected final World world2;
    protected final Chunk chunk1;
    protected final Chunk chunk2;

    public VanillaChunkFallbackBlockView(World world1, World world2, Chunk chunk1, Chunk chunk2)
    {
        this.world1 = world1;
        this.world2 = world2;
        this.chunk1 = chunk1;
        this.chunk2 = chunk2;
    }

    @Override
    public BlockState getBlockState(BlockPos pos)
    {
        BlockState state = BlockState.of(this.chunk1.getBlockState(pos.getX(), pos.getY(), pos.getZ()).getActualState(this.world1, pos));

        if (state.isAirMaterial() == false)
        {
            return state;
        }

        return BlockState.of(this.chunk2.getBlockState(pos.getX(), pos.getY(), pos.getZ()).getActualState(this.world2, pos));
    }

    @Override
    public boolean readBlockEntityToMap(BlockPos pos, Vec3i basePosition, Map<BlockPos, CompoundData> blockTickMap)
    {
        BlockState state = BlockState.of(this.chunk1.getBlockState(pos.getX(), pos.getY(), pos.getZ()));
        Chunk chunk = state.isAirMaterial() == false ? this.chunk1 : this.chunk2;

        return VanillaWorldBlockView.readBlockEntityToMap(pos, basePosition, blockTickMap,
                                                          p -> chunk.getTileEntity(p, EnumCreateEntityType.CHECK));
    }

    @Override
    public boolean readBlockTicksToMap(IntBoundingBox box, Vec3i basePosition, Map<BlockPos, ScheduledBlockTickData> map)
    {
        // TODO Should we bother with implementing this?
        return false;
    }
}
