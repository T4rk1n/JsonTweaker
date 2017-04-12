package com.tark.jsontweaker

import mezz.jei.api.{BlankModPlugin, IJeiRuntime, JEIPlugin}

/**
  * JsonRecipes
  * Created by T4rk on 4/11/2017.
  */
@JEIPlugin
class PluginJei extends BlankModPlugin {
  override def onRuntimeAvailable(jeiRuntime: IJeiRuntime): Unit = {
    Tweaker.jeiRuntime = jeiRuntime
  }
}
