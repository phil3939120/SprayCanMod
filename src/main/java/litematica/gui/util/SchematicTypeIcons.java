package litematica.gui.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import malilib.gui.icon.DefaultIcons;
import malilib.gui.icon.Icon;
import litematica.schematic.LoadedSchematic;
import litematica.schematic.SchematicType;

public class SchematicTypeIcons
{
    public static final SchematicTypeIcons INSTANCE = new SchematicTypeIcons();

    protected final Map<SchematicType, Icon> normalIcons = new HashMap<>();
    protected final Map<SchematicType, Icon> inMemoryOnlyIcons = new HashMap<>();

    protected SchematicTypeIcons()
    {
        this.registerBuiltin();
    }

    public Optional<Icon> getNormalIcon(SchematicType type)
    {
        return Optional.ofNullable(this.normalIcons.get(type));
    }

    public Optional<Icon> getInMemoryOnlyIcon(SchematicType type)
    {
        return Optional.ofNullable(this.inMemoryOnlyIcons.get(type));
    }

    public Optional<Icon> getIcon(LoadedSchematic loadedSchematic)
    {
        SchematicType type = loadedSchematic.schematic.getType();

        if (loadedSchematic.file.isPresent())
        {
            return Optional.ofNullable(this.normalIcons.get(type));
        }

        return Optional.ofNullable(this.inMemoryOnlyIcons.get(type));
    }

    public void register(SchematicType type, Icon normalIcon, Icon inMemoryOnlyIcon)
    {
        this.normalIcons.put(type, normalIcon);
        this.inMemoryOnlyIcons.put(type, inMemoryOnlyIcon);
    }

    protected void registerBuiltin()
    {
        this.register(SchematicType.LITEMATICA,  LitematicaIcons.SCHEMATIC_LITEMATIC,   LitematicaIcons.SCHEMATIC_IN_MEMORY_LITEMATIC);
        this.register(SchematicType.SPONGE,      LitematicaIcons.SCHEMATIC_SPONGE,      LitematicaIcons.SCHEMATIC_IN_MEMORY_SPONGE);
        this.register(SchematicType.SCHEMATICA,  LitematicaIcons.SCHEMATIC_SCHEMATICA,  LitematicaIcons.SCHEMATIC_IN_MEMORY_SCHEMATICA);
        this.register(SchematicType.STRUCTURIZE, LitematicaIcons.SCHEMATIC_STRUCTURIZE, LitematicaIcons.SCHEMATIC_IN_MEMORY_STRUCTURIZE);
        this.register(SchematicType.VANILLA,     LitematicaIcons.SCHEMATIC_VANILLA,     LitematicaIcons.SCHEMATIC_IN_MEMORY_VANILLA);

        this.register(SchematicType.INDEV_WORLD, LitematicaIcons.SCHEMATIC_INDEV_WORLD, LitematicaIcons.SCHEMATIC_IN_MEMORY_INDEV_WORLD);
    }

    public static Icon getIconForType(SchematicType type)
    {
        Optional<Icon> iconOpt = INSTANCE.getNormalIcon(type);
        return iconOpt.orElse(DefaultIcons.EXCLAMATION_11);
    }
}
