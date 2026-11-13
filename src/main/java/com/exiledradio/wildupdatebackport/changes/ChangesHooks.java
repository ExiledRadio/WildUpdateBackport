package com.exiledradio.wildupdatebackport.changes;

import com.exiledradio.wildupdatebackport.ModConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockShulkerBox;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.item.EntityMinecartChest;
import net.minecraft.entity.item.EntityMinecartFurnace;
import net.minecraft.entity.item.EntityMinecartHopper;
import net.minecraft.entity.item.EntityMinecartTNT;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityShulkerBox;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * The static methods patches P4 to P7 put into vanilla code ({@code docs/PATCHES.md}). Each takes vanilla's value and
 * returns it unchanged while its switch is off, so a disabled change behaves exactly as 1.12.2.
 */
public final class ChangesHooks {

    private ChangesHooks() {
    }

    static boolean on(boolean option) {
        return ModConfig.changes.enabled && option;
    }

    // ------------------------------------------------------------------------------------------
    // P4: minecarts with a block drop themselves (1.19's AbstractMinecart.getDropItem).

    /** @return true for the four vanilla carts 1.19 made drop themselves; subclasses of other mods keep their drops */
    static boolean dropsItself(EntityMinecart cart) {
        Class<?> c = cart.getClass();
        return c == EntityMinecartChest.class || c == EntityMinecartFurnace.class || c == EntityMinecartTNT.class
                || c == EntityMinecartHopper.class;
    }

    /** In {@code EntityMinecart.killMinecart}: the item the cart drops, in place of {@code Items.MINECART}. */
    public static Item cartItem(Item vanilla, EntityMinecart cart) {
        if (!on(ModConfig.changes.minecarts) || !dropsItself(cart)) {
            return vanilla;
        }
        Class<?> c = cart.getClass();
        return c == EntityMinecartChest.class ? Items.CHEST_MINECART
                : c == EntityMinecartFurnace.class ? Items.FURNACE_MINECART
                : c == EntityMinecartTNT.class ? Items.TNT_MINECART
                : Items.HOPPER_MINECART;
    }

    /** In the chest and hopper carts' {@code killMinecart}: their separate block drop, gone (air drops nothing). */
    public static Item cartBlockItem(Item vanilla, EntityMinecart cart) {
        return on(ModConfig.changes.minecarts) && dropsItself(cart) ? Items.AIR : vanilla;
    }

    /** As {@link #cartBlockItem} for the furnace and TNT carts, which drop {@code new ItemStack(block)}. */
    public static Block cartBlock(Block vanilla, EntityMinecart cart) {
        return on(ModConfig.changes.minecarts) && dropsItself(cart) ? Blocks.AIR : vanilla;
    }

    // ------------------------------------------------------------------------------------------
    // P5: a playing jukebox is a redstone signal source of 15 (1.19.4's JukeboxBlock.isSignalSource/getSignal).

    /** {@code BlockJukebox.canProvidePower}: true while the change is on. */
    public static boolean jukeboxIsSource(boolean vanilla) {
        return vanilla || on(ModConfig.changes.jukeboxes);
    }

    /**
     * {@code BlockJukebox.getWeakPower}: 15 while a disc plays. A signal source is no longer a 1.12.2 "normal cube",
     * so it would stop passing on the power that reaches it; 1.19.4's jukebox is still a conductor (its level reads
     * the larger of the two), so the power going into it is passed on as well.
     */
    public static int jukeboxPower(int vanilla, IBlockAccess world, BlockPos pos) {
        if (!on(ModConfig.changes.jukeboxes)) {
            return vanilla;
        }
        int conducted = world instanceof World ? ((World) world).getStrongPower(pos) : 0;
        return Math.max(Math.max(vanilla, conducted), Jukeboxes.isPlaying(world.getTileEntity(pos)) ? 15 : 0);
    }

    /**
     * {@code BlockJukebox.isSideSolid}: every side stays solid. Forge reads a signal source as not solid, except the
     * block of redstone, which it special-cases the same way.
     */
    public static boolean jukeboxSideSolid(boolean vanilla) {
        return vanilla || on(ModConfig.changes.jukeboxes);
    }

    // ------------------------------------------------------------------------------------------
    // P6: potions lose their glint (1.19.4 removed PotionItem.isFoil, so only an enchanted potion shines).

    /** P6's stack, stashed at the method's first line: vanilla's ProGuard frames drop the parameter before its returns. */
    private static final ThreadLocal<ItemStack> POTION_STACK = new ThreadLocal<ItemStack>();

    /** P6, from the first line of {@code ItemPotion.hasEffect}. */
    public static void potionBegin(ItemStack stack) {
        POTION_STACK.set(stack);
    }

    /** P6, before each return of {@code ItemPotion.hasEffect}: the stashed stack's answer. */
    public static boolean potionGlint(boolean vanilla) {
        ItemStack stack = POTION_STACK.get();
        if (stack == null) {
            return vanilla;
        }
        POTION_STACK.remove();
        return potionGlint(vanilla, stack);
    }

    /** {@code ItemPotion.hasEffect}. */
    public static boolean potionGlint(boolean vanilla, ItemStack stack) {
        return on(ModConfig.changes.potionGlint) ? stack.isItemEnchanted() : vanilla;
    }

    // ------------------------------------------------------------------------------------------
    // P7: an open shulker box supports blocks on its base only (1.19.3's ShulkerBoxBlock.getBlockSupportShape).

    /** P7's arguments, stashed at the method's first line: vanilla's ProGuard frames drop the parameters before its returns. */
    private static final ThreadLocal<Object[]> SHULKER_ARGS = new ThreadLocal<Object[]>();

    /** P7, from the first line of {@code BlockShulkerBox.getBlockFaceShape}. */
    public static void shulkerBegin(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        SHULKER_ARGS.set(new Object[] {world, state, pos, face});
    }

    /** P7, before each return of {@code BlockShulkerBox.getBlockFaceShape}: the stashed arguments' answer. */
    public static BlockFaceShape shulkerFace(BlockFaceShape vanilla) {
        Object[] args = SHULKER_ARGS.get();
        if (args == null) {
            return vanilla;
        }
        SHULKER_ARGS.remove();
        return shulkerFace(vanilla, (IBlockAccess) args[0], (IBlockState) args[1], (BlockPos) args[2], (EnumFacing) args[3]);
    }

    /**
     * {@code BlockShulkerBox.getBlockFaceShape}. 1.19.3: while not closed, the support shape is a one-pixel plate on the
     * face opposite the lid, so only that face holds a torch; closed, every face does. 1.12.2 had the lid and base
     * faces unsolid while open, the sides solid, and every face unsolid while moving.
     */
    public static BlockFaceShape shulkerFace(BlockFaceShape vanilla, IBlockAccess world, IBlockState state, BlockPos pos,
                                             EnumFacing face) {
        if (!on(ModConfig.changes.shulkerBoxes) || !(state.getBlock() instanceof BlockShulkerBox)) {
            return vanilla;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileEntityShulkerBox)
                || ((TileEntityShulkerBox) te).getAnimationStatus() == TileEntityShulkerBox.AnimationStatus.CLOSED) {
            return BlockFaceShape.SOLID;
        }
        return face == state.getValue(BlockShulkerBox.FACING).getOpposite() ? BlockFaceShape.SOLID : BlockFaceShape.UNDEFINED;
    }
}
