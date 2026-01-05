package litematica.schematic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import malilib.util.FileNameUtils;
import malilib.util.StringUtils;
import malilib.util.data.tag.DataView;
import malilib.util.position.Vec3i;
import litematica.config.Configs;
import litematica.schematic.container.BlockContainer;
import litematica.schematic.container.BlockContainerFactory;

public class SchematicType
{
    public static final SchematicType LITEMATICA = SchematicType.builder()
        .setTranslationKey("litematica.schematic.type.litematica")
        .setBlockContainerFactory(LitematicaSchematic::createDefaultBlockContainer)
        .setSchematicFromDataFactory(LitematicaSchematic::fromData)
        .setSchematicFromRegionsFactory(LitematicaSchematic::fromRegions)
        .setMetadataFromDataFactory(LitematicaSchematic::createAndReadMetadata)
        .setDataValidator(LitematicaSchematic::isValidData)
        .setExtension(LitematicaSchematic.FILE_NAME_EXTENSION)
        .setExtensionValidator(LitematicaSchematic.FILE_NAME_EXTENSION::equalsIgnoreCase)
        .setDefaultSaveVersion(LitematicaSchematic.CURRENT_SCHEMATIC_VERSION)
        .setSupportsMultipleRegions(true)
        .setHasName(true)
        .build();

    public static final SchematicType SCHEMATICA = SchematicType.builder()
        .setTranslationKey("litematica.schematic.type.schematica")
        .setBlockContainerFactory(SchematicaSchematic::createDefaultBlockContainer)
        .setSchematicFromDataFactory(SchematicaSchematic::fromData)
        .setSchematicFromRegionsFactory(SchematicaSchematic::fromRegions)
        .setMetadataFromDataFactory(SchematicaSchematic::createAndReadMetadata)
        .setDataValidator(SchematicaSchematic::isValidData)
        .setExtension(SchematicaSchematic.FILE_NAME_EXTENSION)
        .setExtensionValidator(SchematicaSchematic.FILE_NAME_EXTENSION::equalsIgnoreCase)
        .build();

    public static final SchematicType SPONGE = SchematicType.builder()
        .setTranslationKey("litematica.schematic.type.sponge")
        .setBlockContainerFactory(SpongeSchematic::createDefaultBlockContainer)
        .setSchematicFromDataFactory(SpongeSchematic::fromData)
        .setSchematicFromRegionsFactory(SpongeSchematic::fromRegions)
        .setMetadataFromDataFactory(SpongeSchematic::createAndReadMetadata)
        .setDataValidator(SpongeSchematic::isValidData)
        .setExtension(SpongeSchematic.FILE_NAME_EXTENSION)
        .setExtensionValidator(SpongeSchematic.FILE_NAME_EXTENSION::equalsIgnoreCase)
        .setSavableVersions(SpongeSchematic.SAVABLE_VERSIONS)
        .setDefaultSaveVersion(SpongeSchematic.CURRENT_SCHEMATIC_VERSION)
        .build();

    public static final SchematicType VANILLA = SchematicType.builder()
        .setTranslationKey("litematica.schematic.type.vanilla")
        .setBlockContainerFactory(VanillaSchematic::createDefaultBlockContainer)
        .setSchematicFromDataFactory(VanillaSchematic::fromData)
        .setSchematicFromRegionsFactory(VanillaSchematic::fromRegions)
        .setMetadataFromDataFactory(VanillaSchematic::createAndReadMetadata)
        .setDataValidator(VanillaSchematic::isValidData)
        .setExtension(VanillaSchematic.FILE_NAME_EXTENSION)
        .setExtensionValidator(VanillaSchematic.FILE_NAME_EXTENSION::equalsIgnoreCase)
        .build();

