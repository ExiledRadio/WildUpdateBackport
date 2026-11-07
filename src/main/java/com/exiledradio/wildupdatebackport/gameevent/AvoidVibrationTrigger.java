package com.exiledradio.wildupdatebackport.gameevent;

import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.ICriterionTrigger;
import net.minecraft.advancements.PlayerAdvancements;
import net.minecraft.advancements.critereon.AbstractCriterionInstance;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 1.19's {@code minecraft:avoid_vibration} trigger, under our name ({@code wildupdatebackport:avoid_vibration}):
 * a sneaking player's vibration was ignored by a sculk sensor or a warden. <i>Sneak 100</i> uses it.
 */
public final class AvoidVibrationTrigger implements ICriterionTrigger<AvoidVibrationTrigger.Instance> {

    public static final ResourceLocation ID = new ResourceLocation(WildUpdateBackport.MOD_ID, "avoid_vibration");
    public static final AvoidVibrationTrigger INSTANCE = new AvoidVibrationTrigger();

    private final Map<PlayerAdvancements, Set<Listener<Instance>>> listeners = new HashMap<PlayerAdvancements, Set<Listener<Instance>>>();

    private AvoidVibrationTrigger() {
    }

    /** Adds the trigger to vanilla's list. {@code CriteriaTriggers.register} is private, hence reflection. */
    public static void register() {
        try {
            Method m = ObfuscationReflectionHelper.findMethod(CriteriaTriggers.class, "func_192118_a", ICriterionTrigger.class,
                    ICriterionTrigger.class);
            m.invoke(null, INSTANCE);
        } catch (Exception e) {
            WildUpdateBackport.LOGGER.error("Could not register the avoid_vibration trigger; Sneak 100 cannot be earned.", e);
        }
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void addListener(PlayerAdvancements advancements, Listener<Instance> listener) {
        this.listeners.computeIfAbsent(advancements, k -> new HashSet<Listener<Instance>>()).add(listener);
    }

    @Override
    public void removeListener(PlayerAdvancements advancements, Listener<Instance> listener) {
        Set<Listener<Instance>> set = this.listeners.get(advancements);
        if (set != null) {
            set.remove(listener);
            if (set.isEmpty()) {
                this.listeners.remove(advancements);
            }
        }
    }

    @Override
    public void removeAllListeners(PlayerAdvancements advancements) {
        this.listeners.remove(advancements);
    }

    @Override
    public Instance deserializeInstance(JsonObject json, JsonDeserializationContext context) {
        return new Instance();
    }

    public void trigger(EntityPlayerMP player) {
        Set<Listener<Instance>> set = this.listeners.get(player.getAdvancements());
        if (set != null) {
            for (Listener<Instance> l : new HashSet<Listener<Instance>>(set)) {
                l.grantCriterion(player.getAdvancements());
            }
        }
    }

    /** The trigger takes no conditions. */
    public static final class Instance extends AbstractCriterionInstance {
        public Instance() {
            super(ID);
        }
    }
}
