package litematica.schematic;

import java.nio.file.Path;
import java.util.Optional;

import malilib.util.data.tag.CompoundData;
import malilib.util.data.tag.util.DataFileUtils;

public class LoadedSchematic
{
    public final Schematic schematic;
    public final Optional<Path> file;
    protected boolean modifiedSinceSaved;

    public LoadedSchematic(Schematic schematic)
    {
        this(schematic, Optional.empty());
    }

    public LoadedSchematic(Schematic schematic, Optional<Path> file)
    {
        this.schematic = schematic;
        this.file = file;
    }

    public boolean wasModifiedSinceSaved()
    {
        return this.modifiedSinceSaved;
    }

    public void setModifiedSinceSaved()
    {
        this.modifiedSinceSaved = true;
    }

    public void clearModifiedSinceSaved()
    {
        this.modifiedSinceSaved = false;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {return true;}
        if (o == null || this.getClass() != o.getClass()) {return false;}

        LoadedSchematic that = (LoadedSchematic) o;

        if (this.schematic.equals(that.schematic) == false)
        {
            return false;
        }

        if (this.file.isPresent() != that.file.isPresent())
        {
            return false;
        }

        return this.file.isPresent() == false || this.file.get().equals(that.file.get());
    }

    @Override
    public int hashCode()
    {
        int result = this.schematic.hashCode();
        result = 31 * result + this.file.hashCode();
        return result;
    }

    public static Optional<LoadedSchematic> tryLoadSchematic(Path schematicFile)
    {
        CompoundData data = DataFileUtils.readCompoundDataFromNbtFile(schematicFile);

        if (data == null)
        {
            return Optional.empty();
        }

        Optional<SchematicType> typeOpt = SchematicType.getTypeFromData(schematicFile, data);

        if (typeOpt.isPresent() == false)
        {
            return Optional.empty();
        }

        Optional<Schematic> schematicOpt = typeOpt.get().createSchematicFromData(data);

        if (schematicOpt.isPresent())
        {
            return Optional.of(new LoadedSchematic(schematicOpt.get(), Optional.of(schematicFile)));
        }

        return Optional.empty();
    }
}
