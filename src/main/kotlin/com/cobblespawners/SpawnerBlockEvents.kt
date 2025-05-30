package com.cobblespawners

import com.everlastingutils.command.CommandManager
import com.everlastingutils.utils.logDebug
import com.cobblespawners.utils.*
import com.cobblespawners.api.SpawnerNBTManager
import com.cobblespawners.utils.gui.SpawnerPokemonSelectionGui
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.block.Blocks
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object SpawnerBlockEvents {

    fun registerEvents() {
        registerUseBlockCallback()
        registerBlockBreakCallback()
    }

    /**
     * A simple helper that uses BlanketUtils CommandManager to check if a player has a given permission,
     * falling back to OP-level checks if no permissions system is installed.
     */
    private fun hasPermission(player: ServerPlayerEntity, permission: String, requiredLevel: Int): Boolean {
        // Convert player to ServerCommandSource via player.commandSource
        val source = player.commandSource
        // BlanketUtils command manager checks both permission mods and OP level
        return CommandManager.hasPermissionOrOp(source, permission, requiredLevel, requiredLevel)
    }

    private fun registerUseBlockCallback() {
        UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
            if (
                player is ServerPlayerEntity &&
                hand == Hand.MAIN_HAND &&
                hitResult is BlockHitResult
            ) {
                val blockPos = hitResult.blockPos
                val blockState = world.getBlockState(blockPos)
                val itemInHand = player.getStackInHand(hand)
                val modelData = itemInHand.get(DataComponentTypes.CUSTOM_MODEL_DATA)

                // Check for custom spawner placement
                if (
                    itemInHand.item == Items.SPAWNER &&
                    modelData != null &&
                    modelData.value == 16666
                ) {
                    val blockPosToPlace = hitResult.blockPos.offset(hitResult.side)
                    val blockAtPlacement = world.getBlockState(blockPosToPlace)

                    if (blockAtPlacement.isAir || blockAtPlacement.block.defaultState.isReplaceable) {
                        logDebug("Attempting to place custom spawner at $blockPosToPlace", "cobblespawners")
                        placeCustomSpawner(player, world, blockPosToPlace, itemInHand)
                        return@register ActionResult.SUCCESS
                    }
                }

                // Check for spawner block right-click to open GUI
                if (
                    blockState.block == Blocks.SPAWNER &&
                    CobbleSpawnersConfig.spawners.containsKey(blockPos)
                ) {
                    // Swapped old CommandRegistrar.hasPermission to our helper
                    if (hasPermission(player, "CobbleSpawners.Edit", 2)) {
                        SpawnerPokemonSelectionGui.openSpawnerGui(player, blockPos)
                        return@register ActionResult.SUCCESS
                    } else {
                        player.sendMessage(Text.literal("You don't have permission to manage this spawner."), false)
                    }
                }
            }
            ActionResult.PASS
        }
    }

    /** Generates the next available spawner name in the sequence "spawner_<number>". */
    private fun getNextSpawnerName(): String {
        val existingNumbers = CobbleSpawnersConfig.spawners.values
            .mapNotNull { it.spawnerName }
            .filter { it.startsWith("spawner_") }
            .mapNotNull { it.removePrefix("spawner_").toIntOrNull() }
            .toSet()

        var number = 1
        while (existingNumbers.contains(number)) {
            number++
        }
        return "spawner_$number"
    }

    private fun placeCustomSpawner(
        player: ServerPlayerEntity,
        world: World,
        pos: BlockPos,
        itemInHand: ItemStack
    ) {
        // Check permissions
        if (!hasPermission(player, "CobbleSpawners.Place", 2)) {
            player.sendMessage(Text.literal("You don't have permission to place a custom spawner."), false)
            return
        }

        // Check if a spawner already exists at this location
        if (CobbleSpawnersConfig.spawners.containsKey(pos)) {
            player.sendMessage(Text.literal("A spawner already exists at this location!"), false)
            return
        }

        // Clear liquid blocks if present
        val blockState = world.getBlockState(pos)
        if (blockState.block == Blocks.WATER || blockState.block == Blocks.LAVA) {
            world.setBlockState(pos, Blocks.AIR.defaultState)
        }
        world.setBlockState(pos, Blocks.SPAWNER.defaultState)

        // Prepare dimension and Gson instance
        val dimensionString = "${world.registryKey.value.namespace}:${world.registryKey.value.path}"
        val gson = com.google.gson.Gson()

        // Generate the next sequential spawner name
        val spawnerName = getNextSpawnerName()

        // Retrieve and process custom NBT data
        val nbtComponent = itemInHand.get(DataComponentTypes.CUSTOM_DATA)
        val spawnerData: SpawnerData = if (nbtComponent != null) {
            val nbt = nbtComponent.getNbt() // Retrieve the NBT compound
            val configJson = nbt.getString("CobbleSpawnerConfig")
            val loadedData = gson.fromJson(configJson, SpawnerData::class.java)
            // Use the new sequential name, but copy other settings from the original
            loadedData.copy(
                spawnerPos = pos,
                spawnerName = spawnerName,
                dimension = dimensionString
            )
        } else {
            // Default spawner data with the sequential name
            SpawnerData(
                spawnerPos = pos,
                spawnerName = spawnerName,
                selectedPokemon = mutableListOf(),
                dimension = dimensionString,
                spawnTimerTicks = 200,
                spawnRadius = SpawnRadius(width = 4, height = 4),
                spawnLimit = 4,
                spawnAmountPerSpawn = 1,
                visible = true,
                wanderingSettings = WanderingSettings() // Include default wandering settings
            )
        }

        // Register the spawner
        CobbleSpawnersConfig.spawners[pos] = spawnerData
        CobbleSpawnersConfig.config.spawners.add(spawnerData)
        CobbleSpawnersConfig.saveSpawnerData()
        CobbleSpawnersConfig.saveConfigBlocking()

        // Notify the player and decrement the item if not in creative mode
        player.sendMessage(Text.literal("Custom spawner '${spawnerData.spawnerName}' placed at $pos!"), false)
        if (!player.abilities.creativeMode) {
            itemInHand.decrement(1)
        }
    }

    private fun registerBlockBreakCallback() {
        PlayerBlockBreakEvents.BEFORE.register { world, player, blockPos, blockState, _ ->
            val serverPlayer = player as? ServerPlayerEntity ?: return@register true
            if (world !is ServerWorld) return@register true

            if (
                blockState.block == Blocks.SPAWNER &&
                CobbleSpawnersConfig.spawners.containsKey(blockPos)
            ) {
                if (!hasPermission(serverPlayer, "CobbleSpawners.break", 2)) {
                    serverPlayer.sendMessage(Text.literal("You don't have permission to remove this spawner."), false)
                    return@register false
                }

                // Remove from both the map and config spawners list
                CobbleSpawnersConfig.spawners.remove(blockPos)
                CobbleSpawnersConfig.config.spawners.removeIf { it.spawnerPos == blockPos }
                CobbleSpawnersConfig.saveSpawnerData()
                CobbleSpawnersConfig.saveConfigBlocking()

                SpawnerNBTManager.clearPokemonForSpawner(world, blockPos)
                CobbleSpawners.spawnerValidPositions.remove(blockPos)
                serverPlayer.sendMessage(Text.literal("Custom spawner removed at $blockPos."), false)
                logDebug("Custom spawner removed at $blockPos.", "cobblespawners")
            } else {
                invalidatePositionsIfWithinRadius(world, blockPos)
            }
            true
        }
    }

    private fun invalidatePositionsIfWithinRadius(world: ServerWorld, changedBlockPos: BlockPos) {
        for ((pos, data) in CobbleSpawnersConfig.spawners) {
            val spawnRadius = data.spawnRadius
            if (spawnRadius != null) {
                val distanceSquared = pos.getSquaredDistance(changedBlockPos)
                val maxDistanceSquared = (spawnRadius.width * spawnRadius.width).toDouble()
                if (distanceSquared <= maxDistanceSquared) {
                    CobbleSpawners.spawnerValidPositions.remove(pos)
                    logDebug("Invalidated cached positions for spawner at $pos due to block change @ $changedBlockPos", "cobblespawners")
                }
            }
        }
    }
}