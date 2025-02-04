package com.cobblespawners.utils

import com.blanketutils.command.CommandManager
import com.blanketutils.gui.setCustomName
import com.cobblespawners.CobbleSpawners
import com.cobblespawners.utils.gui.SpawnerListGui
import com.cobblespawners.utils.gui.SpawnerPokemonSelectionGui
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.CustomModelDataComponent
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.math.Vec3d
import org.slf4j.LoggerFactory
import net.minecraft.server.command.CommandManager.argument
import java.util.concurrent.CompletableFuture

/**
 * Refactored to use BlanketUtils' [CommandManager] with minimal Brigadier argument usage.
 * Debug commands have been removed to reduce size. This is a simpler approach:
 * each subcommand parses arguments manually from the raw input string.
 */
object CommandRegistrar {

    private val logger = LoggerFactory.getLogger("CobbleSpawners-CommandRegistrar")
    private val cmdManager = CommandManager("cobblespawners", defaultPermissionLevel = 2, defaultOpLevel = 2)

    fun registerCommands() {
        cmdManager.command("cobblespawners", permission = "cobblespawners.base", aliases = listOf("bcs")) {
            // "edit" subcommand: /cobblespawners edit <spawnerName>
            subcommand("edit", permission = "CobbleSpawners.Edit") {
                executes { ctx -> handleEditCommand(ctx) }
            }

            // "rename" subcommand: /cobblespawners rename <oldName> <newName>
            subcommand("rename", permission = "CobbleSpawners.Rename") {
                executes { ctx -> handleRenameCommand(ctx) }
            }

            // "addmon" subcommand: /cobblespawners addmon <spawnerName> <pokemonName> [formName]
            subcommand("addmon", permission = "CobbleSpawners.Addmon") {
                executes { ctx -> handleAddMonCommand(ctx) }
            }

            // "removemon" subcommand: /cobblespawners removemon <spawnerName> <pokemonName> [formName]
            subcommand("removemon", permission = "CobbleSpawners.Removemon") {
                executes { ctx -> handleRemoveMonCommand(ctx) }
            }

            // "killspawned" subcommand: /cobblespawners killspawned <spawnerName>
            // Enhanced killspawned command with suggestions
            subcommand("killspawned", permission = "CobbleSpawners.Killspawned") {
                executes { ctx ->
                    // Show usage when no arguments are provided
                    sendError(ctx, "Usage: /cobblespawners killspawned <spawnerName>")
                    0
                }

                then(
                    argument("spawnerName", StringArgumentType.string())
                        .suggests { context: CommandContext<ServerCommandSource>, builder: SuggestionsBuilder ->
                            val completableFuture = CompletableFuture<Suggestions>()
                            // Add all existing spawner names as suggestions
                            CobbleSpawnersConfig.spawners.values.forEach { spawner ->
                                builder.suggest(spawner.spawnerName)
                            }
                            completableFuture.complete(builder.build())
                            completableFuture
                        }
                        .executes { ctx ->
                            handleKillSpawned(ctx)
                        }
                )
            }
            // "togglevisibility" subcommand: /cobblespawners togglevisibility <spawnerName>
            subcommand("togglevisibility", permission = "CobbleSpawners.ToggleVisibility") {
                executes { ctx -> handleToggleVisibility(ctx) }
            }

            // "toggleradius" subcommand: /cobblespawners toggleradius <spawnerName>
            subcommand("toggleradius", permission = "CobbleSpawners.ToggleRadius") {
                executes { ctx ->
                    sendError(ctx, "Usage: /cobblespawners toggleradius <spawnerName>")
                    0
                }

                then(
                    argument("spawnerName", StringArgumentType.string())
                        .suggests { context: CommandContext<ServerCommandSource>, builder: SuggestionsBuilder ->
                            val completableFuture = CompletableFuture<Suggestions>()
                            // Add all existing spawner names as suggestions
                            CobbleSpawnersConfig.spawners.values.forEach { spawner ->
                                builder.suggest(spawner.spawnerName)
                            }
                            completableFuture.complete(builder.build())
                            completableFuture
                        }
                        .executes { ctx -> handleToggleRadius(ctx) }
                )
            }


            // "teleport" subcommand: /cobblespawners teleport <spawnerName>
            subcommand("teleport", permission = "CobbleSpawners.Teleport") {
                executes { ctx -> handleTeleportCommand(ctx) }
            }

            // "list" subcommand: /cobblespawners list
            subcommand("list", permission = "CobbleSpawners.List") {
                executes { ctx -> handleListCommand(ctx) }
            }

            // "gui" subcommand: /cobblespawners gui
            subcommand("gui", permission = "CobbleSpawners.GUI") {
                executes { ctx -> handleGuiCommand(ctx) }
            }

            // "reload" subcommand: /cobblespawners reload
            subcommand("reload", permission = "CobbleSpawners.Reload") {
                executes { ctx -> handleReloadCommand(ctx) }
            }

            // "givespawnerblock" subcommand: /cobblespawners givespawnerblock
            subcommand("givespawnerblock", permission = "CobbleSpawners.GiveSpawnerBlock") {
                executes { ctx -> handleGiveSpawnerBlock(ctx) }
            }

            // "help" subcommand: /cobblespawners help
            subcommand("help", permission = "CobbleSpawners.Help") {
                executes { ctx -> handleHelpCommand(ctx) }
            }
        }

        cmdManager.register()
    }

