package com.cobblespawners.utils.gui.pokemonsettings

import com.cobblespawners.utils.CobbleSpawnersConfig
import com.cobblespawners.utils.PokemonSpawnEntry
import com.cobblespawners.utils.SizeSettings
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
import kotlin.math.roundToInt

object SizeSettingsGui {

    private object Slots {
        const val MIN_SIZE_DECREASE_LARGE = 11
        const val MIN_SIZE_DECREASE_SMALL = 12
        const val MIN_SIZE_DISPLAY = 13
        const val MIN_SIZE_INCREASE_SMALL = 14
        const val MIN_SIZE_INCREASE_LARGE = 15
        const val MAX_SIZE_DECREASE_LARGE = 20
        const val MAX_SIZE_DECREASE_SMALL = 21
        const val MAX_SIZE_DISPLAY = 22
        const val MAX_SIZE_INCREASE_SMALL = 23
        const val MAX_SIZE_INCREASE_LARGE = 24
        const val TOGGLE_CUSTOM_SIZE = 40
        const val BACK_BUTTON = 49
    }

    private object Textures {
        const val INCREASE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTU3YTViZGY0MmYxNTIxNzhkMTU0YmIyMjM3ZDlmZDM1NzcyYTdmMzJiY2ZkMzNiZWViOGVkYzQ4MjBiYSJ9fX0="
        const val DECREASE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTZhMDExZTYyNmI3MWNlYWQ5ODQxOTM1MTFlODJlNjVjMTM1OTU2NWYwYTJmY2QxMTg0ODcyZjg5ZDkwOGM2NSJ9fX0="
        const val DISPLAY = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjZkZDg5MTlmZThmNzUwN2I0NjQxYmYzYWE3MmIwNTZlMDg1N2NjMjAyYThlNWViNjZjOWMyMWFhNzNjMzg3NiJ9fX0="
        const val TOGGLE_ON = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTI1YjhlZWQ1YzU2NWJkNDQwZWM0N2M3OWMyMGQ1Y2YzNzAxNjJiMWQ5YjVkZDMxMDBlZDYyODNmZTAxZDZlIn19fQ=="
        const val TOGGLE_OFF = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjNmNzliMjA3ZDYxZTEyMjUyM2I4M2Q2MTUwOGQ5OWNmYTA3OWQ0NWJmMjNkZjJhOWE1MTI3ZjkwNzFkNGIwMCJ9fX0="
        const val BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
    }

    private data class SizeAdjustment(val leftClickDelta: Float, val rightClickDelta: Float)
    private val adjustments = mapOf(
        Slots.MIN_SIZE_DECREASE_LARGE to SizeAdjustment(-1.0f, -5.0f),
        Slots.MIN_SIZE_DECREASE_SMALL to SizeAdjustment(-0.1f, -0.5f),
        Slots.MIN_SIZE_INCREASE_SMALL to SizeAdjustment(0.1f, 0.5f),
        Slots.MIN_SIZE_INCREASE_LARGE to SizeAdjustment(1.0f, 5.0f),
        Slots.MAX_SIZE_DECREASE_LARGE to SizeAdjustment(-1.0f, -5.0f),
        Slots.MAX_SIZE_DECREASE_SMALL to SizeAdjustment(-0.1f, -0.5f),
        Slots.MAX_SIZE_INCREASE_SMALL to SizeAdjustment(0.1f, 0.5f),
        Slots.MAX_SIZE_INCREASE_LARGE to SizeAdjustment(1.0f, 5.0f)
    )

    fun openSizeEditorGui(player: ServerPlayerEntity, spawnerPos: BlockPos, pokemonName: String, formName: String?, additionalAspects: Set<String>) {
        val entry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", additionalAspects)
        if (entry == null) {
            player.sendMessage(Text.literal("Error: Could not find the specified Pokémon in this spawner."), false)
            return
        }
        spawnerGuisOpen[spawnerPos] = player

        val aspectsDisplay = if (additionalAspects.isNotEmpty()) ", ${additionalAspects.joinToString(", ")}" else ""
        val guiTitle = "Edit Size: ${entry.pokemonName} (${entry.formName ?: "Standard"}$aspectsDisplay)"

        CustomGui.openGui(
            player,
            guiTitle,
            generateSizeEditorLayout(entry),
            { context -> handleInteraction(context, player, spawnerPos, pokemonName, formName, additionalAspects) },
            { spawnerGuisOpen.remove(spawnerPos) }
        )
    }

    private fun handleInteraction(context: InteractionContext, player: ServerPlayerEntity, spawnerPos: BlockPos, pokemonName: String, formName: String?, additionalAspects: Set<String>) {
        when (val slot = context.slotIndex) {
            Slots.TOGGLE_CUSTOM_SIZE -> toggleAllowCustomSize(spawnerPos, pokemonName, formName, additionalAspects)
            Slots.BACK_BUTTON -> {
                CustomGui.closeGui(player)
                PokemonEditSubGui.openPokemonEditSubGui(player, spawnerPos, pokemonName, formName, additionalAspects)
                return
            }
            in adjustments -> {
                val adjustment = adjustments[slot] ?: return
                val delta = if (context.clickType == ClickType.LEFT) adjustment.leftClickDelta else adjustment.rightClickDelta
                val isMinSize = slot < Slots.MAX_SIZE_DECREASE_LARGE
                adjustSize(spawnerPos, pokemonName, formName, additionalAspects, isMinSize, delta)
            }
            else -> return
        }
        refreshGui(player, spawnerPos, pokemonName, formName, additionalAspects)
    }

