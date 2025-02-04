package com.cobblespawners.utils

import com.blanketutils.config.ConfigData
import com.blanketutils.config.ConfigManager
import com.blanketutils.config.ConfigMetadata
import kotlinx.coroutines.runBlocking
import net.minecraft.util.math.BlockPos
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

// Data classes remain unchanged.
data class GlobalConfig(
    var debugEnabled: Boolean = false,
    var cullSpawnerPokemonOnServerStop: Boolean = true,
    var showUnimplementedPokemonInGui: Boolean = false,
    var showFormsInGui: Boolean = false
)

data class CaptureSettings(
    var isCatchable: Boolean = true,
    var restrictCaptureToLimitedBalls: Boolean = false,
    var requiredPokeBalls: List<String> = listOf("safari_ball")
)

data class IVSettings(
    var allowCustomIvs: Boolean = false,
    var minIVHp: Int = 0,
    var maxIVHp: Int = 31,
    var minIVAttack: Int = 0,
    var maxIVAttack: Int = 31,
    var minIVDefense: Int = 0,
    var maxIVDefense: Int = 31,
    var minIVSpecialAttack: Int = 0,
    var maxIVSpecialAttack: Int = 31,
    var minIVSpecialDefense: Int = 0,
    var maxIVSpecialDefense: Int = 31,
    var minIVSpeed: Int = 0,
    var maxIVSpeed: Int = 31
)

data class EVSettings(
    var allowCustomEvsOnDefeat: Boolean = false,
    var evHp: Int = 0,
    var evAttack: Int = 0,
    var evDefense: Int = 0,
    var evSpecialAttack: Int = 0,
    var evSpecialDefense: Int = 0,
    var evSpeed: Int = 0
)

data class SpawnSettings(
    var spawnTime: String = "ALL",
    var spawnWeather: String = "ALL",
    var spawnLocation: String = "ALL",
)

data class SizeSettings(
    var allowCustomSize: Boolean = false,
    var minSize: Float = 1.0f,
    var maxSize: Float = 1.0f
)

data class HeldItemsOnSpawn(
    var allowHeldItemsOnSpawn: Boolean = false,
    var itemsWithChance: Map<String, Double> = mapOf(
        "minecraft:cobblestone" to 0.1,
        "cobblemon:pokeball" to 100.0
    )
)

data class PokemonSpawnEntry(
    val pokemonName: String,
    var formName: String? = null,
    var spawnChance: Double,
    var shinyChance: Double,
    var minLevel: Int,
    var maxLevel: Int,
    var sizeSettings: SizeSettings = SizeSettings(),
    val captureSettings: CaptureSettings,
    val ivSettings: IVSettings,
    val evSettings: EVSettings,
    val spawnSettings: SpawnSettings,
    var heldItemsOnSpawn: HeldItemsOnSpawn = HeldItemsOnSpawn()
)

data class SpawnRadius(
    var width: Int = 4,
    var height: Int = 4
)

data class SpawnerData(
    val spawnerPos: BlockPos,
    var spawnerName: String = "default_spawner",
    var selectedPokemon: MutableList<PokemonSpawnEntry> = mutableListOf(),
    val dimension: String = "minecraft:overworld",
    var spawnTimerTicks: Long = 200,
    var spawnRadius: SpawnRadius = SpawnRadius(),
    var spawnLimit: Int = 4,
    var spawnAmountPerSpawn: Int = 1,
    var visible: Boolean = true,
    var showParticles: Boolean = true
)

data class CobbleSpawnersConfigData(
    override val version: String = "1.0.5",
    override val configId: String = "cobblespawners",

    var globalConfig: GlobalConfig = GlobalConfig(),
    var spawners: MutableList<SpawnerData> = mutableListOf()
) : ConfigData

object CobbleSpawnersConfig {
    private val logger = LoggerFactory.getLogger("CobbleSpawnersConfig")
    private const val CURRENT_VERSION = "1.0.5"