    public static final SchematicType STRUCTURIZE = SchematicType.builder()
        .setTranslationKey("litematica.schematic.type.structurize")
        .setBlockContainerFactory(StructurizeSchematic::createDefaultBlockContainer)
        .setSchematicFromDataFactory(StructurizeSchematic::fromData)
        .setSchematicFromRegionsFactory(StructurizeSchematic::fromRegions)
        .setMetadataFromDataFactory(StructurizeSchematic::createAndReadMetadata)
        .setDataValidator(StructurizeSchematic::isValidData)
        .setExtension(StructurizeSchematic.FILE_NAME_EXTENSION)
        .setExtensionValidator(StructurizeSchematic.FILE_NAME_EXTENSION::equalsIgnoreCase)
        .setDefaultSaveVersion(StructurizeSchematic.CURRENT_SCHEMATIC_VERSION)
        .setHasName(true)
        .build();

    public static final SchematicType INDEV_WORLD = SchematicType.builder()
        .setTranslationKey("litematica.schematic.type.indev_world")
        .setBlockContainerFactory(IndevWorldSchematic::createDefaultBlockContainer)
        .setSchematicFromDataFactory(IndevWorldSchematic::fromData)
        .setSchematicFromRegionsFactory(IndevWorldSchematic::fromRegions)
        .setMetadataFromDataFactory(IndevWorldSchematic::createAndReadMetadata)
        .setDataValidator(IndevWorldSchematic::isValidData)
        .setExtension(IndevWorldSchematic.FILE_NAME_EXTENSION)
        .setExtensionValidator(IndevWorldSchematic.FILE_NAME_EXTENSION::equalsIgnoreCase)
        .setHasName(true)
        .build();

    private static ImmutableList<SchematicType> COMMON_TYPES = ImmutableList.of(LITEMATICA, SPONGE, SCHEMATICA, VANILLA, STRUCTURIZE);
    private static ImmutableList<SchematicType> EXTRA_TYPES = ImmutableList.of(INDEV_WORLD);
    private static ImmutableList<SchematicType> ALL_TYPES = buildAllTypesList();

    public static final Predicate<Path> SCHEMATIC_FILE_FILTER = p -> Files.isRegularFile(p) &&
                                                                     Files.isReadable(p) &&
                                                                     getPossibleTypesFromFileName(p).isEmpty() == false;

    private final String extension;
    private final BlockContainerFactory containerFactory;
    private final Function<DataView, Optional<SchematicMetadata>> metadataFromDataFactory;
    private final Function<DataView, Optional<Schematic>> schematicFromDataFactory;
    private final Function<ImmutableMap<String, SchematicRegion>, Optional<Schematic>> schematicFromRegionsFactory;
    private final Function<String, Boolean> extensionValidator;
    private final Function<DataView, Boolean> dataValidator;
    private final ImmutableList<Integer> savableVersions;
    private final String translationKey;
    private final int defaultSaveVersion;
    private final boolean hasName;
    private final boolean supportsMultipleRegions;

    private SchematicType(String translationKey,
                          BlockContainerFactory containerFactory,
                          Function<DataView, Optional<Schematic>> schematicFromDataFactory,
                          Function<ImmutableMap<String, SchematicRegion>, Optional<Schematic>> schematicFromRegionsFactory,
                          Function<DataView, Optional<SchematicMetadata>> metadataFromDataFactory,
                          Function<DataView, Boolean> dataValidator,
                          String extension,
                          Function<String, Boolean> extensionValidator,
                          @Nullable ImmutableList<Integer> savableVersions,
                          int defaultSaveVersion,
                          boolean hasName,
                          boolean supportsMultipleRegions)
    {
        this.translationKey = translationKey;
        this.extension = extension;
        this.containerFactory = containerFactory;
        this.schematicFromDataFactory = schematicFromDataFactory;
        this.schematicFromRegionsFactory = schematicFromRegionsFactory;
        this.metadataFromDataFactory = metadataFromDataFactory;
        this.extensionValidator = extensionValidator;
        this.dataValidator = dataValidator;
        this.savableVersions = savableVersions != null ? savableVersions : ImmutableList.of();
        this.defaultSaveVersion = defaultSaveVersion;
        this.hasName = hasName;
        this.supportsMultipleRegions = supportsMultipleRegions;
    }

    public String getFileNameExtension()
    {
        return this.extension;
    }

    public String getDisplayName()
    {
        return StringUtils.translate(this.translationKey);
    }

