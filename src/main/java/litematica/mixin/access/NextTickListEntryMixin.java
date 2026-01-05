package litematica.mixin.access;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.NextTickListEntry;

@Mixin(NextTickListEntry.class)
public interface NextTickListEntryMixin
{
    @Accessor("tickEntryID")
    long litematica$getTickId();
}
