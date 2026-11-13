package com.exiledradio.wildupdatebackport.allay;

import com.exiledradio.wildupdatebackport.WildUpdateBackport;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.ICriterionTrigger;
import net.minecraft.advancements.PlayerAdvancements;
import net.minecraft.advancements.critereon.AbstractCriterionInstance;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 1.19's two allay triggers, under our names:
 * <ul>
 *   <li>{@code wildupdatebackport:thrown_item_picked_up_by_player} ({@code item}, {@code entity}: the
 *       thrower's entity id), for <i>You've Got a Friend in Me</i>;</li>
 *   <li>{@code wildupdatebackport:allay_drop_item_on_block} ({@code item}, {@code block}), for
 *       <i>Birthday Song</i>.</li>
 * </ul>
 */
public final class AllayTriggers {

    public static final Trigger THROWN_ITEM_PICKED_UP = new Trigger("thrown_item_picked_up_by_player");
    public static final Trigger DROP_ITEM_ON_BLOCK = new Trigger("allay_drop_item_on_block");

    private AllayTriggers() {
    }

    static void register() {
        try {
            Method m = ObfuscationReflectionHelper.findMethod(CriteriaTriggers.class, "func_192118_a", ICriterionTrigger.class,
                    ICriterionTrigger.class);
            m.invoke(null, THROWN_ITEM_PICKED_UP);
            m.invoke(null, DROP_ITEM_ON_BLOCK);
        } catch (Exception e) {
            WildUpdateBackport.LOGGER.error("Could not register the allay triggers; their advancements cannot be earned.", e);
        }
    }

    public static final class Trigger implements ICriterionTrigger<Instance> {
        private final ResourceLocation id;
        private final Map<PlayerAdvancements, Set<Listener<Instance>>> listeners = new HashMap<PlayerAdvancements, Set<Listener<Instance>>>();

        Trigger(String name) {
            this.id = new ResourceLocation(WildUpdateBackport.MOD_ID, name);
        }

        @Override
        public ResourceLocation getId() {
            return this.id;
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
            ItemPredicate item = ItemPredicate.deserialize(json.get("item"));
            Block block = null;
            if (json.has("block")) {
                block = Block.REGISTRY.getObject(new ResourceLocation(JsonUtils.getString(json, "block")));
            }
            ResourceLocation entity = json.has("entity") ? new ResourceLocation(JsonUtils.getString(json, "entity")) : null;
            return new Instance(this.id, item, block, entity);
        }

        /** An allay dropped an item on a block (the block under where it threw it). */
        public void trigger(EntityPlayerMP player, BlockPos pos, ItemStack stack) {
            Block block = player.world.getBlockState(pos).getBlock();
            this.fire(player, stack, block, null);
        }

        /** A player picked up an item something threw. */
        public void trigger(EntityPlayerMP player, ItemStack stack, @Nullable Entity thrower) {
            this.fire(player, stack, null, thrower);
        }

        private void fire(EntityPlayerMP player, ItemStack stack, @Nullable Block block, @Nullable Entity thrower) {
            Set<Listener<Instance>> set = this.listeners.get(player.getAdvancements());
            if (set == null) {
                return;
            }
            List<Listener<Instance>> hit = new ArrayList<Listener<Instance>>();
            for (Listener<Instance> l : set) {
                if (l.getCriterionInstance().test(stack, block, thrower)) {
                    hit.add(l);
                }
            }
            for (Listener<Instance> l : hit) {
                l.grantCriterion(player.getAdvancements());
            }
        }
    }

    public static final class Instance extends AbstractCriterionInstance {
        private final ItemPredicate item;
        @Nullable
        private final Block block;
        @Nullable
        private final ResourceLocation entity;

        Instance(ResourceLocation id, ItemPredicate item, @Nullable Block block, @Nullable ResourceLocation entity) {
            super(id);
            this.item = item;
            this.block = block;
            this.entity = entity;
        }

        boolean test(ItemStack stack, @Nullable Block placed, @Nullable Entity thrower) {
            if (!this.item.test(stack)) {
                return false;
            }
            if (this.block != null && this.block != placed) {
                return false;
            }
            if (this.entity != null && (thrower == null || !this.entity.equals(EntityList.getKey(thrower)))) {
                return false;
            }
            return true;
        }
    }
}
