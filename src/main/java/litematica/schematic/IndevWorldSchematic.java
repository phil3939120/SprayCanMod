package litematica.schematic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import malilib.overlay.message.MessageDispatcher;
import malilib.util.ListUtils;
import malilib.util.data.Constants;
import malilib.util.data.tag.CompoundData;
import malilib.util.data.tag.DataView;
import malilib.util.data.tag.FloatData;
import malilib.util.data.tag.ListData;
import malilib.util.data.tag.ShortData;
import malilib.util.position.BlockPos;
import malilib.util.position.Vec3d;
import malilib.util.position.Vec3i;
import malilib.util.world.BlockState;
import litematica.Litematica;
import litematica.schematic.container.ArrayBlockContainer;
import litematica.schematic.container.BlockContainer;
import litematica.schematic.data.EntityData;
import litematica.util.PositionUtils;

public class IndevWorldSchematic extends BaseSchematic
{
    public static final String FILE_NAME_EXTENSION = "dat";

    @Nullable protected ListData originalSpawnCoordinatesTag;
    @Nullable protected CompoundData originalEnvironmentTag;
    @Nullable protected CompoundData originalPlayerEntityTag;

    public IndevWorldSchematic()
    {
        super(SchematicType.INDEV_WORLD);
    }

    @Override
    public boolean read(DataView data)
    {
        if (isValidData(data) == false)
        {
            return false;
        }

        Vec3i size = readSizeFromTag(data);
        BlockContainer container = this.readBlocks(data, size);

        if (container == null)
        {
            return false;
        }

        Map<BlockPos, CompoundData> blockEntityMap = new HashMap<>();
        List<EntityData> entityList = new ArrayList<>();

        int beErrorCount = this.readBlockEntitiesIndev(data.getList("TileEntities", Constants.NBT.TAG_COMPOUND), blockEntityMap);
        int entityErrorCount = this.readEntitiesIndev(data.getList("Entities", Constants.NBT.TAG_COMPOUND), entityList);

        if (beErrorCount > 0)
        {
            MessageDispatcher.warning("litematica.message.warn.schematic_read.failed_to_read_block_entities",
                                      beErrorCount, blockEntityMap.size());
        }

        if (entityErrorCount > 0)
        {
            MessageDispatcher.warning("litematica.message.warn.schematic_read.failed_to_read_entities",
                                      entityErrorCount, entityList.size());
        }

        DataView mapTag = data.getCompound("Map");
        this.originalSpawnCoordinatesTag = mapTag.getList("Spawn", Constants.NBT.TAG_SHORT);
        this.originalEnvironmentTag = data.getCompound("Environment");

        this.metadata = createAndReadMetadata(data).orElse(new SchematicMetadata());
        this.minecraftDataVersion = data.getIntOrDefault("DataVersion", this.metadata.getMinecraftVersion().dataVersion);
        SchematicRegion region = new SchematicRegion(BlockPos.ORIGIN, size, container, blockEntityMap,
                                                     new HashMap<>(), entityList, this.minecraftDataVersion);

        this.enclosingSize = size;
        this.regions = ImmutableMap.of(this.metadata.getSchematicName(), region);

        return true;
    }

