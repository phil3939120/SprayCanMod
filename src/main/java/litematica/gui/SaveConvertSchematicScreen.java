package litematica.gui;

import java.nio.file.Path;
import java.util.Optional;
import com.google.common.collect.ImmutableList;

import malilib.gui.widget.RadioButtonWidget;
import malilib.overlay.message.MessageDispatcher;
import malilib.util.StringUtils;
import litematica.data.DataManager;
import litematica.schematic.LoadedSchematic;
import litematica.schematic.Schematic;
import litematica.schematic.SchematicMetadata;
import litematica.schematic.SchematicType;
import litematica.schematic.placement.SchematicPlacementManager;
import litematica.schematic.util.SchematicFileUtils;

public class SaveConvertSchematicScreen extends BaseSaveSchematicScreen
{
    protected final RadioButtonWidget<UpdatePlacementsOption> updatePlacementsWidget;
    protected final LoadedSchematic loadedSchematic;
    protected final boolean addUpdatePlacementsElement;

    public SaveConvertSchematicScreen(LoadedSchematic loadedSchematic,
                                      boolean addUpdatePlacementsElement)
    {
        this(loadedSchematic, addUpdatePlacementsElement, "schematic_manager");
    }

    public SaveConvertSchematicScreen(LoadedSchematic loadedSchematic,
                                      boolean addUpdatePlacementsElement,
                                      String browserContext)
    {
        super(10, 74, 20 + 170 + 2, 80, browserContext);

        this.loadedSchematic = loadedSchematic;
        this.addUpdatePlacementsElement = addUpdatePlacementsElement;
        this.originalName = getDefaultFileNameForSchematic(loadedSchematic);

        this.updatePlacementsWidget = new RadioButtonWidget<>(UpdatePlacementsOption.VALUES,
                                                              UpdatePlacementsOption::getDisplayString,
                                                              "litematica.hover.save_schematic.update_dependent_placements");
        this.updatePlacementsWidget.setSelection(UpdatePlacementsOption.NONE, false);

        this.fileNameTextField.setText(this.originalName);

        this.setTitle("litematica.title.screen.save_or_convert_schematic", loadedSchematic.schematic.getMetadata().getSchematicName());
    }

    @Override
    protected void reAddActiveWidgets()
    {
        super.reAddActiveWidgets();

        this.addWidget(this.schematicTypeDropdown);
        this.addWidget(this.saveButton);

        if (this.addUpdatePlacementsElement)
        {
            this.addWidget(this.updatePlacementsWidget);
        }
    }

    @Override
    protected void updateWidgetPositions()
    {
        if (this.addUpdatePlacementsElement)
        {
            this.listY = 76;
            this.totalListMarginY = 80;
        }
        else
        {
            this.listY = 64;
            this.totalListMarginY = 68;
        }

        super.updateWidgetPositions();

        this.schematicTypeDropdown.setPosition(this.fileNameTextField.getX(), this.fileNameTextField.getBottom() + 2);
        this.saveButton.setPosition(this.schematicTypeDropdown.getRight() + 2, this.fileNameTextField.getBottom() + 2);
        this.updatePlacementsWidget.setPosition(this.saveButton.getRight() + 4, this.saveButton.getY());
    }

    @Override
    protected void saveSchematic()
    {
        boolean overwrite = isShiftDown();
        Path file = this.getSchematicFileIfCanSave(overwrite);

        if (file == null)
        {
            return;
        }

        SchematicType outputType = this.schematicTypeDropdown.getSelectedEntry();
        Schematic schematic = this.loadedSchematic.schematic;

        if (outputType != this.loadedSchematic.schematic.getType())
        {
            try
            {
                Optional<Schematic> schematicOpt = outputType.createSchematicFromRegions(schematic.getRegions());

                if (schematicOpt.isPresent())
                {
                    schematic = schematicOpt.get();
                    SchematicMetadata meta = schematic.getMetadata();
                    meta.copyFrom(this.loadedSchematic.schematic.getMetadata());
                    meta.setTimeModifiedToNow();
                }
                else
                {
                    MessageDispatcher.error("litematica.message.error.save_schematic.failed_to_convert");
                }
            }
            catch (Exception e)
            {
                MessageDispatcher.error("litematica.message.error.save_schematic.failed_to_convert");
                MessageDispatcher.error(8000).translate(e.getMessage());
                return;
            }
        }

        if (SchematicFileUtils.writeToFile(schematic, file, overwrite))
        {
            this.onSchematicSaved(file);
        }
        else
        {
            MessageDispatcher.error("litematica.message.error.save_schematic.failed_to_save_converted",
                                    file.getFileName().toString());
        }
    }

    protected void onSchematicSaved(Path newSchematicFile)
    {
        this.loadedSchematic.clearModifiedSinceSaved();
        this.onSchematicChange();

        String key = "litematica.message.success.save_schematic_convert";
        MessageDispatcher.success(key, newSchematicFile.getFileName().toString());

        UpdatePlacementsOption option = this.updatePlacementsWidget.getSelection();

        if (this.addUpdatePlacementsElement && option != UpdatePlacementsOption.NONE)
        {
            boolean selectedOnly = option == UpdatePlacementsOption.SELECTED;
            this.updateDependentPlacements(newSchematicFile, selectedOnly);
        }
    }

    protected void updateDependentPlacements(Path newSchematicFile, boolean selectedOnly)
    {
        if (this.loadedSchematic != null)
        {
            SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
            manager.updateDependentPlacements(this.loadedSchematic, newSchematicFile, selectedOnly);
        }
    }

    public enum UpdatePlacementsOption
    {
        NONE        ("litematica.name.save_schematic.update_placements.none"),
        SELECTED    ("litematica.name.save_schematic.update_placements.selected"),
        ALL         ("litematica.name.save_schematic.update_placements.all");

        public static final ImmutableList<UpdatePlacementsOption> VALUES = ImmutableList.copyOf(values());

        private final String translationKey;

        UpdatePlacementsOption(String translationKey)
        {
            this.translationKey = translationKey;
        }

        public String getDisplayString()
        {
            return StringUtils.translate(this.translationKey);
        }
    }
}
