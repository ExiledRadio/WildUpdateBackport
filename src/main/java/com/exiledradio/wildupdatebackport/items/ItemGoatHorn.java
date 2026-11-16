package com.exiledradio.wildupdatebackport.items;

import com.exiledradio.backportcore.gameevent.GameEvent;
import com.exiledradio.backportcore.gameevent.GameEventContext;
import com.exiledradio.backportcore.gameevent.GameEvents;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 1.19's goat horn ({@code InstrumentItem} over {@code #goat_horns}).
 *
 * <p>Used, it plays its instrument's sound for everyone within 256 blocks (volume 256 / 16, 1.12.2's
 * attenuation scales the same way), emits {@code instrument_play}, and puts every goat horn on a 140-tick
 * cooldown (1.19's cooldown is per item, so one horn blocks them all). It is "used" for those 140 ticks, as
 * 1.19's, which slows the player as any item in use does.
 */
public class ItemGoatHorn extends Item {

    public ItemGoatHorn() {
        this.setMaxStackSize(1);
    }

    /** @return a horn with this instrument */
    public ItemStack create(Instrument instrument) {
        ItemStack stack = new ItemStack(this);
        instrument.write(stack);
        return stack;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        Instrument instrument = Instrument.of(stack);
        player.setActiveHand(hand);
        play(world, player, instrument);
        player.getCooldownTracker().setCooldown(this, Instrument.DURATION);
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
    }

    /**
     * 1.19's {@code InstrumentItem.play}: the sound for everyone but the player (whose own client plays it
     * from its side of the same call), and the game event.
     */
    static void play(World world, EntityPlayer player, Instrument instrument) {
        world.playSound(player, player.posX, player.posY, player.posZ, ItemsContent.hornSound(instrument),
                SoundCategory.RECORDS, Instrument.RANGE / 16.0F, 1.0F);
        GameEvents.emit(world, GameEvent.INSTRUMENT_PLAY, new Vec3d(player.posX, player.posY, player.posZ), GameEventContext.of(player));
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        return Instrument.DURATION;
    }

    /** 1.12.2 has no horn pose; the item's {@code tooting} model (1.19's) shows it raised instead. */
    @Override
    public EnumAction getItemUseAction(ItemStack stack) {
        return EnumAction.NONE;
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (this.isInCreativeTab(tab)) {
            for (Instrument i : Instrument.values()) {
                items.add(this.create(i));
            }
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.GRAY + I18n.translateToLocal(Instrument.of(stack).translationKey()));
    }
}
