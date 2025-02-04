package com.cobblespawners.utils.gui

import com.blanketutils.command.CommandManager
import com.blanketutils.gui.CustomGui
import com.blanketutils.gui.InteractionContext
import com.blanketutils.gui.setCustomName
import com.cobblespawners.utils.*
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos
import java.util.concurrent.ConcurrentHashMap

object SpawnerListGui {
    private const val ITEMS_PER_PAGE = 45
    private val playerPages: ConcurrentHashMap<ServerPlayerEntity, Int> = ConcurrentHashMap() // Track the page per player

    /**
     * Opens the spawner list GUI.
     */
    fun openSpawnerListGui(player: ServerPlayerEntity) {
        // Check GUI permission
        if (!CommandManager.hasPermissionOrOp(player.commandSource, "CobbleSpawners.gui", 2, 2)) {
            player.sendMessage(Text.literal("You don't have permission to use this GUI."), false)
            return
        }

        val currentPage = playerPages[player] ?: 0

        val spawnerList = CobbleSpawnersConfig.spawners.map { (pos, data) ->
            Pair(pos, data)
        }

        if (spawnerList.isEmpty()) {
            player.sendMessage(Text.literal("No spawners available."), false)
            return
        }

        val layout = generateFullGuiLayout(spawnerList, currentPage)

        CustomGui.openGui(
            player,
            "Available Spawners",
            layout,
            { context -> handleButtonClick(context, player, spawnerList) },
            { playerPages.remove(player) }
        )
    }


    /**
     * Generates the full layout for the GUI, including buttons and spawners.
     */
    private fun generateFullGuiLayout(spawnerList: List<Pair<BlockPos, SpawnerData>>, page: Int): List<ItemStack> {
        val layout = generateSpawnerItemsForGui(spawnerList, page).toMutableList()

        val previousPageSlot = 45
        val globalSettingsSlot = 49
        val nextPageSlot = 53

        // Fill only the bottom row with filler panes (slots 45-53)
        for (i in 45..53) {
            layout[i] = createFillerPane()
        }

        // Add Previous Page button
        if (page > 0) {
            layout[previousPageSlot] = createPreviousPageButton()
        }

        // Add "Edit Global Settings" button at slot 49
        layout[globalSettingsSlot] = createGlobalSettingsButton()

        // Add Next Page button
        if ((page + 1) * ITEMS_PER_PAGE < spawnerList.size) {
            layout[nextPageSlot] = createNextPageButton()
        }

        return layout
    }

    /**
     * Generates the GUI items for spawners.
     */
    private fun generateSpawnerItemsForGui(spawnerList: List<Pair<BlockPos, SpawnerData>>, page: Int): List<ItemStack> {
        val layout = MutableList(54) { ItemStack.EMPTY }
        val start = page * ITEMS_PER_PAGE
        val end = minOf(start + ITEMS_PER_PAGE, spawnerList.size)

        for (i in start until end) {
            val (pos, spawnerData) = spawnerList[i]
            val lore = listOf(
                Text.literal("Location: ${pos.x}, ${pos.y}, ${pos.z}").styled { it.withColor(Formatting.GRAY).withItalic(false) },
                Text.literal("Dimension: ${spawnerData.dimension}").styled { it.withColor(Formatting.GRAY).withItalic(false) },
                Text.literal("Click to open GUI for this spawner").styled { it.withColor(Formatting.GRAY).withItalic(false) }
            )
            val spawnerHeadItem = CustomGui.createPlayerHeadButton(
                "SpawnerTexture", // Dummy texture name, can be dynamic if needed
                Text.literal(spawnerData.spawnerName).styled { it.withColor(Formatting.WHITE).withBold(false).withItalic(false) },
                lore,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjQ3ZTJlNWQ1NWI2ZDA0OTQzNTE5YmVkMjU1N2M2MzI5ZTMzYjYwYjkwOWRlZTg5MjNjZDg4YjExNTIxMCJ9fX0=" // Base64 texture value
            )
            layout[i - start] = spawnerHeadItem
        }

        return layout
    }

