package com.cobblespawners

import com.blanketutils.command.CommandManager
import com.blanketutils.utils.logDebug
import com.cobblespawners.utils.*

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
                        logDebug("Attempting to place custom spawner at $blockPosToPlace")
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

    private fun placeCustomSpawner(
        player: ServerPlayerEntity,
        world: net.minecraft.world.World,
        pos: BlockPos,
        itemInHand: ItemStack
    ) {
        if (!hasPermission(player, "CobbleSpawners.Place", 2)) {
            player.sendMessage(Text.literal("You don't have permission to place a custom spawner."), false)
            return
        }

        if (CobbleSpawnersConfig.spawners.containsKey(pos)) {
            player.sendMessage(Text.literal("A spawner already exists at this location!"), false)
            return
        }

        val blockState = world.getBlockState(pos)
        if (blockState.block == Blocks.WATER || blockState.block == Blocks.LAVA) {
            world.setBlockState(pos, Blocks.AIR.defaultState)
        }
        world.setBlockState(pos, Blocks.SPAWNER.defaultState)

        val spawnerName = "spawner_${CobbleSpawnersConfig.spawners.size + 1}"
        val dimensionString = "${world.registryKey.value.namespace}:${world.registryKey.value.path}"

        // Create default pokemon spawn settings
        val defaultPokemonEntry = PokemonSpawnEntry(
            pokemonName = "", // Will be set when pokemon is selected
            formName = null,
            spawnChance = 100.0,
            shinyChance = 1.0,
            minLevel = 1,
            maxLevel = 100,
            sizeSettings = SizeSettings(
                allowCustomSize = false,
                minSize = 1.0f,
                maxSize = 1.0f
            ),
            captureSettings = CaptureSettings(
                isCatchable = true,
                restrictCaptureToLimitedBalls = false,
                requiredPokeBalls = listOf("safari_ball")
            ),
            ivSettings = IVSettings(
                allowCustomIvs = false,
                minIVHp = 0,
                maxIVHp = 31,
                minIVAttack = 0,
                maxIVAttack = 31,
                minIVDefense = 0,
                maxIVDefense = 31,
                minIVSpecialAttack = 0,
                maxIVSpecialAttack = 31,
                minIVSpecialDefense = 0,
                maxIVSpecialDefense = 31,
                minIVSpeed = 0,
                maxIVSpeed = 31
            ),
            evSettings = EVSettings(
                allowCustomEvsOnDefeat = false,
                evHp = 0,
                evAttack = 0,
                evDefense = 0,
                evSpecialAttack = 0,
                evSpecialDefense = 0,
                evSpeed = 0
            ),
            spawnSettings = SpawnSettings(
                spawnTime = "ALL",
                spawnWeather = "ALL",
                spawnLocation = "ALL"
            ),
            heldItemsOnSpawn = HeldItemsOnSpawn(
                allowHeldItemsOnSpawn = false,
                itemsWithChance = mapOf(
                    "minecraft:cobblestone" to 0.1,
                    "cobblemon:pokeball" to 100.0
                )
            )
        )

        // Create SpawnerData with full initialization
        val spawnerData = SpawnerData(
            spawnerPos = pos,
            spawnerName = spawnerName,
            selectedPokemon = mutableListOf(), // We don't add the default entry yet - it will be added when a pokemon is selected
            dimension = dimensionString,
            spawnTimerTicks = 200,
            spawnRadius = SpawnRadius(
                width = 4,
                height = 4
            ),
            spawnLimit = 4,
            spawnAmountPerSpawn = 1,
            visible = true,
            showParticles = true
        )

        // Add the spawner to both the config and map
        CobbleSpawnersConfig.spawners[pos] = spawnerData
        CobbleSpawnersConfig.config.spawners.add(spawnerData)
        CobbleSpawnersConfig.saveSpawnerData()
        CobbleSpawnersConfig.saveConfigBlocking()

        logDebug("Placed spawner '$spawnerName' at $pos with full configuration")

        CobbleSpawners.spawnerValidPositions.remove(pos)
        player.sendMessage(Text.literal("Custom spawner '$spawnerName' placed at $pos!"), false)

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

                SpawnerUUIDManager.clearPokemonForSpawner(blockPos)
                CobbleSpawners.spawnerValidPositions.remove(blockPos)
                serverPlayer.sendMessage(Text.literal("Custom spawner removed at $blockPos."), false)
                logDebug("Custom spawner removed at $blockPos.")
            } else {
                invalidatePositionsIfWithinRadius(world, blockPos)
            }
            true
        }
    }

    private fun invalidatePositionsIfWithinRadius(world: ServerWorld, changedBlockPos: BlockPos) {
        for ((pos, data) in CobbleSpawnersConfig.spawners) {
            val distanceSquared = pos.getSquaredDistance(changedBlockPos)
            val maxDistanceSquared = (data.spawnRadius.width * data.spawnRadius.width).toDouble()
            if (distanceSquared <= maxDistanceSquared) {
                CobbleSpawners.spawnerValidPositions.remove(pos)
                logDebug("Invalidated cached positions for spawner at $pos due to block change @ $changedBlockPos")
            }
        }
    }
}
