package com.cobblespawners.utils

import com.blanketutils.utils.logDebug
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.pokeball.PokeBallCaptureCalculatedEvent
import com.cobblemon.mod.common.api.pokeball.catching.CaptureContext
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.util.concurrent.ConcurrentHashMap

class CatchingTracker {

    // A data class to hold tracking information per player
    data class PokeballTrackingInfo(
        val pokeBallUuid: java.util.UUID,
        val pokeBallEntity: EmptyPokeBallEntity
    )

    // Map to keep track of each player's pokeball tracking info
    private val playerTrackingMap = ConcurrentHashMap<ServerPlayerEntity, PokeballTrackingInfo>()

    fun registerEvents() {
        CobblemonEvents.POKE_BALL_CAPTURE_CALCULATED.subscribe { event ->
            handlePokeBallCaptureCalculated(event)
        }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            playerTrackingMap.forEach { (player, trackingInfo) ->
                val world = player.world as ServerWorld
                if (world.getEntity(trackingInfo.pokeBallUuid) == null) {
                    returnPokeballToPlayer(player, trackingInfo.pokeBallEntity)
                    playerTrackingMap.remove(player)
                }
            }
        }
    }

    private fun handlePokeBallCaptureCalculated(event: PokeBallCaptureCalculatedEvent) {
        val pokeBallEntity: EmptyPokeBallEntity = event.pokeBallEntity
        val pokemonEntity: PokemonEntity = event.pokemonEntity
        val thrower: ServerPlayerEntity? = pokeBallEntity.owner as? ServerPlayerEntity

        logDebug("PokeBallCaptureCalculatedEvent triggered for Pokémon: ${pokemonEntity.pokemon.species.name}, UUID: ${pokemonEntity.uuid}")

        val spawnerInfo = SpawnerUUIDManager.getPokemonInfo(pokemonEntity.uuid)
        if (spawnerInfo != null) {
            logDebug("Pokémon ${pokemonEntity.pokemon.species.name} is from spawner at ${spawnerInfo.spawnerPos}")

            val spawnerData = CobbleSpawnersConfig.spawners[spawnerInfo.spawnerPos]
            val pokemonSpawnEntry = spawnerData?.selectedPokemon?.find {
                it.pokemonName.equals(pokemonEntity.pokemon.species.name, ignoreCase = true)
            }

            if (pokemonSpawnEntry != null) {
                val captureSettings = pokemonSpawnEntry.captureSettings

                // If isCatchable is false, deny capture
                if (!captureSettings.isCatchable) {
                    event.captureResult = CaptureContext(
                        numberOfShakes = 0,
                        isSuccessfulCapture = false,
                        isCriticalCapture = false
                    )
                    logDebug("Capture attempt failed: ${pokemonEntity.pokemon.species.name} is not catchable.")

                    thrower?.sendMessage(
                        Text.literal("This Pokémon cannot be captured!")
                            .formatted(Formatting.RED),
                        false
                    )
                    logDebug("Sent message to player: This Pokémon cannot be captured!")

                    // Track this player’s pokeball for potential removal in the tick event
                    thrower?.let {
                        playerTrackingMap[it] = PokeballTrackingInfo(pokeBallEntity.uuid, pokeBallEntity)
                    }
                    return // Exit early since this Pokémon cannot be caught
                }

                val usedPokeBall = pokeBallEntity.pokeBall
                val usedPokeBallName = usedPokeBall.name.toString()
                val allowedPokeBalls = prepareAllowedPokeBallList(captureSettings.requiredPokeBalls)

                logDebug("Used Pokéball: $usedPokeBallName, Allowed Pokéballs: $allowedPokeBalls")

                if (captureSettings.restrictCaptureToLimitedBalls) {
                    if (!allowedPokeBalls.contains("ALL") && !isValidPokeBall(allowedPokeBalls, usedPokeBallName)) {
                        event.captureResult = CaptureContext(
                            numberOfShakes = 0,
                            isSuccessfulCapture = false,
                            isCriticalCapture = false
                        )
                        logDebug("Capture attempt failed: ${pokemonEntity.pokemon.species.name} can only be captured with one of the following balls: $allowedPokeBalls.")

                        thrower?.sendMessage(
                            Text.literal("Only the following Pokéballs can capture this Pokémon: $allowedPokeBalls!")
                                .formatted(Formatting.RED),
                            false
                        )
                        logDebug("Sent message to player: Only the following Pokéballs can capture this Pokémon: $allowedPokeBalls!")

                        // Track this player’s pokeball for potential removal in the tick event
                        thrower?.let {
                            playerTrackingMap[it] = PokeballTrackingInfo(pokeBallEntity.uuid, pokeBallEntity)
                        }
                    } else {
                        logDebug("Valid Pokéball used successfully to capture ${pokemonEntity.pokemon.species.name}.")
                    }
                } else {
                    logDebug("No capture restriction for Pokémon: ${pokemonEntity.pokemon.species.name}. Any Pokéball is allowed.")
                }
            } else {
                logDebug("Pokémon ${pokemonEntity.pokemon.species.name} not found in the spawner's configuration.")
            }
        } else {
            logDebug("Pokémon ${pokemonEntity.pokemon.species.name} is NOT from a spawner.")
        }
    }


    /**
     * Prepares the list of allowed Poké Balls by ensuring each name has the "cobblemon:" namespace.
     */
    private fun prepareAllowedPokeBallList(allowedPokeBalls: List<String>): List<String> {
        return allowedPokeBalls.map { ball ->
            if (!ball.contains(":")) {
                "cobblemon:$ball" // Add the "cobblemon:" namespace if not present
            } else {
                ball // Already namespaced, keep as is
            }
        }
    }

    private fun isValidPokeBall(allowedPokeBalls: List<String>, usedPokeBallName: String): Boolean {
        val usedPokeBallNamespace = "cobblemon:$usedPokeBallName"
        return allowedPokeBalls.contains(usedPokeBallName) || allowedPokeBalls.contains(usedPokeBallNamespace)
    }

    private fun returnPokeballToPlayer(player: ServerPlayerEntity?, pokeBallEntity: EmptyPokeBallEntity) {
        if (player == null) return

        pokeBallEntity.discard()

        val usedPokeBallItem = pokeBallEntity.pokeBall.item()
        val pokeBallStack = usedPokeBallItem.defaultStack

        val ballPos = pokeBallEntity.blockPos

        val world = player.world
        world.spawnEntity(net.minecraft.entity.ItemEntity(world, ballPos.x + 0.5, ballPos.y + 0.5, ballPos.z + 0.5, pokeBallStack))

        logDebug("Spawned Pokéball ${usedPokeBallItem.name} at ${ballPos.x}, ${ballPos.y}, ${ballPos.z} after failed capture.")
    }
}
