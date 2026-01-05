package litematica.util;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.util.EnumHand;

import malilib.util.game.wrap.EntityWrap;
import malilib.util.game.wrap.GameWrap;
import malilib.util.game.wrap.ItemWrap;
import litematica.data.DataManager;

public class EntityUtils
{
    public static boolean testNotPlayer(Entity entity)
    {
        return (entity instanceof EntityPlayer) == false;
    }

    public static boolean hasToolItem()
    {
        // If the configured tool item has NBT data, then the NBT is compared, otherwise it's ignored

        EntityLivingBase entity = GameWrap.getClientPlayer();
        ItemStack toolItem = DataManager.getToolItem();

        if (ItemWrap.isEmpty(toolItem))
        {
            return ItemWrap.isEmpty(EntityWrap.getMainHandItem(entity));
        }

        return hasToolItemInHand(entity, EnumHand.MAIN_HAND, toolItem) ||
               hasToolItemInHand(entity, EnumHand.OFF_HAND, toolItem);
    }

    protected static boolean hasToolItemInHand(EntityLivingBase entity, EnumHand hand, ItemStack toolItem)
    {
        ItemStack stack = EntityWrap.getHeldItem(entity, hand);

        if (ItemStack.areItemsEqualIgnoreDurability(toolItem, stack))
        {
            return toolItem.hasTagCompound() == false || ItemStack.areItemStackTagsEqual(toolItem, stack);
        }

        return false;
    }

    public static boolean setFakedSneakingState(boolean sneaking)
    {
        EntityPlayerSP player = GameWrap.getClientPlayer();

        if (player != null && player.isSneaking() != sneaking)
        {
            CPacketEntityAction.Action action = sneaking ? CPacketEntityAction.Action.START_SNEAKING : CPacketEntityAction.Action.STOP_SNEAKING;
            player.connection.sendPacket(new CPacketEntityAction(player, action));
            player.movementInput.sneak = sneaking;
            return true;
        }

        return false;
    }
}