    public boolean getHasName()
    {
        return this.hasName;
    }

    public ImmutableList<Integer> getSavableVersions()
    {
        return this.savableVersions;
    }

    public int getDefaultSaveVersion()
    {
        return this.defaultSaveVersion;
    }

    public boolean getSupportsMultipleRegions()
    {
        return this.supportsMultipleRegions;
    }

    public boolean isValidExtension(String extension)
    {
        return this.extensionValidator.apply(extension).booleanValue();
    }

    public boolean isValidData(DataView dataIn)
    {
        return this.dataValidator.apply(dataIn).booleanValue();
    }

    public BlockContainer createContainer(Vec3i containerSize)
    {
        return this.containerFactory.create(containerSize);
    }

    public Optional<Schematic> createSchematicFromRegions(ImmutableMap<String, SchematicRegion> regions)
    {
        return this.schematicFromRegionsFactory.apply(regions);
    }

    public Optional<Schematic> createSchematicFromData(DataView dataIn)
    {
        return this.schematicFromDataFactory.apply(dataIn);
    }

    public Optional<SchematicMetadata> createMetadataFromData(DataView data)
    {
        return this.metadataFromDataFactory.apply(data);
    }

    public static ImmutableList<SchematicType> getSavableTypes()
    {
        if (Configs.Generic.SHOW_EXTRA_SCHEMATIC_TYPES.getBooleanValue())
        {
            return ALL_TYPES;
        }

        return COMMON_TYPES;
    }

    public static List<SchematicType> getPossibleTypesFromFileName(Path file)
    {
        return getPossibleTypesFromFileName(file.getFileName().toString());
    }

    public static List<SchematicType> getPossibleTypesFromFileName(String fileName)
    {
        String extension = FileNameUtils.getFileNameExtension(fileName.toLowerCase(Locale.ROOT));
        List<SchematicType> list = new ArrayList<>();

        for (SchematicType type : ALL_TYPES)
        {
            if (type.isValidExtension(extension))
            {
                list.add(type);
            }
        }

        return list;
    }

    public static List<SchematicType> getAllTypesSortedByProbability(Path file)
    {
        List<SchematicType> possibleTypes = SchematicType.getPossibleTypesFromFileName(file);

        for (SchematicType type : SchematicType.ALL_TYPES)
        {
            if (possibleTypes.contains(type))
            {
                continue;
            }

            possibleTypes.add(type);
        }

        return possibleTypes;
    }

    public static Optional<SchematicType> getTypeFromData(Path file, DataView dataIn)
    {
        List<SchematicType> possibleTypes = SchematicType.getAllTypesSortedByProbability(file);
        return getTypeFromData(possibleTypes, dataIn);
    }

    public static Optional<SchematicType> getTypeFromData(List<SchematicType> possibleTypes, DataView dataIn)
    {
        for (SchematicType type : possibleTypes)
        {
            if (type.isValidData(dataIn))
            {
                return Optional.of(type);
            }
        }

        return Optional.empty();
    }

    /**
     * Register a new "common" SchematicType, which should always be available.
     */
    public static void registerCommonType(SchematicType type)
    {
        if (COMMON_TYPES.contains(type))
        {
            return;
        }

        ImmutableList.Builder<SchematicType> builder = ImmutableList.builder();
        builder.addAll(COMMON_TYPES);
        builder.add(type);
        COMMON_TYPES = builder.build();
        ALL_TYPES = buildAllTypesList();
    }

    /**
     * Register a new "extra / gimmick" SchematicType, which are only available as savable formats
     * when the config option "SHOW_EXTRA_SCHEMATIC_TYPES" is enabled.
     */
    public static void registerExtraType(SchematicType type)
    {
        if (EXTRA_TYPES.contains(type))
        {
            return;
        }

        ImmutableList.Builder<SchematicType> builder = ImmutableList.builder();
        builder.addAll(EXTRA_TYPES);
        builder.add(type);
        EXTRA_TYPES = builder.build();
        ALL_TYPES = buildAllTypesList();
    }

