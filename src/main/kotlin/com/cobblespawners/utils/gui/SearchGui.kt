package com.cobblespawners.utils.gui

import com.everlastingutils.gui.AnvilGuiManager
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.FullyModularAnvilScreenHandler
import com.everlastingutils.gui.setCustomName
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos

object SearchGui {

    private object Constants {
        const val CANCEL_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lYRAFTC5uZXQvdGV4dHVyZS83MjQzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
        const val SEARCH_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTY4M2RjN2JjNmRiZGI1ZGM0MzFmYmUyOGRjNGI5YWU2MjViOWU1MzE3YTI5ZjJjNGVjZmU3YmY1YWU1NmMzOCJ9fX0="
    }

    fun openSearchGui(player: ServerPlayerEntity, spawnerPos: BlockPos) {
        SpawnerPokemonSelectionGui.spawnerGuisOpen[spawnerPos] = player

        val cancelButton = createCancelButton()
        val blockedInput = ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE)
        val placeholderOutput = createPlaceholderOutput()

        AnvilGuiManager.openAnvilGui(
            player = player,
            id = "pokemon_search_${spawnerPos.toShortString()}",
            title = "Search Pokémon",
            initialText = "",
            leftItem = cancelButton,
            rightItem = blockedInput,
            resultItem = placeholderOutput,
            onLeftClick = {
                player.sendMessage(Text.literal("§7Search cancelled."), false)
                goBackToPreviousGui(player, spawnerPos)
            },
            onRightClick = null,
            onResultClick = { context ->
                if (context.handler.currentText.isNotBlank()) {
                    handleSearch(player, spawnerPos, context.handler.currentText)
                }
            },
            onTextChange = { text ->
                val handler = player.currentScreenHandler as? FullyModularAnvilScreenHandler
                if (text.isNotEmpty()) {
                    val updatedSearchButton = createDynamicSearchButton(text)
                    handler?.updateSlot(2, updatedSearchButton)
                } else {
                    handler?.updateSlot(2, createPlaceholderOutput())
                }
            },
            onClose = {
                player.server.execute {
                    if (player.currentScreenHandler !is FullyModularAnvilScreenHandler) {
                        goBackToPreviousGui(player, spawnerPos)
                    }
                }
            }
        )

        player.server.execute {
            (player.currentScreenHandler as? FullyModularAnvilScreenHandler)?.clearTextField()
        }
        player.sendMessage(Text.literal("Enter a Pokémon name to search, or click the X to cancel..."), false)
    }

    private fun createCancelButton(): ItemStack {
        return CustomGui.createPlayerHeadButton(
            textureName = "CancelButton",
            title = Text.literal("§cCancel Search").styled { it.withBold(true).withItalic(false) },
            lore = listOf(
                Text.literal("§7Click to return to Pokémon selection"),
                Text.literal("§7without searching")
            ),
            textureValue = Constants.CANCEL_TEXTURE
        )
    }

    private fun createDynamicSearchButton(searchText: String): ItemStack {
        return CustomGui.createPlayerHeadButton(
            textureName = "SearchButton",
            title = Text.literal("§aSearch: §f$searchText").styled { it.withBold(true).withItalic(false) },
            lore = listOf(
                Text.literal("§aClick to search for this term"),
                Text.literal("§7Enter different text to change search")
            ),
            textureValue = Constants.SEARCH_TEXTURE
        )
    }

    private fun createPlaceholderOutput(): ItemStack {
        return ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE).apply {
            setCustomName(Text.literal(" "))
        }
    }

    private fun handleSearch(player: ServerPlayerEntity, spawnerPos: BlockPos, searchQuery: String) {
        if (searchQuery.isBlank()) return

        SpawnerPokemonSelectionGui.searchTerm = searchQuery.trim()
        SpawnerPokemonSelectionGui.sortMethod = SortMethod.SEARCH
        player.sendMessage(Text.literal("§aSearching for Pokémon containing: §f'$searchQuery'"), false)

        goBackToPreviousGui(player, spawnerPos)
    }

    private fun goBackToPreviousGui(player: ServerPlayerEntity, spawnerPos: BlockPos) {
        player.server.execute {
            SpawnerPokemonSelectionGui.openSpawnerGui(player, spawnerPos)
        }
    }
}