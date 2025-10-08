package com.cobblespawners.utils.gui

import com.cobblespawners.utils.CobbleSpawnersConfig
import com.cobblespawners.utils.gui.pokemonsettings.*
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.InteractionContext
import com.everlastingutils.gui.setCustomName
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos

object PokemonEditSubGui {

    private object Slots {
        const val IV_SETTINGS = 11
        const val EV_SETTINGS = 13
        const val SPAWN_SETTINGS = 15
        const val SIZE_SETTINGS = 20
        const val MOVES_SETTINGS = 22
        const val CAPTURE_SETTINGS = 24
        const val OTHER_SETTINGS = 31
        const val BACK = 49
    }

    private object Textures {
        const val IV_SETTINGS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDg4M2Q2NTZlNDljMzhjNmI1Mzc4NTcyZjMxYzYzYzRjN2E1ZGQ0Mzc1YjZlY2JjYTQzZjU5NzFjMmNjNGZmIn19fQ=="
        const val EV_SETTINGS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODM0NTI5NjRmMWNiYjg5MTQ2Njg0YWE1NTYzOTBhOThjZjM0MmNhOTdjZWZhNmE5Mjk0YTVkMzZlZGQ5MzBmOSJ9fX0="
        const val SPAWN_SETTINGS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjdkNmJlMWRjYTUzNTJhNTY5M2UyOWVhMzVkODA2YjJhMjdjNGE5N2I2NGVlYmJmNjMyYzk5OGQ1OTQ4ZjFjNCJ9fX0="
        const val SIZE_SETTINGS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmI5MmFiZWI0NGMzNGI5OThhMDE4ZWM1YjYwMjJlOGZjMTU4ZWU4YjEzNDA0YzBmZTZkZDA5MTdmZWQ4NDRlYiJ9fX0="
        const val CAPTURE_SETTINGS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTY0YzQ0ODZmOTIzNmY5YTFmYjRiMjFiZjgyM2M1NTZkNmUxNWJmNjg4Yzk2ZDZlZjBkMTc1NTNkYjUwNWIifX19"
        const val OTHER_SETTINGS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWEwMWQxNTZiMTcyMTVjZWYzMzZhZjRjNDRlNmNjOGNjYjI4NWZiMDViYzNmZWI2MmQzMzdmZWIxZjA5MjkwYSJ9fX0="
        const val MOVES_SETTINGS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzJlYmJkYjE4ZDc0NzI4MWI1NDYyZjg1N2VlOTg0Njc1YTM5ZDVhMDI3NDQ0NmEyMmY2NjI2NGE1M2QyYjAzNCJ9fX0="
        const val BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
    }

    private data class ButtonData(
        val title: String,
        val color: Formatting,
        val lore: List<String>,
        val texture: String
    )

    private val subGuiButtons = mapOf(
        Slots.IV_SETTINGS to ButtonData("Edit IVs", Formatting.GREEN, listOf("Fine-tune each stat's Individual Values (IVs)", "to maximize overall performance."), Textures.IV_SETTINGS),
        Slots.EV_SETTINGS to ButtonData("Edit EVs", Formatting.BLUE, listOf("Optimize Effort Values (EVs)", "gained from battle encounters."), Textures.EV_SETTINGS),
        Slots.SPAWN_SETTINGS to ButtonData("Edit Spawn/Level Chances", Formatting.DARK_AQUA, listOf("Customize spawn probabilities", "define minimum/maximum level thresholds."), Textures.SPAWN_SETTINGS),
        Slots.SIZE_SETTINGS to ButtonData("Edit Size", Formatting.GOLD, listOf("Adjust the Pokémon's dimensions", "within the spawner for the desired scale."), Textures.SIZE_SETTINGS),
        Slots.MOVES_SETTINGS to ButtonData("Edit Moves", Formatting.YELLOW, listOf("Configure the initial moves", "that the Pokémon will have when spawned."), Textures.MOVES_SETTINGS),
        Slots.CAPTURE_SETTINGS to ButtonData("Edit Catchable Settings", Formatting.AQUA, listOf("Configure catchability parameters", "to refine capture mechanics."), Textures.CAPTURE_SETTINGS),
        Slots.OTHER_SETTINGS to ButtonData("Edit Other Stats", Formatting.LIGHT_PURPLE, listOf("Modify additional attributes such as level", "and miscellaneous performance parameters."), Textures.OTHER_SETTINGS),
        Slots.BACK to ButtonData("Back", Formatting.RED, listOf("Returns to the spawner Pokémon selection."), Textures.BACK)
    )

