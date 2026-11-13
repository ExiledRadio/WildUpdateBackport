package com.exiledradio.wildupdatebackport.frog;

import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.ICriterionTrigger;
import net.minecraft.advancements.PlayerAdvancements;
import net.minecraft.advancements.critereon.AbstractCriterionInstance;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * {@code wildupdatebackport:leashed_frog}: a player put a lead on a frog, optionally of one {@code variant}
 * ({@code "minecraft:warm"}...). 1.12.2 has no {@code player_interacted_with_entity} trigger, which 1.19's
 * <i>When the Squad Hops into Town</i> uses with a lead and each variant; this trigger stands in for it.
 */
public final class FrogLeashTrigger implements ICriterionTrigger<FrogLeashTrigger.Instance> {

    public static final ResourceLocation ID = new ResourceLocation(WildUpdateBackport.MOD_ID, "leashed_frog");
    public static final FrogLeashTrigger INSTANCE = new FrogLeashTrigger();

    private final Map<PlayerAdvancements, Set<Listener<Instance>>> listeners = new HashMap<PlayerAdvancements, Set<Listener<Instance>>>();

    private FrogLeashTrigger() {
    }

    /** Adds the trigger to vanilla's list ({@code CriteriaTriggers.register} is private). */
    public static void register() {
        try {
            Method m = ObfuscationReflectionHelper.findMethod(CriteriaTriggers.class, "func_192118_a", ICriterionTrigger.class,
                    ICriterionTrigger.class);
            m.invoke(null, INSTANCE);
        } catch (Exception e) {
            WildUpdateBackport.LOGGER.error("Could not register the leashed_frog trigger; When the Squad Hops into Town cannot be earned.", e);
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
        return new Instance(json.has("variant") ? FrogVariant.byId(JsonUtils.getString(json, "variant")) : null);
    }

    public void trigger(EntityPlayerMP player, FrogVariant variant) {
        Set<Listener<Instance>> set = this.listeners.get(player.getAdvancements());
        if (set != null) {
            for (Listener<Instance> l : new HashSet<Listener<Instance>>(set)) {
                if (l.getCriterionInstance().variant == null || l.getCriterionInstance().variant == variant) {
                    l.grantCriterion(player.getAdvancements());
                }
            }
        }
    }

    public static final class Instance extends AbstractCriterionInstance {
        @Nullable
        final FrogVariant variant;

        public Instance(@Nullable FrogVariant variant) {
            super(ID);
            this.variant = variant;
        }
    }
}
