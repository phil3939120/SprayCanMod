package litematica.util.world;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.NextTickListEntry;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import malilib.util.data.tag.CompoundData;
import malilib.util.data.tag.util.DataTypeUtils;
import malilib.util.game.wrap.BlockWrap;
import malilib.util.game.wrap.GameWrap;
import malilib.util.game.wrap.RegistryUtils;
import malilib.util.position.BlockPos;
import malilib.util.position.IntBoundingBox;
import malilib.util.position.Vec3i;
import malilib.util.world.BlockState;
import malilib.util.world.ScheduledBlockTickData;
import litematica.mixin.access.NextTickListEntryMixin;

public class VanillaWorldBlockView implements BlockView
{
    protected final World world;

    public VanillaWorldBlockView(World world)
    {
        this.world = world;
    }

    /*
    @Override
    public BlockState getBlockState(int x, int y, int z)
    {
        Chunk chunk = this.world.getChunk(x >> 4, z >> 4);
        return BlockState.of(chunk.getBlockState(x, y, z));
    }
    */

    @Override
    public BlockState getBlockState(BlockPos pos)
    {
        return BlockState.of(this.world.getBlockState(pos).getActualState(this.world, pos));
    }

    @Override
    public boolean readBlockEntityToMap(BlockPos pos, Vec3i basePosition, Map<BlockPos, CompoundData> blockTickMap)
    {
        return readBlockEntityToMap(pos, basePosition, blockTickMap, this.world::getTileEntity);
    }

    @Override
    public boolean readBlockTicksToMap(IntBoundingBox box, Vec3i basePosition, Map<BlockPos, ScheduledBlockTickData> blockTickMap)
    {
        return readBlockTicksToMap(box, basePosition, blockTickMap, this.world);
    }

    public static boolean readBlockEntityToMap(BlockPos pos,
                                               Vec3i basePosition,
                                               Map<BlockPos, CompoundData> map,
                                               Function<BlockPos, TileEntity> reader)
    {
        TileEntity be = reader.apply(pos);

        if (be != null)
        {
            CompoundData data = BlockWrap.writeBlockEntityToTag(be);

            if (data == null)
            {
                return false;
            }

            BlockPos relPos = pos.subtract(basePosition);
            DataTypeUtils.removeBlockPosFromTag(data);
            map.put(relPos, data);
        }

        return true;
    }

    public static boolean readBlockTicksToMap(IntBoundingBox box,
                                              Vec3i basePosition,
                                              Map<BlockPos, ScheduledBlockTickData> blockTickMap,
                                              World world)
    {
        if ((world instanceof WorldServer) == false)
        {
            return false;
        }

        // The vanilla method checks for "x < maxX" etc.
        IntBoundingBox expandedBox = IntBoundingBox.createProper(
                box.minX,     box.minY,     box.minZ,
                box.maxX + 1, box.maxY + 1, box.maxZ + 1);
        List<NextTickListEntry> pendingTicks = world.getPendingBlockUpdates(expandedBox.toVanillaBox(), false);

        if (pendingTicks == null)
        {
            return false;
        }

        long currentWorldTick = GameWrap.getWorldTotalTick(world);

        // The getPendingBlockUpdates() method doesn't check the y-coordinate... :-<
        for (NextTickListEntry entry : pendingTicks)
        {
            if (entry.position.getY() < box.minY || entry.position.getY() > box.maxY)
            {
                continue;
            }

            BlockPos relPos = new BlockPos(entry.position.getX() - basePosition.getX(),
                                           entry.position.getY() - basePosition.getY(),
                                           entry.position.getZ() - basePosition.getZ());

            // Store the delay, i.e. relative time
            long delay = entry.scheduledTime - currentWorldTick;
            long tickId = ((NextTickListEntryMixin) entry).litematica$getTickId();

            ScheduledBlockTickData tickData = new ScheduledBlockTickData(relPos,
                                                                         RegistryUtils.getBlockIdStr(entry.getBlock()),
                                                                         entry.priority,
                                                                         delay,
                                                                         tickId);

            blockTickMap.put(relPos, tickData);
        }

        return true;
    }
}
