// File: ShinySettingsGui.kt
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
import net.minecraft.util.math.BlockPos
import net.minecraft.util.ClickType
import org.slf4j.LoggerFactory

object SpawnSettingsGui {
    private val logger = LoggerFactory.getLogger(SpawnSettingsGui::class.java)

    // Data class for chance buttons
    data class ChanceButton(
        val slotIndex: Int,
        val chanceType: String, // "shiny" or "spawn"
        val action: String, // "increase" or "decrease"
        val leftDelta: Double,
        val rightDelta: Double
    )

    // Define the chance buttons with their respective increments/decrements
    private val chanceButtons = listOf(
        // Spawn Chance Buttons
        ChanceButton(slotIndex = 10, chanceType = "spawn", action = "decrease", leftDelta = -0.01, rightDelta = -0.05),
        ChanceButton(slotIndex = 11, chanceType = "spawn", action = "decrease", leftDelta = -0.1, rightDelta = -0.5),
        ChanceButton(slotIndex = 12, chanceType = "spawn", action = "decrease", leftDelta = -1.0, rightDelta = -5.0),
        ChanceButton(slotIndex = 14, chanceType = "spawn", action = "increase", leftDelta = 0.01, rightDelta = 0.05),
        ChanceButton(slotIndex = 15, chanceType = "spawn", action = "increase", leftDelta = 0.1, rightDelta = 0.5),
        ChanceButton(slotIndex = 16, chanceType = "spawn", action = "increase", leftDelta = 1.0, rightDelta = 5.0),
        // Shiny Chance Buttons
        ChanceButton(slotIndex = 19, chanceType = "shiny", action = "decrease", leftDelta = -0.01, rightDelta = -0.05),
        ChanceButton(slotIndex = 20, chanceType = "shiny", action = "decrease", leftDelta = -0.1, rightDelta = -0.5),
        ChanceButton(slotIndex = 21, chanceType = "shiny", action = "decrease", leftDelta = -1.0, rightDelta = -5.0),
        ChanceButton(slotIndex = 23, chanceType = "shiny", action = "increase", leftDelta = 0.01, rightDelta = 0.05),
        ChanceButton(slotIndex = 24, chanceType = "shiny", action = "increase", leftDelta = 0.1, rightDelta = 0.5),
        ChanceButton(slotIndex = 25, chanceType = "shiny", action = "increase", leftDelta = 1.0, rightDelta = 5.0)
    )

    private val chanceButtonMap = chanceButtons.associateBy { it.slotIndex }

    // Data class for level buttons
    data class LevelButton(
        val slotIndex: Int,
        val isMinLevel: Boolean, // true for Min Level, false for Max Level
        val action: String // "increase" or "decrease"
    )

    // Define the level adjustment buttons
    private val levelButtons = listOf(
        // Min Level Adjustment Buttons
        LevelButton(slotIndex = 28, isMinLevel = true, action = "decrease"),
        LevelButton(slotIndex = 30, isMinLevel = true, action = "increase"),
        // Max Level Adjustment Buttons
        LevelButton(slotIndex = 32, isMinLevel = false, action = "decrease"),
        LevelButton(slotIndex = 34, isMinLevel = false, action = "increase")
    )

    private val levelButtonMap = levelButtons.associateBy { it.slotIndex }

    /**
     * Opens the Spawn and Shiny Editor GUI for a specific Pokémon and form.
     */
    fun openSpawnShinyEditorGui(
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
            logDebug("Pokémon '$pokemonName' with form '${formName ?: "Standard"}' not found in spawner at $spawnerPos.")
            return
        }

        val layout = generateSpawnShinyEditorLayout(selectedEntry)

        SpawnerPokemonSelectionGui.spawnerGuisOpen[spawnerPos] = player

