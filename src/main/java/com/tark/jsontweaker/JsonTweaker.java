package com.tark.jsontweaker;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.oredict.ShapedOreRecipe;

import java.io.File;

@Mod(
        modid = JsonTweaker.MOD_ID,
        name = JsonTweaker.MOD_NAME,
        version = JsonTweaker.VERSION,
        acceptedMinecraftVersions = "1.11.2"
)
public class JsonTweaker {

    public static final String MOD_ID = "jsontweaker";
    static final String MOD_NAME = "JsonTweaker";
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
