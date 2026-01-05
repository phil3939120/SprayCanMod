package litematica.data;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import litematica.schematic.LoadedSchematic;

public class SchematicHolder
{
    public static final SchematicHolder INSTANCE = new SchematicHolder();

    private final List<LoadedSchematic> schematics = new ArrayList<>();

    public void clearLoadedSchematics()
    {
        this.schematics.clear();
    }

    public List<LoadedSchematic> getAllOf(Path file)
    {
        List<LoadedSchematic> list = new ArrayList<>();

        for (LoadedSchematic loadedSchematic : this.schematics)
        {
            if (loadedSchematic.file.isPresent() && file.equals(loadedSchematic.file.get()))
            {
                list.add(loadedSchematic);
            }
        }

        return list;
    }

    public Optional<LoadedSchematic> getOrLoad(Path file)
    {
        if (Files.isRegularFile(file) == false || Files.isReadable(file) == false)
        {
            return Optional.empty();
        }

        for (LoadedSchematic loadedSchematic : this.schematics)
        {
            if (loadedSchematic.file.isPresent() && file.equals(loadedSchematic.file.get()))
            {
                return Optional.of(loadedSchematic);
            }
        }

        Optional<LoadedSchematic> schematicOpt = LoadedSchematic.tryLoadSchematic(file);

        if (schematicOpt.isPresent())
        {
            this.schematics.add(schematicOpt.get());
        }

        return schematicOpt;
    }

    public void addSchematic(LoadedSchematic loadedSchematic, boolean allowDuplicates)
    {
        if (allowDuplicates || this.schematics.contains(loadedSchematic) == false)
        {
            if (allowDuplicates == false && loadedSchematic.file.isPresent())
            {
                for (LoadedSchematic tmp : this.schematics)
                {
                    if (tmp.file.isPresent() && loadedSchematic.file.get().equals(tmp.file.get()))
                    {
                        return;
                    }
                }
            }

            this.schematics.add(loadedSchematic);
        }
    }

    public boolean removeSchematic(LoadedSchematic loadedSchematic)
    {
        if (this.schematics.remove(loadedSchematic))
        {
            DataManager.getSchematicPlacementManager().removeAllPlacementsOfSchematic(loadedSchematic);
            return true;
        }

        return false;
    }

    public List<LoadedSchematic> getAllSchematics()
    {
        return this.schematics;
    }
}
