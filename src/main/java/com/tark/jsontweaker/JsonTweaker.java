package com.tark.jsontweaker;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.ShapedOreRecipe;


public class JsonTweaker {

    /**
     * Cast the objects from scala to a ShapedOreRecipe cause it won't work otherwise.
     * @param output the item output for the recipe.
     * @param objects coming from scala.
     * @return a shaped ore recipe.
     */
    public static ShapedOreRecipe castRecipes(ItemStack output, Object[] objects) {
        return new ShapedOreRecipe(output, objects);
    }
}
