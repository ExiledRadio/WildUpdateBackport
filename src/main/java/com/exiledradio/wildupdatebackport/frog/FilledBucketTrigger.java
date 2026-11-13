package com.exiledradio.wildupdatebackport.frog;

import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.ICriterionTrigger;
import net.minecraft.advancements.PlayerAdvancements;
import net.minecraft.advancements.critereon.AbstractCriterionInstance;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * {@code wildupdatebackport:filled_bucket}: 1.13's {@code minecraft:filled_bucket} (an {@code item} predicate),
 * which 1.12.2 does not have and 1.19's <i>Bukkit Bukkit</i> uses for the bucket of tadpole.
 */
public final class FilledBucketTrigger implements ICriterionTrigger<FilledBucketTrigger.Instance> {

    public static final ResourceLocation ID = new ResourceLocation(WildUpdateBackport.MOD_ID, "filled_bucket");
    public static final FilledBucketTrigger INSTANCE = new FilledBucketTrigger();

    private final Map<PlayerAdvancements, Set<Listener<Instance>>> listeners = new HashMap<PlayerAdvancements, Set<Listener<Instance>>>();

    private FilledBucketTrigger() {
    }

    /** Adds the trigger to vanilla's list ({@code CriteriaTriggers.register} is private). */
    public static void register() {
        try {
            Method m = ObfuscationReflectionHelper.findMethod(CriteriaTriggers.class, "func_192118_a", ICriterionTrigger.class,
                    ICriterionTrigger.class);
            m.invoke(null, INSTANCE);
        } catch (Exception e) {
            WildUpdateBackport.LOGGER.error("Could not register the filled_bucket trigger; Bukkit Bukkit cannot be earned.", e);
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
        return new Instance(ItemPredicate.deserialize(json.get("item")));
    }

    public void trigger(EntityPlayerMP player, ItemStack stack) {
        Set<Listener<Instance>> set = this.listeners.get(player.getAdvancements());
        if (set != null) {
            for (Listener<Instance> l : new HashSet<Listener<Instance>>(set)) {
                if (l.getCriterionInstance().item.test(stack)) {
                    l.grantCriterion(player.getAdvancements());
                }
            }
        }
    }

    public static final class Instance extends AbstractCriterionInstance {
        final ItemPredicate item;

        public Instance(ItemPredicate item) {
            super(ID);
            this.item = item;
        }
    }
}
