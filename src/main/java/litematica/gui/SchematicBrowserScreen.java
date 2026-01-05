package litematica.gui;

import java.nio.file.Path;
import java.util.Optional;
import javax.annotation.Nullable;

import malilib.gui.widget.CheckBoxWidget;
import malilib.gui.widget.button.GenericButton;
import malilib.gui.widget.list.BaseFileBrowserWidget.DirectoryEntry;
import malilib.gui.widget.list.BaseFileBrowserWidget.DirectoryEntryType;
import malilib.overlay.message.MessageDispatcher;
import litematica.Reference;
import litematica.config.Configs;
import litematica.data.DataManager;
import litematica.data.SchematicHolder;
import litematica.materials.MaterialListUtils;
import litematica.schematic.LoadedSchematic;
import litematica.schematic.placement.SchematicPlacementManager;

public class SchematicBrowserScreen extends BaseSchematicBrowserScreen
{
    protected final GenericButton loadButton;
    protected final GenericButton materialListButton;
    protected final CheckBoxWidget createPlacementCheckbox;

    public SchematicBrowserScreen()
    {
        super(10, 24, 20 + 170 + 2, 70, "schematic_browser");

        this.loadButton             = GenericButton.create("litematica.button.schematic_browser.load_schematic", this::loadSchematic);
        this.materialListButton     = GenericButton.create("litematica.button.misc.material_list", this::createMaterialList);
        this.materialListButton.translateAndAddHoverString("litematica.hover.button.schematic_browser.create_material_list");
        this.createPlacementCheckbox = new CheckBoxWidget("litematica.checkmark.schematic_browser.create_placement",
                                                          "litematica.hover.schematic_browser.create_placement",
                                                          Configs.Internal.CREATE_PLACEMENT_ON_LOAD);

        this.setTitle("litematica.title.screen.schematic_browser", Reference.MOD_VERSION);
    }

    @Override
    protected void reAddActiveWidgets()
    {
        super.reAddActiveWidgets();

        this.addWidget(this.createPlacementCheckbox);
        this.addWidget(this.loadButton);
        this.addWidget(this.mainMenuScreenButton);
        this.addWidget(this.materialListButton);
    }

    @Override
    protected void updateWidgetPositions()
    {
        super.updateWidgetPositions();

        this.loadButton.setX(this.x + 10);
        this.loadButton.setBottom(this.getBottom() - 6);
        this.createPlacementCheckbox.setPosition(this.loadButton.getX(), this.loadButton.getY() - 14);
        this.materialListButton.setPosition(this.loadButton.getRight() + 4, this.loadButton.getY());

        this.mainMenuScreenButton.setRight(this.getRight() - 10);
        this.mainMenuScreenButton.setY(this.loadButton.getY());
    }

    protected void loadSchematic()
    {
        LoadedSchematic loadedSchematic = tryLoadSchematic(this.getListWidget().getEntrySelectionHandler().getLastSelectedEntry());

        if (loadedSchematic == null || loadedSchematic.file.isPresent() == false)
        {
            return;
        }

        SchematicHolder.INSTANCE.addSchematic(loadedSchematic, true);
        MessageDispatcher.success("litematica.message.info.schematic_loaded_to_memory",
                                  loadedSchematic.file.get().getFileName().toString());

        // Clear the parent after loading as schematic, as presumably in most cases
        // the user would just want to close the screen at that point.
        this.setParent(null);

        if (Configs.Internal.CREATE_PLACEMENT_ON_LOAD.getBooleanValue())
        {
            SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
            manager.createPlacementForNewlyLoadedSchematic(loadedSchematic, isShiftDown() == false);
        }
    }

    protected void createMaterialList()
    {
        LoadedSchematic loadedSchematic = tryLoadSchematic(this.getListWidget().getEntrySelectionHandler().getLastSelectedEntry());

        if (loadedSchematic != null)
        {
            MaterialListUtils.openMaterialListScreenFor(loadedSchematic.schematic);
        }
    }

    @Nullable
    public static LoadedSchematic tryLoadSchematic(DirectoryEntry entry)
    {
        Path file = entry != null && entry.getType() == DirectoryEntryType.FILE ? entry.getFullPath() : null;

        if (file == null)
        {
            MessageDispatcher.error("litematica.message.error.schematic_load.no_schematic_selected");
            return null;
        }

        Optional<LoadedSchematic> opt = LoadedSchematic.tryLoadSchematic(file);

        if (opt.isPresent() == false)
        {
            MessageDispatcher.error("litematica.message.error.schematic_load.failed_to_load", entry.getName());
            return null;
        }

        return opt.get();
    }
}
