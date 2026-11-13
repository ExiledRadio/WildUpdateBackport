package com.exiledradio.wildupdatebackport.selftest;

import com.exiledradio.wildupdatebackport.ModConfig;
import com.exiledradio.wildupdatebackport.gameevent.GameEvent;
import com.exiledradio.wildupdatebackport.gameevent.GameEventContext;
import com.exiledradio.wildupdatebackport.gameevent.GameEventTags;
import com.exiledradio.wildupdatebackport.gameevent.GameEvents;
import com.exiledradio.wildupdatebackport.warden.AngerLevel;
import com.exiledradio.wildupdatebackport.warden.AngerManagement;
import com.exiledradio.wildupdatebackport.warden.DarknessFactor;
import com.exiledradio.wildupdatebackport.warden.EntityWarden;
import com.exiledradio.wildupdatebackport.warden.WardenBrain;
import com.exiledradio.wildupdatebackport.warden.WardenBrain.Activity;
import com.exiledradio.wildupdatebackport.warden.WardenBrain.Memory;
import com.exiledradio.wildupdatebackport.warden.WardenContent;
import com.exiledradio.wildupdatebackport.warden.WardenPose;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/** Stream W: the warden and Darkness. */
public final class WardenSelfTest implements SelfTest {

    private WorldServer world;
    private final List<Entity> spawned = new ArrayList<Entity>();

    @Override
    public String name() {
        return "warden";
    }

    @Override
    public void run(Check c) {
        this.world = c.world;
        if (!c.ok(ModConfig.warden.enabled, "warden.enabled is on for the test")) {
            return;
        }
        try {
            registration(c);
            angerManagement(c);
            targeting(c);
            vibrations(c);
            projectiles(c);
            hurtAndTouch(c);
            sniffing(c);
            roarAndFight(c);
            sonicBoom(c);
            melee(c);
            emerging(c);
            digging(c);
            darkness(c);
            saving(c);
        } finally {
            for (Entity e : this.spawned) {
                if (!e.isDead || e.isAddedToWorld()) {
                    this.world.removeEntityDangerously(e);
                }
            }
            this.spawned.clear();
        }
    }

    // --- helpers -----------------------------------------------------------------------------------------------------

    private EntityWarden spawn(BlockPos at, boolean summoned) {
        Entity e = EntityList.createEntityByIDFromName(WardenContent.WARDEN, this.world);
        EntityWarden w = (EntityWarden) e;
        w.setLocationAndAngles(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 0.0F, 0.0F);
        if (summoned) {
            w.getEntityData().setBoolean(WardenContent.SUMMONED, true);
        }
        w.onInitialSpawn(this.world.getDifficultyForLocation(at), null);
        this.world.spawnEntity(w);
        this.spawned.add(w);
        return w;
    }

    private <T extends Entity> T add(T e, BlockPos at) {
        e.setLocationAndAngles(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 0.0F, 0.0F);
        this.world.spawnEntity(e);
        this.spawned.add(e);
        return e;
    }

    /** One server tick of the warden alone, with the world's clock moving on (behaviors time out by it). */
    private void tick(EntityWarden w, int ticks) {
        for (int i = 0; i < ticks && !w.isDead; i++) {
            this.world.getWorldInfo().setWorldTotalTime(this.world.getTotalWorldTime() + 1);
            GameEvents.flush(this.world);
            // 1.12's world loop counts an entity's age before its update.
            w.ticksExisted++;
            w.onUpdate();
        }
    }

