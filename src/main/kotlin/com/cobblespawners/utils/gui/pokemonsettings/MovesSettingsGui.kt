package com.cobblespawners.utils.gui.pokemonsettings

import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblespawners.utils.CobbleSpawnersConfig
import com.cobblespawners.utils.LeveledMove
import com.cobblespawners.utils.MovesSettings
import com.cobblespawners.utils.PokemonSpawnEntry
import com.cobblespawners.utils.gui.PokemonEditSubGui
import com.cobblespawners.utils.gui.SpawnerPokemonSelectionGui.spawnerGuisOpen
import com.everlastingutils.gui.AnvilGuiManager
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.FullyModularAnvilScreenHandler
import com.everlastingutils.gui.InteractionContext
import com.everlastingutils.gui.setCustomName
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

object MovesSettingsGui {

    private val playerPages = ConcurrentHashMap<ServerPlayerEntity, Int>()
    private val defaultMovesCache = ConcurrentHashMap<String, List<LeveledMove>>()

    private object Slots {
        const val HELP = 4
        const val PREV_PAGE = 45
        const val TOGGLE_CUSTOM_MOVES = 48
        const val BACK = 49
        const val ADD_CUSTOM_MOVE = 50
        const val NEXT_PAGE = 53
        val MOVE_SLOTS = (9..44).toList()
        const val MOVES_PER_PAGE = 36
    }

    private object Textures {
        const val BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
    }

    private object TextContent {
        fun formatMoveName(moveId: String): String {
            return moveId.replace("_", " ").replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
        }
    }

    fun openMovesSettingsGui(player: ServerPlayerEntity, spawnerPos: BlockPos, pokemonName: String, formName: String?, additionalAspects: Set<String>) {
        val entry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", additionalAspects)
        if (entry == null) {
            player.sendMessage(Text.literal("Error: Could not find the specified Pokémon in this spawner."), false)
            return
        }

        cacheDefaultMoves(pokemonName)
        val page = playerPages.getOrDefault(player, 0)
        spawnerGuisOpen[spawnerPos] = player

        val aspectsDisplay = if (additionalAspects.isNotEmpty()) ", ${additionalAspects.joinToString(", ")}" else ""
        val guiTitle = "Edit Moves: ${entry.pokemonName} (${entry.formName ?: "Standard"}$aspectsDisplay)"

        CustomGui.openGui(
            player,
            guiTitle,
            generateLayout(player, entry, page),
            { context -> handleInteraction(context, player, spawnerPos, pokemonName, formName, additionalAspects) },
            {
                spawnerGuisOpen.remove(spawnerPos)
                playerPages.remove(player)
            }
        )
    }

    private fun handleInteraction(context: InteractionContext, player: ServerPlayerEntity, spawnerPos: BlockPos, pokemonName: String, formName: String?, additionalAspects: Set<String>) {
        var needsRefresh = true
        when (context.slotIndex) {
            Slots.HELP -> needsRefresh = false
            Slots.BACK -> {
                CustomGui.closeGui(player)
                PokemonEditSubGui.openPokemonEditSubGui(player, spawnerPos, pokemonName, formName, additionalAspects)
                needsRefresh = false
            }
            Slots.ADD_CUSTOM_MOVE -> {
                openAddCustomMoveAnvil(player, spawnerPos, pokemonName, formName, additionalAspects)
                needsRefresh = false
            }
            Slots.TOGGLE_CUSTOM_MOVES -> toggleCustomMoves(spawnerPos, pokemonName, formName, additionalAspects)
            Slots.PREV_PAGE -> changePage(player, -1)
            Slots.NEXT_PAGE -> changePage(player, 1)
            in Slots.MOVE_SLOTS -> handleMoveClick(context, player, spawnerPos, pokemonName, formName, additionalAspects)
            else -> needsRefresh = false
        }
        if (needsRefresh) {
            refreshGui(player, spawnerPos, pokemonName, formName, additionalAspects)
        }
    }

