package lgbt.faith.chiyoko.player

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.nio.file.Files

object ChiyokoConfig {
    private data class Data(
        var autocrack: Boolean = true,
        var autocrackArmour: Boolean = true,
        var autocrackDrops: Boolean = false,
    )

    private val LOGGER = LoggerFactory.getLogger("chiyoko-player")
    private val GSON = GsonBuilder().setPrettyPrinting().create()
    private val PATH = FabricLoader.getInstance().configDir.resolve("chiyoko-player.json")

    private var data = Data()

    var autocrack: Boolean
        get() = data.autocrack
        set(value) {
            data.autocrack = value
            save()
        }

    var autocrackArmour: Boolean
        get() = data.autocrackArmour
        set(value) {
            data.autocrackArmour = value
            save()
        }

    var autocrackDrops: Boolean
        get() = data.autocrackDrops
        set(value) {
            data.autocrackDrops = value
            save()
        }

    fun load() {
        if (!Files.exists(PATH)) return
        try {
            data = GSON.fromJson(Files.readString(PATH), Data::class.java) ?: Data()
        } catch (e: Exception) {
            LOGGER.error("failed to read config, using defaults", e)
        }
    }

    private fun save() {
        try {
            Files.writeString(PATH, GSON.toJson(data))
        } catch (e: Exception) {
            LOGGER.error("failed to save config", e)
        }
    }
}
