package litematica.scheduler.task;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;

import malilib.overlay.message.MessageDispatcher;
import malilib.util.data.tag.CompoundData;
import malilib.util.data.tag.util.DataTypeUtils;
import malilib.util.game.MinecraftVersion;
import malilib.util.game.wrap.EntityWrap;
import malilib.util.game.wrap.GameWrap;
import malilib.util.position.BlockPos;
import malilib.util.position.BlockPos.MutBlockPos;
import malilib.util.position.ChunkPos;
import malilib.util.position.IntBoundingBox;
import malilib.util.position.Vec3d;
import malilib.util.position.Vec3i;
import malilib.util.world.BlockState;
import malilib.util.world.ScheduledBlockTickData;
import litematica.Litematica;
import litematica.render.infohud.InfoHud;
import litematica.scheduler.tasks.TaskProcessChunkBase;
import litematica.schematic.BaseSchematic;
import litematica.schematic.Schematic;
import litematica.schematic.SchematicMetadata;
import litematica.schematic.SchematicRegion;
import litematica.schematic.SchematicSaveSettings;
import litematica.schematic.container.BlockContainer;
import litematica.schematic.data.EntityData;
import litematica.selection.AreaSelection;
import litematica.selection.SelectionBox;
import litematica.util.PositionUtils;
import litematica.util.value.SchematicSaveWorldSelection;
import litematica.util.world.BlockView;
import litematica.util.world.VanillaChunkBlockView;
import litematica.util.world.VanillaChunkFallbackBlockView;
import litematica.world.SchematicWorldHandler;

public class LocalCreateSchematicTask extends TaskProcessChunkBase
{
    protected final AreaSelection area;
    protected final SchematicSaveSettings settings;
    protected final BlockPos origin;
    protected final ImmutableMap<String, SelectionBox> subRegions;
    protected final ArrayListMultimap<ChunkPos, SelectionBox> selectionBoxesPerChunk;
    protected final Map<String, BlockContainer> blockContainers = new HashMap<>();
    protected final Map<String, Map<BlockPos, CompoundData>> blockEntityMaps = new HashMap<>();
    protected final Map<String, Map<BlockPos, ScheduledBlockTickData>> blockTickMaps = new HashMap<>();
    protected final Map<String, List<EntityData>> entityLists = new HashMap<>();
    protected final Set<UUID> existingEntities = new HashSet<>();
    protected final Consumer<Schematic> schematicListener;
    protected final boolean obeyIgnoredBlocks;
    protected final boolean obeyIgnoredBlockStates;
    protected final boolean obeyIgnoredEntities;
    protected long totalBlocks;
    protected int totalEntities;
    protected long totalBlockEntities;
    protected long totalBlockTicks;

    public LocalCreateSchematicTask(AreaSelection area,
                                    SchematicSaveSettings settings,
                                    Consumer<Schematic> schematicListener)
    {
        super("litematica.hud.task_name.local_create_schematic");

        Collection<SelectionBox> allBoxes = area.getAllSelectionBoxes();

        this.area = area.copy();
        this.settings = settings;
        this.schematicListener = schematicListener;
        this.origin = area.getEffectiveOrigin();
        this.subRegions = area.getAllSelectionBoxesMap();
        this.selectionBoxesPerChunk = PositionUtils.getPerChunkBoxes(allBoxes);
        this.obeyIgnoredBlocks = settings.obeyIgnoredBlocks.getBooleanValue() && settings.ignoredBlocks.isEmpty() == false;
        this.obeyIgnoredBlockStates = settings.obeyIgnoredBlockStates.getBooleanValue() && settings.ignoredBlockStates.isEmpty() == false;
        this.obeyIgnoredEntities = settings.obeyIgnoredEntities.getBooleanValue() && settings.ignoredEntities.isEmpty() == false;

        this.setCompletionListener(this::onDataCollected);
        this.addPerChunkBoxes(allBoxes);
    }

