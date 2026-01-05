package litematica.schematic.container;

import malilib.util.position.Vec3i;

public interface BlockContainerFactory
{
    BlockContainer create(Vec3i containerSize);
}
