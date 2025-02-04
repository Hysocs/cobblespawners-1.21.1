// File: SizeSettingsGui.kt
package com.cobblespawners.utils.gui.pokemonsettings

import com.blanketutils.gui.CustomGui
import com.blanketutils.gui.InteractionContext
import com.blanketutils.gui.setCustomName
import com.cobblespawners.utils.*
import com.blanketutils.utils.logDebug

import com.cobblespawners.utils.gui.SpawnerPokemonSelectionGui
import com.cobblespawners.utils.gui.SpawnerPokemonSelectionGui.spawnerGuisOpen
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.ClickType
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos
import org.slf4j.LoggerFactory
import kotlin.math.roundToInt

object SizeSettingsGui {
    private val logger = LoggerFactory.getLogger(SizeSettingsGui::class.java)

    /**
     * Opens the Size Editing GUI for a specific Pokémon and form.
     *
     * @param player The player opening the GUI.
     * @param spawnerPos The position of the spawner.
     * @param pokemonName The name of the Pokémon.
     * @param formName The form of the Pokémon, if any.
     */
    fun openSizeEditorGui(
        player: ServerPlayerEntity,
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?
    ) {
        val selectedEntry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName)
        if (selectedEntry == null) {
            player.sendMessage(
                Text.literal("Pokémon '$pokemonName' with form '${formName ?: "Standard"}' not found in spawner."),
                false
            )
            logger.warn("Pokémon '$pokemonName' with form '${formName ?: "Standard"}' not found in spawner at $spawnerPos.")
            return
        }

        val layout = generateSizeEditorLayout(selectedEntry)

        spawnerGuisOpen[spawnerPos] = player

        val onInteract: (InteractionContext) -> Unit = { context ->
            val clickedItem = context.clickedStack
            val clickedItemName = clickedItem.name?.string ?: ""

            when {
                clickedItemName.startsWith("Allow Custom Sizes:") -> {
                    handleToggleAllowCustomSize(
                        spawnerPos,
                        pokemonName,
                        formName,
                        player,
                        selectedEntry
                    )
                }
                clickedItemName.startsWith("Min Size:") || clickedItemName.startsWith("Max Size:") -> {
                    when (context.clickType) {
                        ClickType.LEFT -> {
                            handleSizeAdjustment(
                                spawnerPos,
                                pokemonName,
                                formName,
                                player,
                                selectedEntry,
                                isMinSize = clickedItemName.startsWith("Min"),
                                increase = false
                            )
                        }
                        ClickType.RIGHT -> {
                            handleSizeAdjustment(
                                spawnerPos,
                                pokemonName,
                                formName,
                                player,
                                selectedEntry,
                                isMinSize = clickedItemName.startsWith("Min"),
                                increase = true
                            )
                        }
                        else -> {}
                    }
                }
                clickedItemName == "Back" -> {
                    CustomGui.closeGui(player)
                    player.sendMessage(Text.literal("Returning to Edit Pokémon menu."), false)
                    SpawnerPokemonSelectionGui.openPokemonEditSubGui(player, spawnerPos, pokemonName, formName)
                }
                else -> {
                    // Ignore other items
                }
            }
        }

        val onClose: (Inventory) -> Unit = {
            spawnerGuisOpen.remove(spawnerPos)
            player.sendMessage(
                Text.literal("Size Settings GUI closed for $pokemonName (${selectedEntry.formName ?: "Standard"})"),
                false
            )
        }

