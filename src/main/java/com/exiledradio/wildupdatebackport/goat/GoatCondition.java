package com.exiledradio.wildupdatebackport.goat;

import com.google.gson.JsonObject;

import net.minecraftforge.common.crafting.IConditionFactory;
import net.minecraftforge.common.crafting.JsonContext;

import java.util.function.BooleanSupplier;

/**
 * The condition {@code wildupdatebackport:goats}: true when our goat is registered (goats on, Caves &amp; Cliffs absent).
 *
 * <p>The boat advancement carries it, because it has no item of ours an {@code item_exists} condition could
 * test. Forge finds this class through {@code advancements/_factories.json}; a condition registered from
 * code would be lost, because Forge clears its condition table each time it loads advancements.
 */
public final class GoatCondition implements IConditionFactory {

    @Override
    public BooleanSupplier parse(JsonContext context, JsonObject json) {
        return GoatContent::enabled;
    }
}
