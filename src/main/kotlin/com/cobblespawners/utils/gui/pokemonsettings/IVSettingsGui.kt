package com.cobblespawners.utils.gui.pokemonsettings

import com.cobblespawners.utils.*
import com.blanketutils.utils.logDebug
import com.cobblespawners.utils.gui.SpawnerPokemonSelectionGui
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

object IVSettingsGui {
    private val logger = LoggerFactory.getLogger(IVSettingsGui::class.java)

    /**
     * Opens the IV editor GUI for a specific Pokémon and form.
     */
    fun openIVEditorGui(player: ServerPlayerEntity, spawnerPos: BlockPos, pokemonName: String, formName: String?) {
        val selectedEntry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName)
        if (selectedEntry == null) {
            player.sendMessage(
                Text.literal("Pokémon '$pokemonName' with form '${formName ?: "Standard"}' not found in spawner."),
                false
            )
            return
        }

        val layout = generateIVEditorLayout(selectedEntry)

        SpawnerPokemonSelectionGui.spawnerGuisOpen[spawnerPos] = player

        val onInteract: (InteractionContext) -> Unit = { context ->
            val clickedItem = context.clickedStack
            val clickedItemName = clickedItem.name?.string ?: ""

            when (clickedItemName) {
                "HP Min", "HP Max",
                "Attack Min", "Attack Max",
                "Defense Min", "Defense Max",
                "Special Attack Min", "Special Attack Max",
                "Special Defense Min", "Special Defense Max",
                "Speed Min", "Speed Max" -> {
                    when (context.clickType) {
                        ClickType.LEFT -> {
                            updateIVValue(
                                spawnerPos,
                                selectedEntry.pokemonName,
                                selectedEntry.formName,
                                clickedItemName,
                                -1,
                                player
                            )
                        }
                        ClickType.RIGHT -> {
                            updateIVValue(
                                spawnerPos,
                                selectedEntry.pokemonName,
                                selectedEntry.formName,
                                clickedItemName,
                                1,
                                player
                            )
                        }
                        else -> {}
                    }
                }
                "Allow Custom IVs: ON", "Allow Custom IVs: OFF" -> {
                    toggleAllowCustomIvsWithoutClosing(
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
            SpawnerPokemonSelectionGui.spawnerGuisOpen.remove(spawnerPos)
            player.sendMessage(
                Text.literal("IV Editor closed for ${selectedEntry.pokemonName} (${selectedEntry.formName ?: "Standard"})"),
                false
            )
        }

        CustomGui.openGui(
            player,
            "Edit IVs for ${selectedEntry.pokemonName} (${selectedEntry.formName ?: "Standard"})",
            layout,
            onInteract,
            onClose
        )
    }

    /**
     * Generates the layout for the IV editor GUI.
     */
    private fun generateIVEditorLayout(selectedEntry: PokemonSpawnEntry): List<ItemStack> {
        val layout = MutableList(54) { ItemStack.EMPTY }

        val ivSettings = selectedEntry.ivSettings

        // Add Player Head items for each IV stat
        layout[0] = createIVStatHeadButton("HP Min", ivSettings.minIVHp)
        layout[1] = createIVStatHeadButton("HP Max", ivSettings.maxIVHp)

        layout[2] = createIVStatHeadButton("Attack Min", ivSettings.minIVAttack)
        layout[3] = createIVStatHeadButton("Attack Max", ivSettings.maxIVAttack)

        layout[4] = createIVStatHeadButton("Defense Min", ivSettings.minIVDefense)
        layout[5] = createIVStatHeadButton("Defense Max", ivSettings.maxIVDefense)

        layout[6] = createIVStatHeadButton("Special Attack Min", ivSettings.minIVSpecialAttack)
        layout[7] = createIVStatHeadButton("Special Attack Max", ivSettings.maxIVSpecialAttack)

        layout[8] = createIVStatHeadButton("Special Defense Min", ivSettings.minIVSpecialDefense)
        layout[9] = createIVStatHeadButton("Special Defense Max", ivSettings.maxIVSpecialDefense)

        layout[10] = createIVStatHeadButton("Speed Min", ivSettings.minIVSpeed)
        layout[11] = createIVStatHeadButton("Speed Max", ivSettings.maxIVSpeed)

        // Fill the rest with gray stained glass panes except for the toggle button and back button
        for (i in 12 until 54) {
            if (i != 31 && i != 49) {
                layout[i] = ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
                    CustomGui.setItemLore(this, listOf(" "))
                    setCustomName(Text.literal(" "))
                }
            }
        }

        // Add toggle button for custom IVs as a Player Head
        layout[31] = createToggleCustomIvsHeadButton(ivSettings.allowCustomIvs)

        // Add the back button as a Player Head
        layout[49] = createBackHeadButton()

        return layout
    }

    /**
     * Creates an IV editing Player Head for a specific stat.
     */
    private fun createIVStatHeadButton(statName: String, value: Int): ItemStack {
        val textureValue = when (statName.lowercase()) {
            "hp min" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWRiMDJiMDQwYzM3MDE1ODkyYTNhNDNkM2IxYmZkYjJlMDFhMDJlZGNjMmY1YjgyMjUwZGNlYmYzZmY0ZjAxZSJ9fX0="
            "hp max" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWRiMDJiMDQwYzM3MDE1ODkyYTNhNDNkM2IxYmZkYjJlMDFhMDJlZGNjMmY1YjgyMjUwZGNlYmYzZmY0ZjAxZSJ9fX0="
            "attack min" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTFkMzgzNDAxZjc3YmVmZmNiOTk4YzJjZjc5YjdhZmVlMjNmMThjNDFkOGE1NmFmZmVkNzliYjU2ZTIyNjdhMyJ9fX0="
            "attack max" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTFkMzgzNDAxZjc3YmVmZmNiOTk4YzJjZjc5YjdhZmVlMjNmMThjNDFkOGE1NmFmZmVkNzliYjU2ZTIyNjdhMyJ9fX0="
            "defense min" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjU1NTFmMzRjNDVmYjE4MTFlNGNjMmZhOGVjMzcxZTQ1YmEwOTc3ZTFkMTUyMTEyMGYwZjU3NTYwZjczZjU5MCJ9fX0="
            "defense max" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjU1NTFmMzRjNDVmYjE4MTFlNGNjMmZhOGVjMzcxZTQ1YmEwOTc3ZTFkMTUyMTEyMGYwZjU3NTYwZjczZjU5MCJ9fX0="
            "special attack min" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzhmZTcwYjc3MzFhYzJmNWIzZDAyNmViMWFiNmE5MjNhOGM1OGI0YmY2ZDNhY2JlMTQ1YjEwYzM2ZTZjZjg5OCJ9fX0="
            "special attack max" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzhmZTcwYjc3MzFhYzJmNWIzZDAyNmViMWFiNmE5MjNhOGM1OGI0YmY2ZDNhY2JlMTQ1YjEwYzM2ZTZjZjg5OCJ9fX0="
            "special defense min" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2VhMmI1MTE4MWFlMTlkMzMzMTNjNmY0YThlOTA2NjU3MDU1NzM2MzliM2RmNzA5NTE0YmQ5NzA5ODUzMzBkZCJ9fX0="
            "special defense max" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2VhMmI1MTE4MWFlMTlkMzMzMTNjNmY0YThlOTA2NjU3MDU1NzM2MzliM2RmNzA5NTE0YmQ5NzA5ODUzMzBkZCJ9fX0="
            "speed min" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDcxMDEzODQxNjUyODg4OTgxNTU0OGI0NjIzZDI4ZDg2YmJiYWU1NjE5ZDY5Y2Q5ZGJjNWFkNmI0Mzc0NCJ9fX0="
            "speed max" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDcxMDEzODQxNjUyODg4OTgxNTU0OGI0NjIzZDI4ZDg2YmJiYWU1NjE5ZDY5Y2Q5ZGJjNWFkNmI0Mzc0NCJ9fX0="
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
     * Creates a toggle button Player Head for custom IVs.
     */
    private fun createToggleCustomIvsHeadButton(isEnabled: Boolean): ItemStack {
        val textureValue = if (isEnabled) {
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTI1YjhlZWQ1YzU2NWJkNDQwZWM0N2M3OWMyMGQ1Y2YzNzAxNjJiMWQ5YjVkZDMxMDBlZDYyODNmZTAxZDZlIn19fQ==" // Replace with actual base64 texture for ON
        } else {
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjNmNzliMjA3ZDYxZTEyMjUyM2I4M2Q2MTUwOGQ5OWNmYTA3OWQ0NWJmMjNkZjJhOWE1MTI3ZjkwNzFkNGIwMCJ9fX0=" // Replace with actual base64 texture for OFF
        }

        return CustomGui.createPlayerHeadButton(
            "ToggleCustomIVs",
            Text.literal("Allow Custom IVs: ${if (isEnabled) "ON" else "OFF"}").styled {
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
     * Updates the IV value for the given Pokémon entry.
     */
    private fun updateIVValue(
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        statName: String,
        delta: Int,
        player: ServerPlayerEntity
    ) {
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName) { selectedEntry ->
            val ivSettings = selectedEntry.ivSettings
            when (statName.lowercase()) {
                "hp min" -> ivSettings.minIVHp = (ivSettings.minIVHp + delta).coerceIn(0, 31)
                "hp max" -> ivSettings.maxIVHp = (ivSettings.maxIVHp + delta).coerceIn(0, 31)
                "attack min" -> ivSettings.minIVAttack = (ivSettings.minIVAttack + delta).coerceIn(0, 31)
                "attack max" -> ivSettings.maxIVAttack = (ivSettings.maxIVAttack + delta).coerceIn(0, 31)
                "defense min" -> ivSettings.minIVDefense = (ivSettings.minIVDefense + delta).coerceIn(0, 31)
                "defense max" -> ivSettings.maxIVDefense = (ivSettings.maxIVDefense + delta).coerceIn(0, 31)
                "special attack min" -> ivSettings.minIVSpecialAttack = (ivSettings.minIVSpecialAttack + delta).coerceIn(0, 31)
                "special attack max" -> ivSettings.maxIVSpecialAttack = (ivSettings.maxIVSpecialAttack + delta).coerceIn(0, 31)
                "special defense min" -> ivSettings.minIVSpecialDefense = (ivSettings.minIVSpecialDefense + delta).coerceIn(0, 31)
                "special defense max" -> ivSettings.maxIVSpecialDefense = (ivSettings.maxIVSpecialDefense + delta).coerceIn(0, 31)
                "speed min" -> ivSettings.minIVSpeed = (ivSettings.minIVSpeed + delta).coerceIn(0, 31)
                "speed max" -> ivSettings.maxIVSpeed = (ivSettings.maxIVSpeed + delta).coerceIn(0, 31)
            }
        } ?: run {
            player.sendMessage(Text.literal("Failed to update IV value."), false)
        }

        // After updating, refresh the GUI
        val updatedEntry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName)
        if (updatedEntry != null) {
            refreshGui(player, updatedEntry)
            logDebug(
                "Updated IVs for ${updatedEntry.pokemonName} (${updatedEntry.formName ?: "Standard"}) at spawner $spawnerPos and saved to JSON."
            )
        }
    }

    /**
     * Refreshes the IV editor GUI items based on the current state.
     */
    private fun refreshGui(player: ServerPlayerEntity, selectedEntry: PokemonSpawnEntry) {
        val layout = generateIVEditorLayout(selectedEntry)

        val screenHandler = player.currentScreenHandler
        layout.forEachIndexed { index, itemStack ->
            if (index < screenHandler.slots.size) {
                screenHandler.slots[index].stack = itemStack
            }
        }

        screenHandler.sendContentUpdates()
    }

    /**
     * Toggles the allowCustomIvs flag without closing the GUI and updates the Player Head lore.
     */
    private fun toggleAllowCustomIvsWithoutClosing(
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        player: ServerPlayerEntity,
        toggleSlot: Int
    ) {
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName) { selectedEntry ->
            val ivSettings = selectedEntry.ivSettings
            ivSettings.allowCustomIvs = !ivSettings.allowCustomIvs
        } ?: run {
            player.sendMessage(Text.literal("Failed to toggle allowCustomIvs."), false)
            return
        }

        // Update the toggle button Player Head to reflect the new value (ON/OFF)
        val selectedEntry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName)
        if (selectedEntry != null) {
            val toggleHead = createToggleCustomIvsHeadButton(selectedEntry.ivSettings.allowCustomIvs)

            // Update the GUI with the new toggle head without closing
            val screenHandler = player.currentScreenHandler
            if (toggleSlot < screenHandler.slots.size) {
                screenHandler.slots[toggleSlot].stack = toggleHead
            }

            screenHandler.sendContentUpdates()

            logDebug(
                "Toggled allowCustomIvs for ${selectedEntry.pokemonName} (${selectedEntry.formName ?: "Standard"}) at spawner $spawnerPos."
            )
        }
    }
}
