package litematica.gui;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import malilib.gui.BaseListScreen;
import malilib.gui.widget.button.GenericButton;
import malilib.gui.widget.list.DataListWidget;
import litematica.schematic.LoadedSchematic;
import litematica.data.SchematicHolder;
import litematica.gui.widget.SchematicInfoWidgetBySchematic;
import litematica.gui.widget.list.entry.BaseSchematicEntryWidget;

public class SelectLoadedSchematicScreen extends BaseListScreen<DataListWidget<LoadedSchematic>>
{
    protected final GenericButton selectSchematicButton;
    protected final SchematicInfoWidgetBySchematic schematicInfoWidget;
    protected final Consumer<LoadedSchematic> schematicConsumer;

    public SelectLoadedSchematicScreen(Consumer<LoadedSchematic> schematicConsumer)
    {
        super(10, 30, 192, 56);

        this.schematicConsumer = schematicConsumer;
        this.schematicInfoWidget = new SchematicInfoWidgetBySchematic(170, 290);
        this.selectSchematicButton = GenericButton.create("litematica.button.select_schematic.confirm", this::onSelectButtonClicked);

        this.setTitle("litematica.title.screen.select_loaded_schematic");
        this.addPreScreenCloseListener(this::clearSchematicInfoCache);
    }

    @Override
    protected void reAddActiveWidgets()
    {
        super.reAddActiveWidgets();

        this.addWidget(this.schematicInfoWidget);
        this.addWidget(this.selectSchematicButton);
    }

    @Override
    protected void updateWidgetPositions()
    {
        super.updateWidgetPositions();

        int y = this.getBottom() - 24;
        this.selectSchematicButton.setPosition(this.x + 10, y);

        this.schematicInfoWidget.setHeight(this.getListHeight());
        this.schematicInfoWidget.setRight(this.getRight() - 10);
        this.schematicInfoWidget.setY(this.getListY());
    }

    @Override
    protected DataListWidget<LoadedSchematic> createListWidget()
    {
        Supplier<List<LoadedSchematic>> supplier = SchematicHolder.INSTANCE::getAllSchematics;
        DataListWidget<LoadedSchematic> listWidget = new DataListWidget<>(supplier, true);
        listWidget.addDefaultSearchBar();
        listWidget.setEntryFilter(BaseSchematicEntryWidget::schematicSearchFilter);
        listWidget.setDataListEntryWidgetFactory(BaseSchematicEntryWidget::new);
        listWidget.setAllowSelection(true);
        listWidget.getEntrySelectionHandler().setSelectionListener(this::onSelectionChange);

        return listWidget;
    }

    protected void onSelectButtonClicked()
    {
        LoadedSchematic loadedSchematic = this.getListWidget().getLastSelectedEntry();

        if (loadedSchematic != null)
        {
            this.openParentScreen();
            this.schematicConsumer.accept(loadedSchematic);
        }
    }

    public void onSelectionChange(@Nullable LoadedSchematic entry)
    {
        this.schematicInfoWidget.setActiveEntry(entry);
    }

    protected void clearSchematicInfoCache()
    {
        this.schematicInfoWidget.clearCache();
    }
}
