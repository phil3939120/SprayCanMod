package litematica.schematic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import malilib.overlay.message.MessageDispatcher;
import malilib.util.ListUtils;
import malilib.util.data.Constants;
import malilib.util.data.Identifier;
import malilib.util.data.palette.Palette;
import malilib.util.data.tag.BaseData;
import malilib.util.data.tag.CompoundData;
import malilib.util.data.tag.DataView;
import malilib.util.game.wrap.RegistryUtils;
import malilib.util.position.BlockPos;
import malilib.util.position.Vec3i;
import malilib.util.world.BlockState;
import litematica.schematic.container.ArrayBlockContainer;
import litematica.schematic.container.BlockContainer;
import litematica.schematic.data.EntityData;
import litematica.util.PositionUtils;

public class SchematicaSchematic extends BaseSchematic
{
    public static final String FILE_NAME_EXTENSION = "schematic";

    public SchematicaSchematic()
    {
        super(SchematicType.SCHEMATICA);
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

        int beErrorCount = this.readBlockEntities(data.getList("TileEntities", Constants.NBT.TAG_COMPOUND), blockEntityMap);
        int entityErrorCount = this.readEntities(data.getList("Entities", Constants.NBT.TAG_COMPOUND), entityList);

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

        data.putString("Materials", "Alpha");
        data.putInt("MinecraftDataVersion", this.minecraftDataVersion);
        data.put("Metadata", this.metadata.write(new CompoundData()));
        data.putShort("Width", (short) size.getX());
        data.putShort("Height", (short) size.getY());
        data.putShort("Length", (short) size.getZ());

        data.put("SchematicaMapping", this.writePaletteToTag(region.getBlockContainer().getPalette()));

        data.put("TileEntities", this.getBlockEntitiesAsListData(region.getBlockEntityMap()));
        data.put("Entities", this.getEntitiesAsListData(region.getEntityList()));

        this.writeBlocksToTag(data, size, region.getBlockContainer());

        return Optional.of(data);
    }

    @Override
    public String getRootTagName()
    {
        // MCEdit and World Edit require the root compound tag to be named "Schematic".
        return "Schematic";
    }

    protected BlockContainer readBlocks(DataView data, Vec3i size)
    {
        Int2ObjectOpenHashMap<BlockState> mapping = this.readInitialBlockMapping(data);
        int safePaletteSize = mapping.size() * 16;  // Max 16 metadata values per block
        int entryWidthBits = ArrayBlockContainer.getRequiredBitWidth(safePaletteSize);
        ArrayBlockContainer container = new ArrayBlockContainer(size, entryWidthBits);

        this.readBlocksFromTag(data, size, container, mapping);

        return container;
    }

    protected Int2ObjectOpenHashMap<BlockState> readInitialBlockMapping(DataView data)
    {
        Int2ObjectOpenHashMap<BlockState> mapping;

        // Schematica mapping
        if (data.contains("SchematicaMapping", Constants.NBT.TAG_COMPOUND))
        {
            mapping = this.readSchematicaPaletteFromTag(data.getCompound("SchematicaMapping"));
        }
        // MCEdit2 mapping
        else if (data.contains("BlockIDs", Constants.NBT.TAG_COMPOUND))
        {
            mapping = this.readMCEdit2PaletteFromTag(data.getCompound("BlockIDs"));
        }
        // No mapping, use the current registry IDs directly
        else
        {
            mapping = createRegistryBasedPalette();
        }

        return mapping;
    }

    protected Int2ObjectOpenHashMap<BlockState> readSchematicaPaletteFromTag(DataView tag)
    {
        Int2ObjectOpenHashMap<BlockState> mapping = new Int2ObjectOpenHashMap<>();
        int errorCount = 0;

        for (String registryName : tag.getKeys())
        {
            if (tag.contains(registryName, Constants.NBT.TAG_SHORT) == false)
            {
                BaseData val = tag.getData(registryName).orElse(null);
                MessageDispatcher.error("litematica.message.error.schematic_read.schematica.palette.invalid_tag",
                                        registryName, val.getDisplayName(), val);
                errorCount++;
                continue;
            }

            int blockId = tag.getShort(registryName);

            if (blockId > 4095)
            {
                MessageDispatcher.error("litematica.message.error.schematic_read.schematica.palette.invalid_id",
                                        blockId, registryName, 4095);
                errorCount++;
                continue;
            }

            BlockState state = BlockState.fromRegistryNameStr(registryName);

            if (state == null)
            {
                MessageDispatcher.error("litematica.message.error.schematic_read.schematica.palette.invalid_block",
                                        registryName);
                errorCount++;
                continue;
            }

            mapping.put(blockId, state);
        }

        if (errorCount > 0)
        {
            MessageDispatcher.error("litematica.message.error.schematic_read.schematica.mapping_errors",
                                    errorCount, mapping.size());
        }

        return mapping;
    }

