// File: GuiManager.kt
package com.cobblespawners.utils.gui

import com.blanketutils.command.CommandManager
import com.blanketutils.gui.CustomGui
import com.blanketutils.gui.InteractionContext
import com.blanketutils.gui.setCustomName
import com.cobblespawners.utils.*
import com.cobblespawners.utils.gui.pokemonsettings.*
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.item.PokemonItem
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species

import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.ClickType
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos
import org.joml.Vector4f
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

enum class SortMethod {
    ALPHABETICAL,
    TYPE,
    SELECTED,
    LETTER_A,
    LETTER_B,
    LETTER_C,
    LETTER_D,
    LETTER_E,
    LETTER_F,
    LETTER_G,
    LETTER_H,
    LETTER_I,
    LETTER_J,
    LETTER_K,
    LETTER_L,
    LETTER_M,
    LETTER_N,
    LETTER_O,
    LETTER_P,
    LETTER_Q,
    LETTER_R,
    LETTER_S,
    LETTER_T,
    LETTER_U,
    LETTER_V,
    LETTER_W,
    LETTER_X,
    LETTER_Y,
    LETTER_Z
}


object SpawnerPokemonSelectionGui {
    private val logger = LoggerFactory.getLogger(SpawnerPokemonSelectionGui::class.java)
    var sortMethod = SortMethod.ALPHABETICAL

    // Tracks the current page per player
    val playerPages: ConcurrentHashMap<ServerPlayerEntity, Int> = ConcurrentHashMap()

    val spawnerGuisOpen: ConcurrentHashMap<BlockPos, ServerPlayerEntity> = ConcurrentHashMap()


    fun isSpawnerGuiOpen(spawnerPos: BlockPos): Boolean {
        return spawnerGuisOpen.containsKey(spawnerPos)
    }

    fun openSpawnerGui(player: ServerPlayerEntity, spawnerPos: BlockPos, page: Int = 0) {

        if (!CommandManager.hasPermissionOrOp(player.commandSource, "CobbleSpawners.Edit", 2, 2)) {
            player.sendMessage(Text.literal("You don't have permission to use this GUI."), false)
            return
        }


        val currentSpawnerData = CobbleSpawnersConfig.spawners[spawnerPos] ?: run {
            player.sendMessage(Text.literal("Spawner data not found"), false)
            return
        }

        spawnerGuisOpen[spawnerPos] = player
        playerPages[player] = page // Track the page for the player

        val layout = generateFullGuiLayout(currentSpawnerData.selectedPokemon, page)

        val onInteract: (InteractionContext) -> Unit = { context ->
            handleButtonClick(context, player, spawnerPos, currentSpawnerData.selectedPokemon, page)
        }

        val onClose: (Inventory) -> Unit = {
            spawnerGuisOpen.remove(spawnerPos)
            playerPages.remove(player)
            CobbleSpawnersConfig.saveSpawnerData()
            player.sendMessage(Text.literal("Spawner data saved and GUI closed"), false)
        }

        val guiTitle = "Select Pokémon for ${currentSpawnerData.spawnerName}"

        CustomGui.openGui(
            player,
            guiTitle,
            layout,
            onInteract,
            onClose
        )
    }

    // Data class to hold species and form together
    data class SpeciesForm(val species: Species, val form: FormData)

