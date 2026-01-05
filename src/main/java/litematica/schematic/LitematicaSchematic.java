package litematica.schematic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import com.google.common.collect.ImmutableMap;

import malilib.overlay.message.MessageDispatcher;
import malilib.util.data.Constants;
import malilib.util.data.tag.CompoundData;
import malilib.util.data.tag.DataView;
import malilib.util.data.tag.ListData;
import malilib.util.data.tag.LongArrayData;
import malilib.util.data.tag.util.DataTypeUtils;
import malilib.util.game.MinecraftVersion;
import malilib.util.position.BlockPos;
import malilib.util.position.Vec3d;
import malilib.util.position.Vec3i;
import malilib.util.world.ScheduledBlockTickData;
import litematica.Litematica;
import litematica.schematic.container.ArrayBlockContainer;
import litematica.schematic.container.BlockContainer;
import litematica.schematic.container.TightLongBackedIntArray;
import litematica.schematic.data.EntityData;
import litematica.util.PositionUtils;

public class LitematicaSchematic extends BaseSchematic
{
    public static final String FILE_NAME_EXTENSION = "litematic";
    public static final int CURRENT_SCHEMATIC_VERSION = 4;

    public LitematicaSchematic()
    {
        super(SchematicType.LITEMATICA);
    }

    @Override
    public boolean read(DataView data)
    {
        if (isValidData(data) == false)
        {
            return false;
        }

        int version = data.getIntOrDefault("Version", -1);
        this.minecraftDataVersion = data.getIntOrDefault("MinecraftDataVersion", -1);

        /* This can't happen after the isValid() check
        if (version == -1)
        {
            MessageDispatcher.error("litematica.error.schematic_read.no_version");
        }
        */

        if (version == 0 || version > CURRENT_SCHEMATIC_VERSION)
        {
            MessageDispatcher.warning("litematica.error.schematic_read.unknown_schematic_version",
                                      version, CURRENT_SCHEMATIC_VERSION);
        }

        if (this.minecraftDataVersion > CURRENT_MINECRAFT_DATA_VERSION)
        {
            MessageDispatcher.warning("litematica.error.schematic_read.future_data_version",
                                      this.minecraftDataVersion, CURRENT_MINECRAFT_DATA_VERSION);
        }

        this.regions = this.readRegions(data, version, this.minecraftDataVersion);
        this.metadata = createAndReadMetadata(data).orElse(new SchematicMetadata());
        this.enclosingSize = this.metadata.getEnclosingSize();

        return true;
    }

    @Override
    public Optional<CompoundData> write()
    {
        CompoundData data = new CompoundData();

        // Note: The Metadata and other small bits should preferably be written before
        // the main Regions tag, so that a faster partial read from disk is possible
        // to just read the Metadata for the schematic browser info panel.
        data.putInt("Version", CURRENT_SCHEMATIC_VERSION);
        data.putInt("MinecraftDataVersion", this.minecraftDataVersion);
        data.put("Metadata", this.metadata.write(new CompoundData()));

        if (this.writeRegions(data) == false)
        {
            return Optional.empty();
        }

        return Optional.of(data);
    }