    /**
     * Basic argument splitter: This reads the entire command string from Brigadier, splits by space,
     * and returns a list of tokens. e.g. "/cobblespawners rename oldName newName" -> ["cobblespawners", "rename", "oldName", "newName"]
     * We use indexes carefully to fetch subcommand arguments.
     */
    private fun getArgs(context: CommandContext<ServerCommandSource>): List<String> {
        return context.input.trim().split("\\s+".toRegex())
    }

    // --------------------------------------------------------------------------------------------
    // Subcommand Handlers
    // --------------------------------------------------------------------------------------------

    private fun handleEditCommand(ctx: CommandContext<ServerCommandSource>): Int {
        val args = getArgs(ctx)
        if (args.size < 3) {
            sendError(ctx, "Usage: /cobblespawners edit <spawnerName>")
            return 0
        }
        val spawnerName = args[2]
        val spawnerEntry = CobbleSpawnersConfig.spawners.values.find { it.spawnerName == spawnerName }
        val player = ctx.source.player as? ServerPlayerEntity

        if (spawnerEntry == null) {
            sendError(ctx, "Spawner '$spawnerName' not found.")
            return 0
        }
        if (player == null) {
            sendError(ctx, "Only players can use /cobblespawners edit.")
            return 0
        }
        SpawnerPokemonSelectionGui.openSpawnerGui(player, spawnerEntry.spawnerPos)
        sendSuccess(ctx, "GUI for spawner '$spawnerName' has been opened.")
        return 1
    }

    private fun handleRenameCommand(ctx: CommandContext<ServerCommandSource>): Int {
        val args = getArgs(ctx)
        if (args.size < 4) {
            sendError(ctx, "Usage: /cobblespawners rename <oldName> <newName>")
            return 0
        }
        val currentName = args[2]
        val newName = args[3]
        val spawnerEntry = CobbleSpawnersConfig.spawners.values.find { it.spawnerName == currentName }

        if (spawnerEntry == null) {
            sendError(ctx, "Spawner '$currentName' not found.")
            return 0
        }
        if (CobbleSpawnersConfig.spawners.values.any { it.spawnerName == newName }) {
            sendError(ctx, "Spawner name '$newName' is already in use.")
            return 0
        }
        spawnerEntry.spawnerName = newName
        CobbleSpawnersConfig.saveSpawnerData()
        sendSuccess(ctx, "Spawner renamed from '$currentName' to '$newName'.")
        return 1
    }

