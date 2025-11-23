
package com.cobblespawners.utils

import com.everlastingutils.scheduling.SchedulerManager
import com.everlastingutils.utils.logDebug
import com.cobblespawners.utils.CobbleSpawnersConfig
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.pokemon.stats.SidemodEvSource
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
        val participatingPlayerMons: ConcurrentHashMap<UUID, Pokemon> = ConcurrentHashMap(),
        val lastActiveOpponentMon: ConcurrentHashMap<UUID, Pokemon> = ConcurrentHashMap(),
        var isOpponentFromSpawner: Boolean = false,
        var spawnerPos: BlockPos? = null,
        val originalEVMap: ConcurrentHashMap<UUID, Map<Stat, Int>> = ConcurrentHashMap(),
        var valuesApplied: Boolean = false,
        var currentActivePlayerPokemon: Pokemon? = null,
        var endCause: BattleEndCause = BattleEndCause.UNKNOWN,
        val startTime: Long = System.currentTimeMillis()
    )

    private val MAX_BATTLE_DURATION = 10 * 60 * 1000

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

        logDebug("Started battle cleanup scheduler with ID: $schedulerId", "cobblespawners")
    }


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
                logDebug("Battle $battleId exceeded maximum duration or ended. Preparing for cleanup.", "cobblespawners")
                applyValuesAfterBattle(battleId)
                battlesToCleanup.add(battleId)
            }
        }

        battlesToCleanup.forEach { cleanupBattle(it) }
    }

    private fun handleBattleStartPre(battleId: UUID) {
        logDebug("Battle pre-start for Battle ID: $battleId", "cobblespawners")
        ongoingBattles[battleId] = BattleInfo(
            battleId = battleId,
            actors = emptyList()
        )
    }

    private fun handleBattleStartPost(battleId: UUID, actors: List<BattleActor>) {
        logDebug("Battle fully started for Battle ID: $battleId with actors: ${actors.map { it.javaClass.simpleName }}", "cobblespawners")
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
                logDebug("Player swapped in Pokémon: ${pokemon.species.name}", "cobblespawners")
                val battleInfo = ongoingBattles[battleId] ?: return
                synchronized(battleInfo) {

                    battleInfo.currentActivePlayerPokemon?.let { oldPokemon ->
                        if (oldPokemon.uuid != pokemon.uuid) {
                            logDebug("Detected swap: Old Pokémon ${oldPokemon.species.name} (UUID: ${oldPokemon.uuid}) is being swapped out for ${pokemon.species.name} (UUID: ${pokemon.uuid})", "cobblespawners")
                            saveOriginalEVs(battleId, oldPokemon)
                        }
                    }
                }
                handlePlayerActivePokemon(battleId, pokemon)
            } else {
                logDebug("Opponent swapped in Pokémon: ${pokemon.species.name}", "cobblespawners")
                handleOpponentActivePokemon(battleId, pokemon)
            }
        } else {
            logDebug("Pokémon sent out outside of tracked battle: ${pokemon.species.name}", "cobblespawners")
        }
    }

    private fun handlePlayerActivePokemon(battleId: UUID, pokemon: Pokemon?) {
        if (pokemon == null) return logDebug("Player active Pokémon is null, skipping EV save.", "cobblespawners")
        val battleInfo = ongoingBattles[battleId] ?: return
        synchronized(battleInfo) {

            battleInfo.currentActivePlayerPokemon = pokemon
            battleInfo.participatingPlayerMons[pokemon.uuid] = pokemon
            saveOriginalEVs(battleId, pokemon)
            logDebug("Tracking Player's Pokémon: ${pokemon.species.name}, UUID: ${pokemon.uuid}", "cobblespawners")
        }
    }


    private fun handleOpponentActivePokemon(battleId: UUID, pokemon: Pokemon?) {
        if (pokemon == null) return logDebug("Opponent active Pokémon is null, skipping battle logic.", "cobblespawners")
        val battleInfo = ongoingBattles[battleId] ?: return
        synchronized(battleInfo) {
            if (!battleInfo.isOpponentFromSpawner) {
                pokemon.entity?.let { entity ->
                    SpawnerNBTManager.getPokemonInfo(entity)?.let { spawnerInfo ->
                        val (speciesName, formName, aspectsWithoutGender) = getPokemonVariantDetails(pokemon)
                        val spawnerData = CobbleSpawnersConfig.spawners[spawnerInfo.spawnerPos]
                        if (spawnerData != null) {
                            val matchingEntry = spawnerData.selectedPokemon.find {
                                it.pokemonName.equals(speciesName, ignoreCase = true) &&
                                        (it.formName?.equals(formName, ignoreCase = true) ?: (formName == "Normal")) &&
                                        it.aspects.map { a -> a.lowercase() }.toSet() == aspectsWithoutGender.map { a -> a.lowercase() }.toSet()
                            }
                            if (matchingEntry != null && matchingEntry.evSettings.allowCustomEvsOnDefeat) {
                                logDebug("Found matching PokémonSpawnEntry for ${pokemon.species.name} with custom EVs enabled", "cobblespawners")
                                battleInfo.lastActiveOpponentMon[pokemon.uuid] = pokemon
                                battleInfo.isOpponentFromSpawner = true
                                battleInfo.spawnerPos = spawnerInfo.spawnerPos

                                battleInfo.currentActivePlayerPokemon?.let { playerPokemon ->
                                    logDebug("Saving baseline EVs for current active player's Pokémon before opponent swap: ${playerPokemon.species.name}", "cobblespawners")
                                    saveOriginalEVs(battleId, playerPokemon)
                                }
                            } else {
                                logDebug("No matching PokémonSpawnEntry with custom EVs for ${pokemon.species.name} (form: $formName, aspects: $aspectsWithoutGender)", "cobblespawners")
                            }
                        } else {
                            logDebug("Spawner data not found for position ${spawnerInfo.spawnerPos}", "cobblespawners")
                        }
                    }
                } ?: logDebug("Opponent's Pokémon is not from a spawner. Skipping EV tracking.", "cobblespawners")
            }
        }
    }

    private fun handleBattleVictory(battleId: UUID) {
        if (!ongoingBattles.containsKey(battleId)) return
        logDebug("Battle victory for Battle ID: $battleId", "cobblespawners")
        val battleInfo = ongoingBattles[battleId] ?: return
        synchronized(battleInfo) {
            battleInfo.endCause = BattleEndCause.NORMAL_VICTORY
        }
        applyValuesAfterBattle(battleId)
        cleanupBattle(battleId)
    }

    private fun handleBattleFlee(battleId: UUID) {
        if (!ongoingBattles.containsKey(battleId)) return
        logDebug("Battle fled for Battle ID: $battleId", "cobblespawners")
        val battleInfo = ongoingBattles[battleId] ?: return
        synchronized(battleInfo) {
            battleInfo.endCause = BattleEndCause.FLED
        }
        cleanupBattle(battleId)
    }

    private fun handlePokemonCaptured(pokemon: Pokemon) {
        val battleId = findBattleIdByPokemon(pokemon)
        if (battleId != null) {
            logDebug("Pokémon captured during battle: ${pokemon.species.name}", "cobblespawners")
            val battleInfo = ongoingBattles[battleId] ?: return
            synchronized(battleInfo) {
                battleInfo.endCause = BattleEndCause.CAPTURED
            }
            applyValuesAfterBattle(battleId)
            cleanupBattle(battleId)
        } else {
            logDebug("Pokémon captured outside of battle: ${pokemon.species.name}", "cobblespawners")
        }
    }

    private fun handlePlayerLogout(player: ServerPlayerEntity) {
        val battleId = findBattleIdByPlayer(player)
        if (battleId != null) {
            logDebug("Player ${player.name.string} logged out during battle $battleId.", "cobblespawners")
            val battleInfo = ongoingBattles[battleId] ?: return
            synchronized(battleInfo) {
                battleInfo.endCause = BattleEndCause.UNKNOWN
            }
            cleanupBattle(battleId)
        } else {
            logDebug("Player ${player.name.string} logged out but was not in a tracked battle.", "cobblespawners")
        }
    }


    private fun applyValuesAfterBattle(battleId: UUID) {
        val battleInfo = ongoingBattles[battleId] ?: return
        synchronized(battleInfo) {
            if (!battleInfo.isOpponentFromSpawner) {
                logDebug("Battle ID: $battleId did not involve spawner Pokémon. Skipping EV modifications.", "cobblespawners")
                return
            }
            if (battleInfo.valuesApplied) {
                logDebug("Values already applied for Battle ID: $battleId", "cobblespawners")
                return
            }
            if (battleInfo.endCause != BattleEndCause.NORMAL_VICTORY) {
                logDebug("Battle ended with cause ${battleInfo.endCause}. Skipping EV application for Battle ID: $battleId", "cobblespawners")
                return
            }
            val opponentPokemon = battleInfo.lastActiveOpponentMon.values.firstOrNull() ?: return
            battleInfo.currentActivePlayerPokemon?.let { playerPokemon ->
                logDebug("Reverting and applying EVs for active player's Pokémon: ${playerPokemon.species.name}", "cobblespawners")
                revertEVsAfterChange(battleId, playerPokemon)
                applyCustomEVs(playerPokemon, opponentPokemon, battleInfo.spawnerPos)
            }
            battleInfo.valuesApplied = true
        }
    }


    private fun saveOriginalEVs(battleId: UUID, pokemon: Pokemon) {
        val battleInfo = ongoingBattles[battleId] ?: return
        if (!battleInfo.isOpponentFromSpawner) {
            logDebug("Skipping EV save for non-spawner battle Pokémon: ${pokemon.species.name}", "cobblespawners")
            return
        }
        val currentEVs = Stats.PERMANENT.associateWith { pokemon.evs.get(it) ?: 0 }
        battleInfo.originalEVMap[pokemon.uuid] = currentEVs
        logDebug("Saved EVs for ${pokemon.species.name}: ${currentEVs.entries.joinToString { "${it.key}: ${it.value}" }}", "cobblespawners")
    }

    private fun revertEVsAfterChange(battleId: UUID, pokemon: Pokemon) {
        logDebug("Reverting EVs for Pokémon: ${pokemon.species.name}", "cobblespawners")
        ongoingBattles[battleId]?.originalEVMap?.get(pokemon.uuid)?.forEach { (stat, ev) ->
            pokemon.evs.set(stat, ev)
        }
    }


    private fun applyCustomEVs(playerPokemon: Pokemon, opponentPokemon: Pokemon, spawnerPos: BlockPos?) {
        if (spawnerPos == null) {
            logDebug("No spawner position available for EV application", "cobblespawners")
            return
        }
        val spawnerData = CobbleSpawnersConfig.spawners[spawnerPos] ?: return
        val (speciesName, formName, aspectsWithoutGender) = getPokemonVariantDetails(opponentPokemon)
        logDebug("Applying custom EVs for defeating ${opponentPokemon.species.name} (form: $formName, aspects: $aspectsWithoutGender)", "cobblespawners")
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

            // Create the EV source for your sidemod
            val evSource = SidemodEvSource("cobblespawners", playerPokemon)

            // Apply EVs using the new API
            customEvs.forEach { (stat, ev) ->
                if (ev > 0) {
                    playerPokemon.evs.add(stat, ev, evSource)
                }
            }

            logDebug("Applied custom EVs to ${playerPokemon.species.name}: $customEvs", "cobblespawners")
        } else {
            logDebug("No matching PokémonSpawnEntry with custom EVs for ${opponentPokemon.species.name} at $spawnerPos", "cobblespawners")
        }
    }

    private fun cleanupBattle(battleId: UUID) {
        ongoingBattles.remove(battleId)
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