    // Helper function to generate the full layout, including buttons and Pokémon items
    private fun generateFullGuiLayout(selectedPokemon: List<PokemonSpawnEntry>, page: Int): List<ItemStack> {
        val layout = generatePokemonItemsForGui(selectedPokemon, page).toMutableList()

        // Define button slots
        val previousPageSlot = 45
        val sortMethodSlot = 48
        val spawnerMenuSlot = 49
        val editSpawnerSettingsSlot = 50
        val nextPageSlot = 53

        // Add Previous Page button if applicable
        if (page > 0) {
            layout[previousPageSlot] = CustomGui.createPlayerHeadButton(
                "PreviousPageTexture",
                Text.literal("Previous").styled { it.withColor(Formatting.YELLOW).withBold(false).withItalic(false) },
                listOf(Text.literal("Click to go to the previous page").styled { it.withColor(Formatting.GRAY).withItalic(false) }),
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTMzYWQ1YzIyZGIxNjQzNWRhYWQ2MTU5MGFiYTUxZDkzNzkxNDJkZDU1NmQ2YzQyMmE3MTEwY2EzYWJlYTUwIn19fQ=="
            )
        } else {
            layout[previousPageSlot] = createFillerPane()
        }

        // Add Sort Method button
        layout[sortMethodSlot] = CustomGui.createPlayerHeadButton(
            "SortButtonTexture",
            Text.literal("Sort Method").styled { it.withColor(Formatting.AQUA).withBold(false).withItalic(false) },
            listOf(
                Text.literal("Current Sort: ${sortMethod.name.lowercase().replaceFirstChar { it.uppercase() }}").styled { it.withColor(Formatting.YELLOW).withItalic(false) },
                Text.literal("Click to change sorting method").styled { it.withColor(Formatting.GRAY).withItalic(false) },
                Text.literal("Right-click to sort by letter").styled { it.withColor(Formatting.GRAY).withItalic(false) } // Add right-click instruction
            ),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWI1ZWU0MTlhZDljMDYwYzE2Y2I1M2IxZGNmZmFjOGJhY2EwYjJhMjI2NWIxYjZjN2U4ZTc4MGMzN2IxMDRjMCJ9fX0="
        )

        // Add Spawner Menu button
        layout[spawnerMenuSlot] = CustomGui.createPlayerHeadButton(
            "SpawnerMenuTexture",
            Text.literal("Spawner List Menu").styled { it.withColor(Formatting.GREEN).withBold(false).withItalic(false) },
            listOf(Text.literal("Click to open the spawner list menu").styled { it.withColor(Formatting.GRAY).withItalic(false) }),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODVmZmI1MjMzMmNiZmNiNWJlNTM1NTNkNjdjNzI2NDNiYTJiYjUxN2Y3ZTg5ZGVkNTNkNGE5MmIwMGNlYTczZSJ9fX0="
        )

        // Add Edit Spawner Settings button
        layout[editSpawnerSettingsSlot] = CustomGui.createPlayerHeadButton(
            "EditSettingsTexture",
            Text.literal("Edit This Spawner's Settings").styled { it.withColor(Formatting.RED).withBold(false).withItalic(false) },
            listOf(Text.literal("Click to edit the current spawner's settings").styled { it.withColor(Formatting.GRAY).withItalic(false) }),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTU5NDRiMzJkYjBlNzE0NjdkYzEzOWNmYzY0NjIwNWIxY2I3NGU4MjlkMmI3M2U1MzA5MzI1YzVkMTRlMDVmYSJ9fX0="
        )

        // Add Next Page button if applicable
        val speciesListSize = getSortedSpeciesList(selectedPokemon).size
        if ((page + 1) * 45 < speciesListSize) {
            layout[nextPageSlot] = CustomGui.createPlayerHeadButton(
                "NextPageTexture",
                Text.literal("Next").styled { it.withColor(Formatting.GREEN).withBold(false).withItalic(false) },
                listOf(Text.literal("Click to go to the next page").styled { it.withColor(Formatting.GRAY).withItalic(false) }),
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGU0MDNjYzdiYmFjNzM2NzBiZDU0M2Y2YjA5NTViYWU3YjhlOTEyM2Q4M2JkNzYwZjYyMDRjNWFmZDhiZTdlMSJ9fX0="
            )
        } else {
            layout[nextPageSlot] = createFillerPane()
        }

        return layout
    }


    private fun refreshGuiItems(player: ServerPlayerEntity, selectedPokemon: List<PokemonSpawnEntry>, page: Int) {
        val layout = generateFullGuiLayout(selectedPokemon, page)
        CustomGui.refreshGui(player, layout)
    }

