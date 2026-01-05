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

public class VanillaChunkBlockView implements BlockView
{
    protected final World world;
    protected final Chunk chunk;

    public VanillaChunkBlockView(World world, Chunk chunk)
    {
        this.world = world;
        this.chunk = chunk;
    }

    /*
    @Override
    public BlockState getBlockState(int x, int y, int z)
    {
        return BlockState.of(this.chunk.getBlockState(x, y, z));
    }
    */

    @Override
    public BlockState getBlockState(BlockPos pos)
    {
        return BlockState.of(this.chunk.getBlockState(pos.getX(), pos.getY(), pos.getZ()).getActualState(this.world, pos));
    }

    @Override
    public boolean readBlockEntityToMap(BlockPos pos, Vec3i basePosition, Map<BlockPos, CompoundData> blockTickMap)
    {
        return VanillaWorldBlockView.readBlockEntityToMap(pos, basePosition, blockTickMap,
                                                          p -> this.chunk.getTileEntity(p, EnumCreateEntityType.CHECK));
    }

    @Override
    public boolean readBlockTicksToMap(IntBoundingBox box, Vec3i basePosition, Map<BlockPos, ScheduledBlockTickData> map)
    {
        return VanillaWorldBlockView.readBlockTicksToMap(box, basePosition, map, this.world);
    }
}
