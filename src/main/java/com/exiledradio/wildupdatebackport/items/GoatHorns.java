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
import java.util.HashMap;
import java.util.Map;
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
 * <h2>The goats: Caves &amp; Cliffs', or our copy of it</h2>
 * Caves &amp; Cliffs' goat (1.17's) has no block-hit ending to its ram, and our copy of it (stream Gt, registered
 * only without Caves &amp; Cliffs) is the same code. So this class adds 1.19's {@code RamTarget} ending from outside
 * to either goat, found by registry name ({@code cavesandcliffsbackport:goat} or {@code wildupdatebackport:goat}),
 * through reflection on its horn and ram methods: while a goat charges with its head lowered, touches no mob, and
 * the block ahead of it (or the one above that) is in 1.19's {@code #snaps_goat_horn}, it plays its ram impact,
 * loses a horn (if it has one; dropped with 1.19's horn-break sound) and ends the ram with a full cooldown. If any
 * of those methods is missing, this logs once and does nothing for that goat.
 */
public final class GoatHorns {

    public static final ResourceLocation CC_GOAT = new ResourceLocation(Owners.CAVES_AND_CLIFFS, "goat");
    /** Our copy of Caves &amp; Cliffs' goat (stream Gt). */
    public static final ResourceLocation OUR_GOAT = new ResourceLocation(WildUpdateBackport.MOD_ID, "goat");

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

    // --- The goats: Caves & Cliffs', or our copy of it ---------------------------------------

    /** Reflection on one goat class (Caves &amp; Cliffs' or our copy, which has the same methods). */
    private static final class GoatMethods {
        Method lowering, left, right, setHorns, screaming, setLowering, setCooldown, nextCooldown;
    }

    /** Goat class to its methods, looked up once; a class whose methods are missing maps to null. */
    private static final Map<Class<?>, GoatMethods> METHODS = new HashMap<Class<?>, GoatMethods>();
    /** Entity class to its registry name when it is one of the two goats, else to null. */
    private static final Map<Class<?>, ResourceLocation> GOATS = new HashMap<Class<?>, ResourceLocation>();

    @Nullable
    private static GoatMethods methods(Class<?> type) {
        if (METHODS.containsKey(type)) {
            return METHODS.get(type);
        }
        GoatMethods g = new GoatMethods();
        try {
            g.lowering = method(type, "isLoweringHead");
            g.left = method(type, "hasLeftHorn");
            g.right = method(type, "hasRightHorn");
            g.setHorns = method(type, "setHorns", boolean.class, boolean.class);
            g.screaming = method(type, "isScreamingGoat");
            g.setLowering = method(type, "setLoweringHead", boolean.class);
            g.setCooldown = method(type, "setRamCooldown", int.class);
            g.nextCooldown = method(type, "nextRamCooldown");
        } catch (ReflectiveOperationException e) {
            g = null;
            WildUpdateBackport.LOGGER.warn("The goat {} has no {}: it will not drop goat horns.", type.getName(), e.getMessage());
        }
        METHODS.put(type, g);
        return g;
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

    /** @return the registry name of Caves &amp; Cliffs' goat or ours, or null for any other entity */
    @Nullable
    static ResourceLocation goatName(EntityLivingBase e) {
        Class<?> type = e.getClass();
        if (GOATS.containsKey(type)) {
            return GOATS.get(type);
        }
        EntityEntry entry = EntityRegistry.getEntry(e.getClass());
        ResourceLocation name = entry == null ? null : entry.getRegistryName();
        if (!CC_GOAT.equals(name) && !OUR_GOAT.equals(name)) {
            name = null;
        }
        GOATS.put(type, name);
        return name;
    }

    public static final class Events {

        @SubscribeEvent
        public void update(LivingEvent.LivingUpdateEvent event) {
            EntityLivingBase e = event.getEntityLiving();
            if (e.world.isRemote || !(e instanceof EntityLiving)) {
                return;
            }
            ResourceLocation name = goatName(e);
            if (name == null) {
                return;
            }
            GoatMethods g = methods(e.getClass());
            if (g == null) {
                return;
            }
            try {
                ramTick((EntityLiving) e, g, name.getNamespace());
            } catch (ReflectiveOperationException | RuntimeException ex) {
                METHODS.put(e.getClass(), null);
                WildUpdateBackport.LOGGER.warn("The goat {} could not be asked about its horns; it will not drop goat horns.", name, ex);
            }
        }
    }

    /**
     * 1.19.2's {@code RamTarget.tick}, its block half: a charging goat that touches no mob, with a {@code
     * #snaps_goat_horn} block ahead of it (or above that), plays its ram impact, drops a horn and ends the ram.
     */
    private static void ramTick(EntityLiving goat, GoatMethods g, String soundNamespace) throws ReflectiveOperationException {
        if (!(Boolean) g.lowering.invoke(goat)) {
            return;
        }
        double length = Math.sqrt(goat.motionX * goat.motionX + goat.motionZ * goat.motionZ);
        // Standing still with the head down is the prepare phase; the ram is the charge.
        if (length < 1.0E-3D) {
            return;
        }
        // A mob in the goat's box is the ram's own business: 1.19 hits it before it looks at blocks.
        for (EntityLivingBase other : goat.world.getEntitiesWithinAABB(EntityLivingBase.class, goat.getEntityBoundingBox())) {
            if (other != goat && other.getClass() != goat.getClass() && other.isEntityAlive()) {
                return;
            }
        }
        World world = goat.world;
        BlockPos ahead = new BlockPos(goat.posX + goat.motionX / length, goat.posY, goat.posZ + goat.motionZ / length);
        if (!snapsHorn(world.getBlockState(ahead)) && !snapsHorn(world.getBlockState(ahead.up()))) {
            return;
        }
        boolean screaming = (Boolean) g.screaming.invoke(goat);
        sound(world, goat, goatSound(soundNamespace, screaming ? "entity.goat.screaming.ram_impact" : "entity.goat.ram_impact"));
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
    private static SoundEvent goatSound(String namespace, String name) {
        return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(namespace, name));
    }

    private static void sound(World world, EntityLivingBase at, @Nullable SoundEvent sound) {
        if (sound != null) {
            world.playSound(null, at.posX, at.posY, at.posZ, sound, SoundCategory.HOSTILE, 1.0F, 1.0F);
        }
    }
}
