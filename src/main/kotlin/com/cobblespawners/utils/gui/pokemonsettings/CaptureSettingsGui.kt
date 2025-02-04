package com.cobblespawners.utils.gui.pokemonsettings

import com.cobblespawners.utils.*
import com.cobblespawners.utils.gui.SpawnerPokemonSelectionGui
import com.cobblespawners.utils.gui.SpawnerPokemonSelectionGui.spawnerGuisOpen
import com.blanketutils.gui.CustomGui
import com.blanketutils.gui.InteractionContext
import com.blanketutils.gui.setCustomName
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.LoreComponent
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.ClickType
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos
import org.slf4j.LoggerFactory

object CaptureSettingsGui {
    private val logger = LoggerFactory.getLogger(CaptureSettingsGui::class.java)

    /**
     * Opens the Capture Settings GUI for a specific Pokémon and form.
     */
    fun openCaptureSettingsGui(
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

        val layout = generateCaptureSettingsLayout(selectedEntry)

        spawnerGuisOpen[spawnerPos] = player

        val onInteract: (InteractionContext) -> Unit = { context ->
            when (context.slotIndex) {
                21 -> { // Toggle catchable status
                    if (context.clickType == ClickType.LEFT) {
                        toggleIsCatchable(spawnerPos, pokemonName, formName, player)
                        updateGuiItem(context, player, "Catchable", selectedEntry.captureSettings.isCatchable, Formatting.GREEN)
                    }
                }
                23 -> { // Toggle restricted capture or open Poké Ball selection
                    if (context.clickType == ClickType.LEFT) {
                        toggleRestrictCapture(spawnerPos, pokemonName, formName, player)
                        updateGuiItem(context, player, "Restrict Capture To Limited Balls", selectedEntry.captureSettings.restrictCaptureToLimitedBalls, Formatting.RED)
                    } else if (context.clickType == ClickType.RIGHT) {
                        CaptureBallSettingsGui.openCaptureBallSettingsGui(player, spawnerPos, pokemonName, formName)
                    }
                }
                49 -> { // Back button
                    CustomGui.closeGui(player)
                    SpawnerPokemonSelectionGui.openPokemonEditSubGui(player, spawnerPos, pokemonName, formName)
                }
            }
        }

        val onClose: (Inventory) -> Unit = {
            spawnerGuisOpen.remove(spawnerPos)
            player.sendMessage(
                Text.literal("Capture Settings GUI closed for $pokemonName (${selectedEntry.formName ?: "Standard"})"),
                false
            )
        }

        CustomGui.openGui(
            player,
            "Edit Capture Settings for $pokemonName (${selectedEntry.formName ?: "Standard"})",
            layout,
            onInteract,
            onClose
        )
    }

    private fun generateCaptureSettingsLayout(selectedEntry: PokemonSpawnEntry): List<ItemStack> {
        val layout = MutableList(54) { createFillerPane() }  // Default filler panes in all slots

        // Toggle isCatchable button (player head) at slot 19
        layout[21] = createIsCatchableHeadButton(selectedEntry.captureSettings.isCatchable)

        // Toggle restrictCaptureToLimitedBalls button (right-click to edit balls) at slot 23
        layout[23] = createRestrictCaptureHeadButton(selectedEntry.captureSettings.restrictCaptureToLimitedBalls)

        // Back button (player head) at slot 49
        layout[49] = createBackHeadButton()

        return layout
    }

