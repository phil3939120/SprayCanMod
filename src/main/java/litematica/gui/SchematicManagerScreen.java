package litematica.gui;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;

import malilib.gui.ConfirmActionScreen;
import malilib.gui.TextInputScreen;
import malilib.gui.widget.button.GenericButton;
import malilib.gui.widget.list.BaseFileBrowserWidget;
import malilib.gui.widget.list.BaseFileBrowserWidget.DirectoryEntry;
import malilib.overlay.message.MessageDispatcher;
import malilib.util.FileNameUtils;
import malilib.util.FileUtils;
import litematica.Reference;
import litematica.config.Hotkeys;
import litematica.data.DataManager;
import litematica.data.SchematicHolder;
import litematica.gui.util.AbstractSchematicInfoCache.SchematicInfo;
import litematica.scheduler.TaskScheduler;
import litematica.scheduler.task.SetSchematicPreviewTask;
import litematica.schematic.LoadedSchematic;
import litematica.schematic.Schematic;
import litematica.schematic.SchematicMetadata;
import litematica.schematic.SchematicType;
import litematica.schematic.placement.SchematicPlacement;
import litematica.schematic.placement.SchematicPlacementManager;
import litematica.schematic.util.SchematicFileUtils;

public class SchematicManagerScreen extends BaseSchematicBrowserScreen
{
    protected final GenericButton convertSchematicButton;
    protected final GenericButton deleteFileButton;
    protected final GenericButton editDescriptionButton;
    protected final GenericButton fixExtensionButton;
    protected final GenericButton removePreviewButton;
    protected final GenericButton renameFileButton;
    protected final GenericButton renameSchematicButton;
    protected final GenericButton setPreviewButton;

    public SchematicManagerScreen()
    {
        super(10, 24, 20 + 170 + 2, 70, "schematic_manager");

        this.convertSchematicButton = GenericButton.create("litematica.button.schematic_manager.convert_format", this::convertSchematic);
        this.deleteFileButton       = GenericButton.create("litematica.button.schematic_manager.delete", this::deleteFile);
        this.editDescriptionButton  = GenericButton.create("litematica.button.schematic_manager.edit_description", this::editDescription);
        this.fixExtensionButton     = GenericButton.create("litematica.button.schematic_manager.fix_extension", this::fixFileNameExtension);
        this.removePreviewButton    = GenericButton.create("litematica.button.schematic_manager.remove_preview", this::removePreview);
        this.renameFileButton       = GenericButton.create("litematica.button.schematic_manager.rename_file", this::renameFile);
        this.renameSchematicButton  = GenericButton.create("litematica.button.schematic_manager.rename_schematic", this::renameSchematic);
        this.setPreviewButton       = GenericButton.create("litematica.button.schematic_manager.set_preview", this::setPreview);

        this.convertSchematicButton.translateAndAddHoverString("litematica.hover.button.schematic_manager.convert_format");
        this.editDescriptionButton.translateAndAddHoverString("litematica.hover.button.schematic_manager.edit_description");
        this.fixExtensionButton.translateAndAddHoverString("litematica.hover.button.schematic_manager.fix_extension");
        this.renameSchematicButton.translateAndAddHoverString("litematica.hover.button.schematic_manager.rename_schematic");
        this.setPreviewButton.translateAndAddHoverString("litematica.hover.button.schematic_manager.set_preview");

        this.setTitle("litematica.title.screen.schematic_manager", Reference.MOD_VERSION);
    }

