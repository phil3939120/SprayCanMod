package litematica.schematic.data;

import malilib.util.data.tag.CompoundData;
import malilib.util.position.Vec3d;

public class EntityData
{
    public final Vec3d pos;
    public final CompoundData data;

    public EntityData(Vec3d posVec, CompoundData data)
    {
        this.pos = posVec;
        this.data = data;
    }

    public EntityData copy()
    {
        return new EntityData(this.pos, this.data.copy());
    }
}