    @Override
    protected boolean canProcessChunk(ChunkPos pos)
    {
        return this.areSurroundingChunksLoaded(pos, this.worldClient, 1);
    }

    @Override
    protected boolean processChunk(ChunkPos pos)
    {
        for (SelectionBox box : this.selectionBoxesPerChunk.get(pos))
        {
            this.readAllFromBox(pos, box);
        }

        return true;
    }

    protected void readAllFromBox(ChunkPos cPos, SelectionBox selectionBox)
    {
        IntBoundingBox box = PositionUtils.getBoundsWithinChunkForBox(selectionBox, cPos.x, cPos.z);

        if (box == null)
        {
            return;
        }

        String regionName = selectionBox.getName();
        BlockPos minCorner = malilib.util.position.PositionUtils.getMinCorner(selectionBox.getCorner1(), selectionBox.getCorner2());
        BlockContainer container = this.blockContainers.computeIfAbsent(regionName, key -> this.createBlockContainer(selectionBox));
        Map<BlockPos, CompoundData> blockEntityMap = this.blockEntityMaps.computeIfAbsent(regionName, key -> new HashMap<>());

        BlockView blockView = this.getBlockView(cPos);

        this.readBlockData(container, blockEntityMap, box, minCorner, this.settings, blockView);

        if (this.settings.saveScheduledBlockTicks.getBooleanValue())
        {
            Map<BlockPos, ScheduledBlockTickData> blockTickMap = this.blockTickMaps.computeIfAbsent(regionName, key -> new HashMap<>());
            long countBefore = blockTickMap.size();
            blockView.readBlockTicksToMap(box, minCorner, blockTickMap);
            this.totalBlockTicks += (blockTickMap.size() - countBefore);
        }

        if (this.settings.saveEntities.getBooleanValue())
        {
            List<EntityData> entityList = this.entityLists.computeIfAbsent(regionName, key -> new ArrayList<>());
            this.totalEntities += this.readEntityData(entityList, this.existingEntities, box, selectionBox.getCorner1(), this.world);
        }
    }

    protected BlockView getBlockView(ChunkPos cPos)
    {
        SchematicSaveWorldSelection sel = this.settings.worldSelection.getValue();

        if (sel == SchematicSaveWorldSelection.SCHEMATIC_ONLY)
        {
            // TODO Swap this to a different SchematicWorldBlockView which supports the custom
            //  block state that preserves the original state information
            World world = SchematicWorldHandler.getSchematicWorld();
            return new VanillaChunkBlockView(world, world.getChunk(cPos.x, cPos.z));
        }
        else if (sel == SchematicSaveWorldSelection.VANILLA_OR_SCHEMATIC)
        {
            World world1 = this.world;
            World world2 = SchematicWorldHandler.getSchematicWorld();
            return new VanillaChunkFallbackBlockView(world1, world2, world1.getChunk(cPos.x, cPos.z), world2.getChunk(cPos.x, cPos.z));
        }
        else if (sel == SchematicSaveWorldSelection.SCHEMATIC_OR_VANILLA)
        {
            World world1 = SchematicWorldHandler.getSchematicWorld();
            World world2 = this.world;
            return new VanillaChunkFallbackBlockView(world1, world2, world1.getChunk(cPos.x, cPos.z), world2.getChunk(cPos.x, cPos.z));
        }

        return new VanillaChunkBlockView(this.world, this.world.getChunk(cPos.x, cPos.z));
    }

    protected BlockContainer createBlockContainer(SelectionBox selectionBox)
    {
        Vec3i containerSize = PositionUtils.getAbsoluteAreaSize(selectionBox);
        return this.createBlockContainer(containerSize);
    }

    protected BlockContainer createBlockContainer(Vec3i containerSize)
    {
        return this.settings.schematicType.createContainer(containerSize);
    }