        val onInteract: (InteractionContext) -> Unit = onInteract@{ context ->
            val slotIndex = context.slotIndex
            val clickType = context.clickType

            // Handle Chance Buttons
            if (chanceButtonMap.containsKey(slotIndex)) {
                val chanceButton = chanceButtonMap[slotIndex]!!
                val delta = when (clickType) {
                    ClickType.LEFT -> chanceButton.leftDelta
                    ClickType.RIGHT -> chanceButton.rightDelta
                    else -> 0.0
                }
                if (delta != 0.0) {
                    if (chanceButton.chanceType == "shiny") {
                        updateShinyChance(spawnerPos, pokemonName, formName, delta, player)
                    } else if (chanceButton.chanceType == "spawn") {
                        updateSpawnChance(spawnerPos, pokemonName, formName, delta, player)
                    }
                }
                return@onInteract
            }

            // Handle Level Adjustment Buttons
            if (levelButtonMap.containsKey(slotIndex)) {
                val levelButton = levelButtonMap[slotIndex]!!
                val delta = when (clickType) {
                    ClickType.LEFT -> if (levelButton.action == "increase") 1 else -1
                    ClickType.RIGHT -> if (levelButton.action == "increase") 5 else -5
                    else -> 0
                }
                if (delta != 0) {
                    adjustLevel(
                        spawnerPos,
                        pokemonName,
                        formName,
                        player,
                        levelButton.isMinLevel,
                        delta
                    )
                }
                return@onInteract
            }

            // Handle Back Button
            val clickedItem = context.clickedStack
            if (clickedItem.item == Items.ARROW) {
                CustomGui.closeGui(player)
                player.sendMessage(Text.literal("Returning to Edit Pokémon menu"), false)
                SpawnerPokemonSelectionGui.openPokemonEditSubGui(player, spawnerPos, pokemonName, formName)
            }
        }

        val onClose: (Inventory) -> Unit = {
            spawnerGuisOpen.remove(spawnerPos)
            player.sendMessage(
                Text.literal("Spawn and Shiny Editor closed for $pokemonName (${formName ?: "Standard"})"),
                false
            )
        }

        val guiTitle = "Edit Spawn & Shiny Chances for $pokemonName (${formName ?: "Standard"})"

