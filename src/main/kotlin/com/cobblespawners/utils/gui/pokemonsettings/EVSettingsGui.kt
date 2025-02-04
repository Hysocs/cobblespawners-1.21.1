package com.cobblespawners.utils.gui.pokemonsettings

import com.cobblespawners.utils.*
import com.blanketutils.utils.logDebug
import com.cobblespawners.utils.gui.SpawnerPokemonSelectionGui
import com.cobblespawners.utils.gui.SpawnerPokemonSelectionGui.spawnerGuisOpen
import com.blanketutils.gui.CustomGui
import com.blanketutils.gui.InteractionContext
import com.blanketutils.gui.setCustomName
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.ClickType
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos
import org.slf4j.LoggerFactory

object EVSettingsGui {
    private val logger = LoggerFactory.getLogger(EVSettingsGui::class.java)

    /**
     * Opens the EV editor GUI for a specific Pokémon and form.
     */
    fun openEVEditorGui(player: ServerPlayerEntity, spawnerPos: BlockPos, pokemonName: String, formName: String?) {
        val selectedEntry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName)
        if (selectedEntry == null) {
            player.sendMessage(
                Text.literal("Pokémon '$pokemonName' with form '${formName ?: "Standard"}' not found in spawner."),
                false
            )
            return
        }

        val layout = generateEVEditorLayout(selectedEntry)

        SpawnerPokemonSelectionGui.spawnerGuisOpen[spawnerPos] = player

        val onInteract: (InteractionContext) -> Unit = { context ->
            val clickedItem = context.clickedStack
            val clickedItemName = clickedItem.name?.string ?: ""

            when (clickedItemName) {
                "HP EV", "Attack EV", "Defense EV",
                "Special Attack EV", "Special Defense EV", "Speed EV" -> {
                    when (context.clickType) {
                        ClickType.LEFT -> {
                            updateEVValue(
                                spawnerPos,
                                selectedEntry.pokemonName,
                                selectedEntry.formName,
                                clickedItemName,
                                -1,
                                player,
                                context.slotIndex
                            )
                        }
                        ClickType.RIGHT -> {
                            updateEVValue(
                                spawnerPos,
                                selectedEntry.pokemonName,
                                selectedEntry.formName,
                                clickedItemName,
                                1,
                                player,
                                context.slotIndex
                            )
                        }
                        else -> {}
                    }
                }
                "Allow Custom EVs: ON", "Allow Custom EVs: OFF" -> {
                    toggleAllowCustomEvsWithoutClosing(
                        spawnerPos,
                        selectedEntry.pokemonName,
                        selectedEntry.formName,
                        player,
                        context.slotIndex
                    )
                }
                "Back" -> {
                    CustomGui.closeGui(player)
                    player.sendMessage(Text.literal("Returning to Edit Pokémon menu"), false)
                    SpawnerPokemonSelectionGui.openPokemonEditSubGui(
                        player,
                        spawnerPos,
                        selectedEntry.pokemonName,
                        selectedEntry.formName
                    )
                }
                else -> {}
            }
        }

        val onClose: (Inventory) -> Unit = {
            spawnerGuisOpen.remove(spawnerPos)
            player.sendMessage(
                Text.literal("EV Editor closed for ${selectedEntry.pokemonName} (${selectedEntry.formName ?: "Standard"})"),
                false
            )
        }