    /**
     * Handles button clicks in the GUI.
     */
    private fun handleButtonClick(
        context: InteractionContext,
        player: ServerPlayerEntity,
        spawnerList: List<Pair<BlockPos, SpawnerData>>
    ) {
        val currentPage = playerPages[player] ?: 0
        val startIndex = currentPage * ITEMS_PER_PAGE
        val endIndex = minOf(startIndex + ITEMS_PER_PAGE, spawnerList.size)

        when (context.slotIndex) {
            45 -> { // Previous Page
                if (currentPage > 0) {
                    playerPages[player] = currentPage - 1
                    refreshGuiItems(player, spawnerList)
                }
            }
            49 -> { // Global Settings Button
                GlobalSettingsGui.openGlobalSettingsGui(player) // Open global settings
            }
            53 -> { // Next Page
                if (endIndex < spawnerList.size) {
                    playerPages[player] = currentPage + 1
                    refreshGuiItems(player, spawnerList)
                }
            }
            else -> { // Spawner Selection
                val clickedName = context.clickedStack.name?.string
                val spawnerData = spawnerList.find { it.second.spawnerName == clickedName }?.second

                if (spawnerData != null) {
                    SpawnerPokemonSelectionGui.openSpawnerGui(player, spawnerData.spawnerPos)
                } else {
                    player.sendMessage(Text.literal("Spawner '$clickedName' not found."), false)
                }
            }
        }
    }

    /**
     * Refreshes the GUI items without closing the GUI.
     */
    private fun refreshGuiItems(player: ServerPlayerEntity, spawnerList: List<Pair<BlockPos, SpawnerData>>) {
        val currentPage = playerPages[player] ?: 0
        val layout = generateFullGuiLayout(spawnerList, currentPage)

        CustomGui.refreshGui(player, layout)
    }

    // Helper methods for creating buttons and filler panes
    private fun createFillerPane(): ItemStack {
        return ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
            setCustomName(Text.literal(" "))
        }
    }

    private fun createGlobalSettingsButton(): ItemStack {
        return CustomGui.createPlayerHeadButton(
            "GlobalSettingsButton",
            Text.literal("Edit Global Settings").styled { it.withColor(Formatting.WHITE) },
            listOf(Text.literal("Click to edit global settings").styled { it.withColor(Formatting.GRAY) }),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTFhYTIwMzU3NTk0YjdjZWFkM2YxN2FkMTU1MjViYTg0NGZlMjRmNDM5OTNhMTViMGU3MTYyMzUwYWQzMDM1OSJ9fX0="
        )
    }

    private fun createNextPageButton(): ItemStack {
        return CustomGui.createPlayerHeadButton(
            "NextPageButton",
            Text.literal("Next Page").styled { it.withColor(Formatting.GREEN) },
            listOf(Text.literal("Click to go to the next page").styled { it.withColor(Formatting.GRAY) }),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGU0MDNjYzdiYmFjNzM2NzBiZDU0M2Y2YjA5NTViYWU3YjhlOTEyM2Q4M2JkNzYwZjYyMDRjNWFmZDhiZTdlMSJ9fX0="
        )
    }

    private fun createPreviousPageButton(): ItemStack {
        return CustomGui.createPlayerHeadButton(
            "PreviousPageButton",
            Text.literal("Previous Page").styled { it.withColor(Formatting.GREEN) },
            listOf(Text.literal("Click to go to the previous page").styled { it.withColor(Formatting.GRAY) }),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTMzYWQ1YzIyZGIxNjQzNWRhYWQ2MTU5MGFiYTUxZDkzNzkxNDJkZDU1NmQ2YzQyMmE3MTEwY2EzYWJlYTUwIn19fQ=="
        )
    }
}