    private fun handleAddMonCommand(ctx: CommandContext<ServerCommandSource>): Int {
        val args = getArgs(ctx)
        if (args.size < 4) {
            sendError(ctx, "Usage: /cobblespawners addmon <spawnerName> <pokemonName> [formName]")
            return 0
        }
        val spawnerName = args[2]
        val pokemonName = args[3].lowercase()
        val formName = if (args.size >= 5) args[4].lowercase() else ""

        val spawnerEntry = CobbleSpawnersConfig.spawners.values.find { it.spawnerName == spawnerName }
        if (spawnerEntry == null) {
            sendError(ctx, "Spawner '$spawnerName' not found.")
            return 0
        }
        val species = PokemonSpecies.getByName(pokemonName)
        if (species == null) {
            sendError(ctx, "Pokémon '$pokemonName' not found.")
            return 0
        }

        val selectedForm = when {
            species.forms.isEmpty() -> "Normal"
            species.forms.any { it.name.equals(formName, ignoreCase = true) } -> formName
            formName.isBlank() || formName.equals("normal", ignoreCase = true) -> "Normal"
            else -> {
                sendError(ctx, "Form '$formName' does not exist for Pokémon '$pokemonName'. Defaulting to 'Normal'.")
                "Normal"
            }
        }

        val newEntry = PokemonSpawnEntry(
            pokemonName = pokemonName,
            formName = selectedForm,
            spawnChance = 50.0,
            shinyChance = 0.0,
            minLevel = 1,
            maxLevel = 100,
            captureSettings = CaptureSettings(
                isCatchable = true,
                restrictCaptureToLimitedBalls = true,
                requiredPokeBalls = listOf("safari_ball")
            ),
            ivSettings = IVSettings(
                allowCustomIvs = false,
                minIVHp = 0, maxIVHp = 31,
                minIVAttack = 0, maxIVAttack = 31,
                minIVDefense = 0, maxIVDefense = 31,
                minIVSpecialAttack = 0, maxIVSpecialAttack = 31,
                minIVSpecialDefense = 0, maxIVSpecialDefense = 31,
                minIVSpeed = 0, maxIVSpeed = 31
            ),
            evSettings = EVSettings(
                allowCustomEvsOnDefeat = false,
                evHp = 0, evAttack = 0, evDefense = 0,
                evSpecialAttack = 0, evSpecialDefense = 0, evSpeed = 0
            ),
            spawnSettings = SpawnSettings(
                spawnTime = "ALL",
                spawnWeather = "ALL",
                spawnLocation = "ALL"
            )
        )

        if (CobbleSpawnersConfig.addPokemonSpawnEntry(spawnerEntry.spawnerPos, newEntry)) {
            sendSuccess(ctx, "Added Pokémon '$pokemonName' (form '$selectedForm') to spawner '$spawnerName'.")
            return 1
        } else {
            sendError(ctx, "Failed to add Pokémon '$pokemonName' to spawner '$spawnerName'.")
            return 0
        }
    }

    private fun handleRemoveMonCommand(ctx: CommandContext<ServerCommandSource>): Int {
        val args = getArgs(ctx)
        if (args.size < 4) {
            sendError(ctx, "Usage: /cobblespawners removemon <spawnerName> <pokemonName> [formName]")
            return 0
        }

        val spawnerName = args[2]
        val pokemonName = args[3]
        val formName = if (args.size >= 5) args[4] else null

        val spawnerEntry = CobbleSpawnersConfig.spawners.values.find { it.spawnerName == spawnerName }
        if (spawnerEntry == null) {
            sendError(ctx, "Spawner '$spawnerName' not found.")
            return 0
        }

        val success = CobbleSpawnersConfig.removePokemonSpawnEntry(spawnerEntry.spawnerPos, pokemonName, formName)
        return if (success) {
            sendSuccess(ctx, "Removed Pokémon '$pokemonName' (form '${formName ?: "any"}') from spawner '$spawnerName'.")
            1
        } else {
            sendError(ctx, "Failed to remove Pokémon '$pokemonName' from spawner '$spawnerName'.")
            0
        }
    }

    private fun handleKillSpawned(ctx: CommandContext<ServerCommandSource>): Int {
        val spawnerName = StringArgumentType.getString(ctx, "spawnerName")
        val spawnerEntry = CobbleSpawnersConfig.spawners.values.find { it.spawnerName == spawnerName }

        if (spawnerEntry == null) {
            sendError(ctx, "Spawner '$spawnerName' not found.")
            return 0
        }

        val spawnerPos = spawnerEntry.spawnerPos
        val server = ctx.source.server
        val worldKey = CobbleSpawners.parseDimension(spawnerEntry.dimension)
        val serverWorld = server.getWorld(worldKey)

        if (serverWorld == null) {
            sendError(ctx, "World '${worldKey.value}' not found.")
            return 0
        }

        val uuids = SpawnerUUIDManager.getUUIDsForSpawner(spawnerPos)
        uuids.forEach { uuid ->
            val entity = serverWorld.getEntity(uuid)
            if (entity is com.cobblemon.mod.common.entity.pokemon.PokemonEntity) {
                entity.discard()
                SpawnerUUIDManager.removePokemon(uuid)
            }
        }

        sendSuccess(ctx, "All Pokémon spawned by '$spawnerName' have been removed.")
        return 1
    }

