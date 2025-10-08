package com.cobblespawners.utils.gui.pokemonsettings

import com.cobblespawners.utils.CobbleSpawnersConfig
import com.cobblespawners.utils.EVSettings
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

object EVSettingsGui {

    private object Slots {
        const val HP = 0
        const val ATTACK = 1
        const val DEFENSE = 2
        const val SPECIAL_ATTACK = 3
        const val SPECIAL_DEFENSE = 4
        const val SPEED = 5
        const val TOGGLE_CUSTOM_EVS = 31
        const val BACK_BUTTON = 49
    }

    private object Textures {
        const val HP = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWRiMDJiMDQwYzM3MDE1ODkyYTNhNDNkM2IxYmZkYjJlMDFhMDJlZGNjMmY1YjgyMjUwZGNlYmYzZmY0ZjAxZSJ9fX0="
        const val ATTACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTFkMzgzNDAxZjc3YmVmZmNiOTk4YzJjZjc5YjdhZmVlMjNmMThjNDFkOGE1NmFmZmVkNzliYjU2ZTIyNjdhMyJ9fX0="
        const val DEFENSE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjU1NTFmMzRjNDVmYjE4MTFlNGNjMmZhOGVjMzcxZTQ1YmEwOTc3ZTFkMTUyMTEyMGYwZjU3NTYwZjczZjU5MCJ9fX0="
        const val SPECIAL_ATTACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzhmZTcwYjc3MzFhYzJmNWIzZDAyNmViMWFiNmE5MjNhOGM1OGI0YmY2ZDNhY2JlMTQ1YjEwYzM2ZTZjZjg5OCJ9fX0="
        const val SPECIAL_DEFENSE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2VhMmI1MTE4MWFlMTlkMzMzMTNjNmY0YThlOTA2NjU3MDU1NzM2MzliM2RmNzA5NTE0YmQ5NzA5ODUzMzBkZCJ9fX0="
        const val SPEED = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDcxMDEzODQxNjUyODg4OTgxNTU0OGI0NjIzZDI4ZDg2YmJiYWU1NjE5ZDY5Y2Q5ZGJjNWFkNmI0Mzc0NCJ9fX0="
        const val TOGGLE_ON = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTI1YjhlZWQ1YzU2NWJkNDQwZWM0N2M3OWMyMGQ1Y2YzNzAxNjJiMWQ5YjVkZDMxMDBlZDYyODNmZTAxZDZlIn19fQ=="
        const val TOGGLE_OFF = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjNmNzliMjA3ZDYxZTEyMjUyM2I4M2Q2MTUwOGQ5OWNmYTA3OWQ0NWJmMjNkZjJhOWE1MTI3ZjkwNzFkNGIwMCJ9fX0="
        const val BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
    }

    private data class StatInfo(val name: String, val texture: String, val getter: (EVSettings) -> Int, val setter: (EVSettings, Int) -> Unit)
    private val stats = mapOf(
        Slots.HP to StatInfo("HP", Textures.HP, { it.evHp }, { evs, v -> evs.evHp = v }),
        Slots.ATTACK to StatInfo("Attack", Textures.ATTACK, { it.evAttack }, { evs, v -> evs.evAttack = v }),
        Slots.DEFENSE to StatInfo("Defense", Textures.DEFENSE, { it.evDefense }, { evs, v -> evs.evDefense = v }),
        Slots.SPECIAL_ATTACK to StatInfo("Special Attack", Textures.SPECIAL_ATTACK, { it.evSpecialAttack }, { evs, v -> evs.evSpecialAttack = v }),
        Slots.SPECIAL_DEFENSE to StatInfo("Special Defense", Textures.SPECIAL_DEFENSE, { it.evSpecialDefense }, { evs, v -> evs.evSpecialDefense = v }),
        Slots.SPEED to StatInfo("Speed", Textures.SPEED, { it.evSpeed }, { evs, v -> evs.evSpeed = v })
    )

