package com.exiledradio.wildupdatebackport.gameevent;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 1.19.2's game event and block tags, as fixed sets (1.12.2 has no tags).
 */
public final class GameEventTags {

    private GameEventTags() {
    }

    private static Set<GameEvent> set(GameEvent... events) {
        return Collections.unmodifiableSet(new HashSet<GameEvent>(Arrays.asList(events)));
    }

    /** {@code #vibrations}: what sculk sensors hear. */
    public static final Set<GameEvent> VIBRATIONS = set(GameEvent.BLOCK_ATTACH, GameEvent.BLOCK_CHANGE, GameEvent.BLOCK_CLOSE,
            GameEvent.BLOCK_DESTROY, GameEvent.BLOCK_DETACH, GameEvent.BLOCK_OPEN, GameEvent.BLOCK_PLACE, GameEvent.BLOCK_ACTIVATE,
            GameEvent.BLOCK_DEACTIVATE, GameEvent.CONTAINER_CLOSE, GameEvent.CONTAINER_OPEN, GameEvent.DISPENSE_FAIL, GameEvent.DRINK,
            GameEvent.EAT, GameEvent.ELYTRA_GLIDE, GameEvent.ENTITY_DAMAGE, GameEvent.ENTITY_DIE, GameEvent.ENTITY_INTERACT,
            GameEvent.ENTITY_PLACE, GameEvent.ENTITY_ROAR, GameEvent.ENTITY_SHAKE, GameEvent.EQUIP, GameEvent.EXPLODE,
            GameEvent.FLUID_PICKUP, GameEvent.FLUID_PLACE, GameEvent.HIT_GROUND, GameEvent.INSTRUMENT_PLAY,
            GameEvent.ITEM_INTERACT_FINISH, GameEvent.LIGHTNING_STRIKE, GameEvent.NOTE_BLOCK_PLAY, GameEvent.PISTON_CONTRACT,
            GameEvent.PISTON_EXTEND, GameEvent.PRIME_FUSE, GameEvent.PROJECTILE_LAND, GameEvent.PROJECTILE_SHOOT, GameEvent.SHEAR,
            GameEvent.SPLASH, GameEvent.STEP, GameEvent.SWIM, GameEvent.TELEPORT, GameEvent.FLAP);

    /** {@code #warden_can_listen}: the vibrations, the shriek, and the tendrils' clicking. */
    public static final Set<GameEvent> WARDEN_CAN_LISTEN;

    static {
        Set<GameEvent> warden = new HashSet<GameEvent>(VIBRATIONS);
        warden.remove(GameEvent.FLAP);
        warden.add(GameEvent.SHRIEK);
        warden.add(GameEvent.SCULK_SENSOR_TENDRILS_CLICKING);
        WARDEN_CAN_LISTEN = Collections.unmodifiableSet(warden);
    }

    /** {@code #shrieker_can_listen}. */
    public static final Set<GameEvent> SHRIEKER_CAN_LISTEN = set(GameEvent.SCULK_SENSOR_TENDRILS_CLICKING);

    /** {@code #allay_can_listen}. */
    public static final Set<GameEvent> ALLAY_CAN_LISTEN = set(GameEvent.NOTE_BLOCK_PLAY);

    /** {@code #ignore_vibrations_sneaking}. */
    public static final Set<GameEvent> IGNORE_VIBRATIONS_SNEAKING = set(GameEvent.HIT_GROUND, GameEvent.PROJECTILE_SHOOT,
            GameEvent.STEP, GameEvent.SWIM, GameEvent.ITEM_INTERACT_START, GameEvent.ITEM_INTERACT_FINISH);

    /** {@code #occludes_vibration_signals}: wool. */
    public static boolean occludes(IBlockState state) {
        return state.getBlock() == Blocks.WOOL;
    }

    private static final net.minecraft.util.ResourceLocation WARDEN = new net.minecraft.util.ResourceLocation("wildupdatebackport", "warden");

    /** {@code #dampens_vibrations}: wool and wool carpets. */
    public static boolean dampens(IBlockState state) {
        Block b = state.getBlock();
        return b == Blocks.WOOL || b == Blocks.CARPET;
    }

    /** 1.19's {@code Entity.dampensVibrations}: a dropped wool or carpet item, and the warden. */
    public static boolean dampens(Entity entity) {
        if (WARDEN.equals(net.minecraft.entity.EntityList.getKey(entity))) {
            return true;
        }
        if (entity instanceof EntityItem) {
            Block b = Block.getBlockFromItem(((EntityItem) entity).getItem().getItem());
            return b == Blocks.WOOL || b == Blocks.CARPET;
        }
        return false;
    }
}
