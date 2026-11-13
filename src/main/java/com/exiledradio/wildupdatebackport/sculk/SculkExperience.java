package com.exiledradio.wildupdatebackport.sculk;

import com.exiledradio.wildupdatebackport.WildUpdateBackport;

import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * A dying mob's experience, for the catalyst: 1.19's {@code getExperienceReward}, {@code shouldDropExperience}
 * and {@code skipDropExperience}.
 *
 * <p>1.12.2 drops experience 20 ticks after death, so the catalyst takes it at death (as 1.19 does) and the
 * later drop is cancelled through {@link LivingExperienceDropEvent}. The amount is the mob's own
 * {@code getExperiencePoints}, which 1.12.2 keeps protected (reached by its SRG name). As in 1.19, it does not
 * matter whether a player killed it.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = WildUpdateBackport.MOD_ID)
public final class SculkExperience {

    private static final Set<EntityLivingBase> CONSUMED = Collections.newSetFromMap(new WeakHashMap<EntityLivingBase, Boolean>());
    private static Method experiencePoints;
    private static final ResourceLocation TADPOLE = new ResourceLocation(WildUpdateBackport.MOD_ID, "tadpole");

    private SculkExperience() {
    }

    public static boolean consumed(EntityLivingBase entity) {
        return CONSUMED.contains(entity);
    }

    public static void consume(EntityLivingBase entity) {
        CONSUMED.add(entity);
    }

    /**
     * 1.19's {@code shouldDropExperience}: monsters always (baby zombies too), the tadpole never (stream F's, by
     * name), anything else when it is not a baby.
     */
    public static boolean shouldDrop(EntityLivingBase entity) {
        if (entity instanceof IMob) {
            return true;
        }
        if (TADPOLE.equals(EntityList.getKey(entity))) {
            return false;
        }
        return !entity.isChild();
    }

    /** @return the experience the entity would drop (a player: 7 per level, at most 100, none with keepInventory) */
    public static int reward(EntityLivingBase entity) {
        try {
            if (experiencePoints == null) {
                experiencePoints = ObfuscationReflectionHelper.findMethod(EntityLivingBase.class, "func_70693_a", int.class, EntityPlayer.class);
            }
            Object xp = experiencePoints.invoke(entity, (EntityPlayer) null);
            return xp instanceof Integer ? Math.max(0, (Integer) xp) : 0;
        } catch (Exception e) {
            WildUpdateBackport.LOGGER.debug("No experience reward for {}", entity, e);
            return 0;
        }
    }

    /** A mob whose experience a catalyst took drops none. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void drop(LivingExperienceDropEvent event) {
        if (SculkContent.enabled() && consumed(event.getEntityLiving())) {
            event.setCanceled(true);
        }
    }
}
