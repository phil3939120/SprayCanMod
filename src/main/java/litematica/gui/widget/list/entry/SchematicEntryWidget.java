package litematica.gui.widget.list.entry;

import java.nio.file.Path;

import malilib.gui.BaseScreen;
import malilib.gui.icon.Icon;
import malilib.gui.widget.button.GenericButton;
import malilib.gui.widget.list.entry.DataListEntryWidgetData;
import malilib.listener.EventListener;
import malilib.overlay.message.MessageDispatcher;
import malilib.util.game.wrap.EntityWrap;
import malilib.util.position.BlockPos;
import litematica.config.Configs;
import litematica.data.DataManager;
import litematica.schematic.LoadedSchematic;
import litematica.data.SchematicHolder;
import litematica.gui.SaveConvertSchematicScreen;
import litematica.gui.util.LitematicaIcons;
import litematica.schematic.placement.SchematicPlacement;
import litematica.schematic.placement.SchematicPlacementManager;
import litematica.schematic.util.SchematicFileUtils;

public class SchematicEntryWidget extends BaseSchematicEntryWidget
{
    protected final GenericButton createPlacementButton;
    protected final GenericButton reloadButton;
    protected final GenericButton saveToFileButton;
    protected final GenericButton unloadButton;
    protected int buttonsStartX;

    public SchematicEntryWidget(LoadedSchematic schematic, DataListEntryWidgetData constructData)
    {
        super(schematic, constructData);

        if (this.useIconButtons())
        {
            this.createPlacementButton = createIconButton20x20(LitematicaIcons.PLACEMENT,    this::createPlacement);
            this.reloadButton          = createIconButton20x20(LitematicaIcons.RELOAD,       this::reloadFromFile);
            this.saveToFileButton      = createIconButton20x20(LitematicaIcons.SAVE_TO_DISK, this::saveToFile);
            this.unloadButton          = createIconButton20x20(LitematicaIcons.TRASH_CAN,    this::unloadSchematic);
        }
        else
        {
            this.createPlacementButton = GenericButton.create("litematica.button.schematic_list.create_placement", this::createPlacement);
            this.reloadButton          = GenericButton.create("litematica.button.schematic_list.reload",           this::reloadFromFile);
            this.saveToFileButton      = GenericButton.create("litematica.button.schematic_list.save_to_file",     this::saveToFile);
            this.unloadButton          = GenericButton.create("litematica.button.schematic_list.unload",           this::unloadSchematic);
        }

        this.createPlacementButton.translateAndAddHoverString("litematica.hover.button.schematic_list.create_placement");
        this.reloadButton.translateAndAddHoverString("litematica.hover.button.schematic_list.reload_schematic");
        this.saveToFileButton.translateAndAddHoverString("litematica.hover.button.schematic_list.save_to_file");
        this.unloadButton.translateAndAddHoverString("litematica.hover.button.schematic_list.unload");

        this.createPlacementButton.setHoverInfoRequiresShift(true);
        this.reloadButton.setHoverInfoRequiresShift(true);
        this.saveToFileButton.setHoverInfoRequiresShift(true);
        this.unloadButton.setHoverInfoRequiresShift(true);
    }

    @Override
    public void reAddSubWidgets()
    {
        super.reAddSubWidgets();

        this.addWidget(this.createPlacementButton);
        this.addWidget(this.reloadButton);
        this.addWidget(this.saveToFileButton);
        this.addWidget(this.unloadButton);
    }

    @Override
    public void updateSubWidgetPositions()
    {
        super.updateSubWidgetPositions();

        this.modificationNoticeIcon.centerVerticallyInside(this);
        this.createPlacementButton.centerVerticallyInside(this);
        this.reloadButton.centerVerticallyInside(this);
        this.saveToFileButton.centerVerticallyInside(this);
        this.unloadButton.centerVerticallyInside(this);

        this.unloadButton.setRight(this.getRight() - 2);
        this.reloadButton.setRight(this.unloadButton.getX() - 1);
        this.saveToFileButton.setRight(this.reloadButton.getX() - 1);
        this.createPlacementButton.setRight(this.saveToFileButton.getX() - 1);
        this.modificationNoticeIcon.setRight(this.createPlacementButton.getX() - 2);

        this.buttonsStartX = this.modificationNoticeIcon.getX() - 1;
    }

    @Override
    public boolean canHoverAt(int mouseX, int mouseY)
    {
        return mouseX <= this.buttonsStartX && super.canHoverAt(mouseX, mouseY);
    }

    public static GenericButton createIconButton20x20(Icon icon, EventListener listener)
    {
        GenericButton button = GenericButton.create(20, 20, icon);
        button.setRenderButtonBackgroundTexture(true);
        button.setActionListener(listener);
        return button;
    }

    protected boolean useIconButtons()
    {
        return Configs.Internal.SCHEMATIC_LIST_ICON_BUTTONS.getBooleanValue();
    }

    protected void createPlacement()
    {
        LoadedSchematic schematic = this.getData();
        BlockPos pos = EntityWrap.getCameraEntityBlockPos();
        String name = schematic.schematic.getMetadata().getSchematicName();
        boolean createAsEnabled = BaseScreen.isShiftDown() == false;

        SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
        SchematicPlacement placement = SchematicPlacement.create(schematic, pos, name, createAsEnabled);
        manager.addSchematicPlacement(placement, true);
        manager.setSelectedSchematicPlacement(placement);
    }

    protected void reloadFromFile()
    {
        LoadedSchematic loadedSchematic = this.getData();

        if (loadedSchematic.file.isPresent())
        {
            Path file = loadedSchematic.file.get();

            if (SchematicFileUtils.readFromFile(loadedSchematic.schematic, file))
            {
                SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
                manager.getAllPlacementsOfSchematic(loadedSchematic).forEach(manager::markChunksForRebuild);
            }
            else
            {
                MessageDispatcher.error("litematica.message.error.schematic_read.failed_to_read_from_file", file);
            }
        }
        else
        {
            MessageDispatcher.error("litematica.error.schematic_read.no_file_set");
        }
    }

    protected void saveToFile()
    {
        BaseScreen.openScreenWithParent(new SaveConvertSchematicScreen(this.getData(), true));
    }

    protected void unloadSchematic()
    {
        SchematicHolder.INSTANCE.removeSchematic(this.getData());
        this.listWidget.refreshEntries();
    }
}