    private fun createFillerPane(): ItemStack {
        return ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
            setCustomName(Text.literal(" "))  // Just a blank filler pane
        }
    }

    private fun createIsCatchableHeadButton(isCatchable: Boolean): ItemStack {
        val textureValue = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTNlNjg3NjhmNGZhYjgxYzk0ZGY3MzVlMjA1YzNiNDVlYzQ1YTY3YjU1OGYzODg0NDc5YTYyZGQzZjRiZGJmOCJ9fX0=" // Replace with actual texture
        return CustomGui.createPlayerHeadButton(
            "CatchableToggle",
            Text.literal("Catchable").styled { it.withColor(Formatting.GREEN).withBold(false).withItalic(false) },
            listOf(
                Text.literal("Left-click to toggle catchable status").styled { it.withColor(Formatting.GRAY).withItalic(false) },
                Text.literal("Status: ${if (isCatchable) "ON" else "OFF"}").styled {
                    it.withColor(if (isCatchable) Formatting.GREEN else Formatting.GRAY).withItalic(false)
                }
            ),
            textureValue
        )
    }

    private fun createRestrictCaptureHeadButton(restrictCapture: Boolean): ItemStack {
        val textureValue = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWQ0MDhjNTY5OGYyZDdhOGExNDE1ZWY5NTkyYWViNGJmNjJjOWFlN2NjZjE4ODQ5NzUzMGJmM2M4Yjk2NDhlNSJ9fX0="
        return CustomGui.createPlayerHeadButton(
            "RestrictCaptureToggle",
            Text.literal("Restrict Capture To Limited Balls").styled { it.withColor(Formatting.RED).withBold(false).withItalic(false) },
            listOf(
                Text.literal("Left-click to toggle restricted capture").styled { it.withColor(Formatting.GRAY).withItalic(false) },
                Text.literal("Status: ${if (restrictCapture) "ON" else "OFF"}").styled {
                    it.withColor(if (restrictCapture) Formatting.GREEN else Formatting.GRAY).withItalic(false)
                },
                Text.literal("Right-click to edit required Poké Balls").styled { it.withColor(Formatting.GRAY).withItalic(false) },
            ),
            textureValue
        )
    }

    private fun createBackHeadButton(): ItemStack {
        val textureValue = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
        return CustomGui.createPlayerHeadButton(
            "BackButton",
            Text.literal("Back").styled { it.withColor(Formatting.WHITE).withBold(false).withItalic(false) },
            listOf(Text.literal("Return to previous menu").styled { it.withColor(Formatting.GRAY).withItalic(false) }),
            textureValue
        )
    }

    private fun toggleIsCatchable(
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        player: ServerPlayerEntity
    ) {
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName) { entry ->
            entry.captureSettings.isCatchable = !entry.captureSettings.isCatchable
        }
        player.sendMessage(Text.literal("Toggled catchable state for $pokemonName."), false)
    }

    private fun toggleRestrictCapture(
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        player: ServerPlayerEntity
    ) {
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName) { entry ->
            entry.captureSettings.restrictCaptureToLimitedBalls = !entry.captureSettings.restrictCaptureToLimitedBalls
        }
        player.sendMessage(Text.literal("Toggled restricted capture for $pokemonName."), false)
    }

    private fun updateGuiItem(context: InteractionContext, player: ServerPlayerEntity, settingName: String, newValue: Boolean, color: Formatting) {
        val itemStack = context.clickedStack

        // Set custom name via DataComponents
        itemStack.setCustomName(
            Text.literal(settingName).styled {
                it.withColor(color).withBold(false).withItalic(false)
            }
        )

        // Retrieve the current lore from DataComponents
        val oldLoreComponent = itemStack.getOrDefault(DataComponentTypes.LORE, LoreComponent(emptyList()))
        val oldLore = oldLoreComponent.lines.toMutableList()

        // Attempt to find and update the "Status:" line
        var updatedStatus = false
        for (i in oldLore.indices) {
            val line = oldLore[i]
            if (line.string.contains("Status:", ignoreCase = true)) {
                // Replace this line with the new status line
                oldLore[i] = Text.literal("Status: ${if (newValue) "ON" else "OFF"}").styled { s ->
                    s.withColor(if (newValue) Formatting.GREEN else Formatting.GRAY).withItalic(false)
                }
                updatedStatus = true
                break
            }
        }

        // If no "Status:" line was found, add a new line
        if (!updatedStatus) {
            oldLore.add(
                Text.literal("Status: ${if (newValue) "ON" else "OFF"}").styled { s ->
                    s.withColor(if (newValue) Formatting.GREEN else Formatting.GRAY).withItalic(false)
                }
            )
        }

        // Update the lore using DataComponents
        itemStack.set(DataComponentTypes.LORE, LoreComponent(oldLore))

        // Update the item in the player's screen
        player.currentScreenHandler.slots[context.slotIndex].stack = itemStack
        player.currentScreenHandler.sendContentUpdates()
    }

}
