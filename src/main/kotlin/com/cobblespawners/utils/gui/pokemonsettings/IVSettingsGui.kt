package com.cobblespawners.utils.gui.pokemonsettings

import com.cobblespawners.utils.CobbleSpawnersConfig
import com.cobblespawners.utils.IVSettings
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

object IVSettingsGui {

    private object Slots {
        const val TOGGLE_CUSTOM_IVS = 31
        const val BACK_BUTTON = 49
    }

    private object Textures {
        const val HP = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWRiMDJiMDQwYzM3MDE1ODkyYTNhNDNkM2IxYmZkYjJlMDFhMDJlZGNjMmY1YjgyMjUwZGNlYmYzZmY0ZjAxZSJ9fX0="
        const val ATTACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTFkMzgzNDAxZjc3YmVmZmNiOTk4YzJjZjc5YjdhZmVlMjNmMThjNDFkOGE1NmFmZmVkNzliYjU2ZTIyNjdhMyJ9fX0="
        const val DEFENSE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjU1NTFmMzRjNDVmYjE4MTFlNGNjMmZhOGVjMzcxZTQ1YmEwOTc3ZTFkMTUyMTEyMGYwZjU3NTYwZjczZjU5MCJ9fX0="
        const val SP_ATTACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzhmZTcwYjc3MzFhYzJmNWIzZDAyNmViMWFiNmE5MjNhOGM1OGI0YmY2ZDNhY2JlMTQ1YjEwYzM2ZTZjZjg5OCJ9fX0="
        const val SP_DEFENSE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2VhMmI1MTE4MWFlMTlkMzMzMTNjNmY0YThlOTA2NjU3MDU1NzM2MzliM2RmNzA5NTE0YmQ5NzA5ODUzMzBkZCJ9fX0="
        const val SPEED = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDcxMDEzODQxNjUyODg4OTgxNTU0OGI0NjIzZDI4ZDg2YmJiYWU1NjE5ZDY5Y2Q5ZGJjNWFkNmI0Mzc0NCJ9fX0="
        const val TOGGLE_ON = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTI1YjhlZWQ1YzU2NWJkNDQwZWM0N2M3OWMyMGQ1Y2YzNzAxNjJiMWQ5YjVkZDMxMDBlZDYyODNmZTAxZDZlIn19fQ=="
        const val TOGGLE_OFF = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjNmNzliMjA3ZDYxZTEyMjUyM2I4M2Q2MTUwOGQ5OWNmYTA3OWQ0NWJmMjNkZjJhOWE1MTI3ZjkwNzFkNGIwMCJ9fX0="
        const val BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
    }

    private data class StatConfig(
        val name: String,
        val texture: String,
        val minSlot: Int,
        val maxSlot: Int,
        val minGetter: (IVSettings) -> Int,
        val minSetter: (IVSettings, Int) -> Unit,
        val maxGetter: (IVSettings) -> Int,
        val maxSetter: (IVSettings, Int) -> Unit
    )

    private val stats = listOf(
        StatConfig("HP", Textures.HP, 10, 11, { it.minIVHp }, { s, v -> s.minIVHp = v }, { it.maxIVHp }, { s, v -> s.maxIVHp = v }),
        StatConfig("Attack", Textures.ATTACK, 13, 14, { it.minIVAttack }, { s, v -> s.minIVAttack = v }, { it.maxIVAttack }, { s, v -> s.maxIVAttack = v }),
        StatConfig("Defense", Textures.DEFENSE, 16, 17, { it.minIVDefense }, { s, v -> s.minIVDefense = v }, { it.maxIVDefense }, { s, v -> s.maxIVDefense = v }),
        StatConfig("Sp. Atk", Textures.SP_ATTACK, 19, 20, { it.minIVSpecialAttack }, { s, v -> s.minIVSpecialAttack = v }, { it.maxIVSpecialAttack }, { s, v -> s.maxIVSpecialAttack = v }),
        StatConfig("Sp. Def", Textures.SP_DEFENSE, 22, 23, { it.minIVSpecialDefense }, { s, v -> s.minIVSpecialDefense = v }, { it.maxIVSpecialDefense }, { s, v -> s.maxIVSpecialDefense = v }),
        StatConfig("Speed", Textures.SPEED, 25, 26, { it.minIVSpeed }, { s, v -> s.minIVSpeed = v }, { it.maxIVSpeed }, { s, v -> s.maxIVSpeed = v })
    )
    private val statSlots = stats.flatMap { listOf(it.minSlot to it, it.maxSlot to it) }.toMap()

