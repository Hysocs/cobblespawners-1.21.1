package com.cobblespawners.utils.gui

import com.cobblespawners.utils.CobbleSpawnersConfig
import com.cobblespawners.utils.GlobalConfig
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.InteractionContext
import com.everlastingutils.gui.setCustomName
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos
import java.util.concurrent.ConcurrentHashMap

object GlobalSettingsGui {

    private val playerSpawnerMap = ConcurrentHashMap<ServerPlayerEntity, BlockPos>()

    private object Slots {
        const val DEBUG_MODE = 11
        const val CULL_ON_STOP = 13
        const val SHOW_UNIMPLEMENTED = 15
        const val SHOW_FORMS = 30
        const val SHOW_ASPECTS = 32
        const val BACK_BUTTON = 49
    }

    private object Textures {
        const val DEBUG_MODE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWI2Y2VlOGZkYTdlZjBiM2FlMGViMDU3OWQ1Njc2Y2UzNmFmN2VmYzU3NGQ4ODcyOGYzODk0ZjZiMTY2NTM4In19fQ=="
        const val CULL_ON_STOP = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzMzZGJmYjdkZmYyNTY0ZjZiMTc2OGQ3MmEyY2I0M2E4ZDY2YWMzZWFmYzI4MmRkOWJhZDIyN2EzYTAzMDg0In19fQ=="
        const val SHOW_UNIMPLEMENTED = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWRlOWZjYzA1YTZmYTM3YTI4MGMzMjMwZTc2OWQyM2EwZDMwMDJjMDQ1MjM0MzU2YmQ1MWY0NzRhMjcwZTQzOSJ9fX0="
        const val SHOW_FORMS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmFmZDU2ZWE5OThjMDhjNDcyY2IyM2VjY2RlMjE3OTk3OGE0ZTRhNGM5ZTRkM2RmOGVlYWMxYmYyZGU2MzhhYSJ9fX0="
        const val SHOW_ASPECTS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDk0ZmE0MDE5MzhmMGNkNjQzOWQ4MWE0M2Q1YTY0MjQxZjE5OWQwMmViYzdhMTk5Y2E4MDkwMzczYTY2YmNhOSJ9fX0="
        const val BACK_BUTTON = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
    }

    fun openGlobalSettingsGui(player: ServerPlayerEntity, spawnerPos: BlockPos? = null) {
        spawnerPos?.let { playerSpawnerMap[player] = it }

        val layout = generateGlobalSettingsLayout(CobbleSpawnersConfig.config.globalConfig)
        val onInteract: (InteractionContext) -> Unit = { context -> handleInteraction(context, player) }
        val onClose: (Inventory) -> Unit = { playerSpawnerMap.remove(player) }

        CustomGui.openGui(player, "Edit Global Settings", layout, onInteract, onClose)
    }

    private fun handleInteraction(context: InteractionContext, player: ServerPlayerEntity) {
        val globalConfig = CobbleSpawnersConfig.config.globalConfig
        var changed = true

        when (context.slotIndex) {
            Slots.DEBUG_MODE -> globalConfig.debugEnabled = !globalConfig.debugEnabled
            Slots.CULL_ON_STOP -> globalConfig.cullSpawnerPokemonOnServerStop = !globalConfig.cullSpawnerPokemonOnServerStop
            Slots.SHOW_UNIMPLEMENTED -> globalConfig.showUnimplementedPokemonInGui = !globalConfig.showUnimplementedPokemonInGui
            Slots.SHOW_FORMS -> globalConfig.showFormsInGui = !globalConfig.showFormsInGui
            Slots.SHOW_ASPECTS -> globalConfig.showAspectsInGui = !globalConfig.showAspectsInGui
            Slots.BACK_BUTTON -> {
                val previousSpawnerPos = playerSpawnerMap.remove(player)
                if (previousSpawnerPos != null) {
                    SpawnerPokemonSelectionGui.openSpawnerGui(player, previousSpawnerPos)
                } else {
                    CustomGui.closeGui(player)
                }
                changed = false
            }
            else -> changed = false
        }

        if (changed) {
            CobbleSpawnersConfig.saveConfigBlocking()
            refreshGui(player)
        }
    }