    protected ImmutableMap<String, SchematicRegion> readRegions(DataView data, int version, int mainDataVersion)
    {
        ImmutableMap.Builder<String, SchematicRegion> builder = ImmutableMap.builder();

        DataView regionsTag = data.getCompound("Regions");

        for (String regionName : regionsTag.getKeys())
        {
            if (regionsTag.contains(regionName, Constants.NBT.TAG_COMPOUND) == false)
            {
                MessageDispatcher.error("litematica.error.schematic_read.litematica.invalid_tag_in_regions", regionName);
                continue;
            }

            DataView regionTag = regionsTag.getCompound(regionName);
            BlockPos regionPos = DataTypeUtils.readBlockPos(regionTag.getCompound("Position"));
            BlockPos regionSize = DataTypeUtils.readBlockPos(regionTag.getCompound("Size"));

            if (regionPos == null || regionSize == null)
            {
                MessageDispatcher.error("litematica.error.schematic_read.litematica.missing_pos_or_size", regionName);
                continue;
            }

            ListData beList = regionTag.getList("TileEntities", Constants.NBT.TAG_COMPOUND);
            ListData entityListData = regionTag.getList("Entities", Constants.NBT.TAG_COMPOUND);
            Map<BlockPos, CompoundData> blockEntityMap = new HashMap<>();
            Map<BlockPos, ScheduledBlockTickData> blockTickMap = new HashMap<>();
            List<EntityData> entityList = new ArrayList<>();
            int beErrorCount = 0;
            int tickErrorCount = 0;
            int entityErrorCount = 0;

            if (version >= 2)
            {
                beErrorCount = this.readBlockEntities_v2(beList, blockEntityMap);
                entityErrorCount = this.readEntities_v2(entityListData, entityList);
            }
            else if (version == 1)
            {
                beErrorCount = this.readBlockEntities_v1(beList, blockEntityMap);
                entityErrorCount = this.readEntities_v1(entityListData, entityList);
            }
            else
            {
                MessageDispatcher.error("litematica.error.schematic_read.litematica.version_0", regionName);
            }

            if (version >= 3)
            {
                ListData tickListData = regionTag.getList("PendingBlockTicks", Constants.NBT.TAG_COMPOUND);
                tickErrorCount = this.readBlockTicks_v3(tickListData, blockTickMap);
            }

            if (entityErrorCount > 0)
            {
                MessageDispatcher.warning("litematica.message.warn.schematic_read.litematica.entity_errors",
                                          entityErrorCount, entityList.size(), regionName);
            }

            if (beErrorCount > 0)
            {
                MessageDispatcher.warning("litematica.message.warn.schematic_read.litematica.block_entity_errors",
                                          beErrorCount, blockEntityMap.size(), regionName);
            }

            if (tickErrorCount > 0)
            {
                MessageDispatcher.warning("litematica.message.warn.schematic_read.litematica.block_tick_errors",
                                          tickErrorCount, blockTickMap.size(), regionName);
            }

            Vec3i size = PositionUtils.getAbsoluteSize(regionSize);
            ListData paletteTag = regionTag.getList("BlockStatePalette", Constants.NBT.TAG_COMPOUND);
            int paletteSize = paletteTag.size();
            long[] blockDataArray = regionTag.getLongArray("BlockStates");

            if (blockDataArray == null || blockDataArray.length == 0)
            {
                MessageDispatcher.error("litematica.error.schematic_read.litematica.invalid_block_data_array", regionName);
                continue;
            }

            int dataVersion = regionTag.getIntOrDefault("DataVersion", mainDataVersion);
            ArrayBlockContainer container = createContainerFromData(size, paletteSize, blockDataArray);

            if (container == null)
            {
                MessageDispatcher.error("litematica.error.schematic_read.litematica.region_container", regionName);
                continue;
            }

            if (readPaletteFromLitematicaFormatTag(paletteTag, container.getPalette(), dataVersion) == false)
            {
                MessageDispatcher.error("litematica.error.schematic_read.litematica.palette_read_failed", regionName);
                continue;
            }

            SchematicRegion region = new SchematicRegion(regionPos, regionSize, container, blockEntityMap,
                                                         blockTickMap, entityList, dataVersion);

            builder.put(regionName, region);
        }

        return builder.build();
    }

    protected int readBlockEntities_v2(ListData listDataIn, Map<BlockPos, CompoundData> blockEntityMapOut)
    {
        return this.readBlockEntities(listDataIn, blockEntityMapOut);
    }

    protected int readBlockEntities_v1(ListData list, Map<BlockPos, CompoundData> blockEntityMapOut)
    {
        final int size = list.size();
        int errorCount = 0;

        for (int i = 0; i < size; ++i)
        {
            CompoundData wrapperData = list.getCompoundAt(i);
            CompoundData beData = wrapperData.getCompound("TileNBT").copy();

            // Note: This within-schematic relative position is not inside the tile tag!
            BlockPos pos = DataTypeUtils.readBlockPos(wrapperData);

            if (pos != null && beData.isEmpty() == false)
            {
                blockEntityMapOut.put(pos, beData);
            }
            else
            {
                errorCount++;
            }
        }

        return errorCount;
    }