    @Override
    public Optional<CompoundData> write()
    {
        CompoundData data = new CompoundData();
        int regionCount = this.regions.size();

        if (regionCount != 1)
        {
            MessageDispatcher.error("litematica.message.error.schematic_save.wrong_region_count", regionCount, 1);
            return Optional.empty();
        }

        SchematicRegion region = ListUtils.getFirstEntry(this.regions.values());
        Vec3i size = PositionUtils.getAbsoluteSize(region.getSize());

        // TODO Are these the real hard limits? Or does it break after 512 horizontal size?
        if (size.getX() > 1024 || size.getY() > 128 || size.getZ() > 1024)
        {
            MessageDispatcher.error("litematica.message.error.schematic_save.indev.too_large");
            return Optional.empty();
        }

        if (this.originalEnvironmentTag != null && this.originalEnvironmentTag.size() > 0)
        {
            data.put("Environment", this.originalEnvironmentTag.copy());
        }
        else
        {
            CompoundData envTag = new CompoundData();
            envTag.putInt("CloudColor", 16777215);
            envTag.putShort("CloudHeight", (short) 66);
            envTag.putInt("FogColor", 16777215);
            envTag.putByte("SkyBrightness", (byte) 15);
            envTag.putInt("SkyColor", 10079487);
            envTag.putShort("SurroundingGroundHeight", (short) 23);
            envTag.putByte("SurroundingGroundType", (byte) 2);
            envTag.putShort("SurroundingWaterHeight", (short) 32);
            envTag.putByte("SurroundingWaterType", (byte) 8);
            envTag.putShort("TimeOfDay", (short) 0);

            data.put("Environment", envTag);
        }

        CompoundData mapTag = new CompoundData();
        mapTag.putShort("Width", (short) size.getX());
        mapTag.putShort("Height", (short) size.getY());
        mapTag.putShort("Length", (short) size.getZ());

        ListData spawnCoords;

        if (this.originalSpawnCoordinatesTag != null && this.originalSpawnCoordinatesTag.size() == 3)
        {
            spawnCoords = this.originalSpawnCoordinatesTag.copy();
        }
        else
        {
            spawnCoords = new ListData(Constants.NBT.TAG_SHORT);
            spawnCoords.add(new ShortData((short) 64));
            spawnCoords.add(new ShortData((short) 64));
            spawnCoords.add(new ShortData((short) 64));
        }

        mapTag.put("Spawn", spawnCoords);

        int blockErrorCount = this.writeBlocksToTag(mapTag, size, region.getBlockContainer());

        if (blockErrorCount > 0)
        {
            MessageDispatcher.warning("litematica.message.warn.schematic_write.indev_world.block_write_errors",
                                      blockErrorCount);
        }

        CompoundData aboutTag = new CompoundData();
        aboutTag.putString("Author", this.metadata.getAuthor());
        aboutTag.putLong("CreatedOn", this.metadata.getTimeCreated());
        aboutTag.putString("Name", this.metadata.getSchematicName());

        data.put("About", aboutTag);
        data.put("Map", mapTag);
        data.put("TileEntities", this.getBlockEntitiesAsListDataIndev(region.getBlockEntityMap()));
        data.put("Entities", this.getEntitiesAsListDataIndev(region.getEntityList()));

        return Optional.of(data);
    }

    protected BlockContainer readBlocks(DataView data, Vec3i size)
    {
        Int2ObjectOpenHashMap<BlockState> mapping = SchematicaSchematic.createRegistryBasedPalette();
        int safePaletteSize = mapping.size() * 16;  // Max 16 metadata values per block
        int entryWidthBits = ArrayBlockContainer.getRequiredBitWidth(safePaletteSize);
        ArrayBlockContainer container = new ArrayBlockContainer(size, entryWidthBits);

        DataView mapTag = data.getCompound("Map");
        this.readBlocksFromTag(mapTag, size, container, mapping);

        return container;
    }

    protected void readBlocksFromTag(DataView data,
                                     Vec3i size,
                                     ArrayBlockContainer container,
                                     Int2ObjectOpenHashMap<BlockState> mapping)
    {
        final int sizeX = size.getX();
        final int sizeY = size.getY();
        final int sizeZ = size.getZ();
        final byte[] blockIdsArr = data.getByteArray("Blocks");
        final byte[] metaArr = data.getByteArray("Data");
        final int numBlocks = sizeX * sizeY * sizeZ;

        if (numBlocks != blockIdsArr.length)
        {
            MessageDispatcher.error("litematica.message.error.schematic_read.schematica.schematic.invalid_block_array_size",
                                    blockIdsArr.length, numBlocks, sizeX, sizeY, sizeZ);
            return;
        }

        if (numBlocks != metaArr.length)
        {
            MessageDispatcher.error("litematica.message.error.schematic_read.schematica.schematic.invalid_metadata_array_size",
                                    metaArr.length, numBlocks);
            return;
        }

        int errorCount = this.readBlocksIndev(blockIdsArr, metaArr, size, container, mapping);

        if (errorCount > 0)
        {
            MessageDispatcher.error("litematica.message.error.schematic_read.schematica.block_errors",
                                    errorCount, numBlocks);
        }
    }

    @SuppressWarnings("deprecation")
    protected int readBlocksIndev(byte[] blockIdsByte,
                                  byte[] metaArr,
                                  Vec3i size,
                                  ArrayBlockContainer container,
                                  Int2ObjectOpenHashMap<BlockState> mapping)
    {
        final int sizeX = size.getX();
        final int sizeY = size.getY();
        final int sizeZ = size.getZ();
        int blockId;
        int metaValue;
        int index = 0;
        int errorCount = 0;

        for (int y = 0; y < sizeY; y++)
        {
            for (int z = 0; z < sizeZ; z++)
            {
                for (int x = 0; x < sizeX; x++)
                {
                    blockId = (blockIdsByte[index] & 0xFF);  // Need to mask because the array type is signed...
                    metaValue = (metaArr[index] >> 4) & 0xF; // Upper 4 bits is meta, lower 4 bits is light

                    BlockState state = mapping.get(blockId);

                    if (state != null)
                    {
                        state = BlockState.of(state.getBlock().getStateFromMeta(metaValue));
                        container.setBlockState(x, y, z, state);
                    }
                    else
                    {
                        Litematica.LOGGER.warn("readBlocksIndev: Failed to read block with id = {}, meta = {}", blockId, metaValue);
                        errorCount++;
                    }

                    index++;
                }
            }
        }

        return errorCount;
    }