    public static ImmutableList<SchematicType> buildAllTypesList()
    {
        ImmutableList.Builder<SchematicType> builder = ImmutableList.builder();

        builder.addAll(COMMON_TYPES);
        builder.addAll(EXTRA_TYPES);

        return builder.build();
    }

    public static SchematicType.Builder builder()
    {
        return new SchematicType.Builder();
    }

    public static class Builder
    {
        private String extension;
        private BlockContainerFactory containerFactory;
        private Function<DataView, Optional<Schematic>> schematicFromDataFactory;
        private Function<ImmutableMap<String, SchematicRegion>, Optional<Schematic>> schematicFromRegionsFactory;
        private Function<DataView, Optional<SchematicMetadata>> metadataFromDataFactory;
        private Function<String, Boolean> extensionValidator;
        private Function<DataView, Boolean> dataValidator;
        private ImmutableList<Integer> savableVersions;
        private String displayName = "?";
        private int defaultSaveVersion = SchematicMetadata.DEFAULT_UNSET_VERSION;
        private boolean hasName;
        private boolean supportsMultipleRegions;

        public SchematicType.Builder setDataValidator(Function<DataView, Boolean> dataValidator)
        {
            this.dataValidator = dataValidator;
            return this;
        }

        public SchematicType.Builder setTranslationKey(String displayName)
        {
            this.displayName = displayName;
            return this;
        }

        public SchematicType.Builder setExtension(String extension)
        {
            this.extension = extension;
            return this;
        }

        public SchematicType.Builder setExtensionValidator(Function<String, Boolean> extensionValidator)
        {
            this.extensionValidator = extensionValidator;
            return this;
        }

        public SchematicType.Builder setBlockContainerFactory(BlockContainerFactory containerFactory)
        {
            this.containerFactory = containerFactory;
            return this;
        }

        public SchematicType.Builder setSchematicFromDataFactory(Function<DataView, Optional<Schematic>> schematicFromDataFactory)
        {
            this.schematicFromDataFactory = schematicFromDataFactory;
            return this;
        }

        public SchematicType.Builder setSchematicFromRegionsFactory(Function<ImmutableMap<String, SchematicRegion>, Optional<Schematic>> schematicFromRegionsFactory)
        {
            this.schematicFromRegionsFactory = schematicFromRegionsFactory;
            return this;
        }

        public SchematicType.Builder setMetadataFromDataFactory(Function<DataView, Optional<SchematicMetadata>> metadataFromDataFactory)
        {
            this.metadataFromDataFactory = metadataFromDataFactory;
            return this;
        }

        public SchematicType.Builder setSupportsMultipleRegions(boolean supportsMultipleRegions)
        {
            this.supportsMultipleRegions = supportsMultipleRegions;
            return this;
        }

        public SchematicType.Builder setHasName(boolean hasName)
        {
            this.hasName = hasName;
            return this;
        }

        public SchematicType.Builder setSavableVersions(ImmutableList<Integer> savableVersions)
        {
            this.savableVersions = savableVersions;
            return this;
        }
        
        public SchematicType.Builder setDefaultSaveVersion(int defaultSaveVersion)
        {
            this.defaultSaveVersion = defaultSaveVersion;
            return this;
        }

        public SchematicType build()
        {
            if (this.containerFactory == null ||
                this.schematicFromDataFactory == null ||
                this.schematicFromRegionsFactory == null ||
                this.metadataFromDataFactory == null ||
                this.dataValidator == null ||
                this.extension == null ||
                this.extensionValidator == null ||
                this.displayName == null)
            {
                throw new IllegalArgumentException("SchematicType.Builder#build(): Some of the values were null!");
            }

            return new SchematicType(this.displayName,
                                     this.containerFactory,
                                     this.schematicFromDataFactory,
                                     this.schematicFromRegionsFactory,
                                     this.metadataFromDataFactory,
                                     this.dataValidator,
                                     this.extension,
                                     this.extensionValidator,
                                     this.savableVersions,
                                     this.defaultSaveVersion,
                                     this.hasName,
                                     this.supportsMultipleRegions);
        }
    }
}