    private fun generateLayout(player: ServerPlayerEntity, entry: PokemonSpawnEntry, page: Int): List<ItemStack> {
        val layout = MutableList(54) { createFillerPane() }
        val movesSettings = entry.moves ?: MovesSettings()

        val combinedMoves = getCombinedMovesForDisplay(entry.pokemonName, movesSettings.selectedMoves)
        val totalPages = ceil(combinedMoves.size.toDouble() / Slots.MOVES_PER_PAGE).toInt().coerceAtLeast(1)
        val currentPage = page.coerceIn(0, totalPages - 1)
        playerPages[player] = currentPage

        layout[Slots.HELP] = createHelpButton()
        layout[Slots.TOGGLE_CUSTOM_MOVES] = createToggleButton(movesSettings.allowCustomInitialMoves)
        layout[Slots.BACK] = createBackButton()
        layout[Slots.ADD_CUSTOM_MOVE] = createAddMoveButton()
        if (currentPage > 0) layout[Slots.PREV_PAGE] = createNavButton(false)
        if (currentPage < totalPages - 1) layout[Slots.NEXT_PAGE] = createNavButton(true)

        val startIndex = currentPage * Slots.MOVES_PER_PAGE
        val pageMoves = combinedMoves.drop(startIndex).take(Slots.MOVES_PER_PAGE)

        pageMoves.forEachIndexed { index, move ->
            val slot = Slots.MOVE_SLOTS.getOrNull(index) ?: return@forEachIndexed
            val isSelected = movesSettings.selectedMoves.any { it.level == move.level && it.moveId.equals(move.moveId, true) }
            layout[slot] = createMoveButton(move, isSelected, entry.pokemonName)
        }

        return layout
    }

    private fun getCombinedMovesForDisplay(pokemonName: String, selectedMoves: List<LeveledMove>): List<LeveledMove> {
        val defaultMoves = getDefaultMoves(pokemonName)
        val available = defaultMoves.filterNot { default -> selectedMoves.any { it.moveId.equals(default.moveId, true) } }
        return (selectedMoves + available).sortedWith(compareBy({ it.level }, { it.moveId }))
    }

