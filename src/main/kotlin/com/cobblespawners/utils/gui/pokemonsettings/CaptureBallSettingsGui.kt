// File: CaptureBallSettingsGui.kt
package com.cobblespawners.utils.gui.pokemonsettings

import com.cobblespawners.utils.CobbleSpawnersConfig
import com.cobblespawners.utils.gui.SpawnerPokemonSelectionGui
import com.blanketutils.gui.CustomGui
import com.blanketutils.gui.InteractionContext
import com.blanketutils.gui.setCustomName
import com.cobblemon.mod.common.item.PokeBallItem
import net.minecraft.component.DataComponentTypes
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
    private val playerPages: ConcurrentHashMap<ServerPlayerEntity, Int> = ConcurrentHashMap() // Tracks the current page per player

    /**
     * Opens the Poké Ball selection GUI for editing selected Poké Balls.
     */
    fun openCaptureBallSettingsGui(
        player: ServerPlayerEntity,
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?
    ) {
        val currentPage = playerPages[player] ?: 0

        val selectedEntry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName)
        val requiredPokeBalls = selectedEntry?.captureSettings?.requiredPokeBalls ?: listOf()

        val availablePokeballs = getAvailablePokeballs()
        val layout = generateFullGuiLayout(availablePokeballs, requiredPokeBalls, currentPage)
        val title = "Select Required Poké Balls for $pokemonName" + if (formName != null) " ($formName)" else ""

        // **Begin Change: Set the spawner GUI as open**
        SpawnerPokemonSelectionGui.spawnerGuisOpen[spawnerPos] = player
        // **End Change**

        CustomGui.openGui(
            player,
            title,
            layout,
            { context -> handleButtonClick(context, player, spawnerPos, pokemonName, formName) },
            {
                playerPages.remove(player)
                // **Begin Change: Remove the spawner GUI from the open map when GUI is closed**
                SpawnerPokemonSelectionGui.spawnerGuisOpen.remove(spawnerPos)
                // **End Change**
            }
        )
    }

    /**
     * Generates the full layout for the GUI, including buttons and Poké Balls.
     */
    private fun generateFullGuiLayout(availablePokeballs: List<ItemStack>, selectedPokeBalls: List<String>, page: Int): List<ItemStack> {
        val layout = generatePokeballItemsForGui(availablePokeballs, selectedPokeBalls, page).toMutableList()

        val previousPageSlot = 45
        val backButtonSlot = 49
        val nextPageSlot = 53

        // Add Previous Page button
        if (page > 0) {
            layout[previousPageSlot] = createPreviousPageButton()
        } else {
            layout[previousPageSlot] = createFillerPane()
        }

        // Add Back button
        layout[backButtonSlot] = createBackButton()

        // Add Next Page button
        if ((page + 1) * ITEMS_PER_PAGE < availablePokeballs.size) {
            layout[nextPageSlot] = createNextPageButton()
        } else {
            layout[nextPageSlot] = createFillerPane()
        }

        return layout
    }

    /**
     * Generates the GUI items for Poké Balls.
     */
    private fun generatePokeballItemsForGui(availablePokeballs: List<ItemStack>, selectedPokeBalls: List<String>, page: Int): List<ItemStack> {
        val layout = MutableList(54) { createFillerPane() }
        val start = page * ITEMS_PER_PAGE
        val end = minOf(start + ITEMS_PER_PAGE, availablePokeballs.size)

        for (i in start until end) {
            val pokeball = availablePokeballs[i].copy() // Use a copy to prevent modifying the original list
            val ballName = pokeball.item.translationKey.split(".").last()
            val isSelected = ballName in selectedPokeBalls

            if (isSelected) {
                addEnchantmentGlint(pokeball)
            }
            updateItemLore(pokeball, isSelected)
            layout[i - start] = pokeball
        }

        return layout
    }

    /**
     * Handles button clicks in the GUI.
     */
    private fun handleButtonClick(
        context: InteractionContext,
        player: ServerPlayerEntity,
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?
    ) {
        val currentPage = playerPages[player] ?: 0
        val availablePokeballs = getAvailablePokeballs()
        val startIndex = currentPage * ITEMS_PER_PAGE
        val endIndex = minOf(startIndex + ITEMS_PER_PAGE, availablePokeballs.size)

        when (context.slotIndex) {
            45 -> { // Previous Page
                if (currentPage > 0) {
                    playerPages[player] = currentPage - 1
                    refreshGuiItems(player, spawnerPos, pokemonName, formName)
                }
            }
            49 -> { // Back Button
                CustomGui.closeGui(player)
                CaptureSettingsGui.openCaptureSettingsGui(player, spawnerPos, pokemonName, formName)
            }
            53 -> { // Next Page
                if (endIndex < availablePokeballs.size) {
                    playerPages[player] = currentPage + 1
                    refreshGuiItems(player, spawnerPos, pokemonName, formName)
                }
            }
            else -> { // Poké Ball Selection
                val clickedBall = context.clickedStack.item as? PokeBallItem ?: return
                val ballName = clickedBall.translationKey.split(".").last()

                val selectedEntry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName)
                var requiredPokeBalls = selectedEntry?.captureSettings?.requiredPokeBalls ?: listOf()

                // Toggle selection state and update the config
                if (ballName in requiredPokeBalls) {
                    removeEnchantmentGlint(context.clickedStack)
                    requiredPokeBalls = requiredPokeBalls.minus(ballName)
                } else {
                    addEnchantmentGlint(context.clickedStack)
                    requiredPokeBalls = requiredPokeBalls.plus(ballName)
                }

                // Update the config with the modified requiredPokeBalls list
                CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName) { entry ->
                    entry.captureSettings.requiredPokeBalls = requiredPokeBalls
                }

                // Refresh the item to reflect the new state
                updateItemLore(context.clickedStack, ballName in requiredPokeBalls)
                player.currentScreenHandler.setStackInSlot(context.slotIndex, 0, context.clickedStack) // Ensure the slot is updated
                player.currentScreenHandler.sendContentUpdates() // Send updates to the GUI
            }
        }
    }


    /**
     * Refreshes the GUI items without closing the GUI.
     */
    private fun refreshGuiItems(
        player: ServerPlayerEntity,
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?
    ) {
        val selectedEntry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName)
        val requiredPokeBalls = selectedEntry?.captureSettings?.requiredPokeBalls ?: listOf()

        val availablePokeballs = getAvailablePokeballs()
        val currentPage = playerPages[player] ?: 0

        val layout = generateFullGuiLayout(availablePokeballs, requiredPokeBalls, currentPage)

        CustomGui.refreshGui(player, layout)
    }

    // Helper methods for creating buttons, managing enchantments, and generating lore
    private fun createFillerPane(): ItemStack {
        return ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
            setCustomName(Text.literal(" "))
        }
    }

    private fun createBackButton(): ItemStack {
        return CustomGui.createPlayerHeadButton(
            "BackButton",
            Text.literal("Back").styled { it.withColor(Formatting.WHITE) },
            listOf(Text.literal("Return to previous menu").styled { it.withColor(Formatting.GRAY) }),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0=" // Placeholder for actual texture value
        )
    }

    private fun createNextPageButton(): ItemStack {
        return CustomGui.createPlayerHeadButton(
            "NextPageButton",
            Text.literal("Next Page").styled { it.withColor(Formatting.GREEN) },
            listOf(Text.literal("Click to go to the next page").styled { it.withColor(Formatting.GRAY) }),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGU0MDNjYzdiYmFjNzM2NzBiZDU0M2Y2YjA5NTViYWU3YjhlOTEyM2Q4M2JkNzYwZjYyMDRjNWFmZDhiZTdlMSJ9fX0=" // Texture for Next Page
        )
    }

    private fun createPreviousPageButton(): ItemStack {
        return CustomGui.createPlayerHeadButton(
            "PreviousPageButton",
            Text.literal("Previous Page").styled { it.withColor(Formatting.GREEN) },
            listOf(Text.literal("Click to go to the previous page").styled { it.withColor(Formatting.GRAY) }),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTMzYWQ1YzIyZGIxNjQzNWRhYWQ2MTU5MGFiYTUxZDkzNzkxNDJkZDU1NmQ2YzQyMmE3MTEwY2EzYWJlYTUwIn19fQ==" // Texture for Previous Page
        )
    }

    private fun getAvailablePokeballs(): List<ItemStack> {
        return Registries.ITEM.stream()
            .filter { it is PokeBallItem }
            .map { ItemStack(it) }
            .toList()
    }

    private fun addEnchantmentGlint(itemStack: ItemStack) {
        // Instead of applying a dummy enchantment, we now use the DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE
        itemStack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)
    }

    private fun removeEnchantmentGlint(itemStack: ItemStack) {
        // To remove the glint, set the ENCHANTMENT_GLINT_OVERRIDE to false
        itemStack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, false)
    }


    private fun updateItemLore(itemStack: ItemStack, isSelected: Boolean) {
        val ballName = itemStack.item.translationKey.split(".").last()
        val lore = listOf(
            Text.literal("Status: ${if (isSelected) "Selected" else "Not Selected"}").styled { it.withColor(if (isSelected) Formatting.LIGHT_PURPLE else Formatting.RED) }
        )
        CustomGui.setItemLore(itemStack, lore.map { it.string })
    }
}