    private lateinit var configManager: ConfigManager<CobbleSpawnersConfigData>
    private var isInitialized = false

    private val configMetadata = ConfigMetadata(
        headerComments = listOf(
            "CobbleSpawners Configuration File",
            "",
            "Global Config: Contains debug settings, culling options, and GUI display preferences.",
            "Spawner Data: Defines each spawner's position, spawn timing, radius, and visibility options.",
            "Pokemon Spawn Entry: Holds the settings for each Pokémon, including spawn chances, levels, sizes, and additional spawn details.",
            "",
            "DO NOT MODIFY 'version' or 'configId' as these are used for configuration management."
        ),
        footerComments = listOf("End of CobbleSpawners Configuration"),
        sectionComments = mapOf(
            "" to "Configuration root",
            "globalConfig" to "Global configuration options for CobbleSpawners.",
            "spawners" to "List of spawner data entries.",
            "version" to "WARNING: Do not edit this value.",
            "configId" to "WARNING: Do not edit this value."
        ),
        includeTimestamp = true,
        includeVersion = true
    )

    val config: CobbleSpawnersConfigData
        get() = configManager.getCurrentConfig()

    val spawners: ConcurrentHashMap<BlockPos, SpawnerData> = ConcurrentHashMap()
    val lastSpawnTicks: ConcurrentHashMap<BlockPos, Long> = ConcurrentHashMap()

    // Debug logging is now disabled.
    private fun logDebug(message: String) { }

    fun initializeAndLoad() {
        if (isInitialized) return
        configManager = ConfigManager(
            currentVersion = CURRENT_VERSION,
            defaultConfig = CobbleSpawnersConfigData(),
            configClass = CobbleSpawnersConfigData::class,
            metadata = configMetadata
        )
        runBlocking { configManager.reloadConfig() }
        loadSpawnerDataInMemory()
        isInitialized = true
    }

    fun reloadBlocking() {
        runBlocking { configManager.reloadConfig() }
        loadSpawnerDataInMemory()
    }

    private fun loadSpawnerDataInMemory() {
        spawners.clear()
        lastSpawnTicks.clear()
        for (spawner in config.spawners) {
            spawners[spawner.spawnerPos] = spawner
        }
    }

    fun saveSpawnerData() {
        val existingConfigData = config.spawners.associateBy { it.spawnerPos }
        config.spawners = spawners.values.map { currentSpawner ->
            existingConfigData[currentSpawner.spawnerPos]?.let { existingSpawner ->
                currentSpawner.copy(
                    selectedPokemon = existingSpawner.selectedPokemon.toMutableList()
                )
            } ?: currentSpawner
        }.toMutableList()

        logDebug("Spawner data saved.")
        saveConfigBlocking()
    }

    private fun roundToOneDecimal(value: Float): Float {
        return (value * 10).roundToInt() / 10f
    }

    fun updateLastSpawnTick(spawnerPos: BlockPos, tick: Long) {
        logDebug("Updated last spawn tick for spawner at $spawnerPos")
        lastSpawnTicks[spawnerPos] = tick
    }

    fun getLastSpawnTick(spawnerPos: BlockPos): Long {
        return lastSpawnTicks[spawnerPos] ?: 0L
    }

    fun addSpawner(spawnerPos: BlockPos, dimension: String): Boolean {
        if (spawners.containsKey(spawnerPos)) {
            logDebug("Spawner at $spawnerPos already exists.")
            return false
        }
        val spawnerData = SpawnerData(
            spawnerPos = spawnerPos,
            dimension = dimension
        )
        spawners[spawnerPos] = spawnerData
        saveSpawnerData()
        logDebug("Added spawner at $spawnerPos.")
        return true
    }

    fun updateSpawner(spawnerPos: BlockPos, update: (SpawnerData) -> Unit): SpawnerData? {
        val spawnerData = spawners[spawnerPos] ?: return null
        update(spawnerData)
        saveSpawnerData()
        return spawnerData
    }