    private fun handleToggleVisibility(ctx: CommandContext<ServerCommandSource>): Int {
        val args = getArgs(ctx)
        if (args.size < 3) {
            sendError(ctx, "Usage: /cobblespawners togglevisibility <spawnerName>")
            return 0
        }
        val spawnerName = args[2]
        val spawnerData = CobbleSpawnersConfig.spawners.values.find { it.spawnerName == spawnerName }
        if (spawnerData == null) {
            sendError(ctx, "Spawner '$spawnerName' not found.")
            return 0
        }
        if (CommandRegistrarUtil.toggleSpawnerVisibility(ctx.source.server, spawnerData.spawnerPos)) {
            sendSuccess(ctx, "Spawner '$spawnerName' visibility toggled.")
            return 1
        }
        sendError(ctx, "Failed to toggle visibility for '$spawnerName'.")
        return 0
    }

    private fun handleToggleRadius(ctx: CommandContext<ServerCommandSource>): Int {
        val spawnerName = StringArgumentType.getString(ctx, "spawnerName")
        val spawnerData = CobbleSpawnersConfig.spawners.values.find { it.spawnerName == spawnerName }
        val player = ctx.source.player

        if (spawnerData == null) {
            sendError(ctx, "Spawner '$spawnerName' not found.")
            return 0
        }
        if (player == null) {
            sendError(ctx, "Only players can use /cobblespawners toggleradius.")
            return 0
        }
        ParticleUtils.toggleVisualization(player, spawnerData)
        sendSuccess(ctx, "Spawn radius visualization toggled for '$spawnerName'.")
        return 1
    }


    private fun handleTeleportCommand(ctx: CommandContext<ServerCommandSource>): Int {
        val args = getArgs(ctx)
        if (args.size < 3) {
            sendError(ctx, "Usage: /cobblespawners teleport <spawnerName>")
            return 0
        }
        val spawnerName = args[2]
        val spawnerData = CobbleSpawnersConfig.spawners.values.find { it.spawnerName == spawnerName }
        val player = ctx.source.player as? ServerPlayerEntity
        if (spawnerData == null) {
            sendError(ctx, "Spawner '$spawnerName' not found.")
            return 0
        }
        if (player == null) {
            sendError(ctx, "Only players can use /cobblespawners teleport.")
            return 0
        }
        val worldKey = CobbleSpawners.parseDimension(spawnerData.dimension)
        val world = ctx.source.server.getWorld(worldKey)
        if (world == null) {
            sendError(ctx, "Dimension '${spawnerData.dimension}' not found.")
            return 0
        }
        val pos = spawnerData.spawnerPos
        player.teleport(world, pos.x + 0.5, pos.y + 0.0, pos.z + 0.5, player.yaw, player.pitch)
        sendSuccess(ctx, "Teleported to spawner '$spawnerName'.")
        return 1
    }

    private fun handleListCommand(ctx: CommandContext<ServerCommandSource>): Int {
        val player = ctx.source.player as? ServerPlayerEntity ?: run {
            sendError(ctx, "Only players can use /cobblespawners list.")
            return 0
        }
        val spawnerList = CobbleSpawnersConfig.spawners.values.joinToString("\n") { data ->
            "${data.spawnerName}: ${data.spawnerPos.x}, ${data.spawnerPos.y}, ${data.spawnerPos.z} (${data.dimension})"
        }
        if (spawnerList.isBlank()) {
            player.sendMessage(Text.literal("No spawners found."), false)
        } else {
            player.sendMessage(Text.literal("Spawners:\n$spawnerList"), false)
        }
        return 1
    }

    private fun handleGuiCommand(ctx: CommandContext<ServerCommandSource>): Int {
        val player = ctx.source.player as? ServerPlayerEntity ?: run {
            sendError(ctx, "Only players can use /cobblespawners gui.")
            return 0
        }
        SpawnerListGui.openSpawnerListGui(player)
        sendSuccess(ctx, "Opened Spawner GUI.")
        return 1
    }

