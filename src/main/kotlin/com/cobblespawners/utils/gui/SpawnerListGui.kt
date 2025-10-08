package com.cobblespawners.utils.gui

import com.cobblespawners.utils.CobbleSpawnersConfig
import com.cobblespawners.utils.SpawnerData
import com.everlastingutils.command.CommandManager
import com.everlastingutils.gui.AnvilGuiManager
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.FullyModularAnvilScreenHandler
import com.everlastingutils.gui.InteractionContext
import com.everlastingutils.gui.setCustomName
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object SpawnerListGui {

    private const val ITEMS_PER_PAGE = 45
    private val playerPages: ConcurrentHashMap<ServerPlayerEntity, Int> = ConcurrentHashMap()
    private val playerSortMethods: ConcurrentHashMap<ServerPlayerEntity, SortMethod> = ConcurrentHashMap()
    private val playerSearchTerms: ConcurrentHashMap<ServerPlayerEntity, String> = ConcurrentHashMap()

    private enum class SortMethod {
        ALPHABETICAL,
        NUMERICAL,
        SEARCH
    }

    private object Slots {
        const val PREVIOUS_PAGE = 45
        const val SORT_BUTTON = 49
        const val NEXT_PAGE = 53
    }

    private object Constants {
        const val GUI_PERMISSION = "CobbleSpawners.gui"
        const val ANVIL_GUI_ID = "spawner_search"
    }

    private object Textures {
        const val SORT_METHOD = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWI1ZWU0MTlhZDljMDYwYzE2Y2I1M2IxZGNmZmFjOGJhY2EwYjJhMjI2NWIxYjZjN2U4ZTc4MGMzN2IxMDRjMCJ9fX0="
        const val PREVIOUS_PAGE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTMzYWQ1YzIyZGIxNjQzNWRhYWQ2MTU5MGFiYTUxZDkzNzkxNDJkZDU1NmQ2YzQyMmE3MTEwY2EzYWJlYTUwIn19fQ=="
        const val NEXT_PAGE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGU0MDNjYzdiYmFjNzM2NzBiZDU0M2Y2YjA5NTViYWU3YjhlOTEyM2Q4M2JkNzYwZjYyMDRjNWFmZDhiZTdlMSJ9fX0="
        const val CANCEL = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
        const val SEARCH = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTY4M2RjN2JjNmRiZGI1ZGM0MzFmYmUyOGRjNGI5YWU2MjViOWU1MzE3YTI5ZjJjNGVjZmU3YmY1YWU1NmMzOCJ9fX0="
        const val SPAWNER_ICON = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjQ3ZTJlNWQ1NWI2ZDA0OTQzNTE5YmVkMjU1N2M2MzI5ZTMzYjYwYjkwOWRlZTg5MjNjZDg4YjExNTIxMCJ9fX0="
    }

    fun openSpawnerListGui(player: ServerPlayerEntity) {
        if (!CommandManager.hasPermissionOrOp(player.commandSource, Constants.GUI_PERMISSION, 2, 2)) {
            player.sendMessage(Text.literal("You don't have permission to use this GUI."), false)
            return
        }

        val filteredSpawnerList = getFilteredSpawnerList(player)
        if (filteredSpawnerList.isEmpty()) {
            val message = if (playerSortMethods.getOrDefault(player, SortMethod.ALPHABETICAL) == SortMethod.SEARCH) {
                "No spawners found matching '${playerSearchTerms[player]}'."
            } else {
                "No spawners available."
            }
            player.sendMessage(Text.literal(message), false)
            return
        }

        val totalPages = (filteredSpawnerList.size + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE
        val currentPage = playerPages.getOrDefault(player, 0).coerceIn(0, totalPages - 1)
        playerPages[player] = currentPage

        CustomGui.openGui(
            player,
            "Available Spawners",
            generateFullGuiLayout(filteredSpawnerList, currentPage, player),
            { context -> handleButtonClick(context, player, filteredSpawnerList) },
            { playerPages.remove(player) }
        )
    }

    private fun generateFullGuiLayout(spawnerList: List<Pair<BlockPos, SpawnerData>>, page: Int, player: ServerPlayerEntity): List<ItemStack> {
        val layout = generateSpawnerItemsForGui(spawnerList, page).toMutableList()
        val totalPages = (spawnerList.size + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE

        for (i in 45..53) {
            layout[i] = createFillerPane()
        }

        if (page > 0) {
            layout[Slots.PREVIOUS_PAGE] = createPreviousPageButton()
        }

        if (page < totalPages - 1) {
            layout[Slots.NEXT_PAGE] = createNextPageButton()
        }

        layout[Slots.SORT_BUTTON] = createSortButton(player)

        return layout
    }

    private fun generateSpawnerItemsForGui(spawnerList: List<Pair<BlockPos, SpawnerData>>, page: Int): List<ItemStack> {
        val layout = MutableList(54) { ItemStack.EMPTY }
        val startIndex = page * ITEMS_PER_PAGE
        val endIndex = minOf(startIndex + ITEMS_PER_PAGE, spawnerList.size)

        val spawnerSubList = spawnerList.subList(startIndex, endIndex)
        spawnerSubList.forEachIndexed { index, (pos, data) ->
            layout[index] = createSpawnerItem(pos, data)
        }

        return layout
    }

    private fun createSpawnerItem(pos: BlockPos, spawnerData: SpawnerData): ItemStack {
        val lore = listOf(
            Text.literal("Location: ${pos.x}, ${pos.y}, ${pos.z}").formatted(Formatting.GRAY),
            Text.literal("Dimension: ${spawnerData.dimension}").formatted(Formatting.GRAY),
            Text.literal(""),
            Text.literal("§eLeft-click§7 to open GUI"),
            Text.literal("§eRight-click§7 to teleport")
        )
        return CustomGui.createPlayerHeadButton(
            "SpawnerTexture",
            Text.literal(spawnerData.spawnerName).formatted(Formatting.WHITE),
            lore,
            Textures.SPAWNER_ICON
        )
    }

    private fun handleButtonClick(context: InteractionContext, player: ServerPlayerEntity, spawnerList: List<Pair<BlockPos, SpawnerData>>) {
        val currentPage = playerPages.getOrDefault(player, 0)

        when (context.slotIndex) {
            Slots.PREVIOUS_PAGE -> {
                if (currentPage > 0) {
                    playerPages[player] = currentPage - 1
                    refreshGuiItems(player)
                }
            }
            Slots.NEXT_PAGE -> {
                val totalPages = (spawnerList.size + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE
                if (currentPage < totalPages - 1) {
                    playerPages[player] = currentPage + 1
                    refreshGuiItems(player)
                }
            }
            Slots.SORT_BUTTON -> {
                when (context.button) {
                    0 -> cycleSortMethod(player)
                    1 -> openSearchGui(player)
                }
            }
            in 0 until ITEMS_PER_PAGE -> {
                val index = (currentPage * ITEMS_PER_PAGE) + context.slotIndex
                if (index < spawnerList.size) {
                    val (pos, _) = spawnerList[index]
                    when (context.button) {
                        0 -> SpawnerPokemonSelectionGui.openSpawnerGui(player, pos)
                        1 -> {
                            player.requestTeleport(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
                            player.sendMessage(Text.literal("Teleported to spawner at (${pos.x}, ${pos.y}, ${pos.z})"), false)
                        }
                    }
                }
            }
        }
    }

    private fun cycleSortMethod(player: ServerPlayerEntity) {
        val currentSort = playerSortMethods.getOrDefault(player, SortMethod.ALPHABETICAL)
        val nextSort = when (currentSort) {
            SortMethod.ALPHABETICAL -> SortMethod.NUMERICAL
            SortMethod.NUMERICAL -> SortMethod.ALPHABETICAL
            SortMethod.SEARCH -> SortMethod.ALPHABETICAL
        }
        playerSortMethods[player] = nextSort
        playerSearchTerms.remove(player)
        refreshGuiItems(player)
    }

    private fun refreshGuiItems(player: ServerPlayerEntity) {
        val spawnerList = getFilteredSpawnerList(player)
        val totalPages = if (spawnerList.isEmpty()) 1 else (spawnerList.size + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE
        val currentPage = playerPages.getOrDefault(player, 0).coerceIn(0, totalPages - 1)
        playerPages[player] = currentPage

        val layout = generateFullGuiLayout(spawnerList, currentPage, player)
        CustomGui.refreshGui(player, layout)
    }

    private fun getFilteredSpawnerList(player: ServerPlayerEntity): List<Pair<BlockPos, SpawnerData>> {
        val sortMethod = playerSortMethods.getOrDefault(player, SortMethod.ALPHABETICAL)
        val searchTerm = playerSearchTerms.getOrDefault(player, "")
        val allSpawners = CobbleSpawnersConfig.spawners.toList()

        return when (sortMethod) {
            SortMethod.ALPHABETICAL -> allSpawners.sortedBy { it.second.spawnerName.lowercase() }
            SortMethod.NUMERICAL -> allSpawners.sortedWith(
                compareBy<Pair<BlockPos, SpawnerData>> { extractNumber(it.second.spawnerName) ?: Int.MAX_VALUE }
                    .thenBy { it.second.spawnerName.lowercase() }
            )
            SortMethod.SEARCH -> allSpawners.filter { it.second.spawnerName.contains(searchTerm, ignoreCase = true) }
                .sortedBy { it.second.spawnerName.lowercase() }
        }
    }

    private fun extractNumber(name: String): Int? {
        val pattern = Pattern.compile("^\\d+")
        val matcher = pattern.matcher(name)
        return if (matcher.find()) matcher.group().toIntOrNull() else null
    }

    private fun openSearchGui(player: ServerPlayerEntity) {
        AnvilGuiManager.openAnvilGui(
            player = player,
            id = Constants.ANVIL_GUI_ID,
            title = "Search Spawners",
            initialText = "",
            leftItem = createCancelButton(),
            rightItem = ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE),
            resultItem = createPlaceholderOutput(),
            onLeftClick = {
                player.sendMessage(Text.literal("§7Search cancelled."), false)
                openSpawnerListGui(player)
            },
            onRightClick = null,
            onResultClick = { context ->
                val searchText = context.handler.currentText
                if (searchText.isNotBlank()) {
                    handleSearch(player, searchText)
                }
            },
            onTextChange = { text ->
                val handler = player.currentScreenHandler as? FullyModularAnvilScreenHandler
                val outputSlot = if (text.isNotEmpty()) createDynamicSearchButton(text) else createPlaceholderOutput()
                handler?.updateSlot(2, outputSlot)
            },
            onClose = {
                player.server.execute { openSpawnerListGui(player) }
            }
        )
        player.sendMessage(Text.literal("Enter a spawner name to search..."), false)
    }

    private fun handleSearch(player: ServerPlayerEntity, searchQuery: String) {
        playerSearchTerms[player] = searchQuery.trim()
        playerSortMethods[player] = SortMethod.SEARCH
        player.sendMessage(Text.literal("§aSearching for spawners containing: §f'$searchQuery'"), false)
        openSpawnerListGui(player)
    }

    private fun createSortButton(player: ServerPlayerEntity): ItemStack {
        val sortMethod = playerSortMethods.getOrDefault(player, SortMethod.ALPHABETICAL)
        val searchTerm = playerSearchTerms.getOrDefault(player, "")
        val buttonText = when (sortMethod) {
            SortMethod.ALPHABETICAL -> "Sort: Alphabetical"
            SortMethod.NUMERICAL -> "Sort: Numerical"
            SortMethod.SEARCH -> "Search: '${if (searchTerm.length > 10) searchTerm.take(7) + "..." else searchTerm}'"
        }
        val lore = listOf(
            Text.literal("§7Current: §f${sortMethod.name.lowercase().replaceFirstChar { it.uppercase() }}"),
            Text.literal(""),
            Text.literal("§eLeft-click§7 to cycle sort methods"),
            Text.literal("§eRight-click§7 to search by name")
        )

        return CustomGui.createPlayerHeadButton(
            "SortButton",
            Text.literal(buttonText).formatted(Formatting.AQUA),
            lore,
            Textures.SORT_METHOD
        )
    }

    private fun createCancelButton(): ItemStack {
        return CustomGui.createPlayerHeadButton(
            "CancelButton",
            Text.literal("Cancel Search").formatted(Formatting.RED),
            listOf(Text.literal("§7Return to spawner list")),
            Textures.CANCEL
        )
    }

    private fun createDynamicSearchButton(searchText: String): ItemStack {
        return CustomGui.createPlayerHeadButton(
            "SearchButton",
            Text.literal("Search: $searchText").formatted(Formatting.GREEN),
            listOf(Text.literal("§7Click to search for this term")),
            Textures.SEARCH
        )
    }

    private fun createPlaceholderOutput(): ItemStack {
        return ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE).apply {
            setCustomName(Text.literal(" "))
        }
    }

    private fun createFillerPane(): ItemStack {
        return ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
            setCustomName(Text.literal(" "))
        }
    }

    private fun createNextPageButton(): ItemStack {
        return CustomGui.createPlayerHeadButton(
            "NextPageButton",
            Text.literal("Next Page").formatted(Formatting.GREEN),
            listOf(Text.literal("Click to go to the next page").formatted(Formatting.GRAY)),
            Textures.NEXT_PAGE
        )
    }

    private fun createPreviousPageButton(): ItemStack {
        return CustomGui.createPlayerHeadButton(
            "PreviousPageButton",
            Text.literal("Previous Page").formatted(Formatting.GREEN),
            listOf(Text.literal("Click to go to the previous page").formatted(Formatting.GRAY)),
            Textures.PREVIOUS_PAGE
        )
    }
}