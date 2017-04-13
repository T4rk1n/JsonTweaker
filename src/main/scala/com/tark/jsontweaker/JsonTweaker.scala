package com.tark.jsontweaker

import java.io.{BufferedWriter, File, FileReader, FileWriter}
import java.nio.file.Files

import com.google.gson._
import com.google.gson.stream.JsonReader
import mezz.jei.api.IJeiRuntime
import net.minecraft.command.ICommandSender
import net.minecraft.item.{Item, ItemStack}
import net.minecraft.item.crafting.{CraftingManager, IRecipe}
import net.minecraft.util.ResourceLocation
import net.minecraft.util.text.TextComponentString
import net.minecraftforge.common.config.Configuration
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.Mod.EventHandler
import net.minecraftforge.fml.common.event.{FMLPostInitializationEvent, FMLPreInitializationEvent, FMLServerStartingEvent}
import net.minecraftforge.oredict.ShapedOreRecipe
import org.apache.logging.log4j.{LogManager, Logger}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, Future}
import scala.util.matching.Regex
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

/**
  * Shaped recipe creator
  * @param output the item to output
  * @param recipeSize not sure this works leaves at 3 to be sure
  */
case class ShapedJsonRecipe(var output: String="", var recipeSize: Int=3) {
  if (recipeSize < 2 || recipeSize > 3) throw new Error(s"Invalid recipe size: $recipeSize")
  var recipeArray = new Array[Array[String]](recipeSize)
  var currentRow = 0

  def addInputRow(item1: String, item2: String, item3: String): ShapedJsonRecipe = {
    if (currentRow >= recipeSize) throw new Error(s"Cannot add a row : maxSize=$recipeSize currentRow=$currentRow")
    recipeArray.update(currentRow, Array(item1, item2, item3))
    currentRow += 1
    this
  }

  def addInputRow(item1: String, item2: String): ShapedJsonRecipe = {
    if (currentRow >= recipeSize) throw new Error(s"Cannot add a row : maxSize=$recipeSize currentRow=$currentRow")
    recipeArray.update(currentRow, Array(item1, item2))
    currentRow += 1
    this
  }

  def toJsonObject: JsonObject = {
    val element = new JsonObject()
    val recipeArrayJson = new JsonArray()
    recipeArray foreach (row => {
      val rowJson = new JsonArray()
      row foreach (item => rowJson.add(new JsonPrimitive(item)))
      recipeArrayJson.add(rowJson)
    })
    element.add("output", new JsonPrimitive(output))
    element.add("input", recipeArrayJson)
    element
  }

  def fromJsonObject (jsonObject: JsonObject) : ShapedJsonRecipe = {
    output = jsonObject.get("output").getAsString
    val inputIterator = jsonObject.get("input").getAsJsonArray.iterator
    currentRow = 0
    while (inputIterator.hasNext) {
      val row = inputIterator.next.getAsJsonArray
      val rowLength = row.size
      if (recipeSize != rowLength) recipeSize = rowLength
      recipeSize match {
        case 2 => addInputRow(row.get(0).getAsString, row.get(1).getAsString)
        case 3 => addInputRow(row.get(0).getAsString, row.get(1).getAsString, row.get(2).getAsString)
      }
    }
    this
  }

  def toShapedOreRecipe: ShapedOreRecipe = {
    val SYMBOLS = "ABCDEFGHI".to[Array]
    var current = 0
    val outputStack = ItemChecker.getItemStackFromString(output)
    val inputMap = mutable.Map[String, Char]()
    var rows = mutable.ArrayBuffer.newBuilder[String]
    recipeArray foreach (row => {
      val currentRow = StringBuilder.newBuilder
      row foreach (item => {
        if (item == "") {
          currentRow.append(' ')
        } else {
          if (inputMap contains item ) {
            val charMap = inputMap get item
            currentRow.append(charMap.get)
          } else {
            val charMap = Option(SYMBOLS(current))
            inputMap += (item -> charMap.get)
            current += 1
            currentRow.append(charMap.get)
          }
        }
      })
      rows += currentRow.result
    })
    val recipeObjects = ArrayBuffer.newBuilder[AnyRef]
    rows.result foreach ((r) => recipeObjects += r )
    inputMap foreach {
      case (itemKey, mapped) =>
        recipeObjects += Predef char2Character mapped
        val (domain, name, _) = ItemChecker parseItemString itemKey
        if (domain == "ore") recipeObjects += name
        else recipeObjects += ItemChecker getItemStackFromString itemKey
    }
    JsonTweaker.castRecipes(outputStack, recipeObjects.result().toArray)
  }
}