    private fun handleReloadCommand(ctx: CommandContext<ServerCommandSource>): Int {
        CobbleSpawnersConfig.reloadBlocking()
        sendSuccess(ctx, "CobbleSpawners configuration reloaded.")
        return 1
    }

    /**
     * **CHANGED** to include the custom model data (16666) on the spawner item,
     * so it can be properly recognized in the "use block" event for placement.
     */
    private fun handleGiveSpawnerBlock(ctx: CommandContext<ServerCommandSource>): Int {
        val player = ctx.source.player as? ServerPlayerEntity ?: run {
            sendError(ctx, "Only players can use /cobblespawners givespawnerblock.")
            return 0
        }
        val customSpawnerItem = ItemStack(Items.SPAWNER).apply {
            count = 1
            // Set a custom model data of 16666 to track spawner placement
            val customModelData = CustomModelDataComponent(16666)
            set(DataComponentTypes.CUSTOM_MODEL_DATA, customModelData)

            // Set a custom name for clarity
            setCustomName(Text.literal("Custom Cobble Spawner"))
        }
        if (player.inventory.insertStack(customSpawnerItem)) {
            sendSuccess(ctx, "A custom spawner has been added to your inventory.")
        } else {
            sendError(ctx, "Inventory full. Could not add the custom spawner.")
        }
        return 1
    }

    private fun handleHelpCommand(ctx: CommandContext<ServerCommandSource>): Int {
        val player = ctx.source.player as? ServerPlayerEntity ?: run {
            sendError(ctx, "Only players can view help.")
            return 0
        }
        val helpText = Text.literal("CobbleSpawners Commands:\n").append(
            Text.literal(
                """
                /cobblespawners reload
                /cobblespawners list
                /cobblespawners gui
                /cobblespawners edit <spawnerName>
                /cobblespawners rename <oldName> <newName>
                /cobblespawners addmon <spawnerName> <pokemonName> [formName]
                /cobblespawners removemon <spawnerName> <pokemonName> [formName]
                /cobblespawners killspawned <spawnerName>
                /cobblespawners togglevisibility <spawnerName>
                /cobblespawners toggleradius <spawnerName>
                /cobblespawners teleport <spawnerName>
                /cobblespawners givespawnerblock
                """.trimIndent()
            )
        )
        player.sendMessage(helpText, false)
        return 1
    }

    // --------------------------------------------------------------------------------------------
    // Utility methods
    // --------------------------------------------------------------------------------------------

    private fun sendSuccess(ctx: CommandContext<ServerCommandSource>, message: String) {
        CommandManager.sendSuccess(ctx.source, message, false)
    }

    private fun sendError(ctx: CommandContext<ServerCommandSource>, message: String) {
        CommandManager.sendError(ctx.source, message)
    }
}

/**
 * Just factoring out the "toggleSpawnerVisibility" logic to keep the main file shorter.
 * (Originally at the bottom of the old CommandRegistrar.)
 */
object CommandRegistrarUtil {
    private val logger = LoggerFactory.getLogger("CobbleSpawners-CmdUtil")

    fun toggleSpawnerVisibility(server: net.minecraft.server.MinecraftServer, spawnerPos: net.minecraft.util.math.BlockPos): Boolean {
        val spawnerData = CobbleSpawnersConfig.getSpawner(spawnerPos)
            ?: run {
                logger.error("No spawner found at $spawnerPos.")
                return false
            }
        spawnerData.visible = !spawnerData.visible
        val dimensionKey = CobbleSpawners.parseDimension(spawnerData.dimension)
        val world = server.getWorld(dimensionKey) ?: return false

        return try {
            if (spawnerData.visible) {
                world.setBlockState(spawnerPos, net.minecraft.block.Blocks.SPAWNER.defaultState)
            } else {
                world.setBlockState(spawnerPos, net.minecraft.block.Blocks.AIR.defaultState)
            }
            CobbleSpawnersConfig.saveSpawnerData()
            true
        } catch (e: Exception) {
            logger.error("Error toggling spawner at $spawnerPos: ${e.message}")
            false
        }
    }
}