    @Override
    protected void reAddActiveWidgets()
    {
        super.reAddActiveWidgets();

        DirectoryEntry entry = this.getListWidget().getLastSelectedEntry();

        if (entry != null)
        {
            this.addWidget(this.convertSchematicButton);
            this.addWidget(this.deleteFileButton);
            this.addWidget(this.editDescriptionButton);
            this.addWidget(this.renameFileButton);
            this.addWidget(this.renameSchematicButton);
            this.addWidget(this.setPreviewButton);

            Optional<LoadedSchematic> opt = this.getLastSelectedSchematic();
    
            if (opt.isPresent())
            {
                Schematic schematic = opt.get().schematic;

                if (schematic.getMetadata().getPreviewImagePixelData() != null)
                {
                    this.addWidget(this.removePreviewButton);
                }

                /*
                if (this.hasWrongExtension(entry, schematic.getType()))
                {
                    this.addWidget(this.fixExtensionButton);
                }
                */
            }

            // Temporary solution (WIP rewriting schematics, only Sponge schematics fully load atm)
            SchematicInfo info = this.schematicInfoWidget.getSelectedSchematicInfo();

            if (info != null && this.hasWrongExtension(entry, info.schematicType))
            {
                this.addWidget(this.fixExtensionButton);
            }
        }
    }

    @Override
    protected void updateWidgetPositions()
    {
        super.updateWidgetPositions();

        int y = this.getBottom() - 44;
        this.renameSchematicButton.setPosition(this.x + 10, y);
        this.setPreviewButton.setPosition(this.renameSchematicButton.getRight() + 2, y);
        this.editDescriptionButton.setPosition(this.setPreviewButton.getRight() + 2, y);
        this.fixExtensionButton.setPosition(this.editDescriptionButton.getRight() + 2, y);

        y += 21;
        this.renameFileButton.setPosition(this.x + 10, y);
        this.convertSchematicButton.setPosition(this.renameFileButton.getRight() + 2, y);
        this.deleteFileButton.setPosition(this.convertSchematicButton.getRight() + 2, y);
        this.removePreviewButton.setPosition(this.deleteFileButton.getRight() + 2, y);
    }

    @Override
    protected BaseFileBrowserWidget createListWidget()
    {
        BaseFileBrowserWidget listWidget = super.createListWidget();
        listWidget.setAllowFileOperations(true);
        return listWidget;
    }

    @Override
    public void onSelectionChange(@Nullable DirectoryEntry entry)
    {
        super.onSelectionChange(entry);
        this.reAddActiveWidgets();
    }

    protected void convertSchematic()
    {
        Optional<LoadedSchematic> opt = this.getLastSelectedSchematic();

        if (opt.isPresent())
        {
            SaveConvertSchematicScreen screen = new SaveConvertSchematicScreen(opt.get(), false);
            screen.setParent(this);
            openScreen(screen);
        }
    }

    protected void deleteFile()
    {
        DirectoryEntry entry = this.getListWidget().getLastSelectedEntry();

        if (entry != null)
        {
            String title = "litematica.title.screen.schematic_manager.confirm_file_deletion";
            String msg = "litematica.info.schematic_manager.confirm_file_deletion";
            Path file = entry.getFullPath().toAbsolutePath();
            String fileName = file.toString();
            ConfirmActionScreen screen = new ConfirmActionScreen(320, title, () -> this.executeFileDelete(file), msg, fileName);
            screen.setParent(this);
            openPopupScreen(screen);
        }
    }

    protected void editDescription()
    {
        Optional<LoadedSchematic> opt = this.getLastSelectedSchematic();

        if (opt.isPresent())
        {
            LoadedSchematic loadedSchematic = opt.get();
            SchematicMetadata meta = loadedSchematic.schematic.getMetadata();
            String title = "litematica.title.screen.schematic_manager.edit_description";
            // TODO use a TextAreaWidget once one has been implemented in malilib
            TextInputScreen screen = new TextInputScreen(title, meta.getDescription(), str -> this.setSchematicDescription(str, loadedSchematic));
            screen.setParent(this);
            openPopupScreen(screen);
        }
    }

