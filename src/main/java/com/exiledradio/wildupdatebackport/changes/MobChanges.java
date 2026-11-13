package com.exiledradio.wildupdatebackport.changes;

import com.exiledradio.wildupdatebackport.ModConfig;
import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityWitherSkeleton;
import net.minecraft.entity.passive.AbstractHorse;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.DimensionType;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Random;

/**
 * 1.19.3's and 1.19.4's changes to mobs, each from Mojang's code of that version:
 * <ul>
 *   <li>creepers ignite with a fire charge ({@code Creeper.mobInteract}, the {@code creeper_igniters} tag);</li>
 *   <li>an enderman's carried block drops as mined with a Silk Touch diamond axe ({@code EnderMan.dropCustomDeathLoot});</li>
 *   <li>foals' stats vary around their parents' average ({@code AbstractHorse.createOffspringAttribute});</li>
 *   <li>endermen, skeletons and wither skeletons spawn in the Nether at light level 7 and below (the Nether's
 *       {@code monster_spawn_light_level} of 7 and block light limit of 15).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = WildUpdateBackport.MOD_ID)
public final class MobChanges {

    private MobChanges() {
    }

    // ------------------------------------------------------------------------------------------
    // Creepers and fire charges.

    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof EntityCreeper) || !ChangesHooks.on(ModConfig.changes.creeperFireCharge)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        // Flint and steel is vanilla 1.12.2's own path; the fire charge is the tag's new member.
        if (stack.getItem() != Items.FIRE_CHARGE) {
            return;
        }
        EntityCreeper creeper = (EntityCreeper) event.getTarget();
        EntityPlayer player = event.getEntityPlayer();
        creeper.world.playSound(player, creeper.posX, creeper.posY, creeper.posZ, SoundEvents.ITEM_FIRECHARGE_USE, creeper.getSoundCategory(),
                1.0F, creeper.getRNG().nextFloat() * 0.4F + 0.8F);
        if (!creeper.world.isRemote) {
            creeper.ignite();
            // 1.12.2's EntityPlayer.interactOn gives a creative player back what an interaction used; this event
            // ends before that, so the creative check is here.
            if (!player.capabilities.isCreativeMode) {
                stack.shrink(1);
            }
        }
        player.swingArm(event.getHand());
        event.setCanceled(true);
        event.setCancellationResult(EnumActionResult.SUCCESS);
    }

    // ------------------------------------------------------------------------------------------
    // Endermen's carried block.

    private static Method silkTouchDrop;

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void enderman(LivingDropsEvent event) {
        if (!(event.getEntityLiving() instanceof EntityEnderman) || !ChangesHooks.on(ModConfig.changes.endermanBlockDrops)) {
            return;
        }
        EntityEnderman enderman = (EntityEnderman) event.getEntityLiving();
        IBlockState held = enderman.getHeldBlockState();
        if (held == null) {
            return;
        }
        // 1.12.2's own drop (EntityEnderman.dropEquipment): the block's item with its metadata, if it has subtypes.
        Block block = held.getBlock();
        Item item = Item.getItemFromBlock(block);
        int meta = item.getHasSubtypes() ? block.getMetaFromState(held) : 0;
        Iterator<EntityItem> it = event.getDrops().iterator();
        boolean removed = false;
        while (it.hasNext()) {
            ItemStack s = it.next().getItem();
            if (s.getItem() == item && s.getMetadata() == meta && s.getCount() == 1 && !s.hasTagCompound() && item != Items.AIR) {
                it.remove();
                removed = true;
                break;
            }
        }
        if (!removed && item != Items.AIR) {
            return; // someone else already changed the drops
        }
        for (ItemStack s : carriedBlockDrops(enderman.world, new BlockPos(enderman), held)) {
            if (!s.isEmpty()) {
                EntityItem e = new EntityItem(enderman.world, enderman.posX, enderman.posY, enderman.posZ, s);
                e.setDefaultPickupDelay();
                event.getDrops().add(e);
            }
        }
    }

    /** What a block drops when mined with a Silk Touch tool: its silk drop when it can be silk harvested, else its drops. */
    public static NonNullList<ItemStack> carriedBlockDrops(World world, BlockPos pos, IBlockState state) {
        NonNullList<ItemStack> out = NonNullList.create();
        Block block = state.getBlock();
        if (block.canSilkHarvest(world, pos, state, null)) {
            try {
                if (silkTouchDrop == null) {
                    silkTouchDrop = ObfuscationReflectionHelper.findMethod(Block.class, "func_180643_i", ItemStack.class, IBlockState.class);
                }
                out.add((ItemStack) silkTouchDrop.invoke(block, state));
                return out;
            } catch (ReflectiveOperationException e) {
                WildUpdateBackport.LOGGER.warn("Block.getSilkTouchDrop is not reachable; an enderman's block drops as mined.", e);
            }
        }
        block.getDrops(out, world, pos, state, 0);
        return out;
    }

    // ------------------------------------------------------------------------------------------
    // Foals.

    /** 1.19.4's {@code AbstractHorse} ranges: {@code generateMaxHealth}, {@code generateJumpStrength}, {@code generateSpeed}. */
    public static final double MIN_HEALTH = 15.0F;
    public static final double MAX_HEALTH = 15.0F + 7.0F + 8.0F;
    public static final double MIN_JUMP = (float) (0.4F + 0.0 * 0.2 + 0.0 * 0.2 + 0.0 * 0.2);
    public static final double MAX_JUMP = (float) (0.4F + 1.0 * 0.2 + 1.0 * 0.2 + 1.0 * 0.2);
    public static final double MIN_SPEED = (float) ((0.45F + 0.0 * 0.3 + 0.0 * 0.3 + 0.0 * 0.3) * 0.25);
    public static final double MAX_SPEED = (float) ((0.45F + 1.0 * 0.3 + 1.0 * 0.3 + 1.0 * 0.3) * 0.25);

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void foal(BabyEntitySpawnEvent event) {
        if (!(event.getChild() instanceof AbstractHorse) || !(event.getParentA() instanceof AbstractHorse)
                || !(event.getParentB() instanceof AbstractHorse) || !ChangesHooks.on(ModConfig.changes.foalStats)) {
            return;
        }
        AbstractHorse a = (AbstractHorse) event.getParentA();
        AbstractHorse b = (AbstractHorse) event.getParentB();
        AbstractHorse child = (AbstractHorse) event.getChild();
        Random random = a.getRNG();
        offspring(a, b, child, SharedMonsterAttributes.MAX_HEALTH.getName(), MIN_HEALTH, MAX_HEALTH, random);
        offspring(a, b, child, "horse.jumpStrength", MIN_JUMP, MAX_JUMP, random);
        offspring(a, b, child, SharedMonsterAttributes.MOVEMENT_SPEED.getName(), MIN_SPEED, MAX_SPEED, random);
    }

    private static void offspring(EntityLiving a, EntityLiving b, EntityLiving child, String attribute, double min, double max, Random random) {
        IAttributeInstance ia = a.getAttributeMap().getAttributeInstanceByName(attribute);
        IAttributeInstance ib = b.getAttributeMap().getAttributeInstanceByName(attribute);
        IAttributeInstance ic = child.getAttributeMap().getAttributeInstanceByName(attribute);
        if (ia != null && ib != null && ic != null) {
            ic.setBaseValue(createOffspringAttribute(ia.getBaseValue(), ib.getBaseValue(), min, max, random));
        }
    }

    /** 1.19.4's {@code AbstractHorse.createOffspringAttribute}. */
    public static double createOffspringAttribute(double a, double b, double min, double max, Random random) {
        if (max <= min) {
            throw new IllegalArgumentException("Incorrect range for an attribute");
        }
        a = MathHelper.clamp(a, min, max);
        b = MathHelper.clamp(b, min, max);
        double margin = 0.15 * (max - min);
        double spread = Math.abs(a - b) + margin * 2.0;
        double average = (a + b) / 2.0;
        double offset = (random.nextDouble() + random.nextDouble() + random.nextDouble()) / 3.0 - 0.5;
        double value = average + spread * offset;
        if (value > max) {
            return max - (value - max);
        }
        if (value < min) {
            return min + (min - value);
        }
        return value;
    }

    // ------------------------------------------------------------------------------------------
    // Nether spawning light.

    /** 1.19.3's Nether {@code monster_spawn_light_level}. */
    static final int NETHER_SPAWN_LIGHT = 7;

    /**
     * 1.12.2's {@code EntityMob.isValidLightLevel} lets a monster spawn at light {@code i} only if {@code i <= rand(8)}:
     * always in the dark, one try in eight at 7. 1.19.3's Nether takes every spot at 7 or below. For the three mobs
     * 1.19.3 names, the light test is replaced by that; the rest of {@code getCanSpawnHere} stays as vanilla's.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void netherSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (event.isSpawner() || event.getResult() != Event.Result.DEFAULT || !ChangesHooks.on(ModConfig.changes.netherSpawnLight)) {
            return;
        }
        if (!(event.getEntityLiving() instanceof EntityLiving)) {
            return;
        }
        EntityLiving mob = (EntityLiving) event.getEntityLiving();
        Class<?> c = mob.getClass();
        if (c != EntityEnderman.class && c != EntitySkeleton.class && c != EntityWitherSkeleton.class) {
            return;
        }
        World world = mob.world;
        if (world.provider.getDimensionType() != DimensionType.NETHER) {
            return;
        }
        event.setResult(netherSpawnAllowed(mob) ? Event.Result.ALLOW : Event.Result.DENY);
    }

    /** {@code EntityMob.getCanSpawnHere} with 1.19.3's Nether light test in place of 1.12.2's. */
    public static boolean netherSpawnAllowed(EntityLiving mob) {
        World world = mob.world;
        BlockPos pos = new BlockPos(mob.posX, mob.getEntityBoundingBox().minY, mob.posZ);
        if (world.getDifficulty() == EnumDifficulty.PEACEFUL) {
            return false;
        }
        if (world.getLightFor(EnumSkyBlock.SKY, pos) > mob.getRNG().nextInt(32)) {
            return false;
        }
        if (world.getLightFromNeighbors(pos) > NETHER_SPAWN_LIGHT) {
            return false;
        }
        IBlockState below = world.getBlockState(new BlockPos(mob).down());
        if (!below.canEntitySpawn(mob)) {
            return false;
        }
        if (mob instanceof EntityCreature && ((EntityCreature) mob).getBlockPathWeight(pos) < 0.0F) {
            return false;
        }
        return mob.isNotColliding();
    }
}
