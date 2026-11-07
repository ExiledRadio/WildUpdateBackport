package com.exiledradio.wildupdatebackport.common;

import com.exiledradio.wildupdatebackport.WildUpdateBackport;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.util.RecipeItemHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Keeps vanilla's "any planks" recipes from swallowing a new wood's planks.
 *
 * <p>Forge turns vanilla's sign, trapdoor, wooden button and wooden pressure plate recipes into
 * {@code plankWood} ore recipes, and vanilla's are registered first, so six mangrove planks would make an
 * oak trapdoor and the mangrove one could never be crafted. 1.13+ gave each wood its own recipes; here the
 * vanilla recipe stays where it is (same name, recipe book unlock, place in the registry) and only its
 * ingredients learn to refuse the new planks. Sticks, chests, crafting tables and the rest keep taking any
 * planks, as in 1.19.
 */
public final class VanillaRecipes {

    private VanillaRecipes() {
    }

    /**
     * Makes every ingredient of a registered recipe refuse the stacks {@code reject} accepts.
     *
     * @return true if the recipe was found and changed
     */
    public static boolean exclude(IForgeRegistry<IRecipe> registry, String name, Predicate<ItemStack> reject) {
        IRecipe recipe = registry.getValue(new ResourceLocation(name));
        if (recipe == null) {
            return false;
        }
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        boolean changed = false;
        try {
            for (int i = 0; i < ingredients.size(); i++) {
                Ingredient in = ingredients.get(i);
                if (in != Ingredient.EMPTY && accepts(in, reject)) {
                    ingredients.set(i, new Excluding(in, reject));
                    changed = true;
                }
            }
        } catch (UnsupportedOperationException e) {
            WildUpdateBackport.LOGGER.warn("Recipe {} cannot be changed; it may also take the new planks.", name);
            return false;
        }
        return changed;
    }

    private static boolean accepts(Ingredient in, Predicate<ItemStack> reject) {
        for (ItemStack s : in.getMatchingStacks()) {
            if (reject.test(s)) {
                return true;
            }
        }
        return false;
    }

    /** An ingredient that is another one minus some stacks. */
    private static final class Excluding extends Ingredient {

        private final Ingredient base;
        private final Predicate<ItemStack> reject;
        @Nullable
        private IntList packed;

        Excluding(Ingredient base, Predicate<ItemStack> reject) {
            super(new ItemStack[0]);
            this.base = base;
            this.reject = reject;
        }

        @Override
        public boolean apply(@Nullable ItemStack stack) {
            return stack != null && !this.reject.test(stack) && this.base.apply(stack);
        }

        @Override
        public ItemStack[] getMatchingStacks() {
            List<ItemStack> out = new ArrayList<ItemStack>();
            for (ItemStack s : this.base.getMatchingStacks()) {
                if (!this.reject.test(s)) {
                    out.add(s);
                }
            }
            return out.toArray(new ItemStack[0]);
        }

        @Override
        public IntList getValidItemStacksPacked() {
            if (this.packed == null) {
                IntList list = new IntArrayList();
                for (ItemStack s : getMatchingStacks()) {
                    list.add(RecipeItemHelper.pack(s));
                }
                list.sort(it.unimi.dsi.fastutil.ints.IntComparators.NATURAL_COMPARATOR);
                this.packed = list;
            }
            return this.packed;
        }

        @Override
        protected void invalidate() {
            this.packed = null;
        }

        @Override
        public boolean isSimple() {
            return false;
        }
    }
}
