package litematica.schematic;

import java.util.List;
import java.util.Map;

import malilib.util.data.tag.CompoundData;
import malilib.util.position.BlockPos;
import malilib.util.position.Vec3i;
import malilib.util.world.ScheduledBlockTickData;
import litematica.schematic.container.BlockContainer;
import litematica.schematic.data.EntityData;

public class SchematicRegion
{
    protected final BlockPos relativePosition;
    protected final Vec3i size;
    protected final BlockContainer blockContainer;
    protected final Map<BlockPos, CompoundData> blockEntityData;
    protected final Map<BlockPos, ScheduledBlockTickData> blockTickData;
    protected final List<EntityData> entityData;
    protected final int minecraftDataVersion;

    public SchematicRegion(BlockPos relativePosition,
                           Vec3i size,
                           BlockContainer blockContainer,
                           Map<BlockPos, CompoundData> blockEntityData,
                           Map<BlockPos, ScheduledBlockTickData> blockTickData,
                           List<EntityData> entityData,
                           int minecraftDataVersion)
    {
        this.relativePosition = relativePosition;
        this.size = size;
        this.blockContainer = blockContainer;
        this.blockEntityData = blockEntityData;
        this.blockTickData = blockTickData;
        this.entityData = entityData;
        this.minecraftDataVersion = minecraftDataVersion;
    }

    /**
     * @return the relative position of this region in relation to the origin of the entire schematic
     */
    public BlockPos getRelativePosition()
    {
        return this.relativePosition;
    }

    /**
     * @return the size of this region.
     * <b>Note:</b> The size can be negative on any axis, if the second corner is on the negative side
     * on that axis compared to the primary/origin corner.
     */
    public Vec3i getSize()
    {
        return this.size;
    }

    /**
     * @return the block state container used for storing the block states in this region
     */
    public BlockContainer getBlockContainer()
    {
        return this.blockContainer;
    }

    /**
     * @return the BlockEntity map for this region
     */
    public Map<BlockPos, CompoundData> getBlockEntityMap()
    {
        return this.blockEntityData;
    }

    /**
     * @return the entity list for this region
     */
    public List<EntityData> getEntityList()
    {
        return this.entityData;
    }

    /**
     * @return the map of scheduled block tick data in this region
     */
    public Map<BlockPos, ScheduledBlockTickData> getBlockTickMap()
    {
        return this.blockTickData;
    }

    public int getMinecraftDataVersion()
    {
        return this.minecraftDataVersion;
    }
}
