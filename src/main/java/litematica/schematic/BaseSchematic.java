package litematica.schematic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import com.google.common.collect.ImmutableMap;

import malilib.mixin.access.DataFixerMixin;
import malilib.util.ListUtils;
import malilib.util.data.Constants;
import malilib.util.data.palette.Palette;
import malilib.util.data.tag.CompoundData;
import malilib.util.data.tag.ListData;
import malilib.util.data.tag.util.DataTypeUtils;
import malilib.util.game.BlockUtils;
import malilib.util.game.wrap.GameWrap;
import malilib.util.position.BlockPos;
import malilib.util.position.Vec3d;
import malilib.util.position.Vec3i;
import malilib.util.world.BlockState;
import litematica.schematic.container.ArrayBlockContainer;
import litematica.schematic.container.BlockContainer;
import litematica.schematic.container.SparseBlockContainer;
import litematica.schematic.data.EntityData;

public abstract class BaseSchematic implements Schematic
{
    public static final int CURRENT_MINECRAFT_DATA_VERSION = ((DataFixerMixin) GameWrap.getClient().getDataFixer()).malilib$getVersion();

    protected final SchematicType type;

    protected ImmutableMap<String, SchematicRegion> regions = ImmutableMap.of();
    protected SchematicMetadata metadata = new SchematicMetadata();
    protected Vec3i enclosingSize = Vec3i.ZERO;
    protected int minecraftDataVersion = CURRENT_MINECRAFT_DATA_VERSION;

    protected BaseSchematic(SchematicType type)
    {
        this.type = type;
    }

    @Override
    public SchematicType getType()
    {
        return this.type;
    }

    @Override
    public SchematicMetadata getMetadata()
    {
        return this.metadata;
    }

    @Override
    public Vec3i getEnclosingSize()
    {
        return this.enclosingSize;
    }

    @Override
    public ImmutableMap<String, SchematicRegion> getRegions()
    {
        return this.regions;
    }

    public static boolean isSizeValid(@Nullable Vec3i size)
    {
        return size != null && size.getX() > 0 && size.getY() > 0 && size.getZ() > 0;
    }

    public static void copyContainerContents(BlockContainer from, BlockContainer to)
    {
        if ((from instanceof ArrayBlockContainer) && (to instanceof ArrayBlockContainer))
        {
            copyContainerContentsArrayToArray((ArrayBlockContainer) from, (ArrayBlockContainer) to);
        }
        else if ((from instanceof ArrayBlockContainer) && (to instanceof SparseBlockContainer))
        {
            copyContainerContentsArrayToSparse((ArrayBlockContainer) from, (SparseBlockContainer) to);
        }
        else if ((from instanceof SparseBlockContainer) && (to instanceof ArrayBlockContainer))
        {
            copyContainerContentsSparseToArray((SparseBlockContainer) from, (ArrayBlockContainer) to);
        }
    }

    public static void copyContainerContentsArrayToArray(ArrayBlockContainer from, ArrayBlockContainer to)
    {
        Vec3i sizeFrom = from.getSize();
        Vec3i sizeTo = to.getSize();
        final int sizeX = Math.min(sizeFrom.getX(), sizeTo.getX());
        final int sizeY = Math.min(sizeFrom.getY(), sizeTo.getY());
        final int sizeZ = Math.min(sizeFrom.getZ(), sizeTo.getZ());

        for (int y = 0; y < sizeY; ++y)
        {
            for (int z = 0; z < sizeZ; ++z)
            {
                for (int x = 0; x < sizeX; ++x)
                {
                    BlockState state = from.getBlockState(x, y, z);
                    to.setBlockState(x, y, z, state);
                }
            }
        }
    }

    public static void copyContainerContentsSparseToArray(SparseBlockContainer from, ArrayBlockContainer to)
    {
        Vec3i sizeFrom = from.getSize();
        Vec3i sizeTo = to.getSize();
        final int sizeX = Math.min(sizeFrom.getX(), sizeTo.getX());
        final int sizeY = Math.min(sizeFrom.getY(), sizeTo.getY());
        final int sizeZ = Math.min(sizeFrom.getZ(), sizeTo.getZ());

        for (int y = 0; y < sizeY; ++y)
        {
            for (int z = 0; z < sizeZ; ++z)
            {
                for (int x = 0; x < sizeX; ++x)
                {
                    BlockState state = from.getBlockState(x, y, z);
                    to.setBlockState(x, y, z, state);
                }
            }
        }
    }