    protected int readBlockEntitiesIndev(ListData listDataIn, Map<BlockPos, CompoundData> blockEntityMapOut)
    {
        final int size = listDataIn.size();
        int errorCount = 0;

        for (int i = 0; i < size; ++i)
        {
            CompoundData beData = listDataIn.getCompoundAt(i).copy();

            if (beData.isEmpty())
            {
                ++errorCount;
                continue;
            }

            if (beData.contains("Pos", Constants.NBT.TAG_INT) == false)
            {
                ++errorCount;
                continue;
            }

            // TODO Add proper cross-MC-version data conversion
            String id = "minecraft:" + beData.getString("id").toLowerCase(Locale.ROOT);
            beData.putString("id", id);

            int posInt = beData.getInt("Pos");
            beData.remove("Pos");

            int x = posInt & 0x3FF;
            int y = (posInt >> 10) & 0x3FF;
            int z = (posInt >> 20) & 0x3FF;
            BlockPos pos = new BlockPos(x, y, z);

            blockEntityMapOut.put(pos, beData);
        }

        return errorCount;
    }

    protected int readEntitiesIndev(ListData listDataIn, List<EntityData> entityListOut)
    {
        final int size = listDataIn.size();
        int errorCount = 0;

        for (int i = 0; i < size; ++i)
        {
            CompoundData entityData = listDataIn.getCompoundAt(i).copy();

            if (entityData.containsList("Pos", Constants.NBT.TAG_FLOAT) == false)
            {
                ++errorCount;
                continue;
            }

            ListData list = entityData.getList("Pos", Constants.NBT.TAG_FLOAT);

            // TODO Add proper cross-MC-version data conversion
            String id = entityData.getString("id");

            // Skip the player
            if (id.equals("LocalPlayer"))
            {
                this.originalPlayerEntityTag = entityData.copy();
                continue;
            }

            if (list.size() != 3)
            {
                ++errorCount;
                continue;
            }

            id = "minecraft:" + id.toLowerCase(Locale.ROOT);
            entityData.putString("id", id);

            Vec3d pos = new Vec3d(list.getFloatAt(0), list.getFloatAt(1), list.getFloatAt(2));
            entityListOut.add(new EntityData(pos, entityData));
        }

        return errorCount;
    }

    protected int writeBlocksToTag(CompoundData data, Vec3i size, BlockContainer container)
    {
        final int sizeX = size.getX();
        final int sizeY = size.getY();
        final int sizeZ = size.getZ();

        final int numBlocks = sizeX * sizeY * sizeZ;
        final byte[] blockIdArr = new byte[numBlocks];
        final byte[] metaArr = new byte[numBlocks];
        int index = 0;
        int errorCount = 0;

        for (int y = 0; y < sizeY; y++)
        {
            for (int z = 0; z < sizeZ; z++)
            {
                for (int x = 0; x < sizeX; x++)
                {
                    BlockState state = container.getBlockState(x, y, z);
                    int blockId = state.getOldBlockId();

                    if (blockId <= 255)
                    {
                        int meta = state.getBlock().getMetaFromState(state.vanillaState()) & 0xF;

                        blockIdArr[index] = (byte) (blockId & 0xFF);
                        metaArr[index] = (byte) (meta << 4);    // upper 4 bits is meta, lower 4 bits is light
                    }
                    else
                    {
                        ++errorCount;
                    }

                    index++;
                }
            }
        }

        data.putByteArray("Blocks", blockIdArr);
        data.putByteArray("Data", metaArr);

        return errorCount;
    }

    protected ListData getBlockEntitiesAsListDataIndev(Map<BlockPos, CompoundData> blockEntityMap)
    {
        ListData list = new ListData(Constants.NBT.TAG_COMPOUND);

        for (Map.Entry<BlockPos, CompoundData> entry : blockEntityMap.entrySet())
        {
            CompoundData tag = entry.getValue().copy();
            BlockPos pos = entry.getKey();
            int posInt = ((pos.getZ() & 0x3FF) << 20) | ((pos.getY() & 0x3FF) << 10) | (pos.getX() & 0x3FF);

            // TODO Add proper cross-MC-version data conversion
            String id = tag.getString("id");

            if (id.startsWith("minecraft:"))
            {
                id = id.substring(10, 11).toUpperCase(Locale.ROOT) + id.substring(11);
            }

            tag.putString("id", id);
            tag.putInt("Pos", posInt);

            list.add(tag);
        }

        return list;
    }

