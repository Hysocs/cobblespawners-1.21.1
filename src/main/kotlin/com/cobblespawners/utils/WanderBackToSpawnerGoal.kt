package com.cobblespawners.utils

import net.minecraft.entity.ai.goal.Goal
import net.minecraft.entity.mob.MobEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.Heightmap
import java.util.EnumSet
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class WanderBackToSpawnerGoal(
    private val entity: MobEntity,
    private val spawnerCenter: Vec3d,
    private val speed: Double,
    private val settings: WanderingSettings,
    private val tickDelay: Int = 10
) : Goal() {


    private val allowedRadius: Double = settings.wanderDistance.toDouble()
    private val allowedRadiusSquared = allowedRadius * allowedRadius
    private var targetPos: Vec3d? = null
    private var ticksSinceCheck = entity.random.nextInt(tickDelay)


    init {

        controls = EnumSet.of(Control.MOVE)
    }


    override fun canStart(): Boolean {

        if (!settings.enabled) return false

        if (--ticksSinceCheck > 0) return false
        ticksSinceCheck = tickDelay

        val distanceSq = entity.pos.squaredDistanceTo(spawnerCenter)

        return distanceSq > allowedRadiusSquared
    }


    override fun start() {

        entity.navigation.stop()


        targetPos = if (settings.wanderType.equals("RADIUS", ignoreCase = true)) {

            findRandomTargetInRadius()
        } else {

            spawnerCenter
        }


        if (targetPos != null) {

            val path = entity.navigation.findPathTo(targetPos!!.x, targetPos!!.y, targetPos!!.z, 0)
            if (path != null && path.reachesTarget()) {
                entity.navigation.startMovingAlong(path, speed)
            } else {

                entity.navigation.startMovingTo(targetPos!!.x, targetPos!!.y, targetPos!!.z, speed)

            }
        } else {

            entity.navigation.startMovingTo(spawnerCenter.x, spawnerCenter.y, spawnerCenter.z, speed)

        }
    }


    private fun findRandomTargetInRadius(): Vec3d? {
        for (i in 0..9) {
            val randomAngle = entity.random.nextDouble() * 2.0 * Math.PI

            val randomDist = sqrt(entity.random.nextDouble()) * allowedRadius

            val offsetX = cos(randomAngle) * randomDist
            val offsetZ = sin(randomAngle) * randomDist

            val potentialX = spawnerCenter.x + offsetX
            val potentialZ = spawnerCenter.z + offsetZ


            val targetBlockPos = BlockPos.ofFloored(potentialX, entity.y, potentialZ)
            val targetY = entity.world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, targetBlockPos.x, targetBlockPos.z)

            val potentialTarget = Vec3d(potentialX, targetY.toDouble(), potentialZ)


            if (potentialTarget.squaredDistanceTo(spawnerCenter) <= allowedRadiusSquared) {

                if (entity.navigation.findPathTo(potentialTarget.x, potentialTarget.y, potentialTarget.z, 0) != null) {
                    return potentialTarget
                }
            }
        }

        return null
    }


    /**
     * Continue the goal until the entity is back within radius AND navigation finishes.
     * Note: We check against the originally calculated target position's completion.
     */
    override fun shouldContinue(): Boolean {

        if (!settings.enabled) return false


        val isInsideRadius = entity.pos.squaredDistanceTo(spawnerCenter) <= allowedRadiusSquared
        val navigationIdle = entity.navigation.isIdle

        return !navigationIdle

    }

    /**
     * Stop the goal and halt navigation.
     */
    override fun stop() {

        targetPos = null
        entity.navigation.stop()
    }
}