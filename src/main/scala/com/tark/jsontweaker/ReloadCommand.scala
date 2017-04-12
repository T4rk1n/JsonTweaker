package com.tark.jsontweaker

import java.util

import net.minecraft.command.{ICommand, ICommandSender}
import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.BlockPos
import net.minecraftforge.server.permission.DefaultPermissionLevel

trait CommandTrait extends ICommand {
  def requireOp : Boolean

  override def compareTo(o: ICommand) : Int = {
    this.getName.compareTo(o.getName)
  }

  override def getUsage(sender: ICommandSender): String = {
    s"/$getName"
  }

  override def checkPermission(server: MinecraftServer, sender: ICommandSender): Boolean = {
    if (requireOp) sender.canUseCommand(2, getName) else true
  }
}


class ReloadCommand extends CommandTrait {

  override def requireOp: Boolean = true

  override def getAliases: util.List[String] = {
    util.Collections.emptyList[String]()
  }

  override def isUsernameIndex(args: Array[String], index: Int): Boolean = false

  override def getName: String = "jtreload"

  override def execute(server: MinecraftServer, sender: ICommandSender, args: Array[String]): Unit = {
    Tweaker.readRecipesFiles()
  }

  override def getTabCompletions(server: MinecraftServer, sender: ICommandSender, args: Array[String], targetPos: BlockPos): util.List[String] = {
    util.Collections.emptyList[String]()
  }
}