    protected void fixFileNameExtension()
    {
        DirectoryEntry entry = this.getListWidget().getLastSelectedEntry();
        //Optional<LoadedSchematic> opt = this.getLastSelectedSchematic();
        SchematicInfo info = this.schematicInfoWidget.getSelectedSchematicInfo();

        //if (entry != null && opt.isPresent())
        if (entry != null && info != null)
        {
            //SchematicType type = opt.get().schematic.getType();
            SchematicType type = info.schematicType;

            if (this.hasWrongExtension(entry, type))
            {
                String oldName = entry.getName();
                int dotIndex = oldName.lastIndexOf('.');
                String newExtension = type.getFileNameExtension();
                String newName = oldName.substring(0, dotIndex + 1) + newExtension;
                Path file = entry.getFullPath();
                Path newFile = file.getParent().resolve(newName);

                if (FileUtils.renameFile(file, newFile, MessageDispatcher::error))
                {
                    this.onSchematicChange();
                }
                else
                {
                    MessageDispatcher.error("litematica.message.error.schematic_manager.fix_extension.rename_failed");
                }
            }
        }
        else
        {
            MessageDispatcher.error("litematica.message.error.schematic_manager.fix_extension.nothing_selected");
        }
    }

    protected boolean hasWrongExtension(DirectoryEntry entry, SchematicType type)
    {
        return FileNameUtils.getFileNameExtension(entry.getName()).equalsIgnoreCase(type.getFileNameExtension()) == false;
    }

    protected void removePreview()
    {
        Optional<LoadedSchematic> opt = this.getLastSelectedSchematic();
        Path file = this.lastSelectedSchematicFile;

        if (opt.isPresent() && file != null && file.getFileName() != null)
        {
            String title = "litematica.title.screen.schematic_manager.confirm_preview_removal";
            String msg = "litematica.info.schematic_manager.confirm_preview_removal";
            String name = file.getFileName().toString();
            ConfirmActionScreen screen = new ConfirmActionScreen(320, title, () -> this.removeSchematicPreviewImage(opt.get()), msg, name);
            screen.setParent(this);
            openPopupScreen(screen);
        }
    }

    protected boolean executeFileDelete(Path file)
    {
        try
        {
            boolean success = FileUtils.delete(file);
            this.onSchematicChange();
            return success;
        }
        catch (Exception e)
        {
            MessageDispatcher.error("malilib.message.error.failed_to_delete_file", file.toAbsolutePath().toString());
        }

        return false;
    }

    protected void renameFile()
    {
        DirectoryEntry entry = this.getListWidget().getLastSelectedEntry();

        if (entry != null)
        {
            Path oldFile = entry.getFullPath();
            String oldName = FileNameUtils.getFileNameWithoutExtension(oldFile.getFileName().toString());
            String title = "litematica.title.screen.schematic_manager.rename_file";
            TextInputScreen screen = new TextInputScreen(title, oldName,
                                                         (str) -> this.renameFileToName(oldFile, str));
            screen.setParent(this);
            openPopupScreen(screen);
        }
    }

    protected void setPreview()
    {
        Optional<LoadedSchematic> opt = this.getLastSelectedSchematic();

        if (opt.isPresent())
        {
            LoadedSchematic loadedSchematic = opt.get();
            Schematic schematic = loadedSchematic.schematic;

            if (schematic.getType() == SchematicType.LITEMATICA)
            {
                SetSchematicPreviewTask task = new SetSchematicPreviewTask(loadedSchematic);
                TaskScheduler.getInstanceClient().scheduleTask(task, 1);

                String hotkeyName = Hotkeys.SET_SCHEMATIC_PREVIEW.getDisplayName();
                String hotkeyValue = Hotkeys.SET_SCHEMATIC_PREVIEW.getKeyBind().getKeysDisplayString();
                MessageDispatcher.generic(6000).translate("litematica.message.info.schematic_manager.set_preview",
                                                          hotkeyName, hotkeyValue);
            }
            else
            {
                MessageDispatcher.error("litematica.message.error.schematic_manager.schematic_type_has_no_preview",
                                        schematic.getType().getDisplayName());
            }
        }
    }

    protected void renameSchematic()
    {
        Optional<LoadedSchematic> opt = this.getLastSelectedSchematic();

        if (opt.isPresent())
        {
            LoadedSchematic loadedSchematic = opt.get();

            /*
            if (loadedSchematic.schematic.getType().getHasName() == false)
            {
                MessageDispatcher.error("litematica.message.error.schematic_manager.schematic_type_has_no_name");
                return;
            }
            */

            String oldName = loadedSchematic.schematic.getMetadata().getSchematicName();
            String title = "litematica.title.screen.schematic_manager.rename_schematic";
            TextInputScreen screen = new TextInputScreen(title, oldName, str -> this.renameSchematicToName(loadedSchematic, str));
            screen.setParent(this);
            openPopupScreen(screen);
        }
    }