    fun openEVEditorGui(player: ServerPlayerEntity, spawnerPos: BlockPos, pokemonName: String, formName: String?, additionalAspects: Set<String>) {
        val entry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", additionalAspects)
        if (entry == null) {
            player.sendMessage(Text.literal("Error: Could not find the specified Pokémon in this spawner."), false)
            return
        }
        spawnerGuisOpen[spawnerPos] = player

        val aspectsDisplay = if (additionalAspects.isNotEmpty()) ", ${additionalAspects.joinToString(", ")}" else ""
        val guiTitle = "Edit EVs: ${entry.pokemonName} (${entry.formName ?: "Standard"}$aspectsDisplay)"

        CustomGui.openGui(
            player,
            guiTitle,
            generateEVEditorLayout(entry),
            { context -> handleInteraction(context, player, spawnerPos, pokemonName, formName, additionalAspects) },
            { spawnerGuisOpen.remove(spawnerPos) }
        )
    }

    private fun handleInteraction(context: InteractionContext, player: ServerPlayerEntity, spawnerPos: BlockPos, pokemonName: String, formName: String?, additionalAspects: Set<String>) {
        var needsRefresh = true
        when (val slot = context.slotIndex) {
            Slots.TOGGLE_CUSTOM_EVS -> toggleAllowCustomEvs(spawnerPos, pokemonName, formName, additionalAspects)
            Slots.BACK_BUTTON -> {
                CustomGui.closeGui(player)
                PokemonEditSubGui.openPokemonEditSubGui(player, spawnerPos, pokemonName, formName, additionalAspects)
                needsRefresh = false
            }
            in stats -> {
                val delta = when (context.clickType) {
                    ClickType.LEFT -> -1
                    ClickType.RIGHT -> 1
                    else -> 0
                }
                if (delta != 0) {
                    adjustEV(spawnerPos, pokemonName, formName, additionalAspects, slot, delta)
                } else {
                    needsRefresh = false
                }
            }
            else -> needsRefresh = false
        }
        if (needsRefresh) {
            refreshGui(player, spawnerPos, pokemonName, formName, additionalAspects)
        }
    }

    private fun generateEVEditorLayout(entry: PokemonSpawnEntry): List<ItemStack> {
        val layout = MutableList(54) { createFillerPane() }
        stats.forEach { (slot, stat) ->
            layout[slot] = createStatButton(stat, entry.evSettings)
        }
        layout[Slots.TOGGLE_CUSTOM_EVS] = createToggleButton(entry.evSettings.allowCustomEvsOnDefeat)
        layout[Slots.BACK_BUTTON] = createBackButton()
        return layout
    }

    private fun adjustEV(spawnerPos: BlockPos, pokemonName: String, formName: String?, additionalAspects: Set<String>, slot: Int, delta: Int) {
        val stat = stats[slot] ?: return
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", additionalAspects) { entry ->
            val evs = entry.evSettings
            val currentValue = stat.getter(evs)
            stat.setter(evs, (currentValue + delta).coerceIn(0, 252))
        }
        CobbleSpawnersConfig.saveSpawnerData()
    }

    private fun toggleAllowCustomEvs(spawnerPos: BlockPos, pokemonName: String, formName: String?, additionalAspects: Set<String>) {
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", additionalAspects) { entry ->
            entry.evSettings.allowCustomEvsOnDefeat = !entry.evSettings.allowCustomEvsOnDefeat
        }
        CobbleSpawnersConfig.saveSpawnerData()
    }

    private fun createStatButton(stat: StatInfo, settings: EVSettings): ItemStack {
        return createButton(
            title = Text.literal("${stat.name} EV").formatted(Formatting.WHITE),
            lore = listOf(
                Text.literal("§aCurrent Value: §f${stat.getter(settings)}"),
                Text.literal(""),
                Text.literal("§eLeft-click to decrease"),
                Text.literal("§eRight-click to increase")
            ),
            texture = stat.texture
        )
    }

    private fun createToggleButton(enabled: Boolean): ItemStack {
        val status = if (enabled) "ON" else "OFF"
        val color = if (enabled) Formatting.GREEN else Formatting.RED
        return createButton(
            title = Text.literal("Allow Custom EVs: $status").formatted(color),
            lore = listOf(
                Text.literal("§7If ON, EVs will be awarded on defeat."),
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
        val layout = generateEVEditorLayout(entry)
        layout.forEachIndexed { index, itemStack ->
            if (index < screenHandler.slots.size) {
                screenHandler.slots[index].stack = itemStack
            }
        }
        screenHandler.sendContentUpdates()
    }
}