    private EntityPlayerMP survivalPlayer(String name, BlockPos at) {
        EntityPlayerMP p = Players.real(this.world, name);
        p.interactionManager.setGameType(GameType.SURVIVAL);
        p.setPosition(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
        return p;
    }

    // --- tests ---------------------------------------------------------------------------------------------------------

    private void registration(Check c) {
        Entity e = EntityList.createEntityByIDFromName(new ResourceLocation("wildupdatebackport", "warden"), this.world);
        c.ok(e instanceof EntityWarden, "the shrieker's name finds the warden");
        c.ok(EntityList.ENTITY_EGGS.containsKey(WardenContent.WARDEN), "the warden has a spawn egg");
        Potion darkness = ForgeRegistries.POTIONS.getValue(new ResourceLocation("wildupdatebackport", "darkness"));
        c.ok(darkness != null && darkness == WardenContent.darkness, "the shrieker's name finds Darkness");
        if (darkness != null) {
            c.ok(darkness.isBadEffect(), "Darkness is harmful");
            c.eq(2696993, darkness.getLiquidColor(), "Darkness color");
        }
        c.ok(ForgeRegistries.SOUND_EVENTS.containsKey(new ResourceLocation("wildupdatebackport", "entity.warden.listening_angry")),
                "listening_angry is registered once by whoever comes first");
        EntityWarden w = (EntityWarden) e;
        c.eq(500.0D, w.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).getBaseValue(), "health");
        c.eq(30.0D, w.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getBaseValue(), "attack damage");
        c.eq(0.3D, w.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getBaseValue(), "speed");
        c.eq(1.0D, w.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).getBaseValue(), "knockback resistance");
        c.ok(w.isImmuneToFire(), "fire immune");
        c.eq(0.9F, w.width, "width");
        c.eq(2.9F, w.height, "height");
        c.ok(GameEventTags.dampens(w), "the warden's own vibrations are dampened");
        c.ok(GameEventTags.WARDEN_CAN_LISTEN.contains(GameEvent.SHRIEK), "the warden hears shrieks");
    }

    private void angerManagement(Check c) {
        BlockPos area = c.area().add(16, 0, 16);
        EntityPig pig = add(new EntityPig(this.world), area.add(2, 0, 0));
        EntityPig pig2 = add(new EntityPig(this.world), area.add(3, 0, 0));
        EntityPlayerMP player = survivalPlayer("anger", area.add(4, 0, 0));
        AngerManagement m = new AngerManagement(x -> true);
        c.eq(35, m.increaseAnger(pig, 35), "anger starts at the amount");
        c.eq(35, m.getActiveAnger(null), "a lone suspect's anger is the highest");
        c.eq(150, m.increaseAnger(pig, 200), "anger stops at 150");
        m.increaseAnger(pig2, 90);
        m.increaseAnger(player, 50);
        c.eq(pig, m.getActiveEntity(), "the angriest of two angry suspects comes first");
        m.increaseAnger(player, 40);
        c.eq(player, m.getActiveEntity(), "an angry player comes before angry mobs");
        m.clearAnger(player);
        c.eq(0, m.anger(player), "cleared");
        m.tick(this.world, x -> true);
        c.eq(149, m.anger(pig), "anger drops by 1 a second");
        c.eq(AngerLevel.CALM, AngerLevel.byAnger(39), "39 is calm");
        c.eq(AngerLevel.AGITATED, AngerLevel.byAnger(40), "40 is agitated");
        c.eq(AngerLevel.ANGRY, AngerLevel.byAnger(80), "80 is angry");
        AngerManagement small = new AngerManagement(x -> true);
        small.increaseAnger(pig, 2);
        small.tick(this.world, x -> true);
        c.eq(1, small.anger(pig), "2 ticks down to 1");
        small.tick(this.world, x -> true);
        c.eq(0, small.anger(pig), "a suspect at 1 is forgotten");
        AngerManagement filtered = new AngerManagement(x -> true);
        filtered.increaseAnger(pig, 50);
        filtered.tick(this.world, x -> false);
        c.eq(0, filtered.anger(pig), "a suspect that can no longer be targeted is forgotten");
        // Saved by UUID, found again in the world.
        NBTTagCompound tag = m.write();
        AngerManagement read = new AngerManagement(x -> true);
        read.read(tag);
        read.tick(this.world, x -> true);
        read.tick(this.world, x -> true);
        c.eq(147, read.anger(pig), "saved anger comes back by UUID (and keeps dropping)");
    }

    private void targeting(Check c) {
        BlockPos area = c.area().add(16, 0, 16);
        EntityWarden w = spawn(area, false);
        EntityPlayerMP player = survivalPlayer("target", area.add(3, 0, 0));
        c.ok(w.canTargetEntity(player), "a survival player can be targeted");
        player.interactionManager.setGameType(GameType.CREATIVE);
        c.ok(!w.canTargetEntity(player), "a creative player cannot");
        player.interactionManager.setGameType(GameType.SPECTATOR);
        c.ok(!w.canTargetEntity(player), "a spectator cannot");
        c.ok(w.canTargetEntity(add(new EntityPig(this.world), area.add(2, 0, 0))), "a pig can");
        c.ok(!w.canTargetEntity(add(new EntityArmorStand(this.world), area.add(2, 0, 2))), "an armor stand cannot");
        c.ok(!w.canTargetEntity(spawn(area.add(5, 0, 5), false)), "another warden cannot");
        EntityPig god = add(new EntityPig(this.world), area.add(4, 0, 4));
        god.setEntityInvulnerable(true);
        c.ok(!w.canTargetEntity(god), "an invulnerable mob cannot");
    }

    private void vibrations(Check c) {
        BlockPos area = c.area().add(16, 0, 16);
        EntityWarden w = spawn(area, false);
        EntityPig pig = add(new EntityPig(this.world), area.add(6, 0, 0));
        tick(w, 1);
        boolean taken = w.listener().handle(this.world, GameEvent.STEP, pig.getPositionVector(), GameEventContext.of(pig));
        c.ok(taken, "the warden hears a pig's step");
        c.ok(w.listener().isReceiving(), "the vibration travels");
        tick(w, 8);
        c.eq(35, w.angerManagement().anger(pig), "a vibration makes it 35 angry at the source");
        c.ok(w.brain().has(Memory.VIBRATION_COOLDOWN), "then it does not listen for 40 ticks");
        c.ok(w.brain().has(Memory.DISTURBANCE_LOCATION), "and goes to look where it came from");
        c.ok(!w.listener().handle(this.world, GameEvent.STEP, pig.getPositionVector(), GameEventContext.of(pig)),
                "nothing is heard during the cooldown");
        tick(w, 41);
        c.ok(!w.brain().has(Memory.VIBRATION_COOLDOWN), "the cooldown ends after 40 ticks");
        EntityPlayerMP creative = survivalPlayer("vibration", area.add(3, 0, 0));
        creative.interactionManager.setGameType(GameType.CREATIVE);
        c.ok(!w.listener().handle(this.world, GameEvent.STEP, creative.getPositionVector(), GameEventContext.of(creative)),
                "a creative player's vibrations are ignored");
        EntityWarden other = spawn(area.add(4, 0, 4), false);
        c.ok(!w.listener().handle(this.world, GameEvent.STEP, other.getPositionVector(), GameEventContext.of(other)),
                "another warden's steps are not heard");
    }

    private void projectiles(Check c) {
        BlockPos area = c.area().add(16, 0, 16);
        EntityWarden w = spawn(area, false);
        // The shooter is found again by UUID, so it must be in the world: a skeleton.
        net.minecraft.entity.monster.EntitySkeleton shooter = add(new net.minecraft.entity.monster.EntitySkeleton(this.world), area.add(10, 0, 0));
        tick(w, 1);
        EntityTippedArrow arrow = add(new EntityTippedArrow(this.world, shooter), area.add(3, 0, 0));
        w.listener().handle(this.world, GameEvent.PROJECTILE_LAND, arrow.getPositionVector(), GameEventContext.of(arrow));
        tick(w, 5);
        c.eq(10, w.angerManagement().anger(shooter), "a first projectile makes it 10 angry at the shooter");
        c.ok(w.brain().has(Memory.RECENT_PROJECTILE), "and remembers a projectile for 100 ticks");
        tick(w, 41);
        w.listener().handle(this.world, GameEvent.PROJECTILE_LAND, arrow.getPositionVector(), GameEventContext.of(arrow));
        tick(w, 5);
        c.eq(10 + 35 - 2, w.angerManagement().anger(shooter), "a second one soon after adds the full 35");
    }

    private void hurtAndTouch(Check c) {
        BlockPos area = c.area().add(16, 0, 16);
        EntityWarden w = spawn(area, false);
        EntityPlayerMP player = survivalPlayer("hitter", area.add(2, 0, 0));
        tick(w, 1);
        w.attackEntityFrom(DamageSource.causePlayerDamage(player), 1.0F);
        c.eq(100, w.angerManagement().anger(player), "being hurt makes it 100 angry at the attacker");
        c.eq(player, w.brain().get(Memory.ATTACK_TARGET), "and attack them at once");
        c.eq(200L, w.brain().timeToLive(Memory.SONIC_BOOM_COOLDOWN), "with the sonic boom 200 ticks away");
        c.eq(499.0F, w.getHealth(), "the hit hurts");

        EntityWarden w2 = spawn(area.add(8, 0, 8), false);
        EntityPig pig = add(new EntityPig(this.world), area.add(9, 0, 8));
        tick(w2, 1);
        invokeCollide(w2, pig);
        c.eq(35, w2.angerManagement().anger(pig), "touching it makes it 35 angry");
        c.ok(w2.brain().has(Memory.TOUCH_COOLDOWN), "with a touch cooldown");
        invokeCollide(w2, pig);
        c.eq(35, w2.angerManagement().anger(pig), "no more anger during the 20-tick touch cooldown");
    }

    private static void invokeCollide(EntityWarden w, Entity e) {
        try {
            java.lang.reflect.Method m = net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(
                    net.minecraft.entity.EntityLivingBase.class, "collideWithEntity", "func_82167_n", Entity.class);
            m.invoke(w, e);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void sniffing(Check c) {
        BlockPos area = c.area().add(16, 0, 16);
        EntityWarden w = spawn(area, false);
        EntityPig pig = add(new EntityPig(this.world), area.add(2, 0, 0));
        tick(w, 21);
        c.eq(pig, w.brain().get(Memory.NEAREST_ATTACKABLE), "the sensor finds the nearest attackable");
        boolean sniffed = false;
        for (int i = 0; i < 220 && !sniffed; i++) {
            tick(w, 1);
            sniffed = w.getPose() == WardenPose.SNIFFING;
        }
        c.ok(sniffed, "it sniffs within 100-200 ticks");
        c.eq(Activity.SNIFF, w.brain().activity(), "sniffing activity");
        tick(w, WardenBrain.SNIFFING_DURATION + 2);
        c.eq(WardenPose.STANDING, w.getPose(), "sniffing ends after 84 ticks");
        c.ok(w.angerManagement().anger(pig) >= 34, "a sniffed mob within 6 blocks gets 35 anger: " + w.angerManagement().anger(pig));
    }

    private void roarAndFight(Check c) {
        BlockPos area = c.area().add(16, 0, 16);
        EntityWarden w = spawn(area, false);
        EntityIronGolem golem = add(new EntityIronGolem(this.world), area.add(3, 0, 0));
        tick(w, 1);
        w.increaseAngerAt(golem, 80, false);
        c.eq(golem, w.getEntityAngryAt(), "angry at 80");
        tick(w, 2);
        c.eq(golem, w.brain().get(Memory.ROAR_TARGET), "an angry warden picks a roar target");
        c.eq(Activity.ROAR, w.brain().activity(), "and roars");
        tick(w, 1);
        c.eq(WardenPose.ROARING, w.getPose(), "roaring pose");
        c.eq(100, w.angerManagement().anger(golem), "roaring adds 20 anger");
        tick(w, WardenBrain.ROAR_DURATION);
        c.eq(golem, w.brain().get(Memory.ATTACK_TARGET), "after the roar it attacks");
        tick(w, 1);
        c.eq(Activity.FIGHT, w.brain().activity(), "fight activity");
        c.ok(w.brain().has(Memory.SONIC_BOOM_COOLDOWN), "no sonic boom for the first 10 seconds");
        // Anger below 80 makes the target invalid.
        w.clearAnger(golem);
        w.increaseAngerAt(golem, 10, false);
        tick(w, 2);
        c.ok(!w.brain().has(Memory.ATTACK_TARGET), "a target it is no longer angry at is dropped");
    }

    private void sonicBoom(Check c) {
        BlockPos area = c.area().add(4, 0, 16);
        EntityWarden w = spawn(area, false);
        EntityZombie zombie = add(new EntityZombie(this.world), area.add(10, 0, 0));
        zombie.setItemStackToSlot(EntityEquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        zombie.setItemStackToSlot(EntityEquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        zombie.setItemStackToSlot(EntityEquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        zombie.setItemStackToSlot(EntityEquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        zombie.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(40.0D);
        zombie.setHealth(40.0F);
        tick(w, 1);
        w.increaseAngerAt(zombie, 120, false);
        w.setAttackTargetFromBrain(zombie);
        w.brain().erase(Memory.SONIC_BOOM_COOLDOWN);
        // The fight activity begins at the end of the first tick, the boom on the second.
        tick(w, 2);
        c.ok(w.brain().has(Memory.ATTACK_COOLING_DOWN), "the sonic boom starts charging at 10 blocks");
        c.ok(w.brain().has(Memory.SONIC_BOOM_SOUND_DELAY), "it lands after the charge");
        tick(w, WardenBrain.SONIC_BOOM_SOUND_TICKS - 1);
        c.eq(40.0F, zombie.getHealth(), "not before 34 ticks");
        double vx = zombie.motionX;
        tick(w, 2);
        c.eq(30.0F, zombie.getHealth(), "10 damage through diamond armor");
        c.ok(zombie.motionX - vx > 2.0D, "knocked back 2.5 away: " + (zombie.motionX - vx));
        DamageSource s = WardenContent.sonicBoom(w);
        c.ok(s.isUnblockable() && s.isMagicDamage() && s.canHarmInCreative(), "through armor and shields, magic, past protection");
        c.eq("death.attack.sonic_boom", "death.attack." + s.getDamageType(), "death message key");
        tick(w, WardenBrain.SONIC_BOOM_DURATION);
        long left = w.brain().timeToLive(Memory.SONIC_BOOM_COOLDOWN);
        c.ok(left > 0 && left <= 40, "a 40-tick cooldown follows: " + left);

        // Out of reach: 16 blocks across.
        EntityWarden w2 = spawn(area.add(0, 0, 10), false);
        EntityZombie far = add(new EntityZombie(this.world), area.add(16, 0, 10));
        tick(w2, 1);
        w2.increaseAngerAt(far, 120, false);
        w2.setAttackTargetFromBrain(far);
        w2.brain().erase(Memory.SONIC_BOOM_COOLDOWN);
        far.setPosition(w2.posX + 15.5D, w2.posY, w2.posZ);
        tick(w2, 2);
        c.ok(!w2.brain().has(Memory.SONIC_BOOM_SOUND_DELAY), "no sonic boom at 15.5 blocks across");
    }

    private void melee(Check c) {
        BlockPos area = c.area().add(16, 0, 16);
        EntityWarden w = spawn(area, false);
        EntityIronGolem golem = add(new EntityIronGolem(this.world), area.add(1, 0, 0));
        tick(w, 21);
        w.increaseAngerAt(golem, 120, false);
        w.setAttackTargetFromBrain(golem);
        boolean hit = false;
        for (int i = 0; i < 30 && !hit; i++) {
            golem.setPosition(w.posX + 1.5D, w.posY, w.posZ);
            tick(w, 1);
            hit = golem.getHealth() < 100.0F;
        }
        c.ok(hit, "it hits in melee");
        c.eq(70.0F, golem.getHealth(), "30 damage");
        c.ok(w.brain().has(Memory.ATTACK_COOLING_DOWN), "18 ticks between hits");
        c.eq(40L, w.brain().timeToLive(Memory.SONIC_BOOM_COOLDOWN), "a melee hit puts the sonic boom 40 ticks away");
    }

    private void emerging(Check c) {
        BlockPos area = c.area().add(16, 0, 16);
        EntityWarden w = spawn(area, true);
        c.eq(WardenPose.EMERGING, w.getPose(), "a summoned warden emerges");
        c.eq(1.0F, w.height, "1 block tall while emerging");
        c.ok(w.brain().has(Memory.IS_EMERGING), "emerging memory");
        tick(w, 2);
        c.eq(Activity.EMERGE, w.brain().activity(), "emerge activity");
        c.ok(!w.attackEntityFrom(DamageSource.GENERIC, 10.0F), "invulnerable while emerging");
        c.ok(!w.attackEntityFrom(DamageSource.causeExplosionDamage((net.minecraft.entity.EntityLivingBase) null), 10.0F),
                "explosions too");
        c.ok(w.isImmuneToExplosions(), "ignores explosions while emerging");
        c.eq(500.0F, w.getHealth(), "unhurt");
        // As in 1.19, an idle stroll picked on the very first tick (before the emerge activity) is walked first.
        int emerging = 2;
        while (w.getPose() == WardenPose.EMERGING && emerging < 400) {
            tick(w, 1);
            emerging++;
        }
        c.ok(emerging >= WardenBrain.EMERGE_DURATION, "emerges for at least 134 ticks: " + emerging);
        c.eq(WardenPose.STANDING, w.getPose(), "then stands");
        c.eq(2.9F, w.height, "full height again");
        c.ok(w.attackEntityFrom(DamageSource.GENERIC, 10.0F), "hurtable once out");
        EntityWarden egg = spawn(area.add(6, 0, 6), false);
        c.eq(WardenPose.STANDING, egg.getPose(), "a warden from an egg does not emerge");
        c.eq((long) WardenBrain.DIGGING_COOLDOWN, egg.brain().timeToLive(Memory.DIG_COOLDOWN), "digs after 1200 quiet ticks");
    }

    private void digging(Check c) {
        BlockPos area = c.area().add(16, 0, 16);
        EntityWarden w = spawn(area, false);
        tick(w, 3);
        c.ok(w.onGround, "standing on the floor");
        w.brain().erase(Memory.DIG_COOLDOWN);
        w.brain().erase(Memory.WALK_TARGET);
        w.getNavigator().clearPath();
        for (int i = 0; i < 300 && w.getPose() != WardenPose.DIGGING; i++) {
            tick(w, 1);
        }
        c.eq(WardenPose.DIGGING, w.getPose(), "with no disturbance for 1200 ticks it digs");
        c.ok(!w.attackEntityFrom(DamageSource.GENERIC, 10.0F), "invulnerable while digging");
        tick(w, WardenBrain.DIGGING_DURATION - 2);
        c.ok(!w.isDead, "still digging before 100 ticks");
        tick(w, 3);
        c.ok(w.isDead, "gone after 100 ticks");

        EntityWarden kept = spawn(area.add(8, 0, 8), false);
        EntityPig pig = add(new EntityPig(this.world), area.add(10, 0, 8));
        tick(kept, 2);
        tick(kept, 1);
        long before = kept.brain().timeToLive(Memory.DIG_COOLDOWN);
        kept.increaseAngerAt(pig, 10, false);
        c.ok(kept.brain().timeToLive(Memory.DIG_COOLDOWN) > before, "a disturbance puts digging off again");
        kept.enablePersistence();
        tick(kept, 1);
        c.eq((long) WardenBrain.DIGGING_COOLDOWN - 1, kept.brain().timeToLive(Memory.DIG_COOLDOWN), "a persistent warden never digs");
    }

    private void darkness(Check c) {
        BlockPos area = c.area().add(16, 0, 16);
        EntityPlayerMP near = survivalPlayer("dark_near", area.add(10, 0, 0));
        EntityPlayerMP far = survivalPlayer("dark_far", area.add(21, 0, 0));
        EntityPlayerMP creative = survivalPlayer("dark_creative", area.add(3, 0, 0));
        creative.interactionManager.setGameType(GameType.CREATIVE);
        this.world.playerEntities.add(near);
        this.world.playerEntities.add(far);
        this.world.playerEntities.add(creative);
        try {
            Vec3d at = new Vec3d(area.getX() + 0.5D, area.getY(), area.getZ() + 0.5D);
            WardenContent.applyDarknessAround(this.world, at, null, EntityWarden.DARKNESS_RADIUS);
            PotionEffect got = near.getActivePotionEffect(WardenContent.darkness);
            c.ok(got != null && got.getDuration() == 260 && !got.doesShowParticles(), "Darkness 260 ticks within 20 blocks, no particles");
            c.ok(far.getActivePotionEffect(WardenContent.darkness) == null, "not beyond 20 blocks");
            c.ok(creative.getActivePotionEffect(WardenContent.darkness) == null, "not in creative");
            near.removePotionEffect(WardenContent.darkness);
            near.addPotionEffect(new PotionEffect(WardenContent.darkness, 250, 0, false, false));
            WardenContent.applyDarknessAround(this.world, at, null, EntityWarden.DARKNESS_RADIUS);
            c.eq(250, near.getActivePotionEffect(WardenContent.darkness).getDuration(), "kept while it has 200 ticks or more");

            EntityWarden w = spawn(area, false);
            near.removePotionEffect(WardenContent.darkness);
            int until = 0;
            while ((w.ticksExisted + w.getEntityId()) % EntityWarden.DARKNESS_INTERVAL != EntityWarden.DARKNESS_INTERVAL - 1 && until++ < 200) {
                tick(w, 1);
            }
            c.ok(near.getActivePotionEffect(WardenContent.darkness) == null, "before the warden's pulse");
            tick(w, 1);
            c.ok(near.getActivePotionEffect(WardenContent.darkness) != null, "the warden gives Darkness every 6 seconds");
        } finally {
            this.world.playerEntities.remove(near);
            this.world.playerEntities.remove(far);
            this.world.playerEntities.remove(creative);
        }

        // The client's factor and pulse (1.19's FactorData and LightTexture).
        DarknessFactor f = new DarknessFactor();
        f.tick(260);
        c.eq(0.0F, f.get(1.0F), "the factor starts at 0");
        for (int d = 259; d >= 249; d--) {
            f.tick(d);
        }
        c.ok(Math.abs(f.get(1.0F) - 0.5F) < 1.0E-4F, "halfway after 11 ticks: " + f.get(1.0F));
        for (int d = 248; d >= 238; d--) {
            f.tick(d);
        }
        c.eq(1.0F, f.get(1.0F), "full after 22 ticks");
        f.tick(237);
        f.tick(300);
        c.eq(1.0F, f.get(1.0F), "lengthened, it stays full");
        for (int d = 299; d >= 11; d--) {
            f.tick(d);
        }
        c.ok(Math.abs(f.get(1.0F) - 0.5F) < 1.0E-4F, "halfway down 11 ticks before the end: " + f.get(1.0F));
        for (int d = 10; d >= 0; d--) {
            f.tick(d);
        }
        c.eq(0.0F, f.get(1.0F), "0 at the end");
        c.ok(Math.abs(DarknessFactor.pulse(0, 0.0F, 1.0F) - 0.45F) < 1.0E-5F, "the pulse peaks at 0.45");
        c.eq(0.0F, DarknessFactor.pulse(40, 0.0F, 1.0F), "and rests at 0 (an 80-tick cycle)");
        c.ok(Math.abs(DarknessFactor.pulse(0, 0.0F, 0.5F) - 0.225F) < 1.0E-5F, "scaled by the factor");
    }

    private void saving(Check c) {
        BlockPos area = c.area().add(16, 0, 16);
        EntityWarden w = spawn(area, true);
        EntityPig pig = add(new EntityPig(this.world), area.add(3, 0, 0));
        tick(w, 5);
        w.increaseAngerAt(pig, 60, false);
        NBTTagCompound tag = new NBTTagCompound();
        w.writeToNBT(tag);
        EntityWarden copy = (EntityWarden) EntityList.createEntityByIDFromName(WardenContent.WARDEN, this.world);
        copy.readFromNBT(tag);
        c.ok(copy.brain().has(Memory.IS_EMERGING), "emerging is saved");
        c.ok(copy.brain().has(Memory.DIG_COOLDOWN), "the dig cooldown is saved");
        c.ok(copy.brain().timeToLive(Memory.IS_EMERGING) > 100, "with its time left");
        copy.angerManagement().tick(this.world, copy::canTargetEntity);
        copy.angerManagement().tick(this.world, copy::canTargetEntity);
        c.eq(58, copy.angerManagement().anger(pig), "anger is saved by UUID");
    }
}