        CustomGui.openGui(
            player,
            guiTitle,
            layout,
            onInteract,
            onClose
        )
    }

    /**
     * Generates the layout for the Spawn and Shiny Editor GUI.
     */
    private fun generateSpawnShinyEditorLayout(selectedEntry: PokemonSpawnEntry): List<ItemStack> {
        val layout = MutableList(54) { ItemStack.EMPTY }

        // Add Chance Buttons to the layout
        chanceButtons.forEach { button ->
            val itemStack = createChanceButtonItemStack(button, selectedEntry)
            layout[button.slotIndex] = itemStack
        }

        // Add Level Adjustment Buttons to the layout
        levelButtons.forEach { button ->
            val itemStack = createLevelAdjustmentButtonItemStack(button, selectedEntry)
            layout[button.slotIndex] = itemStack
        }

        // Add Current Spawn Chance Display
        layout[13] = createCurrentSpawnChanceDisplay(selectedEntry.spawnChance)
        // Add Current Shiny Chance Display
        layout[22] = createCurrentShinyChanceDisplay(selectedEntry.shinyChance)
        // Add Current Min Level Display
        layout[29] = createCurrentMinLevelDisplay(selectedEntry.minLevel)
        // Add Current Max Level Display
        layout[33] = createCurrentMaxLevelDisplay(selectedEntry.maxLevel)

        // Fill the rest with gray stained glass panes except for the button slots and back button
        val excludedSlots = chanceButtons.map { it.slotIndex } +
                levelButtons.map { it.slotIndex } +
                listOf(13, 22, 29, 33, 49)

        for (i in 0 until 54) {
            if (i !in excludedSlots) {
                layout[i] = ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
                    setCustomName(Text.literal(" "))
                }
            }
        }

        // Back Button remains at slot 49
        layout[49] = ItemStack(Items.ARROW).apply {
            setCustomName(Text.literal("Back"))
            CustomGui.setItemLore(this, listOf("§eClick to return"))
        }

        return layout
    }

    /**
     * Creates a Chance Button ItemStack.
     */
    private fun createChanceButtonItemStack(button: ChanceButton, selectedEntry: PokemonSpawnEntry): ItemStack {
        val (slotIndex, chanceType, action, leftDelta, rightDelta) = button
        val itemName = "${action.capitalize()} ${chanceType.capitalize()} Chance"
        val itemStack = ItemStack(Items.PAPER).apply {
            setCustomName(Text.literal(itemName))
            val currentChance = if (chanceType == "shiny") selectedEntry.shinyChance else selectedEntry.spawnChance
            val lore = listOf(
                "§aCurrent ${chanceType.capitalize()} Chance: §f${"%.2f".format(currentChance)}%",
                "§eLeft-click: ${if (leftDelta > 0) "+" else ""}${"%.2f".format(leftDelta)}%",
                "§eRight-click: ${if (rightDelta > 0) "+" else ""}${"%.2f".format(rightDelta)}%"
            )
            CustomGui.setItemLore(this, lore)
        }
        return itemStack
    }

    /**
     * Creates a Level Adjustment Button ItemStack.
     */
    private fun createLevelAdjustmentButtonItemStack(button: LevelButton, selectedEntry: PokemonSpawnEntry): ItemStack {
        val (slotIndex, isMinLevel, action) = button
        val levelType = if (isMinLevel) "Min Level" else "Max Level"
        val itemName = "${action.capitalize()} $levelType"
        val currentLevel = if (isMinLevel) selectedEntry.minLevel else selectedEntry.maxLevel
        val itemStack = ItemStack(Items.PAPER).apply {
            setCustomName(Text.literal(itemName))
            val lore = listOf(
                "§aCurrent $levelType: §f$currentLevel",
                "§eLeft-click: Adjust by ${if (action == "increase") "+" else ""}1",
                "§eRight-click: Adjust by ${if (action == "increase") "+" else ""}5"
            )
            CustomGui.setItemLore(this, lore)
        }
        return itemStack
    }

    /**
     * Creates the Current Spawn Chance Display.
     */
    private fun createCurrentSpawnChanceDisplay(chance: Double): ItemStack {
        return ItemStack(Items.BOOK).apply {
            setCustomName(Text.literal("Current Spawn Chance"))
            CustomGui.setItemLore(this, listOf(
                "§aSpawn Chance: §f${"%.2f".format(chance)}%"
            ))
        }
    }

    /**
     * Creates the Current Shiny Chance Display.
     */
    private fun createCurrentShinyChanceDisplay(chance: Double): ItemStack {
        return ItemStack(Items.BOOK).apply {
            setCustomName(Text.literal("Current Shiny Chance"))
            CustomGui.setItemLore(this, listOf(
                "§aShiny Chance: §f${"%.2f".format(chance)}%"
            ))
        }
    }

    /**
     * Creates the Current Min Level Display.
     */
    private fun createCurrentMinLevelDisplay(minLevel: Int): ItemStack {
        return ItemStack(Items.BOOK).apply {
            setCustomName(Text.literal("Current Min Level"))
            CustomGui.setItemLore(this, listOf(
                "§aMin Level: §f$minLevel"
            ))
        }
    }

    /**
     * Creates the Current Max Level Display.
     */
    private fun createCurrentMaxLevelDisplay(maxLevel: Int): ItemStack {
        return ItemStack(Items.BOOK).apply {
            setCustomName(Text.literal("Current Max Level"))
            CustomGui.setItemLore(this, listOf(
                "§aMax Level: §f$maxLevel"
            ))
        }
    }

    /**
     * Updates the spawn chance value for the selected Pokémon and form.
     */
    private fun updateSpawnChance(
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        delta: Double,
        player: ServerPlayerEntity
    ) {
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName) { selectedEntry ->
            selectedEntry.spawnChance = (selectedEntry.spawnChance + delta).coerceIn(0.0, 100.0)
        } ?: run {
            player.sendMessage(Text.literal("Failed to update spawn chance."), false)
            return
        }

        // Refresh the GUI
        val updatedEntry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName)
        if (updatedEntry != null) {
            refreshGui(player, updatedEntry)
            logDebug(
                "Updated spawnChance to ${updatedEntry.spawnChance}% for $pokemonName (${formName ?: "Standard"}) at spawner $spawnerPos."
            )
            player.sendMessage(
                Text.literal("Spawn Chance set to ${"%.2f".format(updatedEntry.spawnChance)}% for $pokemonName."),
                false
            )
        }
    }

    /**
     * Updates the shiny chance value for the selected Pokémon and form.
     */
    private fun updateShinyChance(
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        delta: Double,
        player: ServerPlayerEntity
    ) {
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName) { selectedEntry ->
            selectedEntry.shinyChance = (selectedEntry.shinyChance + delta).coerceIn(0.0, 100.0)
        } ?: run {
            player.sendMessage(Text.literal("Failed to update shiny chance."), false)
            return
        }

        // Refresh the GUI
        val updatedEntry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName)
        if (updatedEntry != null) {
            refreshGui(player, updatedEntry)
            logDebug(
                "Updated shinyChance to ${updatedEntry.shinyChance}% for $pokemonName (${formName ?: "Standard"}) at spawner $spawnerPos."
            )
            player.sendMessage(
                Text.literal("Shiny Chance set to ${"%.2f".format(updatedEntry.shinyChance)}% for $pokemonName."),
                false
            )
        }
    }

    /**
     * Adjusts the Min or Max Level based on the delta.
     * Updates all related level adjustment buttons' lore.
     */
    private fun adjustLevel(
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        player: ServerPlayerEntity,
        isMinLevel: Boolean,
        delta: Int
    ) {
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName) { selectedEntry ->
            if (isMinLevel) {
                val newMin = (selectedEntry.minLevel + delta).coerceAtLeast(1)
                    .coerceAtMost(selectedEntry.maxLevel) // Ensure minLevel <= maxLevel
                selectedEntry.minLevel = newMin
            } else {
                val newMax = (selectedEntry.maxLevel + delta).coerceAtLeast(selectedEntry.minLevel)
                    .coerceAtMost(100) // Assuming 100 is the max level; adjust as needed
                selectedEntry.maxLevel = newMax
            }
        } ?: run {
            player.sendMessage(Text.literal("Failed to adjust level."), false)
            return
        }

        // Retrieve the updated entry
        val updatedEntry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName)
        if (updatedEntry != null) {
            // Update the current level display
            val displaySlot = if (isMinLevel) 29 else 33
            val updatedDisplayItem = if (isMinLevel) {
                createCurrentMinLevelDisplay(updatedEntry.minLevel)
            } else {
                createCurrentMaxLevelDisplay(updatedEntry.maxLevel)
            }

            val screenHandler = player.currentScreenHandler
            if (displaySlot < screenHandler.slots.size) {
                screenHandler.slots[displaySlot].stack = updatedDisplayItem
            }

            // Update all related level adjustment buttons for the adjusted level type
            levelButtons.filter { it.isMinLevel == isMinLevel }.forEach { button ->
                val updatedButton = createLevelAdjustmentButtonItemStack(button, updatedEntry)
                if (button.slotIndex < screenHandler.slots.size) {
                    screenHandler.slots[button.slotIndex].stack = updatedButton
                }
            }

            screenHandler.sendContentUpdates()

            logger.info(
                "Adjusted ${if (isMinLevel) "min" else "max"} level for $pokemonName (${updatedEntry.formName ?: "Standard"}) at spawner $spawnerPos to ${if (isMinLevel) updatedEntry.minLevel else updatedEntry.maxLevel}."
            )

            // Notify the player
            player.sendMessage(
                Text.literal("Set ${if (isMinLevel) "Min" else "Max"} Level to ${if (isMinLevel) updatedEntry.minLevel else updatedEntry.maxLevel} for $pokemonName."),
                false
            )
        }
    }

    /**
     * Refreshes the Spawn and Shiny Editor GUI items based on the current state.
     */
    private fun refreshGui(player: ServerPlayerEntity, selectedEntry: PokemonSpawnEntry) {
        val layout = generateSpawnShinyEditorLayout(selectedEntry)

        val screenHandler = player.currentScreenHandler
        layout.forEachIndexed { index, itemStack ->
            if (index < screenHandler.slots.size) {
                screenHandler.slots[index].stack = itemStack
            }
        }

        screenHandler.sendContentUpdates()
    }
}