    fun getSpawner(spawnerPos: BlockPos): SpawnerData? {
        return spawners[spawnerPos]
    }

    fun removeSpawner(spawnerPos: BlockPos): Boolean {
        val removed = spawners.remove(spawnerPos) != null
        if (removed) {
            lastSpawnTicks.remove(spawnerPos)
            saveSpawnerData()
            logDebug("Removed spawner at $spawnerPos.")
            return true
        } else {
            logDebug("Spawner not found at $spawnerPos.")
            return false
        }
    }

    fun updatePokemonSpawnEntry(
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        update: (PokemonSpawnEntry) -> Unit
    ): PokemonSpawnEntry? {
        val spawnerData = spawners[spawnerPos] ?: return null
        val selectedEntry = spawnerData.selectedPokemon.find {
            it.pokemonName.equals(pokemonName, ignoreCase = true) &&
                    (it.formName.equals(formName, ignoreCase = true) || (it.formName == null && formName == null))
        } ?: return null

        update(selectedEntry)
        selectedEntry.sizeSettings.minSize = roundToOneDecimal(selectedEntry.sizeSettings.minSize)
        selectedEntry.sizeSettings.maxSize = roundToOneDecimal(selectedEntry.sizeSettings.maxSize)
        saveSpawnerData()
        return selectedEntry
    }

    fun getPokemonSpawnEntry(spawnerPos: BlockPos, pokemonName: String, formName: String?): PokemonSpawnEntry? {
        val spawnerData = spawners[spawnerPos] ?: return null
        return spawnerData.selectedPokemon.find {
            it.pokemonName.equals(pokemonName, ignoreCase = true) &&
                    (it.formName.equals(formName, ignoreCase = true) || (it.formName == null && formName == null))
        }
    }

    fun addPokemonSpawnEntry(spawnerPos: BlockPos, entry: PokemonSpawnEntry): Boolean {
        val spawnerData = spawners[spawnerPos] ?: return false
        if (spawnerData.selectedPokemon.any {
                it.pokemonName.equals(entry.pokemonName, ignoreCase = true) &&
                        (it.formName.equals(entry.formName, ignoreCase = true) || (it.formName == null && entry.formName == null))
            }
        ) {
            logDebug("Pokémon '${entry.pokemonName}' already selected.")
            return false
        }
        spawnerData.selectedPokemon.add(entry)
        saveSpawnerData()
        logDebug("Added Pokémon '${entry.pokemonName}' to spawner at $spawnerPos.")
        return true
    }

    fun removePokemonSpawnEntry(spawnerPos: BlockPos, pokemonName: String, formName: String?): Boolean {
        val spawnerData = spawners[spawnerPos] ?: return false
        val removed = spawnerData.selectedPokemon.removeIf {
            it.pokemonName.equals(pokemonName, ignoreCase = true) &&
                    (it.formName.equals(formName, ignoreCase = true) || (it.formName == null && formName == null))
        }
        if (removed) {
            saveSpawnerData()
            logDebug("Removed Pokémon '$pokemonName' from spawner at $spawnerPos.")
            return true
        } else {
            logDebug("Pokémon '$pokemonName' not found in spawner at $spawnerPos.")
            return false
        }
    }

    fun saveConfigBlocking() {
        runBlocking {
            configManager.saveConfig(configManager.getCurrentConfig())
        }
    }

    fun createDefaultSpawner(spawnerPos: BlockPos, dimension: String, spawnerName: String): SpawnerData {
        val spawnerData = SpawnerData(
            spawnerPos = spawnerPos,
            spawnerName = spawnerName,
            selectedPokemon = mutableListOf(),
            dimension = dimension,
            spawnTimerTicks = 200,
            spawnRadius = SpawnRadius(4, 4),
            spawnLimit = 4,
            spawnAmountPerSpawn = 1,
            visible = true,
            showParticles = true
        )
        spawners[spawnerPos] = spawnerData
        config.spawners.add(spawnerData)
        saveSpawnerData()
        saveConfigBlocking()
        return spawnerData
    }