    protected Int2ObjectOpenHashMap<BlockState> readMCEdit2PaletteFromTag(DataView tag)
    {
        Int2ObjectOpenHashMap<BlockState> mapping = new Int2ObjectOpenHashMap<>();
        int errorCount = 0;

        for (String numericIdStr : tag.getKeys())
        {
            String registryName = tag.getString(numericIdStr);
            int blockId;

            try
            {
                blockId = Integer.parseInt(numericIdStr);
            }
            catch (NumberFormatException e)
            {
                MessageDispatcher.error("litematica.message.error.schematic_read.mcedit2.palette.id_not_number",
                                        numericIdStr, registryName);
                errorCount++;
                continue;
            }

            BlockState state = BlockState.fromRegistryNameStr(registryName);

            if (state == null)
            {
                MessageDispatcher.error("litematica.message.error.schematic_read.mcedit2.missing_block_data",
                                        registryName);
                errorCount++;
                continue;
            }

            mapping.put(blockId, state);
        }

        if (errorCount > 0)
        {
            MessageDispatcher.error("litematica.message.error.schematic_read.schematica.mapping_errors",
                                    errorCount, mapping.size());
        }

        return mapping;
    }

    public static Int2ObjectOpenHashMap<BlockState> createRegistryBasedPalette()
    {
        Int2ObjectOpenHashMap<BlockState> mapping = new Int2ObjectOpenHashMap<>();
        List<Identifier> blockIds = RegistryUtils.getRegisteredBlockIds();
        int errorCount = 0;

        for (Identifier id : blockIds)
        {
            BlockState state = BlockState.fromRegistryName(id);
            int blockId = state.getOldBlockId();

            if (blockId < 0)
            {
                MessageDispatcher.error("litematica.message.error.schematic_read.registry_palette.missing_block_data",
                                        blockId, id);
                errorCount++;
                continue;
            }

            mapping.put(blockId, state);
        }

        if (errorCount > 0)
        {
            MessageDispatcher.error("litematica.message.error.schematic_read.schematica.mapping_errors",
                                    errorCount, mapping.size());
        }

        return mapping;
    }

    protected void readBlocksFromTag(DataView data,
                                     Vec3i size,
                                     ArrayBlockContainer container,
                                     Int2ObjectOpenHashMap<BlockState> mapping)
    {
        // This method was implemented based on
        // https://minecraft.wiki/w/Schematic_file_format
        // as it was on 2018-04-18.

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

        int errorCount;

        if (data.contains("AddBlocks", Constants.NBT.TAG_BYTE_ARRAY))
        {
            byte[] addArr = data.getByteArray("AddBlocks");
            errorCount = this.readBlocks12Bit(blockIdsArr, addArr, metaArr, size, container, mapping);
        }
        // Old Schematica format
        else if (data.contains("Add", Constants.NBT.TAG_BYTE_ARRAY))
        {
            // FIXME is this array 4 or 8 bits per block?
            MessageDispatcher.error("litematica.message.error.schematic_read.schematica.old_schematica_format_not_supported");
            return;
        }
        else
        {
            errorCount = this.readBlocks8Bit(blockIdsArr, metaArr, size, container, mapping);
        }

        if (errorCount > 0)
        {
            MessageDispatcher.error("litematica.message.error.schematic_read.schematica.block_errors",
                                    errorCount, numBlocks);
        }
    }

