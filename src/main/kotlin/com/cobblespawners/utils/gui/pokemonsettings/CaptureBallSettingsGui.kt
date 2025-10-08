package com.cobblespawners.utils.gui.pokemonsettings

import com.cobblespawners.utils.CobbleSpawnersConfig
import com.cobblespawners.utils.gui.SpawnerPokemonSelectionGui
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.InteractionContext
import com.everlastingutils.gui.setCustomName
import com.cobblemon.mod.common.item.PokeBallItem
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.LoreComponent
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos
import java.util.concurrent.ConcurrentHashMap

object CaptureBallSettingsGui {
    private const val ITEMS_PER_PAGE = 45
    private val playerPages = ConcurrentHashMap<ServerPlayerEntity, Int>()
    private val availablePokeBalls: List<ItemStack> by lazy {
        Registries.ITEM.stream()
            .filter { it is PokeBallItem }
            .map { ItemStack(it) }
            .sorted(compareBy { it.name.string })
            .toList()
    }

    private object Slots {
        const val PREV_PAGE = 45
        const val BACK_BUTTON = 49
        const val NEXT_PAGE = 53
    }

    private object Textures {
        const val PREV_PAGE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTMzYWQ1YzIyZGIxNjQzNWRhYWQ2MTU5MGFiYTUxZDkzNzkxNDJkZDU1NmQ2YzQyMmE3MTEwY2EzYWJlYTUwIn19fQ=="
        const val NEXT_PAGE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGU0MDNjYzdiYmFjNzM2NzBiZDU0M2Y2YjA5NTViYWU3YjhlOTEyM2Q4M2JkNzYwZjYyMDRjNWFmZDhiZTdlMSJ9fX0="
        const val BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
    }

    fun openCaptureBallSettingsGui(player: ServerPlayerEntity, spawnerPos: BlockPos, pokemonName: String, formName: String?, additionalAspects: Set<String>) {
        val entry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", additionalAspects)
        if (entry == null) {
            player.sendMessage(Text.literal("Error: Could not find the specified Pokémon in this spawner."), false)
            return
        }
        SpawnerPokemonSelectionGui.spawnerGuisOpen[spawnerPos] = player

        val aspectsDisplay = if (additionalAspects.isNotEmpty()) ", ${additionalAspects.joinToString(", ")}" else ""
        val title = "Select Balls for $pokemonName (${entry.formName ?: "Standard"}$aspectsDisplay)"

        CustomGui.openGui(
            player,
            title,
            generateFullGuiLayout(entry.captureSettings.requiredPokeBalls, playerPages.getOrDefault(player, 0)),
            { context -> handleInteraction(context, player, spawnerPos, pokemonName, formName, additionalAspects) },
            {
                playerPages.remove(player)
                SpawnerPokemonSelectionGui.spawnerGuisOpen.remove(spawnerPos)
            }
        )
    }

    private fun handleInteraction(context: InteractionContext, player: ServerPlayerEntity, spawnerPos: BlockPos, pokemonName: String, formName: String?, additionalAspects: Set<String>) {
        val currentPage = playerPages.getOrDefault(player, 0)
        var needsRefresh = true

        when (context.slotIndex) {
            Slots.PREV_PAGE -> if (currentPage > 0) playerPages[player] = currentPage - 1
            Slots.NEXT_PAGE -> if ((currentPage + 1) * ITEMS_PER_PAGE < availablePokeBalls.size) playerPages[player] = currentPage + 1
            Slots.BACK_BUTTON -> {
                needsRefresh = false
                CustomGui.closeGui(player)
                CaptureSettingsGui.openCaptureSettingsGui(player, spawnerPos, pokemonName, formName, additionalAspects)
            }
            in 0 until ITEMS_PER_PAGE -> {
                val ballName = (context.clickedStack.item as? PokeBallItem)?.let { Registries.ITEM.getId(it).path }
                if (ballName != null) {
                    togglePokeballSelection(spawnerPos, pokemonName, formName, additionalAspects, ballName)
                }
            }
            else -> needsRefresh = false
        }

        if (needsRefresh) {
            refreshGui(player, spawnerPos, pokemonName, formName, additionalAspects)
        }
    }

    private fun togglePokeballSelection(spawnerPos: BlockPos, pokemonName: String, formName: String?, additionalAspects: Set<String>, ballName: String) {
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", additionalAspects) { entry ->
            val requiredBalls = entry.captureSettings.requiredPokeBalls.toMutableList()
            if (ballName in requiredBalls) {
                requiredBalls.remove(ballName)
            } else {
                requiredBalls.add(ballName)
            }
            entry.captureSettings.requiredPokeBalls = requiredBalls
        }
        CobbleSpawnersConfig.saveSpawnerData()
    }

    private fun generateFullGuiLayout(selectedPokeBalls: List<String>, page: Int): List<ItemStack> {
        val layout = generatePokeballItemsForGui(selectedPokeBalls, page).toMutableList()
        val totalPages = (availablePokeBalls.size + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE

        layout[Slots.PREV_PAGE] = if (page > 0) createButton(Text.literal("Previous Page").formatted(Formatting.GREEN), Textures.PREV_PAGE) else createFillerPane()
        layout[Slots.BACK_BUTTON] = createButton(Text.literal("Back").formatted(Formatting.WHITE), Textures.BACK)
        layout[Slots.NEXT_PAGE] = if (page < totalPages - 1) createButton(Text.literal("Next Page").formatted(Formatting.GREEN), Textures.NEXT_PAGE) else createFillerPane()

        return layout
    }

    private fun generatePokeballItemsForGui(selectedPokeBalls: List<String>, page: Int): List<ItemStack> {
        val layout = MutableList(54) { createFillerPane() }
        val startIndex = page * ITEMS_PER_PAGE
        val pageItems = availablePokeBalls.drop(startIndex).take(ITEMS_PER_PAGE)

        pageItems.forEachIndexed { index, itemStack ->
            val ballItem = itemStack.copy()
            val ballName = (ballItem.item as? PokeBallItem)?.let { Registries.ITEM.getId(it).path } ?: ""
            val isSelected = ballName in selectedPokeBalls

            val statusText = if (isSelected) Text.literal("Selected").formatted(Formatting.GREEN) else Text.literal("Not Selected").formatted(Formatting.RED)
            ballItem.set(DataComponentTypes.LORE, LoreComponent(listOf(Text.literal("Status: ").append(statusText))))

            if (isSelected) {
                ballItem.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)
            }

            layout[index] = ballItem
        }
        return layout
    }

    private fun createButton(title: Text, texture: String, lore: List<Text> = emptyList()): ItemStack {
        return CustomGui.createPlayerHeadButton(title.string.replace(" ", ""), title, lore, texture)
    }

    private fun createFillerPane(): ItemStack {
        return ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
            setCustomName(Text.literal(" "))
        }
    }

    private fun refreshGui(player: ServerPlayerEntity, spawnerPos: BlockPos, pokemonName: String, formName: String?, additionalAspects: Set<String>) {
        val entry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", additionalAspects) ?: return
        val screenHandler = player.currentScreenHandler ?: return
        val currentPage = playerPages.getOrDefault(player, 0)
        val layout = generateFullGuiLayout(entry.captureSettings.requiredPokeBalls, currentPage)

        layout.forEachIndexed { index, itemStack ->
            if (index < screenHandler.slots.size) {
                screenHandler.slots[index].stack = itemStack
            }
        }
        screenHandler.sendContentUpdates()
    }
}