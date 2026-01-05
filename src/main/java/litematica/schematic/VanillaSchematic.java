package litematica.schematic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import malilib.overlay.message.MessageDispatcher;
import malilib.util.ListUtils;
import malilib.util.data.Constants;
import malilib.util.data.palette.Palette;
import malilib.util.data.tag.CompoundData;
import malilib.util.data.tag.DataView;
import malilib.util.data.tag.ListData;
import malilib.util.data.tag.util.DataTypeUtils;
import malilib.util.game.MinecraftVersion;
import malilib.util.position.BlockPos;
import malilib.util.position.Vec3d;
import malilib.util.position.Vec3i;
import malilib.util.world.BlockState;
import litematica.schematic.container.BlockContainer;
import litematica.schematic.container.NonResizingHashMapPalette;
import litematica.schematic.container.SparseBlockContainer;
import litematica.schematic.data.EntityData;
import litematica.util.PositionUtils;

public class VanillaSchematic extends BaseSchematic
{
    public static final String FILE_NAME_EXTENSION = "nbt";

    public VanillaSchematic()
    {
        super(SchematicType.VANILLA);
    }

    @Override
    public boolean read(DataView data)
    {
        if (isValidData(data) == false)
        {
            return false;
        }

        Vec3i size = readSizeFromTag(data);
        this.metadata = createAndReadMetadata(data).orElse(new SchematicMetadata());
        this.minecraftDataVersion = data.getIntOrDefault("DataVersion", this.metadata.getMinecraftVersion().dataVersion);

        BlockContainer container = new SparseBlockContainer(size);
        Map<BlockPos, CompoundData> blockEntityMap = new HashMap<>();
        int errorCount = this.readBlocks(data, container, blockEntityMap, this.minecraftDataVersion);

        if (errorCount > 0)
        {
            MessageDispatcher.warning("litematica.message.warn.schematic_read.failed_to_read_blocks",
                                      errorCount, container.getTotalBlockCount());
        }

        List<EntityData> entityList = new ArrayList<>();
        errorCount = this.readEntities(data, entityList);

        if (errorCount > 0)
        {
            MessageDispatcher.warning("litematica.message.warn.schematic_read.failed_to_read_entities",
                                      errorCount, entityList.size());
        }

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
        int regionCount = this.getRegions().size();

        if (regionCount != 1)
        {
            MessageDispatcher.error("litematica.message.error.schematic_save.wrong_region_count", regionCount, 1);
            return Optional.empty();
        }

        data.put("Metadata", this.metadata.write(new CompoundData()));
        data.putString("author", this.metadata.getAuthor());
        data.putInt("DataVersion", this.minecraftDataVersion);
        DataTypeUtils.writeVec3iToListTag(data, "size", this.enclosingSize);

        SchematicRegion region = ListUtils.getFirstEntry(this.getRegions().values());
        this.writeBlocks(data, region.getBlockContainer(), region.getBlockEntityMap());
        this.writeEntities(data, region.getEntityList());

        return Optional.of(data);
    }

    protected int readBlocks(DataView data,
                             BlockContainer container,
                             Map<BlockPos, CompoundData> blockEntityMapOut,
                             int dataVersion)
    {
        int errorCount = 0;

        ListData paletteData = data.getList("palette", Constants.NBT.TAG_COMPOUND);
        Palette<BlockState> palette = container.getPalette();

        if (readPaletteFromLitematicaFormatTag(paletteData, palette, dataVersion) == false)
        {
            MessageDispatcher.error("litematica.message.error.schematic_read.vanilla.failed_to_read_palette");
            return -1;
        }

        ListData blockList = data.getList("blocks", Constants.NBT.TAG_COMPOUND);
        final int count = blockList.size();

        for (int i = 0; i < count; ++i)
        {
            CompoundData blockTag = blockList.getCompoundAt(i);
            BlockPos pos = DataTypeUtils.readBlockPosFromListTag(blockTag, "pos");

            if (pos == null)
            {
                MessageDispatcher.error("litematica.message.error.schematic_read.vanilla.failed_to_read_block_pos");
                errorCount++;
                continue;
            }

            int stateId = blockTag.getInt("state");
            BlockState state = palette.getValue(stateId);

            if (state == null)
            {
                state = BlockState.AIR;
            }

            container.setBlockState(pos.getX(), pos.getY(), pos.getZ(), state);

            if (blockTag.contains("nbt", Constants.NBT.TAG_COMPOUND))
            {
                blockEntityMapOut.put(pos, blockTag.getCompound("nbt").copy());
            }
        }

        return errorCount;
    }

    protected int readEntities(DataView data, List<EntityData> entityListOut)
    {
        ListData list = data.getList("entities", Constants.NBT.TAG_COMPOUND);
        final int size = list.size();
        int errorCount = 0;

        for (int i = 0; i < size; ++i)
        {
            CompoundData tag = list.getCompoundAt(i);
            Vec3d pos = DataTypeUtils.readVec3dFromListTag(tag, "pos");

            if (pos != null && tag.contains("nbt", Constants.NBT.TAG_COMPOUND))
            {
                entityListOut.add(new EntityData(pos, tag.getCompound("nbt").copy()));
            }
            else
            {
                errorCount++;
            }
        }

        return errorCount;
    }