    private fun handleMoveClick(context: InteractionContext, player: ServerPlayerEntity, spawnerPos: BlockPos, pokemonName: String, formName: String?, additionalAspects: Set<String>) {
        val entry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", additionalAspects) ?: return
        val page = playerPages[player] ?: 0
        val combinedMoves = getCombinedMovesForDisplay(pokemonName, entry.moves?.selectedMoves ?: emptyList())
        val clickedIndex = (page * Slots.MOVES_PER_PAGE) + Slots.MOVE_SLOTS.indexOf(context.slotIndex)
        val move = combinedMoves.getOrNull(clickedIndex) ?: return

        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", additionalAspects) { currentEntry ->
            val settings = currentEntry.moves ?: MovesSettings()
            val selected = settings.selectedMoves.toMutableList()
            val existing = selected.find { it.level == move.level && it.moveId.equals(move.moveId, true) }

            if (existing != null) {
                if (context.button == 1) { // Right-click to toggle forced
                    val newForcedState = !existing.forced
                    selected[selected.indexOf(existing)] = existing.copy(forced = newForcedState)
                } else { // Left-click to remove
                    selected.remove(existing)
                }
            } else { // Not selected, so add it
                selected.add(move)
            }
            currentEntry.moves = settings.copy(selectedMoves = selected)
        }
        CobbleSpawnersConfig.saveSpawnerData()
    }

    private fun toggleCustomMoves(spawnerPos: BlockPos, pokemonName: String, formName: String?, additionalAspects: Set<String>) {
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", additionalAspects) { entry ->
            val settings = entry.moves ?: MovesSettings()
            entry.moves = settings.copy(allowCustomInitialMoves = !settings.allowCustomInitialMoves)
        }
        CobbleSpawnersConfig.saveSpawnerData()
    }

    private fun changePage(player: ServerPlayerEntity, delta: Int) {
        val currentPage = playerPages.getOrDefault(player, 0)
        playerPages[player] = (currentPage + delta).coerceAtLeast(0)
    }

    private fun openAddCustomMoveAnvil(player: ServerPlayerEntity, spawnerPos: BlockPos, pokemonName: String, formName: String?, additionalAspects: Set<String>) {
        val closeAndReopenGui = { player.server.execute { openMovesSettingsGui(player, spawnerPos, pokemonName, formName, additionalAspects) } }

        AnvilGuiManager.openAnvilGui(
            player = player,
            id = "add_custom_move_${spawnerPos.toShortString()}",
            title = "Enter Move Name",
            initialText = "",
            leftItem = createButton(ItemStack(Items.BARRIER), Text.literal("Cancel").formatted(Formatting.RED)),
            resultItem = createButton(ItemStack(Items.PAPER), Text.literal("Type a move name...").formatted(Formatting.GRAY)),
            onLeftClick = { closeAndReopenGui() },
            onResultClick = { context ->
                val moveName = context.handler.currentText.trim().lowercase().replace(Regex("\\s+"), "_")
                if (moveName.isNotBlank() && Moves.getByName(moveName) != null) {
                    CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", additionalAspects) { entry ->
                        val settings = entry.moves ?: MovesSettings()
                        val selected = settings.selectedMoves.toMutableList()
                        if (selected.none { it.moveId.equals(moveName, true) }) {
                            selected.add(LeveledMove(1, moveName, false))
                            entry.moves = settings.copy(selectedMoves = selected)
                        }
                    }
                    CobbleSpawnersConfig.saveSpawnerData()
                    closeAndReopenGui()
                } else {
                    player.sendMessage(Text.literal("Invalid move name.").formatted(Formatting.RED), false)
                }
            },
            onTextChange = onTextChange@{ text ->
                val handler = player.currentScreenHandler as? FullyModularAnvilScreenHandler ?: return@onTextChange
                val button = if (text.isNotBlank()) {
                    createButton(ItemStack(Items.PAPER), Text.literal("Add: $text").formatted(Formatting.GREEN))
                } else {
                    createButton(ItemStack(Items.PAPER), Text.literal("Type a move name...").formatted(Formatting.GRAY))
                }
                handler.updateSlot(2, button)
            },
            onClose = { closeAndReopenGui() }
        )
    }

    private fun createMoveButton(move: LeveledMove, isSelected: Boolean, pokemonName: String): ItemStack {
        val name = TextContent.formatMoveName(move.moveId)
        val isDefault = getDefaultMoves(pokemonName).any { it.moveId.equals(move.moveId, true) }

        return if (isSelected) {
            val item = if (isDefault) ItemStack(Items.PAPER) else ItemStack(Items.FILLED_MAP)
            val prefix = if (!isDefault) "§d[Custom] " else "§f"
            val suffix = if (move.forced) " §6(Forced)" else ""
            createButton(item, Text.literal("$prefix$name$suffix"), listOf(
                Text.literal("§7Level: §f${move.level}"),
                Text.literal(""),
                Text.literal("§eLeft-click to remove"),
                Text.literal("§eRight-click to toggle Forced")
            ))
        } else {
            createButton(ItemStack(Items.BLUE_STAINED_GLASS_PANE), Text.literal(name).formatted(Formatting.AQUA), listOf(
                Text.literal("§7Level: §f${move.level}"),
                Text.literal(""),
                Text.literal("§eClick to add")
            ))
        }
    }

    private fun createHelpButton() = createButton(ItemStack(Items.BOOK), Text.literal("Help").formatted(Formatting.GOLD), listOf(
        Text.literal("§7Moves are chosen from highest to lowest level."),
        Text.literal("§7Up to 4 moves are selected for a spawned Pokémon."),
        Text.literal("§dForced§7 moves always take priority."),
        Text.literal("§7Right-click a selected move to toggle §dForced§7 status.")
    ))

    private fun createToggleButton(enabled: Boolean) = createButton(
        ItemStack(if (enabled) Items.LIME_CONCRETE else Items.RED_CONCRETE),
        Text.literal("Custom Moves: ${if (enabled) "ON" else "OFF"}").formatted(if (enabled) Formatting.GREEN else Formatting.RED),
        listOf(Text.literal("§7Click to toggle custom move selection."))
    )

    private fun createAddMoveButton() = createButton(
        ItemStack(Items.WRITABLE_BOOK),
        Text.literal("Add Custom Move").formatted(Formatting.YELLOW),
        listOf(Text.literal("§7Manually add a move by name."))
    )

    private fun createBackButton() = CustomGui.createPlayerHeadButton(
        "BackButton",
        Text.literal("Back").formatted(Formatting.RED),
        listOf(Text.literal("§7Return to the previous menu.")),
        Textures.BACK
    )

    private fun createNavButton(isNext: Boolean) = createButton(
        ItemStack(Items.ARROW),
        Text.literal(if (isNext) "Next Page" else "Previous Page").formatted(Formatting.WHITE)
    )

    private fun createFillerPane() = ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply { setCustomName(Text.literal(" ")) }

    private fun createButton(item: ItemStack, title: Text, lore: List<Text> = emptyList()): ItemStack {
        return item.apply {
            setCustomName(title)
            if (lore.isNotEmpty()) CustomGui.setItemLore(this, lore)
        }
    }

    private fun cacheDefaultMoves(pokemonName: String) {
        if (!defaultMovesCache.containsKey(pokemonName)) {
            val species = PokemonSpecies.getByName(pokemonName.lowercase())
            defaultMovesCache[pokemonName] = species?.let { CobbleSpawnersConfig.getDefaultInitialMoves(it) } ?: emptyList()
        }
    }

    private fun getDefaultMoves(pokemonName: String): List<LeveledMove> = defaultMovesCache[pokemonName] ?: emptyList()

    private fun refreshGui(player: ServerPlayerEntity, spawnerPos: BlockPos, pokemonName: String, formName: String?, additionalAspects: Set<String>) {
        val entry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", additionalAspects) ?: return
        val page = playerPages.getOrDefault(player, 0)
        val layout = generateLayout(player, entry, page)
        CustomGui.refreshGui(player, layout)
    }
}