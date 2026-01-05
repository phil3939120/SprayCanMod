package litematica.gui;

import malilib.config.option.BooleanConfig;
import malilib.gui.BaseScreen;
import malilib.gui.widget.BaseTextFieldWidget;
import malilib.gui.widget.DropDownListWidget;
import malilib.gui.widget.IconWidget;
import malilib.gui.widget.LabelWidget;
import malilib.gui.widget.button.BooleanConfigButton;
import malilib.gui.widget.button.GenericButton;
import malilib.gui.widget.button.OnOffButton;
import malilib.input.Keys;
import malilib.overlay.message.MessageDispatcher;
import malilib.util.game.wrap.GameWrap;
import litematica.config.Configs;
import litematica.data.SchematicHolder;
import litematica.gui.util.SchematicTypeIcons;
import litematica.gui.widget.SchematicSaveSettingsWidget;
import litematica.scheduler.TaskScheduler;
import litematica.scheduler.task.LocalCreateSchematicTask;
import litematica.schematic.LoadedSchematic;
import litematica.schematic.Schematic;
import litematica.schematic.SchematicSaveSettings;
import litematica.schematic.SchematicSaveSettings.SaveSide;
import litematica.schematic.SchematicType;
import litematica.selection.AreaSelection;

public class CreateInMemorySchematicScreen extends BaseScreen
{
    protected final AreaSelection selection;
    protected final SchematicSaveSettings settings;
    protected final BooleanConfig customSettingsEnabled = new BooleanConfig("customSettings", false);

    protected final SchematicSaveSettingsWidget settingsWidget;
    protected final DropDownListWidget<SchematicType> schematicTypeDropdown;
    protected final LabelWidget nameLabel;
    protected final BaseTextFieldWidget nameTextField;
    protected final BooleanConfigButton customSettingsButton;
    protected final GenericButton saveButton;
    protected boolean supportServerSideSaving;

    public CreateInMemorySchematicScreen(AreaSelection selection)
    {
        this.selection = selection;
        this.settings = new SchematicSaveSettings();
        this.settings.tryLoad(Configs.Internal.SCHEMATIC_SAVE_SETTINGS.getValue());

        this.nameLabel = new LabelWidget("litematica.label.schematic_save.schematic_name_colon");
        this.nameTextField = new BaseTextFieldWidget(240, 16, selection.getName());
        this.nameTextField.setFocused(true);

        this.settingsWidget = new SchematicSaveSettingsWidget(180, 170, this.settings);
        this.schematicTypeDropdown = new DropDownListWidget<>(20, 6, SchematicType.getSavableTypes(), SchematicType::getDisplayName, t -> new IconWidget(SchematicTypeIcons.getIconForType(t)));
        this.schematicTypeDropdown.setSelectedEntry(SchematicType.LITEMATICA);
        this.saveButton = GenericButton.create(20, "litematica.button.schematic_save.create_schematic", this::createSchematic);

        this.customSettingsEnabled.setBooleanValue(Configs.Internal.SAVE_WITH_CUSTOM_SETTINGS.getBooleanValue());
        this.customSettingsEnabled.setValueChangeCallback((n, o) -> this.onCustomSettingsToggled());
        this.customSettingsButton = new BooleanConfigButton(-1, 20, this.customSettingsEnabled, OnOffButton.OnOffStyle.TEXT_ON_OFF, "litematica.button.schematic_save.custom_settings");

        this.supportServerSideSaving = false; // TODO

        if (GameWrap.isSinglePlayer() == false && this.supportServerSideSaving == false)
        {
            this.settingsWidget.setClientOnlyWarnings(true);
        }

        this.useTitleHierarchy = false;

        this.setTitle("litematica.title.screen.create_in_memory_schematic");
        this.addPreScreenCloseListener(this::saveSettings);

        this.setScreenWidthAndHeight(280, 280);
        this.centerOnScreen();
    }

    @Override
    protected void reAddActiveWidgets()
    {
        super.reAddActiveWidgets();

        this.addWidget(this.nameLabel);
        this.addWidget(this.nameTextField);
        this.addWidget(this.schematicTypeDropdown);
        this.addWidget(this.saveButton);
        this.addWidget(this.customSettingsButton);

        if (this.customSettingsEnabled.getBooleanValue())
        {
            this.addWidget(this.settingsWidget);
        }
    }

    @Override
    protected void updateWidgetPositions()
    {
        super.updateWidgetPositions();

        int x = this.x + 6;
        int y = this.y + 30;

        this.nameLabel.setPosition(x, y + 4);
        this.nameTextField.setPosition(this.nameLabel.getRight() + 2, y);

        y = this.nameTextField.getBottom() + 4;

        this.saveButton.setPosition(x, y);
        this.schematicTypeDropdown.setPosition(this.saveButton.getRight() + 4, y);

        this.customSettingsButton.setPosition(x, this.saveButton.getBottom() + 10);
        this.settingsWidget.setPosition(x, this.customSettingsButton.getBottom() + 2);
    }

    @Override
    public boolean onKeyTyped(int keyCode, int scanCode, int modifiers)
    {
        if (this.nameTextField.isFocused() && keyCode == Keys.KEY_ENTER)
        {
            this.createSchematic();
            return true;
        }

        return super.onKeyTyped(keyCode, scanCode, modifiers);
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

    protected void createSchematic()
    {
        SchematicType type = this.schematicTypeDropdown.getSelectedEntry();

        if (type == null)
        {
            return;
        }

        SchematicSaveSettings effectiveSettings;

        if (this.customSettingsEnabled.getBooleanValue())
        {
            effectiveSettings = this.settingsWidget.getSaveSettings(type);
        }
        else
        {
            effectiveSettings = new SchematicSaveSettings();
        }

        SaveSide side = effectiveSettings.saveSide.getValue();

        if (SaveSchematicFromAreaScreen.shouldSaveOnDedicatedServerSide(side))
        {
            this.saveSchematicOnServer(effectiveSettings);
        }
        else
        {
            this.saveSchematicOnClient(effectiveSettings);
        }
    }

    protected void saveSchematicOnClient(SchematicSaveSettings settings)
    {
        String name = this.nameTextField.getText();
        LocalCreateSchematicTask task = new LocalCreateSchematicTask(this.selection, settings,
                                                                     sch -> this.onInMemorySchematicCreated(sch, name));

        TaskScheduler.getServerInstanceIfExistsOrClient().scheduleTask(task, 10);
    }

    protected void saveSchematicOnServer(SchematicSaveSettings settings)
    {
        /*
        SchematicSavePacketHandler.INSTANCE.requestSchematicSaveAllAtOnce(this.selection, settings,
                                                                          sch -> this.writeSchematicToFile(sch, file, overwrite));
        */
    }

    protected void onInMemorySchematicCreated(Schematic schematic, String name)
    {
        SchematicHolder.INSTANCE.addSchematic(new LoadedSchematic(schematic), true);
        MessageDispatcher.success("litematica.message.in_memory_schematic_created", name);
    }
}