        CustomGui.openGui(
            player,
            "Edit EVs for ${selectedEntry.pokemonName} (${selectedEntry.formName ?: "Standard"})",
            layout,
            onInteract,
            onClose
        )
    }

    /**
     * Generates the layout for the EV editor GUI.
     */
    private fun generateEVEditorLayout(selectedEntry: PokemonSpawnEntry): List<ItemStack> {
        val layout = MutableList(54) { ItemStack.EMPTY }

        val evSettings = selectedEntry.evSettings

        // Add Player Head items for each EV stat
        layout[0] = createEVStatHeadButton("HP EV", evSettings.evHp)
        layout[1] = createEVStatHeadButton("Attack EV", evSettings.evAttack)
        layout[2] = createEVStatHeadButton("Defense EV", evSettings.evDefense)
        layout[3] = createEVStatHeadButton("Special Attack EV", evSettings.evSpecialAttack)
        layout[4] = createEVStatHeadButton("Special Defense EV", evSettings.evSpecialDefense)
        layout[5] = createEVStatHeadButton("Speed EV", evSettings.evSpeed)

        // Fill the rest with gray stained glass panes except for the toggle button and back button
        for (i in 6 until 54) {
            if (i != 31 && i != 49) {
                layout[i] = ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
                    CustomGui.setItemLore(this, listOf(" "))
                    setCustomName(Text.literal(" "))
                }
            }
        }

        // Add toggle button for custom EVs as a Player Head
        layout[31] = createToggleCustomEvsHeadButton(evSettings.allowCustomEvsOnDefeat)

        // Add the back button as a Player Head
        layout[49] = createBackHeadButton()

        return layout
    }

    /**
     * Creates an EV editing Player Head for a specific stat.
     */
    private fun createEVStatHeadButton(statName: String, value: Int): ItemStack {
        val textureValue = when (statName.lowercase()) {
            "hp ev" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWRiMDJiMDQwYzM3MDE1ODkyYTNhNDNkM2IxYmZkYjJlMDFhMDJlZGNjMmY1YjgyMjUwZGNlYmYzZmY0ZjAxZSJ9fX0=" // Replace with actual texture
            "attack ev" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTFkMzgzNDAxZjc3YmVmZmNiOTk4YzJjZjc5YjdhZmVlMjNmMThjNDFkOGE1NmFmZmVkNzliYjU2ZTIyNjdhMyJ9fX0=" // Replace with actual texture
            "defense ev" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjU1NTFmMzRjNDVmYjE4MTFlNGNjMmZhOGVjMzcxZTQ1YmEwOTc3ZTFkMTUyMTEyMGYwZjU3NTYwZjczZjU5MCJ9fX0=" // Replace with actual texture
            "special attack ev" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzhmZTcwYjc3MzFhYzJmNWIzZDAyNmViMWFiNmE5MjNhOGM1OGI0YmY2ZDNhY2JlMTQ1YjEwYzM2ZTZjZjg5OCJ9fX0=" // Replace with actual texture
            "special defense ev" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2VhMmI1MTE4MWFlMTlkMzMzMTNjNmY0YThlOTA2NjU3MDU1NzM2MzliM2RmNzA5NTE0YmQ5NzA5ODUzMzBkZCJ9fX0=" // Replace with actual texture
            "speed ev" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDcxMDEzODQxNjUyODg4OTgxNTU0OGI0NjIzZDI4ZDg2YmJiYWU1NjE5ZDY5Y2Q5ZGJjNWFkNmI0Mzc0NCJ9fX0=" // Replace with actual texture
            else -> "default_base64_texture"
        }

        return CustomGui.createPlayerHeadButton(
            statName.replace(" ", "") + "Head",
            Text.literal(statName).styled { it.withColor(Formatting.WHITE).withBold(true) },
            listOf(
                Text.literal("§aCurrent Value:").styled { it.withColor(Formatting.GREEN) },
                Text.literal("§7Value: §f$value"),
                Text.literal("§eLeft-click to decrease"),
                Text.literal("§eRight-click to increase")
            ),
            textureValue
        )
    }

    /**
     * Creates a toggle button Player Head for custom EVs.
     */
    private fun createToggleCustomEvsHeadButton(isEnabled: Boolean): ItemStack {
        val textureValue = if (isEnabled) {
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTI1YjhlZWQ1YzU2NWJkNDQwZWM0N2M3OWMyMGQ1Y2YzNzAxNjJiMWQ5YjVkZDMxMDBlZDYyODNmZTAxZDZlIn19fQ==" // Replace with actual base64 texture for ON
        } else {
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjNmNzliMjA3ZDYxZTEyMjUyM2I4M2Q2MTUwOGQ5OWNmYTA3OWQ0NWJmMjNkZjJhOWE1MTI3ZjkwNzFkNGIwMCJ9fX0=" // Replace with actual base64 texture for OFF
        }

        return CustomGui.createPlayerHeadButton(
            "ToggleCustomEVs",
            Text.literal("Allow Custom EVs: ${if (isEnabled) "ON" else "OFF"}").styled {
                it.withColor(if (isEnabled) Formatting.GREEN else Formatting.RED)
                    .withBold(true)
            },
            listOf(
                Text.literal("§eClick to toggle"),
            ),
            textureValue
        )
    }

    /**
     * Creates a back button Player Head.
     */
    private fun createBackHeadButton(): ItemStack {
        val textureValue = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0=" // Replace with actual base64 texture for Back
        return CustomGui.createPlayerHeadButton(
            "BackButton",
            Text.literal("Back").styled { it.withColor(Formatting.WHITE).withBold(false) },
            listOf(Text.literal("§7Return to the previous menu")),
            textureValue
        )
    }

    /**
     * Updates the EV value for the given Pokémon entry.
     */
    private fun updateEVValue(
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        statName: String,
        delta: Int,
        player: ServerPlayerEntity,
        slotIndex: Int
    ) {
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName) { selectedEntry ->
            val evSettings = selectedEntry.evSettings
            when (statName.lowercase()) {
                "hp ev" -> evSettings.evHp = (evSettings.evHp + delta).coerceIn(0, 252)
                "attack ev" -> evSettings.evAttack = (evSettings.evAttack + delta).coerceIn(0, 252)
                "defense ev" -> evSettings.evDefense = (evSettings.evDefense + delta).coerceIn(0, 252)
                "special attack ev" -> evSettings.evSpecialAttack = (evSettings.evSpecialAttack + delta).coerceIn(0, 252)
                "special defense ev" -> evSettings.evSpecialDefense = (evSettings.evSpecialDefense + delta).coerceIn(0, 252)
                "speed ev" -> evSettings.evSpeed = (evSettings.evSpeed + delta).coerceIn(0, 252)
            }
        } ?: run {
            player.sendMessage(Text.literal("Failed to update EV value."), false)
            return
        }

        // After updating, refresh the GUI
        val updatedEntry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName)
        if (updatedEntry != null) {
            refreshGui(player, updatedEntry)
            logDebug(
                "Updated EVs for ${updatedEntry.pokemonName} (${updatedEntry.formName ?: "Standard"}) at spawner $spawnerPos and saved to JSON."
            )
        }
    }

    /**
     * Refreshes the EV editor GUI items based on the current state.
     */
    private fun refreshGui(player: ServerPlayerEntity, selectedEntry: PokemonSpawnEntry) {
        val layout = generateEVEditorLayout(selectedEntry)

        val screenHandler = player.currentScreenHandler
        layout.forEachIndexed { index, itemStack ->
            if (index < screenHandler.slots.size) {
                screenHandler.slots[index].stack = itemStack
            }
        }

        screenHandler.sendContentUpdates()
    }

    /**
     * Toggles the allowCustomEvsOnDefeat flag without closing the GUI and updates the Player Head lore.
     */
    private fun toggleAllowCustomEvsWithoutClosing(
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        player: ServerPlayerEntity,
        toggleSlot: Int
    ) {
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName) { selectedEntry ->
            val evSettings = selectedEntry.evSettings
            evSettings.allowCustomEvsOnDefeat = !evSettings.allowCustomEvsOnDefeat
        } ?: run {
            player.sendMessage(Text.literal("Failed to toggle allowCustomEvsOnDefeat."), false)
            return
        }

        // Update the toggle button Player Head to reflect the new value (ON/OFF)
        val selectedEntry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName)
        if (selectedEntry != null) {
            val toggleHead = createToggleCustomEvsHeadButton(selectedEntry.evSettings.allowCustomEvsOnDefeat)

            // Update the GUI with the new toggle head without closing
            val screenHandler = player.currentScreenHandler
            if (toggleSlot < screenHandler.slots.size) {
                screenHandler.slots[toggleSlot].stack = toggleHead
            }

            screenHandler.sendContentUpdates()

            logDebug(
                "Toggled allowCustomEvsOnDefeat for ${selectedEntry.pokemonName} (${selectedEntry.formName ?: "Standard"}) at spawner $spawnerPos."
            )
        }
    }
}
