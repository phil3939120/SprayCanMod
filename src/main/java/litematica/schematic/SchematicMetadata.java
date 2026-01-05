package litematica.schematic;

import java.util.Optional;
import javax.annotation.Nullable;

import malilib.util.StringUtils;
import malilib.util.data.tag.CompoundData;
import malilib.util.data.tag.DataView;
import malilib.util.data.tag.util.DataTypeUtils;
import malilib.util.game.MinecraftVersion;
import malilib.util.position.BlockPos;
import malilib.util.position.Vec3i;

public class SchematicMetadata
{
    public static final String DEFAULT_NAME = "<no name>";
    public static final int DEFAULT_UNSET_VERSION = -1;

    protected String schematicName = DEFAULT_NAME;
    protected String author = "";
    protected String description = "";
    protected Vec3i enclosingSize = Vec3i.ZERO;
    protected MinecraftVersion minecraftVersion = MinecraftVersion.MC_UNKNOWN;
    @Nullable protected BlockPos originalOrigin;
    protected long timeCreated = -1;
    protected long timeModified = -1;
    protected int schematicVersion = DEFAULT_UNSET_VERSION;
    protected int regionCount = -1;
    protected int entityCount = -1;
    protected long totalVolume = -1;
    protected long totalBlocks = -1;
    protected long blockEntityCount = -1;
    protected long blockTickCount = -1;
    @Nullable protected int[] thumbnailPixelData;

    public String getSchematicName()
    {
        return this.schematicName;
    }

    public String getAuthor()
    {
        return this.author;
    }

    public String getDescription()
    {
        return this.description;
    }

    @Nullable
    public int[] getPreviewImagePixelData()
    {
        return this.thumbnailPixelData;
    }

    public int getRegionCount()
    {
        return this.regionCount;
    }

    public int getEntityCount()
    {
        return this.entityCount;
    }

    public long getTotalVolume()
    {
        return this.totalVolume;
    }

    public long getTotalBlocks()
    {
        return this.totalBlocks;
    }

    public long getBlockEntityCount()
    {
        return this.blockEntityCount;
    }

    public long getBlockTickCount()
    {
        return this.blockTickCount;
    }

    public Vec3i getEnclosingSize()
    {
        return this.enclosingSize;
    }

    public long getTimeCreated()
    {
        return this.timeCreated;
    }

    public long getTimeModified()
    {
        return this.timeModified;
    }

    public int getSchematicVersion()
    {
        return this.schematicVersion;
    }

    public MinecraftVersion getMinecraftVersion()
    {
        return this.minecraftVersion;
    }

    public Optional<BlockPos> getOriginalOrigin()
    {
        return Optional.ofNullable(this.originalOrigin);
    }

    public boolean wasModified()
    {
        return this.timeCreated != this.timeModified;
    }

    public void setSchematicName(String schematicName)
    {
        if (schematicName == null)
        {
            schematicName = DEFAULT_NAME;
        }

        this.schematicName = schematicName;
    }

    public void setAuthor(String author)
    {
        if (author == null)
        {
            author = "";
        }

        this.author = author;
    }

    public void setDescription(String description)
    {
        if (description == null)
        {
            description = "";
        }

        this.description = description;
    }

    public void setPreviewImagePixelData(@Nullable int[] pixelData)
    {
        this.thumbnailPixelData = pixelData;
    }

    public void setRegionCount(int regionCount)
    {
        this.regionCount = regionCount;
    }

    public void setEntityCount(int entityCount)
    {
        this.entityCount = entityCount;
    }

    public void setBlockEntityCount(long blockEntityCount)
    {
        this.blockEntityCount = blockEntityCount;
    }

    public void setBlockTickCount(long blockTickCount)
    {
        this.blockTickCount = blockTickCount;
    }

    public void setTotalVolume(long totalVolume)
    {
        this.totalVolume = totalVolume;
    }

    public void setTotalBlocks(long totalBlocks)
    {
        this.totalBlocks = totalBlocks;
    }

    public void setEnclosingSize(Vec3i enclosingSize)
    {
        this.enclosingSize = enclosingSize;
    }

    public void setTimeCreated(long timeCreated)
    {
        this.timeCreated = timeCreated;
    }

    public void setTimeModified(long timeModified)
    {
        this.timeModified = timeModified;
    }

    public void setTimeModifiedToNow()
    {
        this.timeModified = System.currentTimeMillis();
    }

    public void setTimeModifiedToNowIfNotRecentlyCreated()
    {
        long currentTime = System.currentTimeMillis();

        // Allow 10 minutes to set the description and thumbnail image etc.
        // without marking the schematic as modified
        if (currentTime - this.timeCreated > 10L * 60L * 1000L)
        {
            this.timeModified = currentTime;
        }
    }

    public void setSchematicVersion(int schematicVersion)
    {
        this.schematicVersion = schematicVersion;
    }