object VanillaItems {
  val STRING    = "<minecraft:string>"
  val SLIMEBALL = "<minecraft:slime_ball>"
  val WEB       = "<minecraft:web>"
  val REDSTONE  = "<ore:dustRedstone>"
}

/**
  * Simple pattern checker inspired by minetweaker
  */
object ItemChecker {
  val itemPattern: Regex = "<([\\w_]*):([\\w_]*):?(\\d*)>".r

  def parseItemString(itemString: String): (String, String, String) = {
    val array = itemPattern.findFirstMatchIn(itemString).get
    (array.group(1), array.group(2), array.group(3))
  }

  def getItemStackFromString(itemString: String): ItemStack = {
    val (domain, name, meta) = parseItemString(itemString)
    val item = Item.REGISTRY.getObject(new ResourceLocation(domain, name))
    if (item == null) throw new Error(s"Invalid item : $domain.$name")
    new ItemStack(item, 1, if (meta != "")  meta.toInt else 0)
  }
}

/**
  *
  * @param file holding the recipes
  */
case class JsonRecipesHolder(file: File) {
  val shapedRecipes: mutable.Builder[ShapedJsonRecipe, ArrayBuffer[ShapedJsonRecipe]] = mutable.ArrayBuffer.newBuilder[ShapedJsonRecipe]
  var removeRecipes: mutable.Builder[String, ArrayBuffer[String]] = mutable.ArrayBuffer.newBuilder[String]

  def readFile(): JsonRecipesHolder = {
    val reader = new JsonReader(new FileReader(file))
    val jsonObject = (new JsonParser() parse reader).getAsJsonObject
    reader.close()
    if (jsonObject.has("remove")) {
      val removal = jsonObject.getAsJsonArray("remove").iterator
      while (removal.hasNext) addRemove(removal.next.getAsString)
    }
    if (jsonObject.has("shaped")) {
      val shaped = jsonObject.getAsJsonArray("shaped").iterator
      while (shaped.hasNext) {
        val rec = shaped.next.getAsJsonObject
        val recOut = rec.get("output").getAsString
        val recIn = rec.get("input").getAsJsonArray
        val recIt = recIn.iterator()
        val currentRecipe = ShapedJsonRecipe(recOut, recIn.size)
        while (recIt.hasNext) {
          val curRow = recIt.next().getAsJsonArray
          recIn.size match {
            case 2 => currentRecipe.addInputRow(curRow.get(0).getAsString,curRow.get(1).getAsString)
            case 3 => currentRecipe.addInputRow(curRow.get(0).getAsString, curRow.get(1).getAsString, curRow.get(2).getAsString)
          }
        }
        addShapedRecipe(currentRecipe)
      }
    }
    this
  }

  def writeFile(): JsonRecipesHolder = {
    val jsonObject = new JsonObject
    val shapedArray = new JsonArray
    shapedRecipes.result foreach (rec => {
      shapedArray.add(rec.toJsonObject)
    })
    jsonObject.add("shaped", shapedArray)
    val fileWrite = new BufferedWriter(new FileWriter(file))
    val builder = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    try {
      builder.toJson(jsonObject, fileWrite)
      fileWrite.flush()
    } finally {
      fileWrite.close()
    }
    this
  }

  def addShapedRecipe(shapedJsonRecipe: ShapedJsonRecipe): JsonRecipesHolder = {
    shapedRecipes += shapedJsonRecipe
    this
  }

  def addRemove(toRemove: String): JsonRecipesHolder = {
    removeRecipes += toRemove
    this
  }
}

object Configs {
  var enableDefaultRecipes = true
  var enableReloadCommand = true
  var asyncInit = true
}


@Mod(modid = Tweaker.MODID, name = Tweaker.MOD_NAME, version = Tweaker.VERSION, acceptedMinecraftVersions = "1.11.2", modLanguage = "scala")
object Tweaker {
  final val MODID = "jsontweaker"
  final val MOD_NAME = "JsonTweaker"
  final val VERSION = "0.0.1.1"
  val LOGGER: Logger = LogManager.getLogger(MODID)
  val craftingManager: CraftingManager = CraftingManager.getInstance()

  var removedRecipes: Array[IRecipe] = Array[IRecipe]()
  var addedRecipes: Array[String] = Array[String]()

  var configDir = ""
  var jeiRuntime: IJeiRuntime = _