    protected void readBlockData(BlockContainer container,
                                 Map<BlockPos, CompoundData> blockEntityMapOut,
                                 IntBoundingBox box,
                                 BlockPos minCorner,
                                 SchematicSaveSettings settings,
                                 BlockView blockView)
    {
        boolean saveBlocks = settings.saveBlocks.getBooleanValue();
        boolean saveBlockEntities = settings.saveBlockEntities.getBooleanValue();

        if (saveBlocks == false && saveBlockEntities == false)
        {
            return;
        }

        MutBlockPos mutPos = new MutBlockPos();

        int minCornerX = minCorner.getX();
        int minCornerY = minCorner.getY();
        int minCornerZ = minCorner.getZ();
        int errorCount = 0;
        int blockEntityCountBefore = blockEntityMapOut.size();

        for (int y = box.minY; y <= box.maxY; y++)
        {
            for (int z = box.minZ; z <= box.maxZ; z++)
            {
                for (int x = box.minX; x <= box.maxX; x++)
                {
                    mutPos.set(x, y, z);
                    BlockState state = blockView.getBlockState(mutPos);

                    if (this.shouldSaveBlock(state, mutPos) == false)
                    {
                        continue;
                    }

                    int relX = x - minCornerX;
                    int relY = y - minCornerY;
                    int relZ = z - minCornerZ;

                    if (saveBlocks)
                    {
                        container.setBlockState(relX, relY, relZ, state);
                        this.totalBlocks++;
                    }

                    if (saveBlockEntities == false || state.getBlock().hasTileEntity() == false)
                    {
                        continue;
                    }

                    if (blockView.readBlockEntityToMap(mutPos, minCorner, blockEntityMapOut) == false)
                    {
                        errorCount++;
                    }
                }
            }
        }

        this.totalBlockEntities += (blockEntityMapOut.size() - blockEntityCountBefore);

        if (errorCount > 0)
        {
            MessageDispatcher.warning("litematica.message.warn.schematic_read.failed_to_read_block_entities",
                                      errorCount, blockEntityMapOut.size());
        }
    }

    protected boolean shouldSaveBlock(BlockState state, MutBlockPos mutPos)
    {
        if (state.getBlock() == Blocks.AIR)
        {
            return false;
        }

        if ((this.obeyIgnoredBlocks && this.settings.ignoredBlocks.contains(state.getBlock())) ||
            (this.obeyIgnoredBlockStates && this.settings.ignoredBlockStates.contains(state)))
        {
            return false;
        }

        if (this.settings.exposedBlocksOnly.getBooleanValue())
        {
            return true;    // TODO
        }

        return true;
    }

    protected int readEntityData(List<EntityData> entityListOut,
                                 Set<UUID> existingEntities,
                                 IntBoundingBox box,
                                 BlockPos regionPosAbs,
                                 World world)
    {
        AxisAlignedBB bb = PositionUtils.createAABBFrom(box);
        List<Entity> entities = world.getEntitiesInAABBexcluding(null, bb, e -> (e instanceof EntityPlayer) == false);
        int regionOriginX = regionPosAbs.getX();
        int regionOriginY = regionPosAbs.getY();
        int regionOriginZ = regionPosAbs.getZ();
        int entityCount = 0;
        int errorCount = 0;

        for (Entity entity : entities)
        {
            UUID uuid = EntityWrap.getUuid(entity);

            // This entity was already saved to some region
            if (existingEntities.contains(uuid))
            {
                continue;
            }

            if (this.obeyIgnoredEntities &&
                this.settings.ignoredEntities.contains(entity.getClass()))
            {
                continue;
            }

            double x = EntityWrap.getX(entity);
            double y = EntityWrap.getY(entity);
            double z = EntityWrap.getZ(entity);

            // Only take entities whose origin is within the region
            if (x < bb.minX || x > bb.maxX ||
                y < bb.minY || y > bb.maxY ||
                z < bb.minZ || z > bb.maxZ)
            {
                continue;
            }

            try
            {
                CompoundData data = EntityWrap.writeEntityToTag(entity);

                if (data != null)
                {
                    Vec3d relPos = new Vec3d(x - regionOriginX, y - regionOriginY, z - regionOriginZ);
                    DataTypeUtils.writeVec3dToListTag(data, relPos);

                    entityListOut.add(new EntityData(relPos, data));
                    existingEntities.add(uuid);
                    entityCount++;
                }
            }
            catch (Exception e)
            {
                Litematica.LOGGER.warn("Failed to save entity {} at {}", entity, entity.getPositionVector());
                errorCount++;
            }
        }

        if (errorCount > 0)
        {
            MessageDispatcher.warning("litematica.message.warn.schematic_read.failed_to_read_entities",
                                      errorCount, entityListOut.size());
        }

        return entityCount;
    }

