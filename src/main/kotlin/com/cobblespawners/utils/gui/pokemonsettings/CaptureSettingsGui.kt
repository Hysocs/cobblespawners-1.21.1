package com.cobblespawners.utils.gui.pokemonsettings

import com.cobblespawners.utils.CobbleSpawnersConfig
import com.cobblespawners.utils.PokemonSpawnEntry
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

object CaptureSettingsGui {

    private object Slots {
        const val CATCHABLE_TOGGLE = 21
        const val RESTRICT_CAPTURE = 23
        const val BACK_BUTTON = 49
    }

    private object Textures {
        const val CATCHABLE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTNlNjg3NjhmNGZhYjgxYzk0ZGY3MzVlMjA1YzNiNDVlYzQ1YTY3YjU1OGYzODg0NDc5YTYyZGQzZjRiZGJmOCJ9fX0="
        const val RESTRICT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWQ0MDhjNTY5OGYyZDdhOGExNDE1ZWY5NTkyYWViNGJmNjJjOWFlN2NjZjE4ODQ5NzUzMGJmM2M4Yjk2NDhlNSJ9fX0="
        const val BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
    }

    fun openCaptureSettingsGui(player: ServerPlayerEntity, spawnerPos: BlockPos, pokemonName: String, formName: String?, additionalAspects: Set<String>) {
        val entry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", additionalAspects)
        if (entry == null) {
            player.sendMessage(Text.literal("Error: Could not find the specified Pokémon in this spawner."), false)
            return
        }
        spawnerGuisOpen[spawnerPos] = player

        val aspectsDisplay = if (additionalAspects.isNotEmpty()) ", ${additionalAspects.joinToString(", ")}" else ""
        val guiTitle = "Edit Capture: ${entry.pokemonName} (${entry.formName ?: "Standard"}$aspectsDisplay)"

        CustomGui.openGui(
            player,
            guiTitle,
            generateCaptureSettingsLayout(entry),
            { context -> handleInteraction(context, player, spawnerPos, pokemonName, formName, additionalAspects) },
            { spawnerGuisOpen.remove(spawnerPos) }
        )
    }

    private fun handleInteraction(context: InteractionContext, player: ServerPlayerEntity, spawnerPos: BlockPos, pokemonName: String, formName: String?, additionalAspects: Set<String>) {
        var needsRefresh = true
        when (context.slotIndex) {
            Slots.CATCHABLE_TOGGLE -> toggleIsCatchable(spawnerPos, pokemonName, formName, additionalAspects)
            Slots.RESTRICT_CAPTURE -> {
                if (context.clickType == ClickType.RIGHT) {
                    needsRefresh = false
                    CaptureBallSettingsGui.openCaptureBallSettingsGui(player, spawnerPos, pokemonName, formName, additionalAspects)
                } else {
                    toggleRestrictCapture(spawnerPos, pokemonName, formName, additionalAspects)
                }
            }
            Slots.BACK_BUTTON -> {
                needsRefresh = false
                CustomGui.closeGui(player)
                PokemonEditSubGui.openPokemonEditSubGui(player, spawnerPos, pokemonName, formName, additionalAspects)
            }
            else -> needsRefresh = false
        }

        if (needsRefresh) {
            refreshGui(player, spawnerPos, pokemonName, formName, additionalAspects)
        }
    }

    private fun generateCaptureSettingsLayout(entry: PokemonSpawnEntry): List<ItemStack> {
        val layout = MutableList(54) { createFillerPane() }
        val settings = entry.captureSettings

        layout[Slots.CATCHABLE_TOGGLE] = createToggleButton(
            title = "Catchable",
            color = Formatting.GREEN,
            enabled = settings.isCatchable,
            lore = listOf("If OFF, this Pokémon cannot be caught."),
            texture = Textures.CATCHABLE
        )
        layout[Slots.RESTRICT_CAPTURE] = createRestrictCaptureButton(settings.restrictCaptureToLimitedBalls)
        layout[Slots.BACK_BUTTON] = createBackButton()

        return layout
    }

    private fun toggleIsCatchable(spawnerPos: BlockPos, pokemonName: String, formName: String?, additionalAspects: Set<String>) {
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", additionalAspects) { entry ->
            entry.captureSettings.isCatchable = !entry.captureSettings.isCatchable
        }
        CobbleSpawnersConfig.saveSpawnerData()
    }

    private fun toggleRestrictCapture(spawnerPos: BlockPos, pokemonName: String, formName: String?, additionalAspects: Set<String>) {
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", additionalAspects) { entry ->
            entry.captureSettings.restrictCaptureToLimitedBalls = !entry.captureSettings.restrictCaptureToLimitedBalls
        }
        CobbleSpawnersConfig.saveSpawnerData()
    }

    private fun createToggleButton(title: String, color: Formatting, enabled: Boolean, lore: List<String>, texture: String): ItemStack {
        val status = if (enabled) "ON" else "OFF"
        val statusColor = if (enabled) Formatting.GREEN else Formatting.RED
        val fullLore = mutableListOf(
            Text.literal("§7${lore.firstOrNull() ?: ""}"),
            Text.literal(""),
            Text.literal("Status: ").append(Text.literal(status).formatted(statusColor)),
            Text.literal(""),
            Text.literal("§eLeft-click to toggle")
        )
        return createButton(Text.literal(title).formatted(color), fullLore, texture)
    }

    private fun createRestrictCaptureButton(enabled: Boolean): ItemStack {
        val status = if (enabled) "ON" else "OFF"
        val statusColor = if (enabled) Formatting.GREEN else Formatting.RED
        val lore = listOf(
            Text.literal("§7If ON, only specific Poké Balls can be used."),
            Text.literal(""),
            Text.literal("Status: ").append(Text.literal(status).formatted(statusColor)),
            Text.literal(""),
            Text.literal("§eLeft-click to toggle"),
            Text.literal("§eRight-click to edit ball list")
        )
        return createButton(
            Text.literal("Restrict Capture Balls").formatted(Formatting.RED),
            lore,
            Textures.RESTRICT
        )
    }

    private fun createBackButton(): ItemStack {
        return createButton(
            title = Text.literal("Back").formatted(Formatting.WHITE),
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
        val layout = generateCaptureSettingsLayout(entry)
        layout.forEachIndexed { index, itemStack ->
            if (index < screenHandler.slots.size) {
                screenHandler.slots[index].stack = itemStack
            }
        }
        screenHandler.sendContentUpdates()
    }
}