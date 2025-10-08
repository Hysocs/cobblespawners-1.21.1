package com.cobblespawners.utils.gui.pokemonsettings

import com.cobblespawners.utils.CobbleSpawnersConfig
import com.cobblespawners.utils.PokemonSpawnEntry
import com.cobblespawners.utils.SpawnChanceType
import com.cobblespawners.utils.gui.PokemonEditSubGui
import com.cobblespawners.utils.gui.SpawnerPokemonSelectionGui.spawnerGuisOpen
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.InteractionContext
import com.everlastingutils.gui.setCustomName
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.ClickType
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos

object SpawnSettingsGui {

    private data class ChanceButton(val slot: Int, val leftDelta: Double, val rightDelta: Double)
    private data class LevelButton(val slot: Int, val isMin: Boolean, val delta: Int)

    private object Slots {
        const val SPAWN_CHANCE_DISPLAY = 13
        const val MIN_LEVEL_DISPLAY = 20
        const val MAX_LEVEL_DISPLAY = 24
        const val SPAWN_CHANCE_TYPE_TOGGLE = 31
        const val BACK_BUTTON = 49

        val CHANCE_BUTTONS = listOf(
            ChanceButton(10, -0.01, -0.05),
            ChanceButton(11, -0.1, -0.5),
            ChanceButton(12, -1.0, -5.0),
            ChanceButton(14, 0.01, 0.05),
            ChanceButton(15, 0.1, 0.5),
            ChanceButton(16, 1.0, 5.0)
        ).associateBy { it.slot }

        val LEVEL_BUTTONS = listOf(
            LevelButton(19, isMin = true, delta = -1),
            LevelButton(21, isMin = true, delta = 1),
            LevelButton(23, isMin = false, delta = -1),
            LevelButton(25, isMin = false, delta = 1)
        ).associateBy { it.slot }
    }

    private object Textures {
        const val DISPLAY = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjZkZDg5MTlmZThmNzUwN2I0NjQxYmYzYWE3MmIwNTZlMDg1N2NjMjAyYThlNWViNjZjOWMyMWFhNzNjMzg3NiJ9fX0="
        const val CHANCE_ADJUST = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjZmZjBhYTQ4NTQ0N2JiOGRjZjQ1OTkyM2I0OWY5MWM0M2IwNDBiZDU2ZTYzMTVkYWE4YjZmODNiNGMzZWI1MSJ9fX0="
        const val LEVEL_ADJUST = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2E4Yjg3ZTQ2Y2ZlOGEyZGMzNTI1YzFjNjdkOGE2OWEyNWZkMGE3ZjcyNGE2ZmE5MTFhZDc0YWRiNmQ4MmMyIn19fQ=="
        const val TOGGLE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzdkZmE4ZjBjYzkxYjVkODE0YTE4NWM1ZTgwYjVkYzVjYWMxOTgxMTNiMWU5ZWQ4NzM4NmM5OTgzMzk5OWYifX19"
        const val BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
    }

    fun openSpawnShinyEditorGui(player: ServerPlayerEntity, spawnerPos: BlockPos, pokemonName: String, formName: String?, additionalAspects: Set<String>) {
        val entry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", additionalAspects)
        if (entry == null) {
            player.sendMessage(Text.literal("Could not find the specified Pokémon in the spawner."), false)
            return
        }

        spawnerGuisOpen[spawnerPos] = player

        val aspectsDisplay = if (entry.aspects.isNotEmpty()) ", ${entry.aspects.joinToString(", ")}" else ""
        val guiTitle = "Edit: ${entry.pokemonName} (${entry.formName ?: "Standard"}$aspectsDisplay)"

        CustomGui.openGui(
            player,
            guiTitle,
            generateSpawnEditorLayout(entry),
            { context -> handleInteraction(context, player, spawnerPos, pokemonName, formName, additionalAspects) },
            { spawnerGuisOpen.remove(spawnerPos) }
        )
    }

    private fun handleInteraction(context: InteractionContext, player: ServerPlayerEntity, spawnerPos: BlockPos, pokemonName: String, formName: String?, additionalAspects: Set<String>) {
        when (val slot = context.slotIndex) {
            in Slots.CHANCE_BUTTONS -> {
                val button = Slots.CHANCE_BUTTONS[slot]!!
                val delta = if (context.clickType == ClickType.LEFT) button.leftDelta else button.rightDelta
                adjustSpawnChance(player, spawnerPos, pokemonName, formName, additionalAspects, delta)
            }
            in Slots.LEVEL_BUTTONS -> {
                val button = Slots.LEVEL_BUTTONS[slot]!!
                val delta = if (context.clickType == ClickType.LEFT) button.delta else button.delta * 5
                adjustLevel(player, spawnerPos, pokemonName, formName, additionalAspects, button.isMin, delta)
            }
            Slots.SPAWN_CHANCE_TYPE_TOGGLE -> toggleSpawnChanceType(player, spawnerPos, pokemonName, formName, additionalAspects)
            Slots.BACK_BUTTON -> {
                CustomGui.closeGui(player)
                PokemonEditSubGui.openPokemonEditSubGui(player, spawnerPos, pokemonName, formName, additionalAspects)
            }
        }
    }