    public void setMinecraftVersion(MinecraftVersion minecraftVersion)
    {
        this.minecraftVersion = minecraftVersion;
    }

    public void setOriginalOrigin(@Nullable BlockPos originalOrigin)
    {
        this.originalOrigin = originalOrigin;
    }

    public void copyFrom(SchematicMetadata other)
    {
        this.schematicName = other.schematicName;
        this.author = other.author;
        this.description = other.description;
        this.enclosingSize = other.enclosingSize;

        this.timeCreated = other.timeCreated;
        this.timeModified = other.timeModified;
        this.schematicVersion = other.schematicVersion;
        this.minecraftVersion = other.minecraftVersion;

        this.regionCount = other.regionCount;
        this.entityCount = other.entityCount;
        this.totalVolume = other.totalVolume;
        this.totalBlocks = other.totalBlocks;
        this.blockEntityCount = other.blockEntityCount;
        this.blockTickCount = other.blockTickCount;
        this.originalOrigin = other.originalOrigin;

        if (other.thumbnailPixelData != null)
        {
            this.thumbnailPixelData = new int[other.thumbnailPixelData.length];
            System.arraycopy(other.thumbnailPixelData, 0, this.thumbnailPixelData, 0, this.thumbnailPixelData.length);
        }
        else
        {
            this.thumbnailPixelData = null;
        }
    }

    public CompoundData write(CompoundData tag)
    {
        tag.putString("Name", this.schematicName);

        if (StringUtils.isEmpty(this.author) == false)
        {
            tag.putString("Author", this.author);
        }

        if (StringUtils.isEmpty(this.description) == false)
        {
            tag.putString("Description", this.description);
        }

        if (this.schematicVersion >= 0)
        {
            tag.putInt("SchematicVersion", this.schematicVersion);
        }

        tag.putLong("TimeCreated", this.timeCreated);

        if (this.timeModified > 0 && this.timeModified != this.timeCreated)
        {
            tag.putLong("TimeModified", this.timeModified);
        }

        tag.put("EnclosingSize", DataTypeUtils.createVec3iTag(this.enclosingSize));

        if (this.minecraftVersion != null)
        {
            tag.putInt("McDataVersion", this.minecraftVersion.dataVersion);
            tag.putString("McVersion", this.minecraftVersion.displayName);
        }

        tag.putInt("RegionCount", this.regionCount);

        if (this.entityCount >= 0)
        {
            tag.putInt("EntityCount", this.entityCount);
        }

        tag.putLong("TotalVolume", this.totalVolume);

        if (this.totalBlocks >= 0)
        {
            tag.putLong("TotalBlocks", this.totalBlocks);
        }

        if (this.blockEntityCount >= 0)
        {
            tag.putLong("BlockEntityCount", this.blockEntityCount);
        }

        if (this.blockTickCount >= 0)
        {
            tag.putLong("BlockTickCount", this.blockTickCount);
        }

        if (this.originalOrigin != null)
        {
            tag.put("Origin", DataTypeUtils.createVec3iTag(this.originalOrigin));
        }

        if (this.thumbnailPixelData != null)
        {
            tag.putIntArray("PreviewImageData", this.thumbnailPixelData);
        }

        return tag;
    }

    public void read(DataView dataIn)
    {
        this.schematicName = dataIn.getStringOrDefault("Name", DEFAULT_NAME);
        this.author = dataIn.getStringOrDefault("Author", "");
        this.description = dataIn.getStringOrDefault("Description", "");
        this.enclosingSize = DataTypeUtils.readVec3iOrDefault(dataIn, "EnclosingSize", Vec3i.ZERO);

        this.timeCreated = dataIn.getLongOrDefault("TimeCreated", -1);
        this.timeModified = dataIn.getLongOrDefault("TimeModified", -1);
        this.schematicVersion = dataIn.getIntOrDefault("SchematicVersion", DEFAULT_UNSET_VERSION);

        int mcDataVersion = dataIn.getIntOrDefault("McDataVersion", -1);
        this.minecraftVersion = MinecraftVersion.getOrCreateVersionFromDataVersion(mcDataVersion);

        this.regionCount = dataIn.getIntOrDefault("RegionCount", -1);
        this.entityCount = dataIn.getIntOrDefault("EntityCount", -1);
        this.totalVolume = dataIn.getLongOrDefault("TotalVolume", -1);
        this.totalBlocks = dataIn.getLongOrDefault("TotalBlocks", -1);
        this.blockEntityCount = dataIn.getLongOrDefault("BlockEntityCount", -1);    // FIXME was this already saved in public builds? If so then it should be read from an int tag as a backup
        this.blockTickCount = dataIn.getLongOrDefault("BlockTickCount", -1);

        this.thumbnailPixelData = dataIn.getIntArrayOrDefault("PreviewImageData", null);
        this.originalOrigin = DataTypeUtils.readBlockPos(dataIn.getCompound("Origin"));
    }
}
