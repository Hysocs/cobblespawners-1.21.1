// File: BattleTracker.kt
package com.cobblespawners.utils

import com.everlastingutils.scheduling.SchedulerManager
import com.everlastingutils.utils.logDebug
import com.cobblespawners.utils.CobbleSpawnersConfig
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.battles.actor.PokemonBattleActor
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblespawners.api.SpawnerNBTManager
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.BlockPos
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class BattleTracker {
    private val logger = org.slf4j.LoggerFactory.getLogger("cobblespawners")

    private fun logDebug(message: String) {
        if (CobbleSpawnersConfig.config.globalConfig.debugEnabled) {
            logger.info("[DEBUG] $message")
        }
    }

    enum class BattleEndCause {
        NORMAL_VICTORY,
        FLED,
        CAPTURED,
        UNKNOWN
    }

    val ongoingBattles = ConcurrentHashMap<UUID, BattleInfo>()

    data class BattleInfo(
        val battleId: UUID,
        var actors: List<BattleActor>,
        val participatingPlayerMons: ConcurrentHashMap<UUID, Pokemon> = ConcurrentHashMap(), // Tracks all player Pokémon that participated
        val lastActiveOpponentMon: ConcurrentHashMap<UUID, Pokemon> = ConcurrentHashMap(),
        var isOpponentFromSpawner: Boolean = false,
        var spawnerPos: BlockPos? = null,
        val originalEVMap: ConcurrentHashMap<UUID, Map<Stat, Int>> = ConcurrentHashMap(),
        var valuesApplied: Boolean = false,
        var currentActivePlayerPokemon: Pokemon? = null,
        var endCause: BattleEndCause = BattleEndCause.UNKNOWN,
        val startTime: Long = System.currentTimeMillis()
    )

    private val MAX_BATTLE_DURATION = 10 * 60 * 1000 // 10 minutes in milliseconds

    fun registerEvents() {
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe { event ->
            handleBattleStartPre(event.battle.battleId)
        }
        CobblemonEvents.BATTLE_STARTED_POST.subscribe { event ->
            handleBattleStartPost(event.battle.battleId, event.battle.actors.toList())
        }
        CobblemonEvents.POKEMON_SENT_POST.subscribe { event ->
            handlePokemonSent(event.pokemon)
        }
        CobblemonEvents.BATTLE_VICTORY.subscribe { event ->
            handleBattleVictory(event.battle.battleId)
        }
        CobblemonEvents.BATTLE_FLED.subscribe { event ->
            handleBattleFlee(event.battle.battleId)
        }
        CobblemonEvents.POKEMON_CAPTURED.subscribe { event ->
            handlePokemonCaptured(event.pokemon)
        }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            handlePlayerLogout(handler.player)
        }
    }

    /**
     * Starts a scheduler to periodically clean up ended or timed-out battles.
     */
    fun startCleanupScheduler(server: MinecraftServer) {
        val schedulerId = "cobblespawners-battle-cleanup"
        SchedulerManager.scheduleAtFixedRate(
            schedulerId,
            server,
            0,
            1000,
            TimeUnit.MILLISECONDS,
            { cleanupBattles(server) }
        )
        logDebug("Started battle cleanup scheduler with ID: $schedulerId")
        println("Started battle cleanup scheduler with ID: $schedulerId")
    }

    /**
     * Cleans up battles that have ended or exceeded the maximum duration.
     */
    private fun cleanupBattles(server: MinecraftServer) {
        val currentTime = System.currentTimeMillis()
        val battlesToCleanup = mutableListOf<UUID>()

        ongoingBattles.forEach { (battleId, battleInfo) ->
            val battleEnded = battleInfo.actors.any { actor ->
                when (actor) {
                    is PlayerBattleActor -> actor.getPlayerUUIDs().any { uuid ->
                        server.playerManager.getPlayer(uuid)?.let { player -> playerIsNotInBattle(player) } ?: true
                    }
                    else -> false
                }
            }

            if (battleEnded || currentTime - battleInfo.startTime > MAX_BATTLE_DURATION) {
                synchronized(battleInfo) {
                    battleInfo.endCause = BattleEndCause.UNKNOWN
                }
                println("Battle $battleId exceeded maximum duration or ended. Preparing for cleanup.")
                applyValuesAfterBattle(battleId)
                battlesToCleanup.add(battleId)
                logDebug("Battle $battleId exceeded maximum duration or ended and was marked for cleanup.")
            }
        }

        battlesToCleanup.forEach { cleanupBattle(it) }
    }

    private fun handleBattleStartPre(battleId: UUID) {
        logDebug("Battle pre-start for Battle ID: $battleId")
        println("Battle pre-start for Battle ID: $battleId")
        ongoingBattles[battleId] = BattleInfo(
            battleId = battleId,
            actors = emptyList()
        )
    }

    private fun handleBattleStartPost(battleId: UUID, actors: List<BattleActor>) {
        logDebug("Battle fully started for Battle ID: $battleId")
        println("Battle fully started for Battle ID: $battleId with actors: ${actors.map { it.javaClass.simpleName }}")
        val battleInfo = ongoingBattles[battleId] ?: return
        battleInfo.actors = actors

        actors.forEach { actor ->
            when (actor) {
                is PlayerBattleActor -> handlePlayerActivePokemon(battleId, actor.pokemonList.firstOrNull()?.effectedPokemon)
                is PokemonBattleActor -> handleOpponentActivePokemon(battleId, actor.pokemonList.firstOrNull()?.effectedPokemon)
            }
        }
    }

    private fun handlePokemonSent(pokemon: Pokemon) {
        val battleId = findBattleIdByPokemon(pokemon)
        if (battleId != null) {
            if (pokemon.entity?.owner is ServerPlayerEntity) {
                println("Player swapped in Pokémon: ${pokemon.species.name}")
                logDebug("Player swapped in Pokémon: ${pokemon.species.name}")
                val battleInfo = ongoingBattles[battleId] ?: return
                synchronized(battleInfo) {
                    // If there is an existing active Pokémon and it's different from the one being sent in,
                    // update its baseline EVs before switching.
                    battleInfo.currentActivePlayerPokemon?.let { oldPokemon ->
                        if (oldPokemon.uuid != pokemon.uuid) {
                            println("Detected swap: Old Pokémon ${oldPokemon.species.name} (UUID: ${oldPokemon.uuid}) is being swapped out for ${pokemon.species.name} (UUID: ${pokemon.uuid})")
                            saveOriginalEVs(battleId, oldPokemon)
                        }
                    }
                }
                handlePlayerActivePokemon(battleId, pokemon)
            } else {
                println("Opponent swapped in Pokémon: ${pokemon.species.name}")
                logDebug("Opponent swapped in Pokémon: ${pokemon.species.name}")
                handleOpponentActivePokemon(battleId, pokemon)
            }
        } else {
            println("Pokémon sent out outside of tracked battle: ${pokemon.species.name}")
            logDebug("Pokémon sent out outside of tracked battle: ${pokemon.species.name}")
        }
    }

    private fun handlePlayerActivePokemon(battleId: UUID, pokemon: Pokemon?) {
        if (pokemon == null) return logDebug("Player active Pokémon is null, skipping EV save.")
        val battleInfo = ongoingBattles[battleId] ?: return
        synchronized(battleInfo) {
            // Set as current active Pokémon and update baseline EVs
            battleInfo.currentActivePlayerPokemon = pokemon
            battleInfo.participatingPlayerMons[pokemon.uuid] = pokemon
            println("Setting active player's Pokémon: ${pokemon.species.name} (UUID: ${pokemon.uuid})")
            saveOriginalEVs(battleId, pokemon)
            logDebug("Tracking Player's Pokémon: ${pokemon.species.name}, UUID: ${pokemon.uuid}")
        }
    }

    /**
     * Handles when an opponent Pokémon is sent into battle, checking if it’s from a spawner
     * and has custom EVs enabled based on species, form, and aspects (excluding gender).
     */
    private fun handleOpponentActivePokemon(battleId: UUID, pokemon: Pokemon?) {
        if (pokemon == null) return logDebug("Opponent active Pokémon is null, skipping battle logic.")
        val battleInfo = ongoingBattles[battleId] ?: return
        synchronized(battleInfo) {
            if (!battleInfo.isOpponentFromSpawner) {
                pokemon.entity?.let { entity ->
                    SpawnerNBTManager.getPokemonInfo(entity)?.let { spawnerInfo ->
                        val (speciesName, formName, aspectsWithoutGender) = getPokemonVariantDetails(pokemon)
                        println("Opponent Pokémon details: species=$speciesName, form=$formName, aspects=$aspectsWithoutGender")
                        val spawnerData = CobbleSpawnersConfig.spawners[spawnerInfo.spawnerPos]
                        if (spawnerData != null) {
                            val matchingEntry = spawnerData.selectedPokemon.find {
                                it.pokemonName.equals(speciesName, ignoreCase = true) &&
                                        (it.formName?.equals(formName, ignoreCase = true) ?: (formName == "Normal")) &&
                                        it.aspects.map { a -> a.lowercase() }.toSet() == aspectsWithoutGender.map { a -> a.lowercase() }.toSet()
                            }
                            if (matchingEntry != null && matchingEntry.evSettings.allowCustomEvsOnDefeat) {
                                println("Found matching PokémonSpawnEntry for ${pokemon.species.name} with custom EVs enabled")
                                battleInfo.lastActiveOpponentMon[pokemon.uuid] = pokemon
                                battleInfo.isOpponentFromSpawner = true
                                battleInfo.spawnerPos = spawnerInfo.spawnerPos
                                // Save EVs for the current player Pokémon if available
                                battleInfo.currentActivePlayerPokemon?.let { playerPokemon ->
                                    println("Saving baseline EVs for current active player's Pokémon before opponent swap: ${playerPokemon.species.name}")
                                    saveOriginalEVs(battleId, playerPokemon)
                                }
                            } else {
                                println("No matching PokémonSpawnEntry with custom EVs for ${pokemon.species.name} (form: $formName, aspects: $aspectsWithoutGender)")
                            }
                        } else {
                            println("Spawner data not found for position ${spawnerInfo.spawnerPos}")
                        }
                    }
                } ?: logDebug("Opponent's Pokémon is not from a spawner. Skipping EV tracking.")
            }
        }
    }

    private fun handleBattleVictory(battleId: UUID) {
        if (!ongoingBattles.containsKey(battleId)) return
        println("Battle victory for Battle ID: $battleId")
        logDebug("Battle victory for Battle ID: $battleId")
        val battleInfo = ongoingBattles[battleId] ?: return
        synchronized(battleInfo) {
            battleInfo.endCause = BattleEndCause.NORMAL_VICTORY
        }
        applyValuesAfterBattle(battleId)
        cleanupBattle(battleId)
    }

    private fun handleBattleFlee(battleId: UUID) {
        if (!ongoingBattles.containsKey(battleId)) return
        println("Battle flee for Battle ID: $battleId")
        logDebug("Battle fled for Battle ID: $battleId")
        val battleInfo = ongoingBattles[battleId] ?: return
        synchronized(battleInfo) {
            battleInfo.endCause = BattleEndCause.FLED
        }
        cleanupBattle(battleId)
    }

    private fun handlePokemonCaptured(pokemon: Pokemon) {
        val battleId = findBattleIdByPokemon(pokemon)
        if (battleId != null) {
            println("Pokémon captured during battle: ${pokemon.species.name}")
            logDebug("Pokémon captured during battle: ${pokemon.species.name}")
            val battleInfo = ongoingBattles[battleId] ?: return
            synchronized(battleInfo) {
                battleInfo.endCause = BattleEndCause.CAPTURED
            }
            applyValuesAfterBattle(battleId)
            cleanupBattle(battleId)
        } else {
            println("Pokémon captured outside of battle: ${pokemon.species.name}")
            logDebug("Pokémon captured outside of battle: ${pokemon.species.name}")
        }
    }

    private fun handlePlayerLogout(player: ServerPlayerEntity) {
        val battleId = findBattleIdByPlayer(player)
        if (battleId != null) {
            println("Player ${player.name.string} logged out during battle $battleId.")
            logDebug("Player ${player.name.string} logged out during battle $battleId.")
            val battleInfo = ongoingBattles[battleId] ?: return
            synchronized(battleInfo) {
                battleInfo.endCause = BattleEndCause.UNKNOWN
            }
            cleanupBattle(battleId)
        } else {
            println("Player ${player.name.string} logged out but was not in a tracked battle.")
            logDebug("Player ${player.name.string} logged out but was not in a tracked battle.")
        }
    }

    /**
     * Applies custom EVs after a battle victory against a spawner Pokémon.
     * Now only applies the EV modifications to the currently active (alive) player Pokémon.
     */
    private fun applyValuesAfterBattle(battleId: UUID) {
        val battleInfo = ongoingBattles[battleId] ?: return
        synchronized(battleInfo) {
            if (!battleInfo.isOpponentFromSpawner) {
                println("Battle ID: $battleId did not involve spawner Pokémon. Skipping EV modifications.")
                logDebug("Battle ID: $battleId did not involve spawner Pokémon. Skipping EV modifications.")
                return
            }
            if (battleInfo.valuesApplied) {
                println("EV values already applied for Battle ID: $battleId")
                logDebug("Values already applied for Battle ID: $battleId")
                return
            }
            if (battleInfo.endCause != BattleEndCause.NORMAL_VICTORY) {
                println("Battle ended with cause ${battleInfo.endCause}. Skipping EV application for Battle ID: $battleId")
                logDebug("Battle ended with cause ${battleInfo.endCause}. Skipping EV application for Battle ID: $battleId")
                return
            }
            val opponentPokemon = battleInfo.lastActiveOpponentMon.values.firstOrNull() ?: return
            battleInfo.currentActivePlayerPokemon?.let { playerPokemon ->
                println("Reverting and applying EVs for active player's Pokémon: ${playerPokemon.species.name}")
                logDebug("Reverting and applying EVs for active player's Pokémon: ${playerPokemon.species.name}")
                revertEVsAfterChange(battleId, playerPokemon)
                applyCustomEVs(playerPokemon, opponentPokemon, battleInfo.spawnerPos)
            }
            battleInfo.valuesApplied = true
        }
    }

    /**
     * Saves (or updates) the baseline EVs for a Pokémon.
     * This function now always records the current EVs regardless of whether they were saved before.
     */
    private fun saveOriginalEVs(battleId: UUID, pokemon: Pokemon) {
        val battleInfo = ongoingBattles[battleId] ?: return
        if (!battleInfo.isOpponentFromSpawner) {
            println("Skipping EV save for non-spawner battle Pokémon: ${pokemon.species.name}")
            logDebug("Skipping EV save for non-spawner battle Pokémon: ${pokemon.species.name}")
            return
        }
        val currentEVs = Stats.PERMANENT.associateWith { pokemon.evs.get(it) ?: 0 }
        battleInfo.originalEVMap[pokemon.uuid] = currentEVs
        println("Saving original EVs for Pokémon: ${pokemon.species.name} (UUID: ${pokemon.uuid}). EVs: $currentEVs")
        logDebug("Saved EVs for ${pokemon.species.name}: ${currentEVs.entries.joinToString { "${it.key}: ${it.value}" }}")
    }

    private fun revertEVsAfterChange(battleId: UUID, pokemon: Pokemon) {
        println("Reverting EVs for Pokémon: ${pokemon.species.name}")
        ongoingBattles[battleId]?.originalEVMap?.get(pokemon.uuid)?.forEach { (stat, ev) ->
            pokemon.evs.set(stat, ev)
        }
    }

    /**
     * Applies custom EVs to the player’s Pokémon based on the defeated opponent Pokémon’s details.
     */
    private fun applyCustomEVs(playerPokemon: Pokemon, opponentPokemon: Pokemon, spawnerPos: BlockPos?) {
        if (spawnerPos == null) {
            println("No spawner position available for EV application")
            return
        }
        val spawnerData = CobbleSpawnersConfig.spawners[spawnerPos] ?: return
        val (speciesName, formName, aspectsWithoutGender) = getPokemonVariantDetails(opponentPokemon)
        println("Applying custom EVs for defeating ${opponentPokemon.species.name} (form: $formName, aspects: $aspectsWithoutGender)")
        val matchingEntry = spawnerData.selectedPokemon.find {
            it.pokemonName.equals(speciesName, ignoreCase = true) &&
                    (it.formName?.equals(formName, ignoreCase = true) ?: (formName == "Normal")) &&
                    it.aspects.map { a -> a.lowercase() }.toSet() == aspectsWithoutGender.map { a -> a.lowercase() }.toSet()
        }
        if (matchingEntry != null && matchingEntry.evSettings.allowCustomEvsOnDefeat) {
            val customEvs = mapOf(
                Stats.HP to matchingEntry.evSettings.evHp,
                Stats.ATTACK to matchingEntry.evSettings.evAttack,
                Stats.DEFENCE to matchingEntry.evSettings.evDefense,
                Stats.SPECIAL_ATTACK to matchingEntry.evSettings.evSpecialAttack,
                Stats.SPECIAL_DEFENCE to matchingEntry.evSettings.evSpecialDefense,
                Stats.SPEED to matchingEntry.evSettings.evSpeed
            )
            customEvs.forEach { (stat, ev) -> playerPokemon.evs.add(stat, ev) }
            println("Applied custom EVs to ${playerPokemon.species.name}: $customEvs")
        } else {
            println("No matching PokémonSpawnEntry with custom EVs for ${opponentPokemon.species.name} at $spawnerPos")
        }
    }

    private fun cleanupBattle(battleId: UUID) {
        ongoingBattles.remove(battleId)
        println("Cleaned up battle tracking for Battle ID: $battleId")
        logDebug("Cleaned up battle tracking for Battle ID: $battleId")
    }

    private fun findBattleIdByPokemon(pokemon: Pokemon): UUID? {
        return ongoingBattles.values.find { battleInfo ->
            battleInfo.actors.any { actor ->
                actor.pokemonList.any { it.effectedPokemon.uuid == pokemon.uuid }
            }
        }?.battleId
    }

    private fun findBattleIdByPlayer(player: ServerPlayerEntity): UUID? {
        return ongoingBattles.values.find { battleInfo ->
            battleInfo.actors.any { actor ->
                actor is PlayerBattleActor && actor.getPlayerUUIDs().contains(player.uuid)
            }
        }?.battleId
    }

    private fun playerIsNotInBattle(player: ServerPlayerEntity): Boolean {
        return getActiveBattleAndActor(player) == null
    }

    private fun getActiveBattleAndActor(player: ServerPlayerEntity): Pair<PokemonBattle, BattleActor>? {
        val battle = BattleRegistry.getBattleByParticipatingPlayer(player)
        val actor = battle?.getActor(player)
        return if (battle != null && actor != null) Pair(battle, actor) else null
    }

    /**
     * Extracts species name, form name, and aspects (excluding gender) from a Pokémon.
     */
    private fun getPokemonVariantDetails(pokemon: Pokemon): Triple<String, String, Set<String>> {
        val speciesName = pokemon.species.name
        val formName = if (pokemon.form.name == "Standard") "Normal" else pokemon.form.name
        val pokemonAspects = pokemon.aspects.toSet()
        val genderAspects = setOf("male", "female")
        val aspectsWithoutGender = pokemonAspects.filter { it !in genderAspects }.toSet()
        return Triple(speciesName, formName, aspectsWithoutGender)
    }
}