    protected ListData getEntitiesAsListDataIndev(List<EntityData> entityList)
    {
        ListData list = new ListData(Constants.NBT.TAG_COMPOUND);

        if (this.originalPlayerEntityTag != null)
        {
            list.add(this.originalPlayerEntityTag.copy());
        }

        for (EntityData entityData : entityList)
        {
            CompoundData tag = entityData.data.copy();
            ListData posTag = new ListData(Constants.NBT.TAG_FLOAT);

            posTag.add(new FloatData((float) entityData.pos.x));
            posTag.add(new FloatData((float) entityData.pos.y));
            posTag.add(new FloatData((float) entityData.pos.z));

            // TODO Add proper cross-MC-version data conversion
            String id = tag.getString("id");

            if (id.startsWith("minecraft:"))
            {
                id = id.substring(10, 11).toUpperCase(Locale.ROOT) + id.substring(11);
            }

            tag.putString("id", id);
            tag.put("Pos", posTag);

            list.add(tag);
        }

        return list;
    }

    public static boolean isValidData(DataView data)
    {
        if (data.contains("Environment", Constants.NBT.TAG_COMPOUND) == false ||
            data.contains("Map", Constants.NBT.TAG_COMPOUND) == false)
        {
            return false;
        }

        DataView mapTag = data.getCompound("Map");

        if (mapTag.contains("Width", Constants.NBT.TAG_SHORT) &&
            mapTag.contains("Height", Constants.NBT.TAG_SHORT) &&
            mapTag.contains("Length", Constants.NBT.TAG_SHORT) &&
            mapTag.contains("Blocks", Constants.NBT.TAG_BYTE_ARRAY))
        {
            return isSizeValid(readSizeFromTag(data));
        }

        return false;
    }

    protected static Vec3i readSizeFromTag(DataView tag)
    {
        DataView mapTag = tag.getCompound("Map");

        return new Vec3i(mapTag.getShort("Width"),
                         mapTag.getShort("Height"),
                         mapTag.getShort("Length"));
    }

    public static BlockContainer createDefaultBlockContainer(Vec3i containerSize)
    {
        return new ArrayBlockContainer(containerSize, 8);
    }

    public static Optional<SchematicMetadata> createAndReadMetadata(DataView data)
    {
        if (isValidData(data) == false)
        {
            return Optional.empty();
        }

        SchematicMetadata metadata = new SchematicMetadata();
        Vec3i size = readSizeFromTag(data);

        metadata.setEnclosingSize(size);
        metadata.setTotalVolume((long) size.getX() * size.getY() * size.getZ());
        metadata.setEntityCount(data.getList("Entities", Constants.NBT.TAG_COMPOUND).size());
        metadata.setBlockEntityCount(data.getList("TileEntities", Constants.NBT.TAG_COMPOUND).size());

        if (data.contains("About", Constants.NBT.TAG_COMPOUND))
        {
            CompoundData aboutTag = data.getCompound("About");

            metadata.setTimeCreated(aboutTag.getLong("CreatedOn"));
            metadata.setAuthor(aboutTag.getString("Author"));
            metadata.setSchematicName(aboutTag.getString("Name"));
        }

        return Optional.of(metadata);
    }

    public static Optional<Schematic> fromData(DataView data)
    {
        IndevWorldSchematic schematic = new IndevWorldSchematic();

        if (schematic.read(data))
        {
            return Optional.of(schematic);
        }

        return Optional.empty();
    }

    public static Optional<Schematic> fromRegions(ImmutableMap<String, SchematicRegion> regions)
    {
        Optional<SchematicRegion> regionOpt = getOrConvertToSingleRegion(regions, SchematicType.INDEV_WORLD);

        if (regionOpt.isPresent() == false)
        {
            return Optional.empty();
        }

        SchematicRegion region = regionOpt.get();
        IndevWorldSchematic schematic = new IndevWorldSchematic();

        schematic.regions = regions;
        schematic.enclosingSize = PositionUtils.getAbsoluteSize(region.getSize());
        schematic.minecraftDataVersion = region.getMinecraftDataVersion();

        return Optional.of(schematic);
    }
}