    fun createDefaultPokemonEntry(pokemonName: String, formName: String? = null): PokemonSpawnEntry {
        return PokemonSpawnEntry(
            pokemonName = pokemonName,
            formName = formName,
            spawnChance = 50.0,
            shinyChance = 0.0,
            minLevel = 1,
            maxLevel = 100,
            sizeSettings = SizeSettings(allowCustomSize = false, minSize = 1.0f, maxSize = 1.0f),
            captureSettings = CaptureSettings(isCatchable = true, restrictCaptureToLimitedBalls = false, requiredPokeBalls = listOf("poke_ball")),
            ivSettings = IVSettings(allowCustomIvs = false, minIVHp = 0, maxIVHp = 31, minIVAttack = 0, maxIVAttack = 31,
                minIVDefense = 0, maxIVDefense = 31, minIVSpecialAttack = 0, maxIVSpecialAttack = 31,
                minIVSpecialDefense = 0, maxIVSpecialDefense = 31, minIVSpeed = 0, maxIVSpeed = 31),
            evSettings = EVSettings(allowCustomEvsOnDefeat = false, evHp = 0, evAttack = 0, evDefense = 0, evSpecialAttack = 0, evSpecialDefense = 0, evSpeed = 0),
            spawnSettings = SpawnSettings(spawnTime = "ALL", spawnWeather = "ALL", spawnLocation = "ALL"),
            heldItemsOnSpawn = HeldItemsOnSpawn(allowHeldItemsOnSpawn = false, itemsWithChance = mapOf("minecraft:cobblestone" to 0.1, "cobblemon:pokeball" to 100.0))
        )
    }

    fun addDefaultPokemonToSpawner(spawnerPos: BlockPos, pokemonName: String, formName: String?): Boolean {
        val spawnerData = spawners[spawnerPos] ?: return false
        if (spawnerData.selectedPokemon.any {
                it.pokemonName.equals(pokemonName, ignoreCase = true) &&
                        (it.formName?.equals(formName, ignoreCase = true) ?: (formName == null))
            }) {
            logDebug("Pokémon already exists in spawner")
            return false
        }
        val newEntry = createDefaultPokemonEntry(pokemonName, formName)
        val updatedPokemonList = spawnerData.selectedPokemon.toMutableList().apply { add(newEntry) }
        spawners[spawnerPos] = spawnerData.copy(selectedPokemon = updatedPokemonList)
        config.spawners.find { it.spawnerPos == spawnerPos }?.selectedPokemon = updatedPokemonList
        saveSpawnerData()
        return true
    }

    fun removeAndSavePokemonFromSpawner(spawnerPos: BlockPos, pokemonName: String, formName: String?): Boolean {
        val spawnerData = spawners[spawnerPos] ?: return false
        val updatedPokemonList = spawnerData.selectedPokemon.toMutableList()
        val removed = updatedPokemonList.removeIf {
            it.pokemonName.equals(pokemonName, ignoreCase = true) &&
                    (it.formName?.equals(formName, ignoreCase = true) ?: (formName == null))
        }
        if (removed) {
            spawners[spawnerPos] = spawnerData.copy(selectedPokemon = updatedPokemonList)
            config.spawners.find { it.spawnerPos == spawnerPos }?.selectedPokemon = updatedPokemonList
            saveSpawnerData()
            return true
        }
        return false
    }

    fun removeAndSaveSpawner(spawnerPos: BlockPos): Boolean {
        val removed = spawners.remove(spawnerPos) != null
        if (removed) {
            config.spawners.removeIf { it.spawnerPos == spawnerPos }
            lastSpawnTicks.remove(spawnerPos)
            saveSpawnerData()
            return true
        }
        return false
    }
}
