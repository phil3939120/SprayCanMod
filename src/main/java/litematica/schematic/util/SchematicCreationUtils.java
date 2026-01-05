package litematica.schematic.util;

import malilib.gui.BaseScreen;
import malilib.gui.TextInputScreen;
import malilib.input.ActionResult;
import malilib.overlay.message.MessageDispatcher;
import litematica.data.DataManager;
import litematica.data.SchematicHolder;
import litematica.gui.CreateInMemorySchematicScreen;
import litematica.gui.SaveSchematicFromAreaScreen;
import litematica.scheduler.TaskScheduler;
import litematica.scheduler.task.LocalCreateSchematicTask;
import litematica.schematic.LoadedSchematic;
import litematica.schematic.Schematic;
import litematica.schematic.SchematicSaveSettings;
import litematica.schematic.projects.SchematicProject;
import litematica.selection.AreaSelection;
import litematica.selection.AreaSelectionManager;

public class SchematicCreationUtils
{
    public static ActionResult saveSchematic(boolean inMemoryOnly)
    {
        AreaSelectionManager sm = DataManager.getAreaSelectionManager();
        AreaSelection area = sm.getCurrentSelection();

        if (area != null)
        {
            if (DataManager.getSchematicProjectsManager().hasProjectOpen())
            {
                String title = "litematica.title.screen.schematic_vcs.save_new_version";
                SchematicProject project = DataManager.getSchematicProjectsManager().getCurrentProject();
                TextInputScreen screen = new TextInputScreen(title, project.getCurrentVersionName(),
                                                             DataManager.getSchematicProjectsManager()::commitNewVersion);
                BaseScreen.openPopupScreenWithCurrentScreenAsParent(screen);
            }
            else if (inMemoryOnly)
            {
                BaseScreen.openPopupScreenWithCurrentScreenAsParent(new CreateInMemorySchematicScreen(area));
            }
            else
            {
                BaseScreen.openScreenWithParent(new SaveSchematicFromAreaScreen(area));
            }

            return ActionResult.SUCCESS;
        }

        return ActionResult.FAIL;
    }

    public static boolean saveInMemorySchematic(String name, AreaSelection area)
    {
        // TODO Allow changing the settings
        SchematicSaveSettings settings = new SchematicSaveSettings();
        LocalCreateSchematicTask task = new LocalCreateSchematicTask(area, settings,
                                            sch -> onInMemorySchematicCreated(sch, name));
        TaskScheduler.getServerInstanceIfExistsOrClient().scheduleTask(task, 10);

        return true;
    }

    private static void onInMemorySchematicCreated(Schematic schematic, String name)
    {
        SchematicHolder.INSTANCE.addSchematic(new LoadedSchematic(schematic), true);
        MessageDispatcher.success("litematica.message.in_memory_schematic_created", name);
    }
}