  @EventHandler
  def preInit(event : FMLPreInitializationEvent): Unit = {
    configDir = event.getModConfigurationDirectory.getAbsolutePath
    val configs = new Configuration(new File(s"$configDir/$MODID/$MODID.cfg"), "2")
    Configs.enableDefaultRecipes = configs.getBoolean("enableDefaultRecipes", "recipes", true, "Enable the default recipes in vanilla.json")
    Configs.enableReloadCommand = configs.getBoolean("enableReloadCommand","commands", true, "Enable the command to reload the recipes.")
    Configs.asyncInit = configs.getBoolean("asyncInit", "debug", true, "Disable the asynchronous file process from init if it cause problems")
    configs.save()
    val defaultRecipeFile = new File(s"$configDir/$MODID/vanilla.json")
    if (!defaultRecipeFile.exists()) {
      LOGGER.info("Creating default recipes.")
      val vanillaRecipes = JsonRecipesHolder(defaultRecipeFile)
      val cobWeb = ShapedJsonRecipe(VanillaItems.WEB)
        .addInputRow(VanillaItems.STRING, "", VanillaItems.STRING)
        .addInputRow("", VanillaItems.SLIMEBALL, "")
        .addInputRow(VanillaItems.STRING, "", VanillaItems.STRING)
      vanillaRecipes.addShapedRecipe(cobWeb).writeFile()
    }
  }

  @EventHandler
  def postInit(event : FMLPostInitializationEvent): Unit = {
    if (Configs.asyncInit) readRecipesFiles() else Await ready[Unit](readRecipesFiles, Duration.Inf)
  }

  @EventHandler
  def onServerStarting(event: FMLServerStartingEvent) : Unit = {
    if (Configs.enableReloadCommand) event.registerServerCommand(new ReloadCommand)
  }

  def readRecipesFiles(): Future[Unit] = Future {
    val walk = Files.walk(new File(s"$configDir/$MODID").toPath).iterator
    while (walk.hasNext) {
      val file = walk.next
      val fileName = file.toFile.getName
      if (fileName endsWith "json") {
        if (!((fileName contains "vanilla") && !Configs.enableDefaultRecipes)) {
          try {
            LOGGER info s"processing $fileName"
            val rec = JsonRecipesHolder(new File(file.toUri)).readFile()
            removeRecipe(rec.removeRecipes.result.toArray, firstOnly = false)
            val recipeResults = rec.shapedRecipes.result
            addedRecipes = addedRecipes ++ recipeResults.map (out => out.output)
            recipeResults foreach registerJsonRecipe
          } catch {
            case e: Exception =>
              LOGGER catching e
              LOGGER error s"Failed to process $fileName"
          }
        }
      }
    }
  }

  def registerJsonRecipe(jsonRecipe: ShapedJsonRecipe ): Unit = {
    try {
      addRecipeToRegistry(jsonRecipe.toShapedOreRecipe)
    } catch {
      case e: Exception =>
        LOGGER catching e
        LOGGER error s"Failed to register recipe for ${jsonRecipe.output}"
    }
  }

  def addRecipeToRegistry(iRecipe: IRecipe): Unit = {
    craftingManager.addRecipe(iRecipe)
    if (jeiRuntime != null) jeiRuntime.getRecipeRegistry.addRecipe(iRecipe)
  }

  def reload(sender: ICommandSender): Future[Unit] = Future {
    LOGGER info "Reloading files."
    removedRecipes foreach addRecipeToRegistry
    removeRecipe(addedRecipes)
    removedRecipes = Array[IRecipe]()
    addedRecipes = Array[String]()
    readRecipesFiles().onComplete(_ => sender.sendMessage(new TextComponentString("Reloaded json tweaks")))
  }

  def removeRecipe(output: Array[String], firstOnly: Boolean=true): Unit = {
    val outputStacks = output map (item => ItemChecker getItemStackFromString item)
    val recipes = craftingManager.getRecipeList
    val recIt = recipes.iterator
    val toRemove = mutable.ArrayBuffer.newBuilder[IRecipe]
    def i() { while (recIt.hasNext) {
      val rec = recIt.next
      def re() : Boolean  = {
        outputStacks foreach(item => {
          if (item isItemEqual rec.getRecipeOutput) {
            recIt.remove()
            toRemove += rec
            if (firstOnly) return true
          }})
        false }
      if (re()) return }}
    i()
    val removed = toRemove.result.to[Array]
    removedRecipes = removedRecipes ++ removed
    if (jeiRuntime != null) removed foreach jeiRuntime.getRecipeRegistry.removeRecipe
  }
}

