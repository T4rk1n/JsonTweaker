package com.tark.jsontweaker

import java.io.{BufferedWriter, File, FileReader, FileWriter}
import java.nio.file.Files

import com.google.gson._
import com.google.gson.stream.JsonReader
import net.minecraft.item.{Item, ItemStack}
import net.minecraft.item.crafting.{CraftingManager, IRecipe}
import net.minecraft.util.ResourceLocation
import net.minecraftforge.common.config.Configuration
import net.minecraftforge.oredict.ShapedOreRecipe
import org.apache.logging.log4j.{LogManager, Logger}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.matching.Regex

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
    JsonTweaker.castRecipes(outputStack, recipeObjects.result().toArray[AnyRef])
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
  var shapedRecipes: mutable.Builder[ShapedJsonRecipe, ArrayBuffer[ShapedJsonRecipe]] = mutable.ArrayBuffer.newBuilder[ShapedJsonRecipe]

  def readFile(): JsonRecipesHolder = {
    val reader = new JsonReader(new FileReader(file))
    val parser = new JsonParser()
    val jsonObject = parser.parse(reader).getAsJsonObject
    reader.close()
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
}

/**
  *
  * @param configDir main directory of the configs.
  * @param modId modId to create a folder and cfg
  */
class RecipeManager(configDir: String, modId: String) {
  val LOGGER: Logger = LogManager.getLogger(JsonTweaker.MOD_ID)
  val craftingManager: CraftingManager = CraftingManager.getInstance()
  var enableDefaultRecipes: Boolean = true

  def preInit(): Unit = {
    val configs = new Configuration(new File(s"$configDir/$modId/$modId.cfg"))
    configs.setCategoryRequiresMcRestart("recipes", true)
    enableDefaultRecipes = configs.getBoolean("enableDefaultRecipes", "recipes", true, "Enable the default recipes in vanilla.json")
    configs.save()
    val defaultRecipeFile = new File(s"$configDir/$modId/vanilla.json")
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

  def postInit(): Unit = {
    val walk = Files.walk(new File(s"$configDir/$modId").toPath).iterator
    while (walk.hasNext) {
      val file = walk.next
      val fileName = file.toFile.getName
      if (fileName endsWith "json") {
        if (!((fileName contains "vanilla") && !enableDefaultRecipes)) {
          LOGGER info s"processing $fileName"
          try {
            JsonRecipesHolder(new File(file.toUri)).readFile().shapedRecipes.result foreach registerJsonRecipe
          }
          catch {
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
      craftingManager.addRecipe(jsonRecipe.toShapedOreRecipe)
    } catch {
      case e: Exception =>
        LOGGER catching e
        LOGGER error s"Failed to register recipe for ${jsonRecipe.output}"
    }
  }

  def removeRecipe(output: String, firstOnly: Boolean=true): Unit = {
    val outputStack = ItemChecker getItemStackFromString output
    val recipes = craftingManager.getRecipeList
    val recIt = recipes.iterator
    val toRemove = mutable.ArrayBuffer.newBuilder[IRecipe]
    var stop = false
    while (recIt.hasNext && !stop) {
      val rec = recIt.next
      if (outputStack isItemEqual rec.getRecipeOutput) {
        toRemove += rec
        if (firstOnly) stop = true
      }
    }
    recipes.removeAll(toRemove.result.to[List].asJava)
  }
}