    @SuppressWarnings("deprecation")
    protected int readBlocks12Bit(byte[] blockIdsByte,
                                  byte[] addArr,
                                  byte[] metaArr,
                                  Vec3i size,
                                  ArrayBlockContainer container,
                                  Int2ObjectOpenHashMap<BlockState> mapping)
    {
        final int numBlocks = blockIdsByte.length;
        int expectedLength = (int) Math.ceil(numBlocks / 2.0);

        if (addArr.length != expectedLength)
        {
            MessageDispatcher.error("litematica.message.error.schematic_read.schematica.schematic.invalid_block_add_array_size",
                                    addArr.length, expectedLength, numBlocks);
            return -1;
        }

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
                    blockId = (blockIdsByte[index] & 0xFF);    // Need to mask because the array type is signed...
                    metaValue = metaArr[index] & 0xF;

                    // Add value is 4 -bits per position, so index / 2
                    int addIndex = index >> 1;

                    // Odd values go to the lower nibble
                    if ((index & 0x1) != 0)
                    {
                        blockId |= ((addArr[addIndex]) & 0x0F) << 8;
                    }
                    // Even values go to the higher nibble
                    else
                    {
                        blockId |= ((addArr[addIndex]) & 0xF0) << 4;
                    }

                    BlockState state = mapping.get(blockId);

                    if (state != null)
                    {
                        state = BlockState.of(state.getBlock().getStateFromMeta(metaValue));
                        container.setBlockState(x, y, z, state);
                    }
                    else
                    {
                        errorCount++;
                    }

                    index++;
                }
            }
        }

        return errorCount;
    }

    @SuppressWarnings("deprecation")
    protected int readBlocks8Bit(byte[] blockIdsByte,
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
                    blockId = (blockIdsByte[index] & 0xFF); // Need to mask because the array type is signed...
                    metaValue = metaArr[index] & 0xF;

                    BlockState state = mapping.get(blockId);

                    if (state != null)
                    {
                        state = BlockState.of(state.getBlock().getStateFromMeta(metaValue));
                        container.setBlockState(x, y, z, state);
                    }
                    else
                    {
                        errorCount++;
                    }

                    index++;
                }
            }
        }

        return errorCount;
    }

    protected CompoundData writePaletteToTag(Palette<BlockState> palette)
    {
        CompoundData tag = new CompoundData();
        List<BlockState> mapping = palette.getMapping();

        for (BlockState state : mapping)
        {
            // TODO Use an INVALID state to indicate errors (that returns the vanilla AIR state)?
            //  The palette should not have nulls or it would need to be checked for
            //  in the code that reads blocks from the container.
            if (state == null)
            {
                continue;
            }

            tag.putShort(state.getRegistryNameStr(), (short) state.getOldBlockId());
        }

        return tag;
    }

    protected void writeBlocksToTag(CompoundData data, Vec3i size, BlockContainer container)
    {
        final int sizeX = size.getX();
        final int sizeY = size.getY();
        final int sizeZ = size.getZ();

        final int numBlocks = sizeX * sizeY * sizeZ;
        final int addSize = (int) Math.ceil((double) numBlocks / 2.0);
        final byte[] blockIdArr = new byte[numBlocks];
        final byte[] metaArr = new byte[numBlocks];
        final byte[] addArr = new byte[addSize];
        int numAdd = 0;
        int index = 0;

        for (int y = 0; y < sizeY; y++)
        {
            for (int z = 0; z < sizeZ; z++)
            {
                for (int x = 0; x < sizeX; x++)
                {
                    BlockState state = container.getBlockState(x, y, z);
                    int blockId = state.getOldBlockId();
                    int meta = state.getBlock().getMetaFromState(state.vanillaState());
                    int add = addArr[index >> 1];

                    blockIdArr[index] = (byte) (blockId & 0xFF);
                    metaArr[index] = (byte) meta;

                    // "Add" data, the higher 4-bits of a 12-bit block ID
                    // Odd values go to the lower nibble
                    if ((index & 0x1) != 0)
                    {
                        add |= ((blockId >>> 8) & 0x0F);
                    }
                    // Even values go to the higher nibble
                    else
                    {
                        add |= ((blockId >>> 4) & 0xF0);
                    }

                    if (add != 0)
                    {
                        addArr[index >> 1] = (byte) add;
                        ++numAdd;
                    }

                    index++;
                }
            }
        }

        data.putByteArray("Blocks", blockIdArr);
        data.putByteArray("Data", metaArr);

        if (numAdd > 0)
        {
            data.putByteArray("AddBlocks", addArr);
        }
    }

    public static boolean isValidData(DataView data)
    {
        if (data.contains("Width", Constants.NBT.TAG_SHORT) &&
            data.contains("Height", Constants.NBT.TAG_SHORT) &&
            data.contains("Length", Constants.NBT.TAG_SHORT) &&
            data.contains("Blocks", Constants.NBT.TAG_BYTE_ARRAY) &&
            data.contains("Data", Constants.NBT.TAG_BYTE_ARRAY))
        {
            return isSizeValid(readSizeFromTag(data));
        }

        return false;
    }

    protected static Vec3i readSizeFromTag(DataView tag)
    {
        return new Vec3i(tag.getShort("Width"),
                         tag.getShort("Height"),
                         tag.getShort("Length"));
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

        if (data.contains("Metadata", Constants.NBT.TAG_COMPOUND))
        {
            metadata.read(data.getCompound("Metadata"));
        }

        metadata.setEnclosingSize(size);
        metadata.setTotalVolume((long) size.getX() * size.getY() * size.getZ());
        metadata.setEntityCount(data.getList("Entities", Constants.NBT.TAG_COMPOUND).size());
        metadata.setBlockEntityCount(data.getList("TileEntities", Constants.NBT.TAG_COMPOUND).size());

        return Optional.of(metadata);
    }

    public static Optional<Schematic> fromData(DataView data)
    {
        SchematicaSchematic schematic = new SchematicaSchematic();

        if (schematic.read(data))
        {
            return Optional.of(schematic);
        }

        return Optional.empty();
    }

    public static Optional<Schematic> fromRegions(ImmutableMap<String, SchematicRegion> regions)
    {
        Optional<SchematicRegion> regionOpt = getOrConvertToSingleRegion(regions, SchematicType.SCHEMATICA);

        if (regionOpt.isPresent() == false)
        {
            return Optional.empty();
        }

        SchematicRegion region = regionOpt.get();
        SchematicaSchematic schematic = new SchematicaSchematic();

        schematic.regions = regions;
        schematic.enclosingSize = PositionUtils.getAbsoluteSize(region.getSize());
        schematic.minecraftDataVersion = region.getMinecraftDataVersion();

        return Optional.of(schematic);
    }
}
