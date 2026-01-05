package litematica.schematic.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import malilib.overlay.message.MessageDispatcher;
import malilib.util.data.tag.CompoundData;
import malilib.util.data.tag.util.DataFileUtils;
import litematica.schematic.Schematic;

public class SchematicFileUtils
{
    public static boolean readFromFile(Schematic schematic, Path file)
    {
        if (Files.isRegularFile(file) == false || Files.isReadable(file) == false)
        {
            return false;
        }

        CompoundData data = DataFileUtils.readCompoundDataFromNbtFile(file);

        if (data == null)
        {
            MessageDispatcher.error("litematica.error.schematic_read.cant_read_nbt",
                                    file.toAbsolutePath().toString());
            return false;
        }

        return schematic.read(data);
    }

    public static boolean writeToFile(Schematic schematic, Path file, boolean overwrite)
    {
        String fileName = file.getFileName().toString();
        String extension = schematic.getType().getFileNameExtension();

        if (fileName.endsWith(extension) == false)
        {
            fileName = fileName + "." + extension;
            file = file.getParent().resolve(fileName);
        }

        if (overwrite == false && Files.exists(file))
        {
            MessageDispatcher.error("litematica.error.schematic_write.file_exists",
                                    file.toAbsolutePath().toString());
            return false;
        }

        if (Files.exists(file) && Files.isWritable(file) == false)
        {
            MessageDispatcher.error("litematica.error.schematic_write.file_not_writable",
                                    file.toAbsolutePath().toString());
            return false;
        }

        try
        {
            Optional<CompoundData> data = schematic.write();

            if (data.isPresent() == false || data.get().size() == 0)
            {
                MessageDispatcher.error("litematica.message.error.schematic_save.serializing_failed.empty");
                return false;
            }

            return DataFileUtils.writeCompoundDataToCompressedNbtFile(file, data.get(), schematic.getRootTagName());
        }
        catch (Exception e)
        {
            String key = "litematica.message.error.schematic_save.serializing_failed.exception";
            MessageDispatcher.error().console(e).translate(key, e.getMessage());
        }

        return false;
    }
}