    public static void copyContainerContentsArrayToSparse(ArrayBlockContainer from, SparseBlockContainer to)
    {
        Vec3i sizeFrom = from.getSize();
        Vec3i sizeTo = to.getSize();
        final int sizeX = Math.min(sizeFrom.getX(), sizeTo.getX());
        final int sizeY = Math.min(sizeFrom.getY(), sizeTo.getY());
        final int sizeZ = Math.min(sizeFrom.getZ(), sizeTo.getZ());

        for (int y = 0; y < sizeY; ++y)
        {
            for (int z = 0; z < sizeZ; ++z)
            {
                for (int x = 0; x < sizeX; ++x)
                {
                    BlockState state = from.getBlockState(x, y, z);
                    to.setBlockState(x, y, z, state);
                }
            }
        }
    }

    public static boolean readPaletteFromLitematicaFormatTag(ListData listData,
                                                             Palette<BlockState> palette,
                                                             int dataVersion)
    {
        final int size = listData.size();
        List<BlockState> list = new ArrayList<>(size);

        for (int id = 0; id < size; ++id)
        {
            CompoundData blockData = listData.getCompoundAt(id);
            BlockState state = BlockState.ofData(blockData, dataVersion);
            list.add(state);
        }

        return palette.setMapping(list);
    }

    public static ListData writePaletteToLitematicaFormatTag(Palette<BlockState> palette)
    {
        final int size = palette.getSize();
        List<BlockState> mapping = palette.getMapping();
        ListData listData = new ListData(Constants.NBT.TAG_COMPOUND);

        for (int id = 0; id < size; ++id)
        {
            CompoundData compound = new CompoundData();
            BlockUtils.writeBlockState(compound, mapping.get(id));
            listData.add(compound);
        }

        return listData;
    }

    protected ListData getBlockEntitiesAsListData(Map<BlockPos, CompoundData> blockEntityMap)
    {
        ListData list = new ListData(Constants.NBT.TAG_COMPOUND);

        for (Map.Entry<BlockPos, CompoundData> entry : blockEntityMap.entrySet())
        {
            CompoundData compound = entry.getValue().copy();
            DataTypeUtils.putVec3i(compound, entry.getKey());
            list.add(compound);
        }

        return list;
    }

    protected ListData getEntitiesAsListData(List<EntityData> entityList)
    {
        ListData list = new ListData(Constants.NBT.TAG_COMPOUND);

        for (EntityData entityData : entityList)
        {
            CompoundData tag = entityData.data.copy();
            DataTypeUtils.writeVec3dToListTag(tag, entityData.pos);
            list.add(tag);
        }

        return list;
    }

    protected int readBlockEntities(ListData listDataIn, Map<BlockPos, CompoundData> blockEntityMapOut)
    {
        final int size = listDataIn.size();
        int errorCount = 0;

        for (int i = 0; i < size; ++i)
        {
            CompoundData beData = listDataIn.getCompoundAt(i).copy();
            BlockPos pos = DataTypeUtils.readBlockPos(beData);
            DataTypeUtils.removeBlockPosFromTag(beData);

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

    protected int readEntities(ListData listDataIn, List<EntityData> entityListOut)
    {
        final int size = listDataIn.size();
        int errorCount = 0;

        for (int i = 0; i < size; ++i)
        {
            CompoundData entityData = listDataIn.getCompoundAt(i).copy();
            Vec3d pos = DataTypeUtils.readVec3dFromListTag(entityData);

            if (pos != null && entityData.isEmpty() == false)
            {
                entityListOut.add(new EntityData(pos, entityData));
            }
            else
            {
                errorCount++;
            }
        }

        return errorCount;
    }

    public static Optional<SchematicRegion> getOrConvertToSingleRegion(ImmutableMap<String, SchematicRegion> regions,
                                                                       SchematicType targetType)
    {
        if (regions.size() < 1)
        {
            return Optional.empty();
        }

        if (regions.size() == 1)
        {
            return Optional.ofNullable(ListUtils.getFirstEntry(regions.values()));
        }

        // TODO convert from multi-region
        return Optional.empty();
    }
}
