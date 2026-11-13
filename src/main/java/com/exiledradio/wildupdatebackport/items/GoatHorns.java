package com.exiledradio.wildupdatebackport.items;

import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.exiledradio.wildupdatebackport.common.Owners;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockStone;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Random;
import java.util.UUID;

/**
 * Goat horns from goats.
 *
 * <h2>For any goat (the API)</h2>
 * {@link #create} is 1.19's {@code Goat.createHorn}: a regular goat's horn is one of the four regular
 * instruments, a screaming goat's one of the four screaming ones, the same one every time for one goat (seeded
 * by its UUID). {@link #drop} throws it as 1.19's {@code dropHorn} does. This is the supplier side of
 * {@code docs/CORE_REQUESTS.md} request 6; a goat that knows nothing of this mod can also make the stack by name:
 * {@code wildupdatebackport:goat_horn} with an {@code instrument} string tag.
 *
 * <h2>Caves &amp; Cliffs' goats (until they call the hook)</h2>
 * Their ram has no block-hit ending yet, so this class adds 1.19's {@code RamTarget} one from outside, by name and
 * reflection on their goat's public horn and ram methods: while a goat charges with its head lowered and the block
 * ahead of it (or the one above that) is in 1.19's {@code #snaps_goat_horn}, it plays its ram impact, loses a horn
 * (if it has one; dropped with 1.19's horn-break sound) and ends the ram with a full cooldown. If any of those
 * methods is missing, this logs once and does nothing.
 */
public final class GoatHorns {

    public static final ResourceLocation CC_GOAT = new ResourceLocation(Owners.CAVES_AND_CLIFFS, "goat");

    private GoatHorns() {
    }

    /** @return the horn this goat drops, or EMPTY when goat horns are switched off */
    public static ItemStack create(boolean screaming, UUID goat) {
        ItemGoatHorn horn = ItemsContent.goatHorn();
        return horn == null ? ItemStack.EMPTY : horn.create(Instrument.forGoat(screaming, goat));
    }

    /**
     * Throws a goat's horn as 1.19's {@code dropHorn}: from the goat's feet, 0.3-0.7 up and up to 0.2 sideways.
     * The goat's own horn state is the caller's.
     *
     * @return the dropped entity, or null with goat horns off
     */
    @Nullable
    public static EntityItem drop(EntityLivingBase goat, boolean screaming) {
        ItemStack stack = create(screaming, goat.getUniqueID());
        if (stack.isEmpty()) {
            return null;
        }
        Random r = goat.getRNG();
        EntityItem item = new EntityItem(goat.world, goat.posX, goat.posY, goat.posZ, stack);
        item.motionX = between(r, -0.2F, 0.2F);
        item.motionY = between(r, 0.3F, 0.7F);
        item.motionZ = between(r, -0.2F, 0.2F);
        goat.world.spawnEntity(item);
        return item;
    }

    private static double between(Random r, float min, float max) {
        return r.nextFloat() * (max - min) + min;
    }

    /** 1.19.2's {@code #snaps_goat_horn}: natural logs, stone, packed ice, and iron, coal, copper and emerald ore. */
    public static boolean snapsHorn(IBlockState state) {
        Block b = state.getBlock();
        if (b == Blocks.LOG || b == Blocks.LOG2) {
            // A log with no axis is 1.12.2's six-sided wood, which is not a natural log.
            return state.getValue(BlockLog.LOG_AXIS) != BlockLog.EnumAxis.NONE;
        }
        if (b == Blocks.STONE) {
            return state.getValue(BlockStone.VARIANT) == BlockStone.EnumType.STONE;
        }
        if (b == Blocks.PACKED_ICE || b == Blocks.IRON_ORE || b == Blocks.COAL_ORE || b == Blocks.EMERALD_ORE) {
            return true;
        }
        return b == Owners.ourBlock("mangrove_log") || (b != Blocks.AIR && b == ccBlock("copper_ore"));
    }

    @Nullable
    private static Block ccBlock(String name) {
        return Owners.present(Owners.CAVES_AND_CLIFFS) ? Owners.block(Owners.CAVES_AND_CLIFFS, name) : null;
    }

    // --- Caves & Cliffs' goats ----------------------------------------------------------------

    /** Reflection on Caves &amp; Cliffs' goat, looked up once. */
    private static final class CcGoat {
        Method lowering, left, right, setHorns, screaming, setLowering, setCooldown, nextCooldown;
    }