        CustomGui.openGui(
            player,
            "Edit Size Settings for $pokemonName (${selectedEntry.formName ?: "Standard"})",
            layout,
            onInteract,
            onClose
        )
    }

    /**
     * Generates the layout for the Size Editing GUI.
     *
     * @param selectedEntry The selected Pokémon spawn entry.
     * @return A list of ItemStacks representing the GUI layout.
     */
    private fun generateSizeEditorLayout(selectedEntry: PokemonSpawnEntry): List<ItemStack> {
        val layout = MutableList(54) { ItemStack.EMPTY }

        // Add Player Head items for interactive elements
        layout[19] = createToggleAllowCustomSizeHeadButton(
            allowCustomSize = selectedEntry.sizeSettings.allowCustomSize
        )

        layout[23] = createSizeAdjusterHeadButton(
            label = "Min Size",
            currentSize = selectedEntry.sizeSettings.minSize,
            isMinSize = true,
            allowCustomSize = selectedEntry.sizeSettings.allowCustomSize
        )

        layout[25] = createSizeAdjusterHeadButton(
            label = "Max Size",
            currentSize = selectedEntry.sizeSettings.maxSize,
            isMinSize = false,
            allowCustomSize = selectedEntry.sizeSettings.allowCustomSize
        )

        // Add the back button as a Player Head
        layout[49] = createBackHeadButton()

        // Fill the rest with gray stained glass panes
        for (i in 0 until 54) {
            if (i !in listOf(19, 23, 25, 49)) {
                layout[i] = createFillerPane()
            }
        }

        return layout
    }

    /**
     * Creates a toggle button Player Head for allowing/disallowing custom size settings.
     *
     * @param allowCustomSize Current state of allowCustomSize.
     * @return The configured ItemStack.
     */
    private fun createToggleAllowCustomSizeHeadButton(allowCustomSize: Boolean): ItemStack {
        val textureValue = if (allowCustomSize) {
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTI1YjhlZWQ1YzU2NWJkNDQwZWM0N2M3OWMyMGQ1Y2YzNzAxNjJiMWQ5YjVkZDMxMDBlZDYyODNmZTAxZDZlIn19fQ=="
        } else {
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjNmNzliMjA3ZDYxZTEyMjUyM2I4M2Q2MTUwOGQ5OWNmYTA3OWQ0NWJmMjNkZjJhOWE1MTI3ZjkwNzFkNGIwMCJ9fX0="
        }

        return CustomGui.createPlayerHeadButton(
            "ToggleCustomSizes",
            Text.literal("Allow Custom Sizes: ${if (allowCustomSize) "ON" else "OFF"}").styled {
                it.withColor(if (allowCustomSize) Formatting.GREEN else Formatting.RED)
                    .withBold(true)
            },
            listOf(
                Text.literal("§eClick to toggle")
            ),
            textureValue
        )
    }

    /**
     * Creates a size adjuster Player Head for a specific size type (Min or Max).
     *
     * @param label The label for the size type.
     * @param currentSize The current size value.
     * @param isMinSize Flag indicating if it's for min size.
     * @param allowCustomSize Flag indicating if custom sizes are allowed.
     * @return The configured ItemStack.
     */
    private fun createSizeAdjusterHeadButton(
        label: String,
        currentSize: Float,
        isMinSize: Boolean,
        allowCustomSize: Boolean
    ): ItemStack {
        val displayName = "$label: %.1f".format(currentSize)

        val textureValue = when (label.lowercase()) {
            "min size" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTZhMDExZTYyNmI3MWNlYWQ5ODQxOTM1MTFlODJlNjVjMTM1OTU2NWYwYTJmY2QxMTg0ODcyZjg5ZDkwOGM2NSJ9fX0=" // Replace with actual base64 texture for Min Size
            "max size" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTU3YTViZGY0MmYxNTIxNzhkMTU0YmIyMjM3ZDlmZDM1NzcyYTdmMzJiY2ZkMzNiZWViOGVkYzQ4MjBiYSJ9fX0=" // Replace with actual base64 texture for Max Size
            else -> "default_base64_texture"
        }

        return CustomGui.createPlayerHeadButton(
            label.replace(" ", "") + "Head",
            Text.literal(displayName).styled {
                it.withColor(
                    if (isMinSize) Formatting.GREEN else Formatting.BLUE
                ).withBold(true)
            },
            listOf(
                Text.literal("§7Left-click to decrease by 0.1"),
                Text.literal("§7Right-click to increase by 0.1"),
            ),
            textureValue
        )
    }

    /**
     * Creates a back button Player Head.
     *
     * @return The configured ItemStack.
     */
    private fun createBackHeadButton(): ItemStack {
        val textureValue = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0=" // Replace with actual base64 texture for Back

        return CustomGui.createPlayerHeadButton(
            "BackButton",
            Text.literal("Back").styled { it.withColor(Formatting.BLUE).withBold(true) },
            listOf(Text.literal("§7Click to return to the previous menu.")),
            textureValue
        )
    }

    /**
     * Creates a filler pane to fill unused slots.
     *
     * @return The configured ItemStack.
     */
    private fun createFillerPane(): ItemStack {
        return ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
            setCustomName(Text.literal(" "))
            CustomGui.setItemLore(this, listOf(" "))
        }
    }

    /**
     * Handles toggling the allowCustomSize flag.
     *
     * @param spawnerPos The position of the spawner.
     * @param pokemonName The name of the Pokémon.
     * @param formName The form of the Pokémon, if any.
     * @param player The player interacting with the GUI.
     * @param selectedEntry The selected Pokémon spawn entry.
     */
    private fun handleToggleAllowCustomSize(
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        player: ServerPlayerEntity,
        selectedEntry: PokemonSpawnEntry
    ) {
        // Toggle the allowCustomSize flag
        selectedEntry.sizeSettings.allowCustomSize = !selectedEntry.sizeSettings.allowCustomSize

        // Save the updated configuration
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName) { entry ->
            entry.sizeSettings = selectedEntry.sizeSettings
        }

        // Update the GUI to reflect the change
        val screenHandler = player.currentScreenHandler
        screenHandler.slots[19].stack = createToggleAllowCustomSizeHeadButton(selectedEntry.sizeSettings.allowCustomSize)
        screenHandler.sendContentUpdates()

        // Notify the player
        val status = if (selectedEntry.sizeSettings.allowCustomSize) "enabled" else "disabled"
        player.sendMessage(
            Text.literal("Custom size settings $status for $pokemonName."),
            false
        )

        logDebug("Toggled allowCustomSize to $status for $pokemonName (${selectedEntry.formName ?: "Standard"}) at spawner $spawnerPos.")
    }

    /**
     * Handles size adjustment logic.
     *
     * @param spawnerPos The position of the spawner.
     * @param pokemonName The name of the Pokémon.
     * @param formName The form of the Pokémon, if any.
     * @param player The player interacting with the GUI.
     * @param selectedEntry The selected Pokémon spawn entry.
     * @param isMinSize Flag indicating if adjusting min size.
     * @param increase Flag indicating if increasing or decreasing.
     */
    private fun handleSizeAdjustment(
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        player: ServerPlayerEntity,
        selectedEntry: PokemonSpawnEntry,
        isMinSize: Boolean,
        increase: Boolean
    ) {
        val adjustmentValue = 0.1f
        val currentSize = if (isMinSize) selectedEntry.sizeSettings.minSize else selectedEntry.sizeSettings.maxSize
        val newSize = if (increase) currentSize + adjustmentValue else currentSize - adjustmentValue

        // Determine bounds
        val minBound = 0.5f
        val maxBound = if (isMinSize) selectedEntry.sizeSettings.maxSize else 3.0f

        val adjustedSize = newSize.coerceIn(minBound, maxBound)
        val roundedSize = roundToOneDecimal(adjustedSize)

        // Check if adjustment is necessary (i.e., no change after rounding)
        if (roundedSize == currentSize) {
            player.sendMessage(
                Text.literal("Cannot adjust ${if (isMinSize) "minimum" else "maximum"} size beyond allowed limits."),
                false
            )
            return
        }

        // Update the size in the config
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName) { entry ->
            if (isMinSize) {
                entry.sizeSettings.minSize = roundedSize
            } else {
                entry.sizeSettings.maxSize = roundedSize
            }
        } ?: run {
            player.sendMessage(Text.literal("Failed to adjust size."), false)
            return
        }

        // Retrieve updated entry
        val updatedEntry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName)
        if (updatedEntry != null) {
            // Update the size display in the GUI
            val screenHandler = player.currentScreenHandler
            val targetSlot = if (isMinSize) 23 else 25
            screenHandler.slots[targetSlot].stack = createSizeAdjusterHeadButton(
                label = if (isMinSize) "Min Size" else "Max Size",
                currentSize = if (isMinSize) updatedEntry.sizeSettings.minSize else updatedEntry.sizeSettings.maxSize,
                isMinSize = isMinSize,
                allowCustomSize = updatedEntry.sizeSettings.allowCustomSize
            )
            screenHandler.sendContentUpdates()

            // Log the adjustment
            logger.info(
                "Adjusted ${if (isMinSize) "min" else "max"} size for $pokemonName (${updatedEntry.formName ?: "Standard"}) at spawner $spawnerPos to ${
                    if (isMinSize) updatedEntry.sizeSettings.minSize else updatedEntry.sizeSettings.maxSize
                }."
            )

            // Notify the player
            player.sendMessage(
                Text.literal(
                    "Set ${if (isMinSize) "minimum" else "maximum"} size to ${
                        if (isMinSize) updatedEntry.sizeSettings.minSize else updatedEntry.sizeSettings.maxSize
                    } for $pokemonName."
                ),
                false
            )
        }
    }

    /**
     * Rounds a Float to one decimal place.
     *
     * @param value The Float value to round.
     * @return The rounded Float value.
     */
    private fun roundToOneDecimal(value: Float): Float {
        return (value * 10).roundToInt() / 10f
    }
}
