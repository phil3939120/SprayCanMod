package litematica.materials;

import java.util.Collection;
import com.google.common.collect.ImmutableList;

import malilib.util.StringUtils;
import litematica.schematic.Schematic;

public class MaterialListSchematic extends MaterialListBase
{
    private final Schematic schematic;
    private final ImmutableList<String> regions;

    public MaterialListSchematic(Schematic schematic, boolean reCreate)
    {
        this(schematic, schematic.getRegions().keySet(), reCreate);
    }

    public MaterialListSchematic(Schematic schematic, Collection<String> subRegions, boolean reCreate)
    {
        super();

        this.schematic = schematic;
        this.regions = ImmutableList.copyOf(subRegions);

        if (reCreate)
        {
            this.reCreateMaterialList();
        }
    }

    @Override
    public void reCreateMaterialList()
    {
        this.materialListAll = ImmutableList.copyOf(MaterialListUtils.createMaterialListFor(this.schematic, this.regions));
        this.refreshPreFilteredList();
        this.updateCounts();
    }

    @Override
    public String getName()
    {
        return this.schematic.getMetadata().getSchematicName();
    }

    @Override
    public String getTitle()
    {
        return StringUtils.translate("litematica.title.screen.material_list.schematic",
                                     this.getName(), this.regions.size(), this.schematic.getRegions().size());
    }
}
