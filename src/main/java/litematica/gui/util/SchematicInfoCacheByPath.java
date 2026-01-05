package litematica.gui.util;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import javax.annotation.Nullable;

import net.minecraft.client.renderer.texture.DynamicTexture;

import malilib.util.FileNameUtils;
import malilib.util.FileUtils;
import malilib.util.data.Identifier;
import malilib.util.data.tag.CompoundData;
import malilib.util.data.tag.util.DataFileUtils;
import litematica.Reference;
import litematica.schematic.SchematicMetadata;
import litematica.schematic.SchematicType;

public class SchematicInfoCacheByPath extends AbstractSchematicInfoCache<Path>
{
    @Override
    @Nullable
    protected SchematicInfo createSchematicInfo(Path file)
    {
        // TODO Use a partial NBT read method to only read the metadata tag
        // TODO (that's only beneficial if it's stored before the bulk schematic data in the stream)
        CompoundData data = DataFileUtils.readCompoundDataFromNbtFile(file);

        if (data == null)
        {
            return null;
        }

        Optional<SchematicType> typeOpt = SchematicType.getTypeFromData(file, data);

        if (typeOpt.isPresent() == false)
        {
            return null;
        }

        SchematicType schematicType = typeOpt.get();
        Optional<SchematicMetadata> metadataOpt = schematicType.createMetadataFromData(data);

        if (metadataOpt.isPresent())
        {
            SchematicMetadata metadata = metadataOpt.get();
            String filePath = FileNameUtils.generateSimpleSafeFileName(file.toAbsolutePath().toString().toLowerCase(Locale.ROOT));
            Identifier iconName = new Identifier(Reference.MOD_ID, filePath);
            DynamicTexture texture = this.createPreviewImage(iconName, metadata);

            if (metadata.getTimeCreated() <= 0)
            {
                metadata.setTimeCreated(FileUtils.getMTime(file));
                metadata.setTimeModified(metadata.getTimeCreated());
            }

            return new SchematicInfo(schematicType, metadata, iconName, texture);
        }

        return null;
    }
}
