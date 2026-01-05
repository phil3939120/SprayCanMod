package litematica.gui;

import java.nio.file.Path;
import javax.annotation.Nullable;
import com.google.common.collect.ImmutableList;

import malilib.config.option.BooleanConfig;
import malilib.gui.widget.DropDownListWidget;
import malilib.gui.widget.button.BooleanConfigButton;
import malilib.gui.widget.button.OnOffButton;
import malilib.overlay.message.MessageDispatcher;
import malilib.util.game.wrap.GameWrap;
import litematica.config.Configs;
import litematica.data.SchematicHolder;
import litematica.gui.widget.SchematicSaveSettingsWidget;
import litematica.scheduler.TaskScheduler;
import litematica.scheduler.task.LocalCreateSchematicTask;
import litematica.schematic.LoadedSchematic;
import litematica.schematic.Schematic;
import litematica.schematic.SchematicSaveSettings;
import litematica.schematic.SchematicSaveSettings.SaveSide;
import litematica.schematic.SchematicType;
import litematica.schematic.util.SchematicFileUtils;
import litematica.selection.AreaSelection;

public class SaveSchematicFromAreaScreen extends BaseSaveSchematicScreen
{
    protected final AreaSelection selection;
    protected final SchematicSaveSettings settings;
    protected final BooleanConfig customSettingsEnabled = new BooleanConfig("customSettings", false);
    protected final DropDownListWidget<Integer> schematicVersionDropdown;

    protected final SchematicSaveSettingsWidget settingsWidget;
    protected final BooleanConfigButton customSettingsButton;
    protected boolean hasSavableVersions;
    protected boolean supportServerSideSaving;

    public SaveSchematicFromAreaScreen(AreaSelection selection)
    {
        super(4, 74, 20 + 164 + 2, 80, "save_schematic_from_area");

        this.selection = selection;

        this.settings = new SchematicSaveSettings();
        this.settings.tryLoad(Configs.Internal.SCHEMATIC_SAVE_SETTINGS.getValue());

        String areaName = selection.getName();
        this.originalName = getFileNameFromDisplayName(areaName);
        this.fileNameTextField.setText(this.originalName);

        this.schematicTypeDropdown.setSelectionListener(this::onTypeSelected);
        this.schematicVersionDropdown = new DropDownListWidget<>(18, 10, ImmutableList.of(), e -> String.format("v%d", e));

        // TODO the dropdown widget hover overflow render does not account for going over the screen edge
        this.settingsWidget = new SchematicSaveSettingsWidget(176, 170, this.settings);
        this.schematicInfoWidget.setWidth(176);

        this.customSettingsEnabled.setBooleanValue(Configs.Internal.SAVE_WITH_CUSTOM_SETTINGS.getBooleanValue());
        this.customSettingsEnabled.setValueChangeCallback((n, o) -> this.onCustomSettingsToggled());

        this.customSettingsButton = new BooleanConfigButton(-1, 18, this.customSettingsEnabled, OnOffButton.OnOffStyle.TEXT_ON_OFF, "litematica.button.schematic_save.custom_settings");

        this.supportServerSideSaving = false; // TODO

        if (GameWrap.isSinglePlayer() == false && this.supportServerSideSaving == false)
        {
            this.settingsWidget.setClientOnlyWarnings(true);
        }

        this.addPreScreenCloseListener(this::saveSettings);
        this.setTitle("litematica.title.screen.save_schematic_from_area", areaName);
    }

    @Override
    protected void reAddActiveWidgets()
    {
        super.reAddActiveWidgets();

        //this.addWidget(this.schematicTypeDropdown);
        this.addWidget(this.customSettingsButton);
        this.addWidget(this.schematicTypeDropdown);
        this.addWidget(this.saveButton);

        if (this.hasSavableVersions)
        {
            this.addWidget(this.schematicVersionDropdown);
        }

        if (this.customSettingsEnabled.getBooleanValue())
        {
            this.addWidget(this.settingsWidget);
        }
    }

    @Override
    protected void updateWidgetPositions()
    {
        super.updateWidgetPositions();

        this.schematicInfoWidget.setRight(this.getRight() - 4);

        int x = this.schematicInfoWidget.getX();
        int y = this.fileNameTextField.getBottom() + 2;

        this.saveButton.setPosition(this.fileNameTextField.getX(), y);
        this.schematicTypeDropdown.setPosition(this.saveButton.getRight() + 2, y);
        this.schematicVersionDropdown.setPosition(this.schematicTypeDropdown.getRight() + 2, y);

        this.customSettingsButton.setPosition(x, this.y + 4);

        if (this.customSettingsEnabled.getBooleanValue())
        {
            int gap = 1;
            this.settingsWidget.setPosition(x, this.customSettingsButton.getBottom() + gap);
            this.schematicInfoWidget.setY(this.settingsWidget.getBottom() + 2);
            this.schematicInfoWidget.setHeight(this.getListHeight() - (this.schematicInfoWidget.getY() - this.getListY()));
        }
        else
        {
            this.schematicInfoWidget.setY(this.getListY());
            this.schematicInfoWidget.setHeight(this.getListHeight());
        }
    }

