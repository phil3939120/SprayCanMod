package litematica.gui;

import malilib.gui.BaseScreen;
import litematica.gui.widget.SchematicInfoWidgetBySchematic;
import litematica.schematic.LoadedSchematic;

public class SchematicInfoPopupScreen extends BaseScreen
{
    protected final SchematicInfoWidgetBySchematic schematicInfoWidget;
    protected final LoadedSchematic loadedSchematic;

    public SchematicInfoPopupScreen(LoadedSchematic loadedSchematic, int height)
    {
        this.loadedSchematic = loadedSchematic;
        this.backgroundColor = 0xFF000000;
        this.renderBorder = true;
        this.useTitleHierarchy = false;

        this.schematicInfoWidget = new SchematicInfoWidgetBySchematic(190, height - 30);

        Runnable clearTask = this::clearCache;
        this.addPreInitListener(clearTask);
        this.addPreScreenCloseListener(clearTask);

        // This needs to be set after the screen has been opened and initialized and the cache won't be cleared after
        this.addPostInitListener(() -> this.schematicInfoWidget.setActiveEntry(this.loadedSchematic));

        this.setTitle("litematica.title.screen.schematic_info_popup");
        this.setScreenWidthAndHeight(200, height);
        this.centerOnScreen();
    }

    @Override
    protected void reAddActiveWidgets()
    {
        super.reAddActiveWidgets();
        this.addWidget(this.schematicInfoWidget);
    }

    @Override
    protected void updateWidgetPositions()
    {
        super.updateWidgetPositions();

        this.schematicInfoWidget.setPosition(this.x + 5, this.y + 20);
    }

    protected void clearCache()
    {
        this.schematicInfoWidget.setActiveEntry(null);
        this.schematicInfoWidget.clearCache();
    }
}
