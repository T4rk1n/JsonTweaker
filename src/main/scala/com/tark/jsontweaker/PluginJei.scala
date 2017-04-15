package com.tark.jsontweaker

import mezz.jei.api.{BlankModPlugin, IJeiRuntime, IModRegistry, JEIPlugin}
import net.minecraft.item.crafting.IRecipe

object JeiThings {
  var registry: IModRegistry = _
  var hasJei = false
  var jeiRuntime: IJeiRuntime = _
  def removeRecipe(iRecipe: IRecipe): Unit = {
     jeiRuntime.getRecipeRegistry removeRecipe iRecipe
  }
  def addRecipe(iRecipe: IRecipe): Unit = {
    jeiRuntime.getRecipeRegistry addRecipe iRecipe
  }
}


/**
  * JsonRecipes
  * Created by T4rk on 4/11/2017.
  */
@JEIPlugin
class PluginJei extends BlankModPlugin {
  override def onRuntimeAvailable(jeiRuntime: IJeiRuntime): Unit = {
    JeiThings.jeiRuntime = jeiRuntime
    JeiThings.hasJei = true
  }
}