    @Override
    protected void saveSchematic()
    {
        boolean overwrite = isShiftDown();
        Path file = this.getSchematicFileIfCanSave(overwrite);
        SchematicSaveSettings effectiveSettings = this.getSaveSettings();

        if (file == null || effectiveSettings == null)
        {
            return;
        }

        if (shouldSaveOnDedicatedServerSide(effectiveSettings.saveSide.getValue()))
        {
            this.saveSchematicOnServer(effectiveSettings, file, overwrite);
        }
        else
        {
            this.saveSchematicOnClient(effectiveSettings, file, overwrite);
        }
    }

    protected void onTypeSelected(SchematicType type)
    {
        if (type != null)
        {
            ImmutableList<Integer> list = type.getSavableVersions();
            this.schematicVersionDropdown.replaceEntryList(list);
            this.schematicVersionDropdown.setSelectedEntry(type.getDefaultSaveVersion());
            this.hasSavableVersions = list.size() > 0;
        }
        else
        {
            this.schematicVersionDropdown.replaceEntryList(ImmutableList.of());
            this.schematicVersionDropdown.setSelectedEntry(0);
            this.hasSavableVersions = false;
        }

        this.reAddActiveWidgets();
    }

    public static boolean shouldSaveOnDedicatedServerSide(SaveSide side)
    {
        if (GameWrap.isSinglePlayer())
        {
            return false;
        }

        boolean supportsServerSideSaving = false; // TODO

        return side == SaveSide.SERVER || (side == SaveSide.AUTO && supportsServerSideSaving);
    }

    @Nullable
    protected SchematicSaveSettings getSaveSettings()
    {
        SchematicType schematicType = this.schematicTypeDropdown.getSelectedEntry();

        if (schematicType == null)
        {
            return null;
        }

        if (this.customSettingsEnabled.getBooleanValue())
        {
            return this.settingsWidget.getSaveSettings(schematicType);
        }

        return new SchematicSaveSettings();
    }

    protected void saveSchematicOnClient(SchematicSaveSettings settings, Path file, boolean overwrite)
    {
        LocalCreateSchematicTask task = new LocalCreateSchematicTask(this.selection, settings,
                                                                     sch -> this.writeSchematicToFile(sch, file, overwrite));

        TaskScheduler.getServerInstanceIfExistsOrClient().scheduleTask(task, 10);
    }

    protected void saveSchematicOnServer(SchematicSaveSettings settings, Path file, boolean overwrite)
    {
        /*
        SchematicSavePacketHandler.INSTANCE.requestSchematicSaveAllAtOnce(this.selection, settings,
                                                                          sch -> this.writeSchematicToFile(sch, file, overwrite));
        */
    }

    protected void writeSchematicToFile(Schematic schematic, Path file, boolean overwrite)
    {
        if (this.hasSavableVersions)
        {
            int version = this.schematicVersionDropdown.getSelectedEntry();

            if (schematic.getType().getSavableVersions().contains(version))
            {
                schematic.getMetadata().setSchematicVersion(version);
            }
        }

        if (SchematicFileUtils.writeToFile(schematic, file, overwrite))
        {
            this.onSchematicSaved(file);
        }
        else
        {
            LoadedSchematic loadedSchematic = new LoadedSchematic(schematic);
            SchematicHolder.INSTANCE.addSchematic(loadedSchematic, false);
            MessageDispatcher.error(8000).translate("litematica.message.error.save_schematic.failed_to_save_from_area",
                                                    file.getFileName().toString());
        }
    }

    protected void onSchematicSaved(Path file)
    {
        this.onSchematicChange();
        MessageDispatcher.success("litematica.message.success.save_schematic_new", file.getFileName().toString());
    }

    protected void onCustomSettingsToggled()
    {
        this.reAddActiveWidgets();
        this.updateWidgetPositions();
    }

    protected void saveSettings()
    {
        Configs.Internal.SAVE_WITH_CUSTOM_SETTINGS.setBooleanValue(this.customSettingsEnabled.getBooleanValue());
        Configs.Internal.SCHEMATIC_SAVE_SETTINGS.setValue(this.settings.toJson().toString());
    }
}