    protected void onDataCollected()
    {
        ImmutableMap<String, SchematicRegion> regions = this.buildSchematicRegions();
        Optional<Schematic> schematicOpt = this.settings.schematicType.createSchematicFromRegions(regions);

        if (schematicOpt.isPresent())
        {
            Schematic schematic = schematicOpt.get();
            this.setMetadataValues(schematic.getMetadata(), regions.size());
            this.schematicListener.accept(schematic);
        }
        else
        {
            MessageDispatcher.error(8000).translate("litematica.message.error.save_schematic.failed_to_create_schematic");
        }
    }

    protected ImmutableMap<String, SchematicRegion> buildSchematicRegions()
    {
        ImmutableMap.Builder<String, SchematicRegion> regionBuilder = ImmutableMap.builder();

        for (SelectionBox box : this.subRegions.values())
        {
            String regionName = box.getName();
            BlockContainer container = this.blockContainers.getOrDefault(regionName, this.createBlockContainer(Vec3i.ZERO));
            Map<BlockPos, CompoundData> blockEntityMap = this.blockEntityMaps.getOrDefault(regionName, new HashMap<>());
            Map<BlockPos, ScheduledBlockTickData> blockTickMap = this.blockTickMaps.getOrDefault(regionName, new HashMap<>());
            List<EntityData> entityList = this.entityLists.getOrDefault(regionName, new ArrayList<>());

            BlockPos relPos = box.getCorner1().subtract(this.origin);
            Vec3i size = box.getSize();
            int dv = BaseSchematic.CURRENT_MINECRAFT_DATA_VERSION;

            SchematicRegion region = new SchematicRegion(relPos, size, container, blockEntityMap, blockTickMap, entityList, dv);
            regionBuilder.put(regionName, region);
        }

        return regionBuilder.build();
    }

    protected void setMetadataValues(SchematicMetadata meta, int regionCount)
    {
        Collection<SelectionBox> boxes = this.subRegions.values();
        meta.setRegionCount(regionCount);
        meta.setEnclosingSize(PositionUtils.getEnclosingAreaSizeOfBoxes(boxes));
        meta.setTotalVolume(PositionUtils.getTotalVolume(boxes));
        meta.setTotalBlocks(this.totalBlocks);
        meta.setEntityCount(this.totalEntities);
        meta.setBlockEntityCount(this.totalBlockEntities);
        meta.setBlockTickCount(this.totalBlockTicks);

        meta.setAuthor(GameWrap.getPlayerName());
        meta.setSchematicName(this.area.getName());
        meta.setOriginalOrigin(this.origin);

        meta.setTimeCreated(System.currentTimeMillis());
        meta.setMinecraftVersion(MinecraftVersion.CURRENT_VERSION);
    }

    @Override
    protected void onStop()
    {
        if (this.finished == false)
        {
            MessageDispatcher.warning().translate("litematica.message.error.schematic_save.interrupted");
        }

        InfoHud.getInstance().removeInfoHudRenderer(this, false);

        this.notifyListener();
    }
}
