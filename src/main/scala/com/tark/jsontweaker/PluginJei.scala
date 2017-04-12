package com.tark.jsontweaker

import mezz.jei.api.ingredients.IModIngredientRegistration
import mezz.jei.api._

/**
  * JsonRecipes
  * Created by T4rk on 4/11/2017.
  */
@JEIPlugin
class PluginJei extends BlankModPlugin {
  var runInstance: IJeiRuntime = _


  override def register(registry: IModRegistry): Unit = {

  }

  override def onRuntimeAvailable(jeiRuntime: IJeiRuntime): Unit = {
    runInstance = jeiRuntime
  }
}