    fun openIVEditorGui(player: ServerPlayerEntity, spawnerPos: BlockPos, pokemonName: String, formName: String?, additionalAspects: Set<String>) {
        val entry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", additionalAspects)
        if (entry == null) {
            player.sendMessage(Text.literal("Error: Could not find the specified Pokémon in this spawner."), false)
            return
        }
        spawnerGuisOpen[spawnerPos] = player
        val aspectsDisplay = if (additionalAspects.isNotEmpty()) ", ${additionalAspects.joinToString(", ")}" else ""
        val guiTitle = "Edit IVs: ${entry.pokemonName} (${entry.formName ?: "Standard"}$aspectsDisplay)"

        CustomGui.openGui(
            player,
            guiTitle,
            generateIVEditorLayout(entry),
            { context -> handleInteraction(context, player, spawnerPos, pokemonName, formName, additionalAspects) },
            { spawnerGuisOpen.remove(spawnerPos) }
        )
    }

    private fun handleInteraction(context: InteractionContext, player: ServerPlayerEntity, spawnerPos: BlockPos, pokemonName: String, formName: String?, additionalAspects: Set<String>) {
        when (val slot = context.slotIndex) {
            Slots.TOGGLE_CUSTOM_IVS -> toggleAllowCustomIvs(spawnerPos, pokemonName, formName, additionalAspects)
            Slots.BACK_BUTTON -> {
                CustomGui.closeGui(player)
                PokemonEditSubGui.openPokemonEditSubGui(player, spawnerPos, pokemonName, formName, additionalAspects)
                return
            }
            in statSlots -> {
                val stat = statSlots[slot] ?: return
                val isMin = slot == stat.minSlot
                val delta = if (context.clickType == ClickType.LEFT) -1 else 1
                updateIVValue(spawnerPos, pokemonName, formName, additionalAspects, isMin, delta, stat)
            }
            else -> return
        }
        refreshGui(player, spawnerPos, pokemonName, formName, additionalAspects)
    }

    private fun generateIVEditorLayout(entry: PokemonSpawnEntry): List<ItemStack> {
        val layout = MutableList(54) { createFillerPane() }
        val ivSettings = entry.ivSettings

        stats.forEach { stat ->
            layout[stat.minSlot] = createStatButton("${stat.name} Min", stat.minGetter(ivSettings), stat.texture)
            layout[stat.maxSlot] = createStatButton("${stat.name} Max", stat.maxGetter(ivSettings), stat.texture)
        }

        layout[Slots.TOGGLE_CUSTOM_IVS] = createToggleButton(ivSettings.allowCustomIvs)
        layout[Slots.BACK_BUTTON] = createBackButton()
        return layout
    }

    private fun updateIVValue(spawnerPos: BlockPos, pokemonName: String, formName: String?, additionalAspects: Set<String>, isMin: Boolean, delta: Int, stat: StatConfig) {
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", additionalAspects) { entry ->
            val ivs = entry.ivSettings
            if (isMin) {
                val newValue = (stat.minGetter(ivs) + delta).coerceIn(0, stat.maxGetter(ivs))
                stat.minSetter(ivs, newValue)
            } else {
                val newValue = (stat.maxGetter(ivs) + delta).coerceIn(stat.minGetter(ivs), 31)
                stat.maxSetter(ivs, newValue)
            }
        }
        CobbleSpawnersConfig.saveSpawnerData()
    }

    private fun toggleAllowCustomIvs(spawnerPos: BlockPos, pokemonName: String, formName: String?, additionalAspects: Set<String>) {
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", additionalAspects) { entry ->
            entry.ivSettings.allowCustomIvs = !entry.ivSettings.allowCustomIvs
        }
        CobbleSpawnersConfig.saveSpawnerData()
    }

    private fun createStatButton(label: String, value: Int, texture: String): ItemStack {
        return createButton(
            title = Text.literal(label).formatted(Formatting.WHITE),
            lore = listOf(
                Text.literal("§aCurrent Value: §f$value"),
                Text.literal(""),
                Text.literal("§eLeft-click to decrease"),
                Text.literal("§eRight-click to increase")
            ),
            texture = texture
        )
    }

    private fun createToggleButton(enabled: Boolean): ItemStack {
        val status = if (enabled) "ON" else "OFF"
        val color = if (enabled) Formatting.GREEN else Formatting.RED
        return createButton(
            title = Text.literal("Allow Custom IVs: $status").formatted(color),
            lore = listOf(
                Text.literal("§7If ON, these IV ranges will be applied."),
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
        return CustomGui.createPlayerHeadButton(title.string.filter { !it.isWhitespace() }, title, lore, texture)
    }

    private fun createFillerPane(): ItemStack {
        return ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
            setCustomName(Text.literal(" "))
        }
    }

    private fun refreshGui(player: ServerPlayerEntity, spawnerPos: BlockPos, pokemonName: String, formName: String?, additionalAspects: Set<String>) {
        val entry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", additionalAspects) ?: return
        val screenHandler = player.currentScreenHandler ?: return
        val layout = generateIVEditorLayout(entry)
        layout.forEachIndexed { index, itemStack ->
            if (index < screenHandler.slots.size) {
                screenHandler.slots[index].stack = itemStack
            }
        }
        screenHandler.sendContentUpdates()
    }
}