    private fun handleButtonClick(
        context: InteractionContext,
        player: ServerPlayerEntity,
        spawnerPos: BlockPos,
        selectedPokemon: List<PokemonSpawnEntry>,
        page: Int
    ) {
        val clickedSlot = context.slotIndex
        val currentPage = playerPages[player] ?: 0
        val speciesListSize = getSortedSpeciesList(selectedPokemon).size

        when (clickedSlot) {
            45 -> { // Previous Page Button
                if (currentPage > 0) {
                    playerPages[player] = currentPage - 1
                    refreshGuiItems(player, selectedPokemon, currentPage - 1)
                }
            }
            48 -> { // Sort Method Button
                if (context.clickType == ClickType.LEFT) {
                    sortMethod = getNextSortMethod(sortMethod)
                    CobbleSpawnersConfig.saveSpawnerData()
                    refreshGuiItems(player, selectedPokemon, currentPage)
                    player.sendMessage(Text.literal("Sort method changed to ${sortMethod.name}"), false)
                } else if (context.clickType == ClickType.RIGHT) {
                    // Placeholder for opening a new GUI for sorting letters
                    SortGui.openSortGui(player, spawnerPos)
                }
            }
            49 -> { // Spawner Menu Button
                SpawnerListGui.openSpawnerListGui(player)
            }
            50 -> { // Edit Spawner Settings Button
                SpawnerSettingsGui.openSpawnerSettingsGui(player, spawnerPos)
            }
            53 -> { // Next Page Button
                if ((currentPage + 1) * 45 < speciesListSize) {
                    playerPages[player] = currentPage + 1
                    refreshGuiItems(player, selectedPokemon, currentPage + 1)
                }
            }
            else -> {
                // Handle Pokémon selection/deselection logic
                val clickedItem = context.clickedStack
                if (clickedItem.item is PokemonItem) {
                    val clickedItemName = CustomGui.stripFormatting(clickedItem.name?.string ?: "")
                    // Extract species and form names
                    val regex = Regex("(.*) \\((.*)\\)")
                    val matchResult = regex.find(clickedItemName)
                    val speciesName: String
                    val formName: String?

                    if (matchResult != null) {
                        val (name, form) = matchResult.destructured
                        speciesName = name
                        formName = if (form.equals("Normal", ignoreCase = true)) "Normal" else form
                    } else {
                        speciesName = clickedItemName
                        formName = "Normal"
                    }

                    when (context.clickType) {
                        ClickType.LEFT -> {
                            val existingEntry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, speciesName, formName)

                            if (existingEntry == null) {
                                // Use our new helper function to add Pokémon with defaults
                                if (CobbleSpawnersConfig.addDefaultPokemonToSpawner(spawnerPos, speciesName, formName)) {
                                    player.sendMessage(
                                        Text.literal("Added $speciesName${if (formName != null) " ($formName)" else ""} to the spawner."),
                                        false
                                    )
                                }
                            } else {
                                // Use our helper function to remove Pokémon
                                if (CobbleSpawnersConfig.removeAndSavePokemonFromSpawner(spawnerPos, speciesName, formName)) {
                                    player.sendMessage(
                                        Text.literal("Removed $speciesName${if (formName != null) " ($formName)" else ""} from the spawner."),
                                        false
                                    )
                                }
                            }

                            // Get fresh spawner data and refresh GUI
                            val updatedSpawnerData = CobbleSpawnersConfig.getSpawner(spawnerPos)
                            if (updatedSpawnerData != null) {
                                refreshGuiItems(player, updatedSpawnerData.selectedPokemon, currentPage)
                            }
                        }
                        ClickType.RIGHT -> {
                            val existingEntry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, speciesName, formName)
                            if (existingEntry != null) {
                                closeMainGuiAndOpenSubGui(player, spawnerPos, speciesName, formName)
                            }
                        }
                        else -> {}
                    }
                }
            }

        }
    }

    private fun closeMainGuiAndOpenSubGui(player: ServerPlayerEntity, spawnerPos: BlockPos, pokemonName: String, formName: String?) {
        CustomGui.closeGui(player)
        openPokemonEditSubGui(player, spawnerPos, pokemonName, formName)
    }

    fun openPokemonEditSubGui(player: ServerPlayerEntity, spawnerPos: BlockPos, pokemonName: String, formName: String?) {
        val selectedEntry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName)
        if (selectedEntry == null) {
            player.sendMessage(Text.literal("Pokemon '$pokemonName' with form '${formName ?: "Normal"}' not found in spawner."), false)
            return
        }

        spawnerGuisOpen[spawnerPos] = player

        val layout = generateSubGuiLayout(selectedEntry)

        val onInteract: (InteractionContext) -> Unit = { context ->
            val clickedSlot = context.slotIndex

            when (clickedSlot) {
                11 -> {
                    CustomGui.closeGui(player)
                    IVSettingsGui.openIVEditorGui(player, spawnerPos, pokemonName, formName)
                }
                13 -> {
                    CustomGui.closeGui(player)
                    EVSettingsGui.openEVEditorGui(player, spawnerPos, pokemonName, formName)
                }
                15 -> {
                    CustomGui.closeGui(player)
                    SpawnSettingsGui.openSpawnShinyEditorGui(player, spawnerPos, pokemonName, formName)
                }
                21 -> {
                    CustomGui.closeGui(player)
                    SizeSettingsGui.openSizeEditorGui(player, spawnerPos, pokemonName, formName)
                }
                23 -> {
                    CustomGui.closeGui(player)
                    CaptureSettingsGui.openCaptureSettingsGui(player, spawnerPos, pokemonName, formName)
                }
                31 -> {
                    CustomGui.closeGui(player)
                    OtherSettingsGui.openOtherEditableGui(player, spawnerPos, pokemonName, formName)
                }
                49 -> {
                    player.sendMessage(Text.literal("Returning to Pokémon List"), false)
                    CustomGui.closeGui(player)
                    openSpawnerGui(player, spawnerPos, playerPages[player] ?: 0)
                }
            }
        }

        val onClose: (Inventory) -> Unit = {
            spawnerGuisOpen.remove(spawnerPos)
            player.sendMessage(Text.literal("Sub GUI closed for ${selectedEntry.pokemonName} (${selectedEntry.formName ?: "Normal"})"), false)
        }

        val subGuiTitle = "Edit Pokémon: ${selectedEntry.pokemonName} (${selectedEntry.formName ?: "Normal"})"

        CustomGui.openGui(
            player,
            subGuiTitle,
            layout,
            onInteract,
            onClose
        )
    }

    private fun generateSubGuiLayout(selectedEntry: PokemonSpawnEntry): List<ItemStack> {
        val layout = MutableList(54) { ItemStack.EMPTY }

        for (i in layout.indices) {
            if (i !in listOf(11, 13, 15, 21, 23, 31, 49)) {
                layout[i] = createFillerPane()
            }
        }

        // Replace with Player Head Button for Edit IVs
        layout[11] = CustomGui.createPlayerHeadButton(
            "EditIVsTexture",
            Text.literal("Edit IVs").styled { it.withColor(Formatting.GREEN).withBold(false).withItalic(false) },
            listOf(
                Text.literal("§7Adjust individual values"),
                Text.literal("§7for each stat (HP, Attack, etc.)")
            ),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTUyODUyNWMwYTc4Mzk4NmQxODYzYjU4YmJiNTExZjgwYWI0MTFkOGRhYTk2Zjg3NGJjZDlmNjU4ODA5YThhOSJ9fX0=" // Replace with actual Base64 texture URL
        )

        // Replace with Player Head Button for Edit EVs
        layout[13] = CustomGui.createPlayerHeadButton(
            "EditEVsTexture",
            Text.literal("Edit EVs").styled { it.withColor(Formatting.BLUE).withBold(false).withItalic(false) },
            listOf(
                Text.literal("§7Adjust effort values"),
                Text.literal("§7earned through battles")
            ),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmI2ZjE3ZjdhNDlkNmEwMmM4NTlmZTcxNWNjM2VhM2I5ZTUxZjE0MzA2MDJhNjAxODhlZDcwZDU3ZDBmNzcwMyJ9fX0=" // Replace with actual Base64 texture URL
        )

        // Replace with Player Head Button for Edit Spawn/Shiny Chances
        layout[15] = CustomGui.createPlayerHeadButton(
            "EditSpawnShinyTexture",
            Text.literal("Edit Spawn/Shiny/Level Chances").styled { it.withColor(Formatting.DARK_AQUA).withBold(false).withItalic(false) },
            listOf(
                Text.literal("§7Modify the spawn and"),
                Text.literal("§7shiny encounter chances")
            ),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2VhODYzYWY4ZTIwOGNjNzBkZjhlZmM4MzU5YzZhY2MwZGVkMzBkY2M1ODhiN2IwNGRjZGU1NWRjNjEzMmY1YyJ9fX0=" // Replace with actual Base64 texture URL
        )

        // Move Edit Size button to slot 21
        layout[21] = CustomGui.createPlayerHeadButton(
            "EditSizeTexture",
            Text.literal("Edit Size").styled { it.withColor(Formatting.GOLD).withBold(false).withItalic(false) },
            listOf(
                Text.literal("§7Adjust the size of the Pokémon"),
                Text.literal("§7within the spawner")
            ),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTQ5NGJhYWFhMjE3NmEwNDAyYzUyNzFmZWZlMTIyOTdhMWE1ODc3YzhlMGJkZmRkNGJlNThlZGVjZjYyY2Y4YyJ9fX0=" // Replace with actual Base64 texture URL
        )

        // Add new button for Edit Catchable Settings at slot 30
        layout[23] = CustomGui.createPlayerHeadButton(
            "EditCatchableSettingsTexture",
            Text.literal("Edit Catchable Settings").styled { it.withColor(Formatting.AQUA).withBold(false).withItalic(false) },
            listOf(
                Text.literal("§7Configure catchable settings"),
                Text.literal("§7for this Pokémon")
            ),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTY0YzQ0ODZmOTIzNmY5YTFmYjRiMjFiZjgyM2M1NTZkNmUxNWJmNjg4Yzk2ZDZlZjBkMTc1NTNkYjUwNWIifX19" // Replace with actual Base64 texture URL
        )

        // Move Edit Other Stats button to slot 23
        layout[31] = CustomGui.createPlayerHeadButton(
            "EditOtherStatsTexture",
            Text.literal("Edit Other Stats").styled { it.withColor(Formatting.LIGHT_PURPLE).withBold(false).withItalic(false) },
            listOf(
                Text.literal("§7Change level, catchability,"),
                Text.literal("§7and other miscellaneous stats")
            ),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDMzZjVjYzlkZTM1ODVkOGY2NDMzMGY0NDY4ZDE1NmJhZjAzNGEyNWRjYjc3M2MwNDc5ZDdjYTUyNmExM2Q2MSJ9fX0=" // Replace with actual Base64 texture URL
        )
        // Replace with Player Head Button for Back
        layout[49] = CustomGui.createPlayerHeadButton(
            "BackButtonTexture",
            Text.literal("Back").styled { it.withColor(Formatting.RED).withBold(false).withItalic(false) },
            listOf(
                Text.literal("§7Return to the main GUI")
            ),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0=" // Replace with actual Base64 texture URL
        )

        return layout
    }


    private fun generatePokemonItemsForGui(
        selectedPokemon: List<PokemonSpawnEntry>,
        page: Int
    ): List<ItemStack> {
        val totalSlots = 54
        val layout = MutableList(totalSlots) { ItemStack.EMPTY }
        val pageSize = 45

        val speciesFormsList = getSortedSpeciesList(selectedPokemon)

        val start = page * pageSize
        val end = minOf(start + pageSize, speciesFormsList.size)

        for (i in start until end) {
            val speciesForm = speciesFormsList[i]
            val species = speciesForm.species
            val form = speciesForm.form

            val isSelected = selectedPokemon.any {
                it.pokemonName.equals(species.name, ignoreCase = true) &&
                        (it.formName?.equals(form.name, ignoreCase = true) ?: (form.name == "Standard" || form.name == "Normal"))
            }

            val itemStack = if (isSelected) {
                createSelectedPokemonItem(species, form, selectedPokemon)
            } else {
                createUnselectedPokemonItem(species, form)
            }

            val slotIndex = i - start
            if (slotIndex in 0 until pageSize) {
                layout[slotIndex] = itemStack
            }
        }

        // Filler panes for the inventory
        val fillerSlots = listOf(46, 47, 51, 52)
        for (i in fillerSlots) {
            layout[i] = createFillerPane()
        }

        return layout
    }

    // Helper function to create selected Pokémon items
    private fun createSelectedPokemonItem(
        species: Species,
        form: FormData,
        selectedPokemon: List<PokemonSpawnEntry>
    ): ItemStack {
        val showFormsInGui = CobbleSpawnersConfig.config.globalConfig.showFormsInGui


        // Build the properties string
        val propertiesStringBuilder = StringBuilder(species.name)
        if (showFormsInGui && form.name != "Standard") {
            if (form.aspects.isNotEmpty()) {
                // Add required aspects to properties string
                for (aspect in form.aspects) {
                    propertiesStringBuilder.append(" ").append("$aspect=true")
                }
            } else {
                // If the form has no required aspects, set the form directly
                propertiesStringBuilder.append(" form=${form.formOnlyShowdownId()}")
            }
        }

        // Parse the properties string
        val properties = PokemonProperties.parse(propertiesStringBuilder.toString())
        val pokemon = properties.create()
        val selectedItem = PokemonItem.from(pokemon, tint = Vector4f(1.0f, 1.0f, 1.0f, 1.0f))
        CustomGui.addEnchantmentGlint(selectedItem)

        val entry = selectedPokemon.find {
            it.pokemonName.equals(species.name, ignoreCase = true) &&
                    (it.formName?.equals(form.name, ignoreCase = true) ?: (form.name == "Standard" || form.name == "Normal"))
        }
        val chance = entry?.spawnChance ?: 50.0
        val shinyChance = entry?.shinyChance ?: 0.0
        val minLevel = entry?.minLevel ?: 1
        val maxLevel = entry?.maxLevel ?: 100
        val spawnTime = entry?.spawnSettings?.spawnTime ?: "ALL"
        val spawnWeather = entry?.spawnSettings?.spawnWeather ?: "ALL"

        val pokemonLore = listOf(
            "§2Type: §a${species.primaryType.name}",
            species.secondaryType?.let { "§2Secondary Type: §a${it.name}" } ?: "",
            if (showFormsInGui && form.name != "Standard") "§2Form: §a${form.name}" else "",
            "----------------",
            "§6Spawn Chance: §e$chance%",
            "§bShiny Chance: §3%.2f%%".format(shinyChance),
            "§dMin Level: §f$minLevel",
            "§dMax Level: §f$maxLevel",
            "§9Spawn Time: §b$spawnTime",
            "§3Spawn Weather: §b$spawnWeather",
            "----------------",
            "§e§lLeft-click§r to §cDeselect",
            "§e§lRight-click§r to §aEdit stats and properties"
        ).filter { it.isNotEmpty() }

        val displayFormName = if (form.name == "Standard") "Normal" else form.name

        val displayName = if (showFormsInGui || form.name != "Standard") {
            "${species.name} ($displayFormName)"
        } else {
            species.name
        }
        selectedItem.setCustomName(Text.literal("§f§n$displayName"))
        CustomGui.setItemLore(selectedItem, pokemonLore)
        return selectedItem
    }

    // Helper function to create unselected Pokémon items
    private fun createUnselectedPokemonItem(species: Species, form: FormData): ItemStack {
        val showFormsInGui = CobbleSpawnersConfig.config.globalConfig.showFormsInGui


        // Build the properties string
        val propertiesStringBuilder = StringBuilder(species.name)
        if (showFormsInGui && form.name != "Standard") {
            if (form.aspects.isNotEmpty()) {
                // Add required aspects to properties string
                for (aspect in form.aspects) {
                    propertiesStringBuilder.append(" ").append("$aspect=true")
                }
            } else {
                // If the form has no required aspects, set the form directly
                propertiesStringBuilder.append(" form=${form.formOnlyShowdownId()}")
            }
        }

        // Parse the properties string
        val properties = PokemonProperties.parse(propertiesStringBuilder.toString())
        val pokemon = properties.create()
        val unselectedItem = PokemonItem.from(pokemon, tint = Vector4f(0.3f, 0.3f, 0.3f, 1f))

        val pokemonLore = listOf(
            "§aType: §f${species.primaryType.name}",
            species.secondaryType?.let { "§aSecondary Type: §f${it.name}" } ?: "",
            if (showFormsInGui && form.name != "Standard") "§aForm: §f${form.name}" else "",
            "----------------",
            "§e§lLeft-click§r to §aSelect"
        ).filter { it.isNotEmpty() }

        val displayFormName = if (form.name == "Standard") "Normal" else form.name

        val displayName = if (showFormsInGui || form.name != "Standard") {
            "${species.name} ($displayFormName)"
        } else {
            species.name
        }
        unselectedItem.setCustomName(Text.literal("§f$displayName"))
        CustomGui.setItemLore(unselectedItem, pokemonLore)
        return unselectedItem
    }

    // Helper function to create filler panes
    private fun createFillerPane(): ItemStack {
        return ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
            setCustomName(Text.literal(" ")) // A simple blank filler item
        }
    }

    // Helper function to determine the next sort method
    private fun getNextSortMethod(currentSort: SortMethod): SortMethod {
        return when (currentSort) {
            SortMethod.ALPHABETICAL -> SortMethod.TYPE
            SortMethod.TYPE -> SortMethod.SELECTED
            SortMethod.SELECTED -> SortMethod.ALPHABETICAL
            else -> SortMethod.ALPHABETICAL // For now, don't include letters in the sort cycle
        }
    }


    fun getSortedSpeciesList(selectedPokemon: List<PokemonSpawnEntry>): List<SpeciesForm> {
        val showUnimplemented = CobbleSpawnersConfig.config.globalConfig.showUnimplementedPokemonInGui
        val showFormsInGui = CobbleSpawnersConfig.config.globalConfig.showFormsInGui

        val speciesList = PokemonSpecies.species.filter { species ->
            showUnimplemented || species.implemented
        }

        val speciesFormsList = speciesList.flatMap { species ->
            val forms = if (showFormsInGui) {
                if (species.forms.isNotEmpty()) {
                    species.forms
                } else {
                    listOf(species.standardForm)
                }
            } else {
                listOf(species.standardForm)
            }

            forms.map { form ->
                SpeciesForm(species, form)
            }
        }

        // Always start with a fresh alphabetically sorted list
        val sortedSpeciesFormsList = speciesFormsList.sortedBy { it.species.name }

        return when (sortMethod) {
            SortMethod.ALPHABETICAL -> sortedSpeciesFormsList
            SortMethod.TYPE -> sortedSpeciesFormsList.sortedBy { it.species.primaryType.name }
            SortMethod.SELECTED -> {
                val selectedSet = selectedPokemon.map {
                    val formName = it.formName ?: "Normal"
                    "${it.pokemonName.lowercase()}_${formName.lowercase()}"
                }.toSet()
                val selectedList = sortedSpeciesFormsList.filter {
                    val formName = if (it.form.name == "Standard") "Normal" else it.form.name
                    selectedSet.contains("${it.species.name.lowercase()}_${formName.lowercase()}")
                }
                val unselectedList = sortedSpeciesFormsList.filter {
                    val formName = if (it.form.name == "Standard") "Normal" else it.form.name
                    !selectedSet.contains("${it.species.name.lowercase()}_${formName.lowercase()}")
                }.shuffled()
                selectedList + unselectedList
            }
            else -> {
                // Handle the sorting when a specific letter is selected
                if (sortMethod.name.startsWith("LETTER_")) {
                    val selectedLetter = sortMethod.name.last().uppercaseChar()

                    // Partition the alphabetically sorted list into two:
                    // 1. Starting from the selected letter onwards.
                    // 2. The rest of the list before the selected letter.
                    val (startingWithLetter, rest) = sortedSpeciesFormsList.partition {
                        it.species.name.first().uppercaseChar() >= selectedLetter
                    }

                    // Concatenate the two lists: starting with the letter and wrapping around
                    return startingWithLetter + rest
                } else {
                    sortedSpeciesFormsList
                }
            }
        }
    }
}