    @Nullable
    private static CcGoat cc;
    private static boolean ccFailed;
    @Nullable
    private static Class<?> ccClass;

    @Nullable
    private static CcGoat cc(Class<?> type) {
        if (cc != null || ccFailed) {
            return cc;
        }
        try {
            CcGoat g = new CcGoat();
            g.lowering = method(type, "isLoweringHead");
            g.left = method(type, "hasLeftHorn");
            g.right = method(type, "hasRightHorn");
            g.setHorns = method(type, "setHorns", boolean.class, boolean.class);
            g.screaming = method(type, "isScreamingGoat");
            g.setLowering = method(type, "setLoweringHead", boolean.class);
            g.setCooldown = method(type, "setRamCooldown", int.class);
            g.nextCooldown = method(type, "nextRamCooldown");
            cc = g;
        } catch (ReflectiveOperationException e) {
            ccFailed = true;
            WildUpdateBackport.LOGGER.warn("Caves & Cliffs' goat has no {}: its goats will not drop goat horns.", e.getMessage());
        }
        return cc;
    }

    private static Method method(Class<?> type, String name, Class<?>... args) throws NoSuchMethodException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, args);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                // up the hierarchy
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static boolean isCcGoat(EntityLivingBase e) {
        if (ccClass != null) {
            return ccClass == e.getClass();
        }
        EntityEntry entry = EntityRegistry.getEntry(e.getClass());
        if (entry != null && CC_GOAT.equals(entry.getRegistryName())) {
            ccClass = e.getClass();
            return true;
        }
        return false;
    }

    public static final class Events {

        @SubscribeEvent
        public void update(LivingEvent.LivingUpdateEvent event) {
            EntityLivingBase e = event.getEntityLiving();
            if (e.world.isRemote || !(e instanceof EntityLiving) || !Owners.present(Owners.CAVES_AND_CLIFFS) || !isCcGoat(e)) {
                return;
            }
            CcGoat g = cc(e.getClass());
            if (g == null) {
                return;
            }
            try {
                ramTick((EntityLiving) e, g);
            } catch (ReflectiveOperationException | RuntimeException ex) {
                ccFailed = true;
                cc = null;
                WildUpdateBackport.LOGGER.warn("Caves & Cliffs' goat could not be asked about its horns; goats will not drop goat horns.", ex);
            }
        }
    }

    private static void ramTick(EntityLiving goat, CcGoat g) throws ReflectiveOperationException {
        if (!(Boolean) g.lowering.invoke(goat)) {
            return;
        }
        double length = Math.sqrt(goat.motionX * goat.motionX + goat.motionZ * goat.motionZ);
        // Standing still with the head down is the prepare phase; the ram is the charge.
        if (length < 1.0E-3D) {
            return;
        }
        World world = goat.world;
        BlockPos ahead = new BlockPos(goat.posX + goat.motionX / length, goat.posY, goat.posZ + goat.motionZ / length);
        if (!snapsHorn(world.getBlockState(ahead)) && !snapsHorn(world.getBlockState(ahead.up()))) {
            return;
        }
        boolean screaming = (Boolean) g.screaming.invoke(goat);
        sound(world, goat, ccSound(screaming ? "entity.goat.screaming.ram_impact" : "entity.goat.ram_impact"));
        boolean left = (Boolean) g.left.invoke(goat);
        boolean right = (Boolean) g.right.invoke(goat);
        if ((left || right) && ItemsContent.goatHorn() != null) {
            // 1.19 takes the one that is left, or either at random.
            boolean loseLeft = !right || (left && goat.getRNG().nextBoolean());
            g.setHorns.invoke(goat, loseLeft ? false : left, loseLeft ? right : false);
            drop(goat, screaming);
            sound(world, goat, ItemsContent.HORN_BREAK);
        }
        g.setLowering.invoke(goat, false);
        g.setCooldown.invoke(goat, (Integer) g.nextCooldown.invoke(goat));
        goat.getNavigator().clearPath();
    }

    @Nullable
    private static SoundEvent ccSound(String name) {
        return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(Owners.CAVES_AND_CLIFFS, name));
    }

    private static void sound(World world, EntityLivingBase at, @Nullable SoundEvent sound) {
        if (sound != null) {
            world.playSound(null, at.posX, at.posY, at.posZ, sound, SoundCategory.HOSTILE, 1.0F, 1.0F);
        }
    }
}
