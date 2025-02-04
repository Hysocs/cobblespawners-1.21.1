// File: BattleTracker.kt
package com.cobblespawners.utils

import com.blanketutils.utils.logDebug
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

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import java.util.*
import java.util.concurrent.ConcurrentHashMap

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
        val lastActivePlayerMon: ConcurrentHashMap<UUID, Pokemon> = ConcurrentHashMap(),
        val lastActiveOpponentMon: ConcurrentHashMap<UUID, Pokemon> = ConcurrentHashMap(),
        var isOpponentFromSpawner: Boolean = false,
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
        ServerTickEvents.START_SERVER_TICK.register { server ->
            onServerTick(server)
        }
    }

    private fun handleBattleStartPre(battleId: UUID) {
        logDebug("Battle pre-start for Battle ID: $battleId")
        ongoingBattles[battleId] = BattleInfo(
            battleId = battleId,
            actors = emptyList()
        )
    }

    private fun handleBattleStartPost(battleId: UUID, actors: List<BattleActor>) {
        logDebug("Battle fully started for Battle ID: $battleId")
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
                logDebug("Player swapped in Pokémon: ${pokemon.species.name}")
                handlePlayerActivePokemon(battleId, pokemon)
            } else {
                logDebug("Opponent swapped in Pokémon: ${pokemon.species.name}")
                handleOpponentActivePokemon(battleId, pokemon)
            }
        } else {
            logDebug("Pokémon sent out outside of tracked battle: ${pokemon.species.name}")
        }
    }

    private fun handlePlayerActivePokemon(battleId: UUID, pokemon: Pokemon?) {
        if (pokemon == null) return logDebug("Player active Pokémon is null, skipping EV save.")

        val battleInfo = ongoingBattles[battleId] ?: return
        synchronized(battleInfo) {
            // Store the current active pokemon regardless of spawner status
            battleInfo.currentActivePlayerPokemon = pokemon

            // Only store in lastActivePlayerMon if we actually need to track it
            if (battleInfo.isOpponentFromSpawner) {
                battleInfo.lastActivePlayerMon[pokemon.uuid] = pokemon
                logDebug("Tracking Player's Pokémon: ${pokemon.species.name}, UUID: ${pokemon.uuid}")
            }
        }
    }


    private fun handleOpponentActivePokemon(battleId: UUID, pokemon: Pokemon?) {
        if (pokemon == null) return logDebug("Opponent active Pokémon is null, skipping battle logic.")

        val battleInfo = ongoingBattles[battleId] ?: return

        synchronized(battleInfo) {
            if (!battleInfo.isOpponentFromSpawner) {
                pokemon.entity?.let { entity ->
                    SpawnerUUIDManager.getPokemonInfo(entity.uuid)?.let { spawnerInfo ->
                        // Check if this spawner Pokemon has custom EVs enabled
                        val hasCustomEvs = CobbleSpawnersConfig.spawners.values
                            .flatMap { it.selectedPokemon }
                            .firstOrNull { it.pokemonName.equals(pokemon.species.name, ignoreCase = true) }
                            ?.evSettings
                            ?.allowCustomEvsOnDefeat ?: false

                        if (hasCustomEvs) {
                            logDebug("Opponent's Pokémon: ${pokemon.species.name} (UUID: ${entity.uuid}) is from spawner at ${spawnerInfo.spawnerPos} and has custom EVs enabled")
                            battleInfo.lastActiveOpponentMon[pokemon.uuid] = pokemon
                            battleInfo.isOpponentFromSpawner = true
                            battleInfo.currentActivePlayerPokemon?.let { playerPokemon ->
                                if (!battleInfo.originalEVMap.containsKey(playerPokemon.uuid)) {
                                    saveOriginalEVs(battleId, playerPokemon)
                                }
                            }
                        } else {
                            logDebug("Opponent's Pokémon: ${pokemon.species.name} has custom EVs disabled. Skipping EV tracking.")
                        }
                    }
                } ?: logDebug("Opponent's Pokémon is not from a spawner. Skipping EV tracking.")
            }
        }
    }


    private fun handleBattleVictory(battleId: UUID) {
        if (!ongoingBattles.containsKey(battleId)) return
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
            logDebug("Pokémon captured during battle: ${pokemon.species.name}")
            val battleInfo = ongoingBattles[battleId] ?: return
            synchronized(battleInfo) {
                battleInfo.endCause = BattleEndCause.CAPTURED
            }
            applyValuesAfterBattle(battleId)
            cleanupBattle(battleId)
        } else {
            logDebug("Pokémon captured outside of battle: ${pokemon.species.name}")
        }
    }

    private fun handlePlayerLogout(player: ServerPlayerEntity) {
        val battleId = findBattleIdByPlayer(player)
        if (battleId != null) {
            logDebug("Player ${player.name.string} logged out during battle $battleId.")
            val battleInfo = ongoingBattles[battleId] ?: return
            synchronized(battleInfo) {
                battleInfo.endCause = BattleEndCause.UNKNOWN
            }
            cleanupBattle(battleId)
        } else {
            logDebug("Player ${player.name.string} logged out but was not in a tracked battle.")
        }
    }

    private fun applyValuesAfterBattle(battleId: UUID) {
        val battleInfo = ongoingBattles[battleId] ?: return

        synchronized(battleInfo) {
            if (!battleInfo.isOpponentFromSpawner) {
                logDebug("Battle ID: $battleId did not involve spawner Pokémon. Skipping EV modifications.")
                return
            }

            if (battleInfo.valuesApplied) {
                logDebug("Values already applied for Battle ID: $battleId")
                return
            }

            if (battleInfo.endCause != BattleEndCause.NORMAL_VICTORY) {
                logDebug("Battle ended with cause ${battleInfo.endCause}. Skipping EV application for Battle ID: $battleId")
                return
            }

            val playerPokemon = battleInfo.currentActivePlayerPokemon ?: return
            val opponentPokemon = battleInfo.lastActiveOpponentMon.values.firstOrNull() ?: return

            if (battleInfo.isOpponentFromSpawner) {
                logDebug("Reverting and applying EVs for Player's Pokémon: ${playerPokemon.species.name}")
                revertEVsAfterChange(battleId, playerPokemon)
                applyCustomEVs(playerPokemon, opponentPokemon.species.name)
            }

            battleInfo.valuesApplied = true
        }
    }

    private fun saveOriginalEVs(battleId: UUID, pokemon: Pokemon) {
        val battleInfo = ongoingBattles[battleId] ?: return
        if (!battleInfo.isOpponentFromSpawner) {
            logDebug("Skipping EV save for non-spawner battle Pokémon: ${pokemon.species.name}")
            return
        }
        val currentEVs = Stats.PERMANENT.associateWith { pokemon.evs.get(it) ?: 0 }
        battleInfo.originalEVMap[pokemon.uuid] = ConcurrentHashMap(currentEVs)
        logDebug("Saved EVs for ${pokemon.species.name}: ${currentEVs.entries.joinToString { "${it.key}: ${it.value}" }}")
    }


    private fun revertEVsAfterChange(battleId: UUID, pokemon: Pokemon) {
        ongoingBattles[battleId]?.originalEVMap?.get(pokemon.uuid)?.forEach { (stat, ev) ->
            pokemon.evs.set(stat, ev)
        }
    }

    private fun applyCustomEVs(pokemon: Pokemon, opponentSpeciesName: String) {
        CobbleSpawnersConfig.spawners.values.flatMap { it.selectedPokemon }
            .firstOrNull { it.pokemonName.equals(opponentSpeciesName, ignoreCase = true) }?.evSettings?.let { settings ->
                if (settings.allowCustomEvsOnDefeat) {
                    val customEvs = mapOf(
                        Stats.HP to settings.evHp, Stats.ATTACK to settings.evAttack,
                        Stats.DEFENCE to settings.evDefense, Stats.SPECIAL_ATTACK to settings.evSpecialAttack,
                        Stats.SPECIAL_DEFENCE to settings.evSpecialDefense, Stats.SPEED to settings.evSpeed
                    )
                    customEvs.forEach { (stat, ev) -> pokemon.evs.add(stat, ev) }
                    //(pokemon.getOwnerPlayer() as? ServerPlayerEntity)?.sendMessage(
                        //Text.literal("Custom EVs applied to ${pokemon.species.name} based on defeating $opponentSpeciesName: ${
                            //customEvs.entries.joinToString { "${it.key}: ${it.value}" }
                        //}"), false
                    //)
                }
            }
    }

    private fun cleanupBattle(battleId: UUID) {
        ongoingBattles.remove(battleId)
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

    private fun onServerTick(server: MinecraftServer) {
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
                applyValuesAfterBattle(battleId)
                battlesToCleanup.add(battleId)
                logDebug("Battle $battleId exceeded maximum duration or ended and was marked for cleanup.")
            }
        }

        battlesToCleanup.forEach { cleanupBattle(it) }
    }

    private fun playerIsNotInBattle(player: ServerPlayerEntity): Boolean {
        return getActiveBattleAndActor(player) == null
    }

    private fun getActiveBattleAndActor(player: ServerPlayerEntity): Pair<PokemonBattle, BattleActor>? {
        val battle = BattleRegistry.getBattleByParticipatingPlayer(player)
        val actor = battle?.getActor(player)
        return if (battle != null && actor != null) Pair(battle, actor) else null
    }
}