    protected int readBlockTicks_v3(ListData list, Map<BlockPos, ScheduledBlockTickData> tickMapOut)
    {
        final int size = list.size();
        long tickIdCounter = 0;
        int errorCount = 0;

        for (int i = 0; i < size; ++i)
        {
            CompoundData tag = list.getCompoundAt(i);

            if (tag.contains("Block", Constants.NBT.TAG_STRING) &&
                tag.contains("Time", Constants.NBT.TAG_ANY_NUMERIC)) // XXX these were accidentally saved as longs in version 3
            {
                String blockName = tag.getString("Block");
                BlockPos pos = DataTypeUtils.readBlockPos(tag);
                int priority = tag.getInt("Priority");
                long delay = tag.getInt("Time");
                long tickId = tag.getLongOrDefault("TickId", tickIdCounter++);
                // Note: the time is a relative delay at this point
                ScheduledBlockTickData entry = new ScheduledBlockTickData(pos, blockName, priority, delay, tickId);

                tickMapOut.put(pos, entry);
            }
            else
            {
                errorCount++;
            }
        }

        return errorCount;
    }

    protected int readEntities_v2(ListData listDataIn, List<EntityData> entityListOut)
    {
        return this.readEntities(listDataIn, entityListOut);
    }

    protected int readEntities_v1(ListData list, List<EntityData> entityListOut)
    {
        final int size = list.size();
        int errorCount = 0;

        for (int i = 0; i < size; ++i)
        {
            CompoundData wrapperData = list.getCompoundAt(i);
            Vec3d pos = DataTypeUtils.readVec3d(wrapperData);
            CompoundData entityData = wrapperData.getCompound("EntityData").copy();

            if (pos != null && entityData.isEmpty() == false)
            {
                // Update the correct position to the Entity NBT, where it is stored in version 2 - not needed in memory yet
                //DataTypeUtils.writeVec3dToListTag(entityData, pos);
                entityListOut.add(new EntityData(pos, entityData));
            }
            else
            {
                errorCount++;
            }
        }

        return errorCount;
    }

    @Nullable
    public static long[] getAsTightLongBackedArray(BlockContainer container)
    {
        if (container instanceof ArrayBlockContainer)
        {
            ArrayBlockContainer arrayContainer = (ArrayBlockContainer) container;

            if (arrayContainer.getIntStorage() instanceof TightLongBackedIntArray)
            {
                return ((TightLongBackedIntArray) arrayContainer.getIntStorage()).getBackingLongArray();
            }
        }

        int bits = ArrayBlockContainer.getRequiredBitWidth(container.getPalette().getSize());
        TightLongBackedIntArray storage = new TightLongBackedIntArray(bits, container.getTotalVolume());
        ArrayBlockContainer arrayContainer = new ArrayBlockContainer(container.getSize(), storage);
        BaseSchematic.copyContainerContents(container, arrayContainer);

        return storage.getBackingLongArray();
    }

    protected boolean writeRegions(CompoundData data)
    {
        if (this.getRegions().isEmpty())
        {
            MessageDispatcher.error("litematica.message.error.schematic_save.no_regions");
            return false;
        }

        CompoundData regionsTag = new CompoundData();

        for (Map.Entry<String, SchematicRegion> entry : this.getRegions().entrySet())
        {
            String regionName = entry.getKey();
            SchematicRegion region = entry.getValue();
            BlockContainer container = region.getBlockContainer();

            Map<BlockPos, CompoundData> blockEntityMap = region.getBlockEntityMap();
            Map<BlockPos, ScheduledBlockTickData> blockTicksMap = region.getBlockTickMap();
            List<EntityData> entityList = region.getEntityList();

            CompoundData regionTag = new CompoundData();

            regionTag.putInt("DataVersion", region.getMinecraftDataVersion());
            regionTag.put("BlockStatePalette", writePaletteToLitematicaFormatTag(container.getPalette()));
            regionTag.put("BlockStates", new LongArrayData(getAsTightLongBackedArray(container)));

            if (blockEntityMap.isEmpty() == false)
            {
                regionTag.put("TileEntities", this.getBlockEntitiesAsListData(blockEntityMap));
            }

            if (blockTicksMap.isEmpty() == false)
            {
                regionTag.put("PendingBlockTicks", this.writeBlockTicksToListData(blockTicksMap));
            }

            // The entity list will not exist, if saveEntities is false when creating the schematic
            if (entityList.isEmpty() == false)
            {
                regionTag.put("Entities", this.getEntitiesAsListData(entityList));
            }

            regionTag.put("Position", DataTypeUtils.createVec3iTag(region.getRelativePosition()));
            regionTag.put("Size", DataTypeUtils.createVec3iTag(region.getSize()));

            regionsTag.put(regionName, regionTag);
        }

        data.put("Regions", regionsTag);

        return true;
    }

