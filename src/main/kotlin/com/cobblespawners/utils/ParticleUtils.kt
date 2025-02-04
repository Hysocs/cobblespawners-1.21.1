// File: ParticleUtils.kt
package com.cobblespawners.utils

import com.cobblespawners.CobbleSpawners
import com.cobblespawners.CobbleSpawners.random
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import java.util.UUID

object ParticleUtils {

    val activeVisualizations = mutableMapOf<UUID, Pair<BlockPos, Long>>()
    const val visualizationInterval: Long = 10L // Changed to 'const' for better performance

    init {
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            activeVisualizations.remove(handler.player.uuid)
        }
    }

    fun visualizeSpawnerPositions(player: ServerPlayerEntity, spawnerData: SpawnerData) {
        val serverWorld = player.world as? ServerWorld ?: return
        val categorizedPositions = CobbleSpawners.spawnerValidPositions[spawnerData.spawnerPos] ?: return

        val flameParticle = ParticleTypes.FLAME
        val blueFlameParticle = ParticleTypes.SOUL_FIRE_FLAME
        val centerPos = spawnerData.spawnerPos

        // Combine all spawn positions from all categories into a single list
        val allValidPositions = categorizedPositions.values.flatten()

        // Filter positions based on chunk proximity
        val nearbyPositions = allValidPositions.filter { spawnPos ->
            val playerChunkX = player.blockPos.x shr 4
            val playerChunkZ = player.blockPos.z shr 4
            val spawnChunkX = spawnPos.x shr 4
            val spawnChunkZ = spawnPos.z shr 4
            kotlin.math.abs(playerChunkX - spawnChunkX) <= 2 && kotlin.math.abs(playerChunkZ - spawnChunkZ) <= 2
        }

        // Send flame particles to the player for each valid spawn position
        nearbyPositions.forEach { spawnPos ->
            player.networkHandler.sendPacket(
                net.minecraft.network.packet.s2c.play.ParticleS2CPacket(
                    flameParticle,
                    true,
                    spawnPos.x + 0.5,
                    spawnPos.y + 1.0,
                    spawnPos.z + 0.5,
                    0.0f, 0.0f, 0.0f,
                    0.01f,
                    1
                )
            )
        }

        // Render the cube outline around the spawner
        renderCubeOutline(player, blueFlameParticle, centerPos, spawnerData.spawnRadius.width, spawnerData.spawnRadius.height)
    }

    private fun renderCubeOutline(
        player: ServerPlayerEntity,
        particleType: ParticleEffect,
        centerPos: BlockPos,
        radiusWidth: Int,
        radiusHeight: Int
    ) {
        for (x in listOf(-radiusWidth, radiusWidth)) {
            for (y in listOf(-radiusHeight, radiusHeight)) {
                for (z in -radiusWidth..radiusWidth) {
                    sendParticleIfInRange(player, particleType, centerPos.x + x, centerPos.y + y, centerPos.z + z)
                }
            }
        }

        for (y in listOf(-radiusHeight, radiusHeight)) {
            for (z in listOf(-radiusWidth, radiusWidth)) {
                for (x in -radiusWidth..radiusWidth) {
                    sendParticleIfInRange(player, particleType, centerPos.x + x, centerPos.y + y, centerPos.z + z)
                }
            }
        }

        for (x in listOf(-radiusWidth, radiusWidth)) {
            for (z in listOf(-radiusWidth, radiusWidth)) {
                for (y in -radiusHeight..radiusHeight) {
                    sendParticleIfInRange(player, particleType, centerPos.x + x, centerPos.y + y, centerPos.z + z)
                }
            }
        }
    }

    fun toggleVisualization(player: ServerPlayerEntity, spawnerData: SpawnerData) {
        val spawnerPos = spawnerData.spawnerPos
        val playerUUID = player.uuid

        if (activeVisualizations[playerUUID]?.first == spawnerPos) {
            activeVisualizations.remove(playerUUID)
            player.sendMessage(Text.literal("Stopped visualizing spawn points for spawner '${spawnerData.spawnerName}'"), false)
            return
        }

        // Check if valid positions are cached; if not, compute and cache them
        if (CobbleSpawners.spawnerValidPositions[spawnerPos].isNullOrEmpty()) {
            val serverWorld = player.world as? ServerWorld
            if (serverWorld != null) {
                val computedPositions = CobbleSpawners.computeValidSpawnPositions(serverWorld, spawnerData)
                if (computedPositions.isNotEmpty()) {
                    CobbleSpawners.spawnerValidPositions[spawnerPos] = computedPositions
                }
                if (computedPositions.isEmpty()) {
                    player.sendMessage(Text.literal("No valid spawn positions found for spawner '${spawnerData.spawnerName}'"), false)
                    return
                }
            }
        }

        activeVisualizations[playerUUID] = spawnerPos to player.world.time
        player.sendMessage(Text.literal("Started visualizing spawn points and cube outline for spawner '${spawnerData.spawnerName}'"), false)
    }

    private fun sendParticleIfInRange(player: ServerPlayerEntity, particleType: ParticleEffect, x: Int, y: Int, z: Int) {
        val playerChunkX = player.blockPos.x shr 4
        val playerChunkZ = player.blockPos.z shr 4
        val particleChunkX = x shr 4
        val particleChunkZ = z shr 4

        if (kotlin.math.abs(playerChunkX - particleChunkX) <= 2 && kotlin.math.abs(playerChunkZ - particleChunkZ) <= 2) {
            player.networkHandler.sendPacket(
                net.minecraft.network.packet.s2c.play.ParticleS2CPacket(
                    particleType,
                    true,
                    x + 0.5,
                    y + 1.0,
                    z + 0.5,
                    0.0f, 0.0f, 0.0f,
                    0.01f,
                    1
                )
            )
        }
    }

    fun spawnMonParticles(world: ServerWorld, spawnPos: BlockPos) {
        val particleCount = 10
        for (i in 0 until particleCount) {
            val xOffset = random.nextDouble() * 0.6 - 0.3
            val yOffset = random.nextDouble() * 0.6 - 0.3
            val zOffset = random.nextDouble() * 0.6 - 0.3
            val velocityX = random.nextDouble() * 0.02 - 0.01
            val velocityY = random.nextDouble() * 0.02 + 0.02
            val velocityZ = random.nextDouble() * 0.02 - 0.01
            world.spawnParticles(
                ParticleTypes.CLOUD,
                spawnPos.x + 0.5 + xOffset,
                spawnPos.y + 1.0 + yOffset,
                spawnPos.z + 0.5 + zOffset,
                1, velocityX, velocityY, velocityZ, 0.01
            )
        }
    }

    fun spawnSpawnerParticles(world: ServerWorld, spawnerPos: BlockPos) {
        val particleCount = 20
        for (i in 0 until particleCount) {
            val xOffset = random.nextDouble() * 0.6 - 0.3
            val yOffset = random.nextDouble() * 0.6 - 0.3
            val zOffset = random.nextDouble() * 0.6 - 0.3
            val velocityX = random.nextDouble() * 0.02 - 0.01
            val velocityY = random.nextDouble() * 0.02 + 0.02
            val velocityZ = random.nextDouble() * 0.02 - 0.01
            world.spawnParticles(
                ParticleTypes.SMOKE,
                spawnerPos.x + 0.5 + xOffset,
                spawnerPos.y + 1.0 + yOffset,
                spawnerPos.z + 0.5 + zOffset,
                1, velocityX, velocityY, velocityZ, 0.01
            )
        }
    }
}
