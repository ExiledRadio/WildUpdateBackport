package com.exiledradio.wildupdatebackport.items;

import com.exiledradio.wildupdatebackport.WildUpdateBackport;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraft.world.storage.loot.functions.LootFunction;

import java.util.Random;

/**
 * 1.19's {@code set_instrument} loot function: a random instrument from one of the instrument tags.
 *
 * <p>In a loot table: {@code {"function": "wildupdatebackport:set_instrument", "options": "#minecraft:regular_goat_horns"}}
 * ({@code #minecraft:screaming_goat_horns}, {@code #minecraft:goat_horns}).
 */
public final class SetInstrument extends LootFunction {

    public static final String REGULAR = "#minecraft:regular_goat_horns";
    public static final String SCREAMING = "#minecraft:screaming_goat_horns";
    public static final String ALL = "#minecraft:goat_horns";

    private final String options;

    public SetInstrument(LootCondition[] conditions, String options) {
        super(conditions);
        this.options = options;
    }

    /** @return the instruments a tag name stands for (unknown names: all eight, logged) */
    static Instrument[] tag(String options) {
        if (REGULAR.equals(options)) {
            return Instrument.REGULAR;
        }
        if (SCREAMING.equals(options)) {
            return Instrument.SCREAMING;
        }
        if (!ALL.equals(options)) {
            WildUpdateBackport.LOGGER.warn("set_instrument: unknown options '{}', using #minecraft:goat_horns", options);
        }
        return Instrument.values();
    }

    @Override
    public ItemStack apply(ItemStack stack, Random rand, LootContext context) {
        Instrument.random(tag(this.options), rand).write(stack);
        return stack;
    }

    public static final class Serializer extends LootFunction.Serializer<SetInstrument> {

        public Serializer() {
            super(new ResourceLocation(WildUpdateBackport.MOD_ID, "set_instrument"), SetInstrument.class);
        }

        @Override
        public void serialize(JsonObject object, SetInstrument function, JsonSerializationContext context) {
            object.addProperty("options", function.options);
        }

        @Override
        public SetInstrument deserialize(JsonObject object, JsonDeserializationContext context, LootCondition[] conditions) {
            return new SetInstrument(conditions, JsonUtils.getString(object, "options"));
        }
    }
}