    fun openPokemonEditSubGui(
        player: ServerPlayerEntity,
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        additionalAspects: Set<String>
    ) {
        CustomGui.closeGui(player)

        val standardFormName = formName ?: "Normal"
        val selectedEntry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, standardFormName, additionalAspects)

        if (selectedEntry == null) {
            val aspectsString = if (additionalAspects.isEmpty()) "\"\"" else additionalAspects.joinToString(", ")
            player.sendMessage(Text.literal("Pokemon '$pokemonName' with form '$standardFormName' and aspects $aspectsString not found in spawner."), false)
            return
        }

        SpawnerPokemonSelectionGui.spawnerGuisOpen[spawnerPos] = player

        val aspectsDisplay = if (additionalAspects.isNotEmpty()) ", " + additionalAspects.joinToString(", ") else ""
        val subGuiTitle = "Edit Pokémon: ${selectedEntry.pokemonName} (${selectedEntry.formName ?: "Normal"}$aspectsDisplay)"

        CustomGui.openGui(
            player,
            subGuiTitle,
            generateSubGuiLayout(),
            { context -> handleSubGuiInteraction(context, player, spawnerPos, pokemonName, formName, additionalAspects) },
            { SpawnerPokemonSelectionGui.spawnerGuisOpen.remove(spawnerPos) }
        )
    }

    private fun handleSubGuiInteraction(
        context: InteractionContext,
        player: ServerPlayerEntity,
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        additionalAspects: Set<String>
    ) {
        val closeAndOpen: ( (ServerPlayerEntity, BlockPos, String, String?, Set<String>) -> Unit ) -> Unit = { openGuiFunc ->
            CustomGui.closeGui(player)
            openGuiFunc(player, spawnerPos, pokemonName, formName, additionalAspects)
        }

        when (context.slotIndex) {
            Slots.IV_SETTINGS -> closeAndOpen(IVSettingsGui::openIVEditorGui)
            Slots.EV_SETTINGS -> closeAndOpen(EVSettingsGui::openEVEditorGui)
            Slots.SPAWN_SETTINGS -> closeAndOpen(SpawnSettingsGui::openSpawnShinyEditorGui)
            Slots.SIZE_SETTINGS -> closeAndOpen(SizeSettingsGui::openSizeEditorGui)
            Slots.MOVES_SETTINGS -> closeAndOpen(MovesSettingsGui::openMovesSettingsGui)
            Slots.CAPTURE_SETTINGS -> closeAndOpen(CaptureSettingsGui::openCaptureSettingsGui)
            Slots.OTHER_SETTINGS -> closeAndOpen(OtherSettingsGui::openOtherEditableGui)
            Slots.BACK -> {
                CustomGui.closeGui(player)
                val page = SpawnerPokemonSelectionGui.playerPages[player] ?: 0
                SpawnerPokemonSelectionGui.openSpawnerGui(player, spawnerPos, page)
            }
        }
    }

    private fun generateSubGuiLayout(): List<ItemStack> {
        val layout = MutableList(54) { createFillerPane() }

        subGuiButtons.forEach { (slot, data) ->
            layout[slot] = createButton(data.title, data.color, data.lore, data.texture)
        }

        return layout
    }

    private fun createButton(
        text: String,
        color: Formatting,
        loreText: List<String>,
        textureValue: String
    ): ItemStack {
        val formattedLore = loreText.map {
            Text.literal("§7$it").styled { style -> style.withItalic(false) }
        }

        return CustomGui.createPlayerHeadButton(
            textureName = text.replace(" ", ""),
            title = Text.literal(text).styled { it.withColor(color).withBold(false).withItalic(false) },
            lore = formattedLore,
            textureValue = textureValue
        )
    }

    private fun createFillerPane(): ItemStack {
        return ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
            setCustomName(Text.literal(" "))
        }
    }
}