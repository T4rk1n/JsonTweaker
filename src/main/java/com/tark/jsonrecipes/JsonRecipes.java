package com.tark.jsonrecipes;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.oredict.ShapedOreRecipe;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Mod(
        modid = JsonRecipes.MOD_ID,
        name = JsonRecipes.MOD_NAME,
        version = JsonRecipes.VERSION
)
public class JsonRecipes {

    public static final String MOD_ID = "jsonrecipes";
    static final String MOD_NAME = "JsonRecipes";
    static final String VERSION = "0.0.1.0";

    private RecipeManager recipeManager;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        File file = event.getModConfigurationDirectory();
        recipeManager = new RecipeManager(file.getAbsolutePath(), MOD_ID);
        recipeManager.preInit();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {

    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        recipeManager.postInit();
    }

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