    protected void writeBlocks(CompoundData data, BlockContainer container, Map<BlockPos, CompoundData> blockEntityMap)
    {
        // Use a fresh palette to re-assign fresh increasing IDs without gaps
        Palette<BlockState> palette = new NonResizingHashMapPalette<>(1024);
        ListData blockList = new ListData(Constants.NBT.TAG_COMPOUND);

        if (container instanceof SparseBlockContainer)
        {
            Long2ObjectOpenHashMap<BlockState> blockMap = ((SparseBlockContainer) container).getBlockMap();

            blockMap.forEach((posLong, state) -> {
                long pos = posLong.longValue();
                int x = SparseBlockContainer.getXFromLong(pos);
                int y = SparseBlockContainer.getYFromLong(pos);
                int z = SparseBlockContainer.getZFromLong(pos);

                this.writeBlockToList(x, y, z, palette.idFor(state), blockList, blockEntityMap);
            });
        }
        else
        {
            Vec3i size = container.getSize();
            final int sizeX = size.getX();
            final int sizeY = size.getY();
            final int sizeZ = size.getZ();

            // TODO add some kind of config to decide if any air should be saved or not
            long volume = PositionUtils.getAreaVolume(size);
            BlockState ignore = volume < 100000 ? null : BlockState.AIR;

            for (int y = 0; y < sizeY; ++y)
            {
                for (int z = 0; z < sizeZ; ++z)
                {
                    for (int x = 0; x < sizeX; ++x)
                    {
                        BlockState state = container.getBlockState(x, y, z);

                        if (state != ignore)
                        {
                            this.writeBlockToList(x, y, z, palette.idFor(state), blockList, blockEntityMap);
                        }
                    }
                }
            }
        }

        ListData paletteTag = writePaletteToLitematicaFormatTag(palette);

        data.put("palette", paletteTag);
        data.put("blocks", blockList);
    }

    protected void writeBlockToList(int x, int y, int z, int stateId, ListData list, Map<BlockPos, CompoundData> blockEntityMap)
    {
        CompoundData blockTag = new CompoundData();
        BlockPos pos = new BlockPos(x, y, z);

        DataTypeUtils.writeVec3iToListTag(blockTag, "pos", pos);
        blockTag.putInt("state", stateId);

        CompoundData beTag = blockEntityMap.get(pos);

        if (beTag != null)
        {
            blockTag.put("nbt", beTag);
        }

        list.add(blockTag);
    }

    protected void writeEntities(CompoundData data, List<EntityData> entityList)
    {
        ListData list = new ListData(Constants.NBT.TAG_COMPOUND);

        for (EntityData entityData : entityList)
        {
            CompoundData entityTag = new CompoundData();
            DataTypeUtils.writeVec3dToListTag(entityTag, "pos", entityData.pos);
            DataTypeUtils.writeVec3iToListTag(entityTag, "blockpos", BlockPos.ofFloored(entityData.pos));

            CompoundData entityNbt = entityData.data.copy();
            entityNbt.remove("Pos");
            entityTag.put("nbt", entityNbt);

            list.add(entityTag);
        }

        data.put("entities", list);
    }

    public static Vec3i readSizeFromTag(DataView data)
    {
        return DataTypeUtils.readBlockPosFromListTagOrDefault(data, "size", BlockPos.ORIGIN);
    }

    public static boolean isValidData(DataView data)
    {
        if (data.containsList("blocks", Constants.NBT.TAG_COMPOUND) &&
            data.containsList("palette", Constants.NBT.TAG_COMPOUND) &&
            //data.contains("DataVersion", Constants.NBT.TAG_INT) &&
            data.containsList("size", Constants.NBT.TAG_INT))
        {
            return isSizeValid(readSizeFromTag(data));
        }

        return false;
    }

    public static BlockContainer createDefaultBlockContainer(Vec3i containerSize)
    {
        return new SparseBlockContainer(containerSize);
    }

    public static Optional<SchematicMetadata> createAndReadMetadata(DataView data)
    {
        if (isValidData(data) == false)
        {
            return Optional.empty();
        }

        SchematicMetadata metadata = new SchematicMetadata();
        Vec3i size = readSizeFromTag(data);

        if (data.contains("Metadata", Constants.NBT.TAG_COMPOUND))
        {
            metadata.read(data.getCompound("Metadata"));
        }

        if (metadata.getAuthor().length() == 0)
        {
            metadata.setAuthor(data.getString("author"));
        }

        if (metadata.getMinecraftVersion().dataVersion <= 0)
        {
            int dataVersion = data.getIntOrDefault("DataVersion", -1);
            metadata.setMinecraftVersion(MinecraftVersion.getOrCreateVersionFromDataVersion(dataVersion));
        }

        metadata.setEnclosingSize(size);
        metadata.setTotalVolume((long) size.getX() * size.getY() * size.getZ());
        metadata.setEntityCount(data.getList("entities", Constants.NBT.TAG_COMPOUND).size());

        return Optional.of(metadata);
    }

    public static Optional<Schematic> fromData(DataView data)
    {
        VanillaSchematic schematic = new VanillaSchematic();

        if (schematic.read(data))
        {
            return Optional.of(schematic);
        }

        return Optional.empty();
    }

    public static Optional<Schematic> fromRegions(ImmutableMap<String, SchematicRegion> regions)
    {
        Optional<SchematicRegion> regionOpt = getOrConvertToSingleRegion(regions, SchematicType.VANILLA);

        if (regionOpt.isPresent() == false)
        {
            return Optional.empty();
        }

        SchematicRegion region = regionOpt.get();
        VanillaSchematic schematic = new VanillaSchematic();

        schematic.regions = regions;
        schematic.enclosingSize = PositionUtils.getAbsoluteSize(region.getSize());
        schematic.minecraftDataVersion = region.getMinecraftDataVersion();

        return Optional.of(schematic);
    }
}