    private fun generateSizeEditorLayout(entry: PokemonSpawnEntry): List<ItemStack> {
        val layout = MutableList(54) { createFillerPane() }
        val sizeSettings = entry.sizeSettings

        adjustments.forEach { (slot, adjustment) ->
            val isMin = slot < Slots.MAX_SIZE_DECREASE_LARGE
            layout[slot] = createSizeAdjustmentButton(isMin, sizeSettings, adjustment)
        }

        layout[Slots.MIN_SIZE_DISPLAY] = createDisplayButton("Min Size", sizeSettings.minSize)
        layout[Slots.MAX_SIZE_DISPLAY] = createDisplayButton("Max Size", sizeSettings.maxSize)
        layout[Slots.TOGGLE_CUSTOM_SIZE] = createToggleButton(sizeSettings.allowCustomSize)
        layout[Slots.BACK_BUTTON] = createBackButton()

        return layout
    }

    private fun adjustSize(spawnerPos: BlockPos, pokemonName: String, formName: String?, additionalAspects: Set<String>, isMinSize: Boolean, delta: Float) {
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", additionalAspects) { entry ->
            val size = entry.sizeSettings
            val newValue = (if (isMinSize) size.minSize else size.maxSize) + delta

            val clampedValue = if (isMinSize) {
                newValue.coerceIn(0.1f, size.maxSize)
            } else {
                newValue.coerceIn(size.minSize, 50.0f)
            }

            val finalValue = (clampedValue * 100).roundToInt() / 100f
            if (isMinSize) size.minSize = finalValue else size.maxSize = finalValue
        }
        CobbleSpawnersConfig.saveSpawnerData()
    }

    private fun toggleAllowCustomSize(spawnerPos: BlockPos, pokemonName: String, formName: String?, additionalAspects: Set<String>) {
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", additionalAspects) { entry ->
            entry.sizeSettings.allowCustomSize = !entry.sizeSettings.allowCustomSize
        }
        CobbleSpawnersConfig.saveSpawnerData()
    }

    private fun createSizeAdjustmentButton(isMin: Boolean, settings: SizeSettings, adjustment: SizeAdjustment): ItemStack {
        val type = if (isMin) "Min" else "Max"
        val color = if (isMin) Formatting.GREEN else Formatting.BLUE
        val isIncrease = adjustment.leftClickDelta > 0
        val action = if (isIncrease) "Increase" else "Decrease"
        val texture = if (isIncrease) Textures.INCREASE else Textures.DECREASE

        return createButton(
            title = Text.literal("$action $type Size").formatted(color),
            lore = listOf(
                Text.literal("§7Current: §f%.2f".format(if (isMin) settings.minSize else settings.maxSize)),
                Text.literal(""),
                Text.literal("§eLeft-click: ${adjustment.leftClickDelta.format()}"),
                Text.literal("§eRight-click: ${adjustment.rightClickDelta.format()}")
            ),
            texture = texture
        )
    }

    private fun createDisplayButton(label: String, value: Float): ItemStack {
        return createButton(
            title = Text.literal(label).formatted(Formatting.WHITE),
            lore = listOf(Text.literal("§aCurrent Value: §f%.2f".format(value))),
            texture = Textures.DISPLAY
        )
    }

    private fun createToggleButton(enabled: Boolean): ItemStack {
        val status = if (enabled) "ON" else "OFF"
        val color = if (enabled) Formatting.GREEN else Formatting.RED
        return createButton(
            title = Text.literal("Allow Custom Sizes: $status").formatted(color),
            lore = listOf(
                Text.literal("§7If ON, size values will be applied."),
                Text.literal("§eClick to toggle")
            ),
            texture = if (enabled) Textures.TOGGLE_ON else Textures.TOGGLE_OFF
        )
    }

    private fun createBackButton(): ItemStack {
        return createButton(
            title = Text.literal("Back").formatted(Formatting.RED),
            lore = listOf(Text.literal("§7Return to the previous menu.")),
            texture = Textures.BACK
        )
    }

    private fun createButton(title: Text, lore: List<Text>, texture: String): ItemStack {
        return CustomGui.createPlayerHeadButton(
            title.string.filter { !it.isWhitespace() },
            title,
            lore,
            texture
        )
    }

    private fun createFillerPane(): ItemStack {
        return ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
            setCustomName(Text.literal(" "))
        }
    }

    private fun refreshGui(player: ServerPlayerEntity, spawnerPos: BlockPos, pokemonName: String, formName: String?, additionalAspects: Set<String>) {
        val entry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", additionalAspects) ?: return
        val screenHandler = player.currentScreenHandler ?: return
        val layout = generateSizeEditorLayout(entry)
        layout.forEachIndexed { index, itemStack ->
            if (index < screenHandler.slots.size) {
                screenHandler.slots[index].stack = itemStack
            }
        }
        screenHandler.sendContentUpdates()
    }

    private fun Float.format(): String = "%.1f".format(this)
}