    protected ListData writeBlockTicksToListData(Map<BlockPos, ScheduledBlockTickData> blockTicksMap)
    {
        ListData list = new ListData(Constants.NBT.TAG_COMPOUND);

        if (blockTicksMap.isEmpty())
        {
            return list;
        }

        for (ScheduledBlockTickData entry : blockTicksMap.values())
        {
            CompoundData tag = new CompoundData();

            tag.putString("Block", entry.blockName);
            tag.putInt("Priority", entry.priority);
            tag.putInt("Time", (int) entry.delay);
            tag.putLong("TickId", entry.tickId);
            DataTypeUtils.putVec3i(tag, entry.pos);

            list.add(tag);
        }

        return list;
    }

    public static boolean isValidData(DataView data)
    {
        return data.contains("Metadata", Constants.NBT.TAG_COMPOUND) &&
               data.contains("Regions", Constants.NBT.TAG_COMPOUND) &&
               data.contains("Version", Constants.NBT.TAG_INT);
    }

    public static BlockContainer createDefaultBlockContainer(Vec3i containerSize)
    {
        TightLongBackedIntArray storage = new TightLongBackedIntArray(2, PositionUtils.getAreaVolume(containerSize));
        return new ArrayBlockContainer(containerSize, storage);
    }

    public static Optional<SchematicMetadata> createAndReadMetadata(DataView data)
    {
        if (isValidData(data) == false)
        {
            return Optional.empty();
        }

        SchematicMetadata metadata = new SchematicMetadata();
        metadata.read(data.getCompound("Metadata"));

        if (metadata.getMinecraftVersion().equals(MinecraftVersion.MC_UNKNOWN))
        {
            metadata.setMinecraftVersion(MinecraftVersion.getOrCreateVersionFromDataVersion(data.getInt("MinecraftDataVersion")));
        }

        if (metadata.getSchematicVersion() <= 0)
        {
            metadata.setSchematicVersion(data.getInt("Version"));
        }

        if (metadata.getRegionCount() <= 0)
        {
            metadata.setRegionCount(data.getCompound("Regions").size());
        }

        if (metadata.getEntityCount() < 0 || metadata.getBlockEntityCount() < 0)
        {
            DataView regionsTag = data.getCompound("Regions");
            int entityCount = 0;
            long blockEntityCount = 0;

            for (String key : regionsTag.getKeys())
            {
                CompoundData tag = regionsTag.getCompound(key);
                entityCount += tag.getList("Entities", Constants.NBT.TAG_COMPOUND).size();
                blockEntityCount += tag.getList("TileEntities", Constants.NBT.TAG_COMPOUND).size();
            }

            metadata.setEntityCount(entityCount);
            metadata.setBlockEntityCount(blockEntityCount);
        }

        return Optional.of(metadata);
    }

    public static Optional<Schematic> fromData(DataView data)
    {
        LitematicaSchematic schematic = new LitematicaSchematic();

        if (schematic.read(data))
        {
            return Optional.of(schematic);
        }

        return Optional.empty();
    }

    public static Optional<Schematic> fromRegions(ImmutableMap<String, SchematicRegion> regions)
    {
        if (regions.size() < 1)
        {
            return Optional.empty();
        }

        LitematicaSchematic schematic = new LitematicaSchematic();

        schematic.regions = regions;
        schematic.enclosingSize = PositionUtils.getEnclosingAreaSize(regions.values());
        schematic.minecraftDataVersion = CURRENT_MINECRAFT_DATA_VERSION;
        schematic.metadata.setSchematicVersion(CURRENT_SCHEMATIC_VERSION);

        return Optional.of(schematic);
    }

    public static ArrayBlockContainer createContainerFromData(Vec3i size, int paletteSize, @Nullable long[] blockStates)
    {
        int entryWidthBits = ArrayBlockContainer.getRequiredBitWidth(paletteSize);
        long volume = PositionUtils.getAreaVolume(size);

        try
        {
            TightLongBackedIntArray storage = new TightLongBackedIntArray(entryWidthBits, volume, blockStates);
            return new ArrayBlockContainer(size, storage);
            //container.palette = createPalette(bits, container);
        }
        catch (Exception e)
        {
            Litematica.LOGGER.error("LitematicaSchematic#createContainerFromData: volume: {}, entryWidthBits: {}, blockStates.length: {}",
                                    volume, entryWidthBits, blockStates.length);
            return null;
        }
    }
}