    private fun generateGlobalSettingsLayout(globalConfig: GlobalConfig): List<ItemStack> {
        val layout = MutableList(54) { ItemStack.EMPTY }

        layout[Slots.DEBUG_MODE] = createToggleButton(
            title = "Debug Mode",
            color = Formatting.GOLD,
            enabled = globalConfig.debugEnabled,
            lore = listOf("Enables detailed logging for troubleshooting."),
            texture = Textures.DEBUG_MODE
        )
        layout[Slots.CULL_ON_STOP] = createToggleButton(
            title = "Cull Spawner Pokémon on Stop",
            color = Formatting.RED,
            enabled = globalConfig.cullSpawnerPokemonOnServerStop,
            lore = listOf("If ON, removes all Pokémon from spawners on server shutdown."),
            texture = Textures.CULL_ON_STOP
        )
        layout[Slots.SHOW_UNIMPLEMENTED] = createToggleButton(
            title = "Show Unimplemented Pokémon",
            color = Formatting.BLUE,
            enabled = globalConfig.showUnimplementedPokemonInGui,
            lore = listOf("If ON, Pokémon not yet in the mod will appear in the GUI."),
            texture = Textures.SHOW_UNIMPLEMENTED
        )
        layout[Slots.SHOW_FORMS] = createToggleButton(
            title = "Show Forms in GUI",
            color = Formatting.GREEN,
            enabled = globalConfig.showFormsInGui,
            lore = listOf("If ON, different Pokémon forms (e.g., Alolan) will be shown."),
            texture = Textures.SHOW_FORMS
        )
        layout[Slots.SHOW_ASPECTS] = createToggleButton(
            title = "Show Aspects in GUI",
            color = Formatting.DARK_PURPLE,
            enabled = globalConfig.showAspectsInGui,
            lore = listOf("If ON, aspects like 'shiny' or gender differences will be listed."),
            texture = Textures.SHOW_ASPECTS
        )
        layout[Slots.BACK_BUTTON] = CustomGui.createPlayerHeadButton(
            "BackButton",
            Text.literal("Back").formatted(Formatting.WHITE),
            listOf(Text.literal("Return to the previous menu.").formatted(Formatting.GRAY)),
            Textures.BACK_BUTTON
        )

        for (i in layout.indices) {
            if (layout[i] == ItemStack.EMPTY) {
                layout[i] = ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
                    setCustomName(Text.literal(" "))
                }
            }
        }
        return layout
    }

    private fun createToggleButton(title: String, color: Formatting, enabled: Boolean, lore: List<String>, texture: String): ItemStack {
        val statusText = if (enabled) "ON" else "OFF"
        val statusColor = if (enabled) Formatting.GREEN else Formatting.RED

        val fullTitle = Text.literal(title).formatted(color)
        val fullLore = mutableListOf(
            Text.literal("§7${lore.firstOrNull() ?: ""}"),
            Text.literal(""),
            Text.literal("Status: ").append(Text.literal(statusText).formatted(statusColor)),
            Text.literal(""),
            Text.literal("§eClick to toggle")
        )

        return CustomGui.createPlayerHeadButton(
            textureName = title.filter { !it.isWhitespace() },
            title = fullTitle,
            lore = fullLore,
            textureValue = texture
        )
    }

    private fun refreshGui(player: ServerPlayerEntity) {
        val screenHandler = player.currentScreenHandler ?: return
        val layout = generateGlobalSettingsLayout(CobbleSpawnersConfig.config.globalConfig)
        layout.forEachIndexed { index, itemStack ->
            if (index < screenHandler.slots.size) {
                screenHandler.slots[index].stack = itemStack
            }
        }
        screenHandler.sendContentUpdates()
    }
}