    protected boolean renameFileToName(Path oldFile, String newName)
    {
        boolean success = FileUtils.renameFileToName(oldFile, newName, MessageDispatcher::error);
        this.onSchematicChange();
        return success;
    }

    protected boolean renameSchematicToName(LoadedSchematic loadedSchematic, String newName)
    {
        SchematicMetadata meta = loadedSchematic.schematic.getMetadata();
        String oldName = meta.getSchematicName();

        meta.setSchematicName(newName);
        meta.setTimeModifiedToNowIfNotRecentlyCreated();
        loadedSchematic.setModifiedSinceSaved();

        if (loadedSchematic.file.isPresent() == false)
        {
            return true;
        }

        if (SchematicFileUtils.writeToFile(loadedSchematic.schematic, loadedSchematic.file.get(), true))
        {
            loadedSchematic.clearModifiedSinceSaved();

            SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
            List<LoadedSchematic> list = SchematicHolder.INSTANCE.getAllOf(loadedSchematic.file.get());

            for (LoadedSchematic loadedSchematicTmp : list)
            {
                loadedSchematicTmp.schematic.getMetadata().setSchematicName(newName);
                loadedSchematicTmp.schematic.getMetadata().setTimeModifiedToNowIfNotRecentlyCreated();

                // Rename all placements that used the old schematic name (i.e. were not manually renamed)
                for (SchematicPlacement placement : manager.getAllPlacementsOfSchematic(loadedSchematic))
                {
                    if (placement.getName().equals(oldName))
                    {
                        placement.setName(newName);
                    }
                }
            }

            MessageDispatcher.success(2000).translate("litematica.message.info.schematic_manager.schematic_renamed");
            this.onSchematicChange();

            return true;
        }
        else
        {
            MessageDispatcher.error("litematica.message.error.schematic_manager.failed_to_save_schematic");
        }

        return false;
    }

    protected boolean setSchematicDescription(String description, LoadedSchematic loadedSchematic)
    {
        SchematicMetadata meta = loadedSchematic.schematic.getMetadata();

        if (Objects.equals(description, meta.getDescription()) == false)
        {
            meta.setDescription(description);
            meta.setTimeModifiedToNowIfNotRecentlyCreated();
            loadedSchematic.setModifiedSinceSaved();

            if (loadedSchematic.file.isPresent())
            {
                if (SchematicFileUtils.writeToFile(loadedSchematic.schematic, loadedSchematic.file.get(), true))
                {
                    loadedSchematic.clearModifiedSinceSaved();
                    MessageDispatcher.success(2000).translate("litematica.message.info.schematic_manager.description_set");
                }
                else
                {
                    MessageDispatcher.error("litematica.message.error.schematic_manager.failed_to_save_schematic");
                }
            }

            this.onSchematicChange();
        }

        return true;
    }

    protected void removeSchematicPreviewImage(LoadedSchematic loadedSchematic)
    {
        SchematicMetadata meta = loadedSchematic.schematic.getMetadata();

        if (meta.getPreviewImagePixelData() != null)
        {
            meta.setPreviewImagePixelData(null);
            meta.setTimeModifiedToNowIfNotRecentlyCreated();
            loadedSchematic.setModifiedSinceSaved();

            if (loadedSchematic.file.isPresent())
            {
                if (SchematicFileUtils.writeToFile(loadedSchematic.schematic, loadedSchematic.file.get(), true))
                {
                    loadedSchematic.clearModifiedSinceSaved();
                    MessageDispatcher.success(2000).translate("litematica.message.info.schematic_manager.preview_removed");
                }
                else
                {
                    MessageDispatcher.error("litematica.message.error.schematic_manager.failed_to_save_schematic");
                }
            }

            this.onSchematicChange();
        }
    }
}