    private fun generateSpawnEditorLayout(entry: PokemonSpawnEntry): List<ItemStack> {
        val layout = MutableList(54) { createFillerPane() }

        Slots.CHANCE_BUTTONS.values.forEach { layout[it.slot] = createChanceButton(it) }
        Slots.LEVEL_BUTTONS.values.forEach { layout[it.slot] = createLevelButton(it) }

        layout[Slots.SPAWN_CHANCE_DISPLAY] = createDisplayItem("Spawn Chance", entry.spawnChance, "%")
        layout[Slots.MIN_LEVEL_DISPLAY] = createDisplayItem("Min Level", entry.minLevel.toDouble())
        layout[Slots.MAX_LEVEL_DISPLAY] = createDisplayItem("Max Level", entry.maxLevel.toDouble())
        layout[Slots.SPAWN_CHANCE_TYPE_TOGGLE] = createToggleSpawnChanceTypeHead(entry)
        layout[Slots.BACK_BUTTON] = createBackButton()

        return layout
    }

    private fun adjustSpawnChance(player: ServerPlayerEntity, spawnerPos: BlockPos, pokemonName: String, formName: String?, aspects: Set<String>, delta: Double) {
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", aspects) { entry ->
            entry.spawnChance = (entry.spawnChance + delta).coerceIn(0.0, 100.0)
        }
        refreshGui(player, spawnerPos, pokemonName, formName, aspects)
    }

    private fun adjustLevel(player: ServerPlayerEntity, spawnerPos: BlockPos, pokemonName: String, formName: String?, aspects: Set<String>, isMinLevel: Boolean, delta: Int) {
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", aspects) { entry ->
            if (isMinLevel) {
                entry.minLevel = (entry.minLevel + delta).coerceIn(1, entry.maxLevel)
            } else {
                entry.maxLevel = (entry.maxLevel + delta).coerceIn(entry.minLevel, 100)
            }
        }
        refreshGui(player, spawnerPos, pokemonName, formName, aspects)
    }

    private fun toggleSpawnChanceType(player: ServerPlayerEntity, spawnerPos: BlockPos, pokemonName: String, formName: String?, aspects: Set<String>) {
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", aspects) { entry ->
            entry.spawnChanceType = if (entry.spawnChanceType == SpawnChanceType.COMPETITIVE) SpawnChanceType.INDEPENDENT else SpawnChanceType.COMPETITIVE
        }
        refreshGui(player, spawnerPos, pokemonName, formName, aspects)
    }

    private fun refreshGui(player: ServerPlayerEntity, spawnerPos: BlockPos, pokemonName: String, formName: String?, aspects: Set<String>) {
        val entry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", aspects) ?: return
        val layout = generateSpawnEditorLayout(entry)
        val screenHandler = player.currentScreenHandler
        layout.forEachIndexed { index, itemStack ->
            if (index < screenHandler.slots.size) {
                screenHandler.slots[index].stack = itemStack
            }
        }
        screenHandler.sendContentUpdates()
    }

    private fun createDisplayItem(label: String, value: Double, unit: String = ""): ItemStack {
        val displayValue = if (unit == "%") "%.2f".format(value) else "${value.toInt()}"
        return CustomGui.createPlayerHeadButton(
            "Display${label.replace(" ", "")}",
            Text.literal(label).formatted(Formatting.AQUA),
            listOf(Text.literal("§f$displayValue$unit")),
            Textures.DISPLAY
        )
    }

    private fun createChanceButton(button: ChanceButton): ItemStack {
        val signLeft = if (button.leftDelta > 0) "+" else ""
        val signRight = if (button.rightDelta > 0) "+" else ""
        val title = if (button.leftDelta > 0) "Increase Spawn Chance" else "Decrease Spawn Chance"
        return CustomGui.createPlayerHeadButton(
            "ChanceButton${button.slot}",
            Text.literal(title).formatted(Formatting.GREEN),
            listOf(
                Text.literal("§eLeft-click: $signLeft${"%.2f".format(button.leftDelta)}%"),
                Text.literal("§eRight-click: $signRight${"%.2f".format(button.rightDelta)}%")
            ),
            Textures.CHANCE_ADJUST
        )
    }

    private fun createLevelButton(button: LevelButton): ItemStack {
        val levelType = if (button.isMin) "Min" else "Max"
        val action = if (button.delta > 0) "Increase" else "Decrease"
        val title = "$action $levelType Level"
        return CustomGui.createPlayerHeadButton(
            "LevelButton${button.slot}",
            Text.literal(title).formatted(Formatting.GOLD),
            listOf(
                Text.literal("§eLeft-click: Adjust by ${button.delta}"),
                Text.literal("§eRight-click: Adjust by ${button.delta * 5}")
            ),
            Textures.LEVEL_ADJUST
        )
    }

    private fun createToggleSpawnChanceTypeHead(entry: PokemonSpawnEntry): ItemStack {
        val typeName = entry.spawnChanceType.name.lowercase().replaceFirstChar { it.uppercase() }
        val loreText = when (entry.spawnChanceType) {
            SpawnChanceType.COMPETITIVE -> "Relative to other Pokémon in the spawner."
            SpawnChanceType.INDEPENDENT -> "Absolute chance, ignores other entries."
        }
        return CustomGui.createPlayerHeadButton(
            "ToggleSpawnChanceType",
            Text.literal("Spawn Chance Type").formatted(Formatting.LIGHT_PURPLE),
            listOf(
                Text.literal("§aCurrent: §f$typeName"),
                Text.literal("§7$loreText"),
                Text.literal(""),
                Text.literal("§eClick to toggle")
            ),
            Textures.TOGGLE
        )
    }

    private fun createBackButton(): ItemStack {
        return CustomGui.createPlayerHeadButton(
            "BackButton",
            Text.literal("Back").formatted(Formatting.RED),
            listOf(Text.literal("§7Return to the previous menu.")),
            Textures.BACK
        )
    }

    private fun createFillerPane(): ItemStack {
        return ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
            setCustomName(Text.literal(" "))
        }
    }
}