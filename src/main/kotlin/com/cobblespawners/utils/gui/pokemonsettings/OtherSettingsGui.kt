package com.cobblespawners.utils.gui.pokemonsettings

import com.cobblespawners.utils.*
import com.cobblespawners.utils.gui.SpawnerPokemonSelectionGui
import com.blanketutils.gui.CustomGui
import com.blanketutils.gui.InteractionContext
import com.blanketutils.gui.setCustomName
import com.cobblespawners.utils.gui.SpawnerPokemonSelectionGui.spawnerGuisOpen
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import net.minecraft.util.Formatting
import org.slf4j.LoggerFactory

object OtherSettingsGui {
    private val logger = LoggerFactory.getLogger(OtherSettingsGui::class.java)

    /**
     * Opens the Other Editable GUI for a specific Pokémon and form.
     */
    fun openOtherEditableGui(player: ServerPlayerEntity, spawnerPos: BlockPos, pokemonName: String, formName: String?) {
        val selectedEntry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName)
        if (selectedEntry == null) {
            player.sendMessage(
                Text.literal("Pokémon '$pokemonName' with form '${formName ?: "Standard"}' not found in spawner."),
                false
            )
            logger.warn("Pokémon '$pokemonName' with form '${formName ?: "Standard"}' not found in spawner at $spawnerPos.")
            return
        }

        val layout = generateOtherEditableLayout(selectedEntry)

        SpawnerPokemonSelectionGui.spawnerGuisOpen[spawnerPos] = player

        val onInteract: (InteractionContext) -> Unit = { context ->
            val clickedItem = context.clickedStack
            val clickedName = clickedItem.name?.string ?: ""

            logger.debug("Player clicked on item: $clickedName")

            when (clickedName) {
                "Spawn Time" -> {
                    logger.debug("Toggle Spawn Time triggered.")
                    toggleSpawnTime(
                        spawnerPos,
                        pokemonName,
                        formName,
                        player,
                        context.slotIndex
                    )
                }
                "Spawn Weather" -> {
                    logger.debug("Toggle Spawn Weather triggered.")
                    toggleSpawnWeather(
                        spawnerPos,
                        pokemonName,
                        formName,
                        player,
                        context.slotIndex
                    )
                }
                "Spawn Location" -> {
                    logger.debug("Toggle Spawn Location triggered.")
                    toggleSpawnLocation(
                        spawnerPos,
                        pokemonName,
                        formName,
                        player,
                        context.slotIndex
                    )
                }
                "Back" -> {
                    logger.debug("Back button clicked.")
                    CustomGui.closeGui(player)
                    player.sendMessage(Text.literal("Returning to Edit Pokémon menu"), false)
                    SpawnerPokemonSelectionGui.openPokemonEditSubGui(
                        player,
                        spawnerPos,
                        pokemonName,
                        formName
                    )
                }
                else -> {
                    logger.warn("Clicked item with unexpected name: $clickedName")
                }
            }
        }

        val onClose: (Inventory) -> Unit = {
            spawnerGuisOpen.remove(spawnerPos)
            player.sendMessage(
                Text.literal("Other Editable GUI closed for $pokemonName (${selectedEntry.formName ?: "Standard"})"),
                false
            )
        }

        CustomGui.openGui(
            player,
            "Edit Other Properties for $pokemonName (${selectedEntry.formName ?: "Standard"})",
            layout,
            onInteract,
            onClose
        )
    }

    /**
     * Generates the layout for the Other Editable GUI using Player Heads.
     */
    private fun generateOtherEditableLayout(selectedEntry: PokemonSpawnEntry): List<ItemStack> {
        val layout = MutableList(54) { ItemStack.EMPTY }

        // Player Head buttons for each setting
        layout[24] = createSpawnTimeHeadButton(selectedEntry.spawnSettings.spawnTime)
        layout[20] = createSpawnWeatherHeadButton(selectedEntry.spawnSettings.spawnWeather)
        layout[31] = createSpawnLocationHeadButton(selectedEntry.spawnSettings.spawnLocation)

        // Fill the rest with gray stained glass panes except for the toggle buttons and back button
        for (i in 0 until 54) {
            if (i !in listOf(20, 24, 31, 49)) {
                layout[i] = ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
                    setCustomName(Text.literal(" "))
                }
            }
        }

        // Back Button as a Player Head
        layout[49] = createBackHeadButton()

        return layout
    }

    /**
     * Creates a Spawn Time toggle Player Head.
     */
    private fun createSpawnTimeHeadButton(spawnTime: String): ItemStack {
        val textureValue = when (spawnTime) {
            "DAY" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWQyZmYwOWNmNmU3OTNjYTg4NzFiNDYwNzBkMWE1ODJmZGMxNmU3YjlmYmE2N2QzYzA4ZjE1YzZlNDdlYjY0NSJ9fX0=" // Replace with actual base64 texture
            "NIGHT" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTk1MTRiMGY2N2E4YTFhMGJmODNjMmY4ODE3NTViNjA1MWIyYmQ0MmVlMzMwMjM0NGM1MzE1YWI3ZWQzNjk2ZSJ9fX0=" // Replace with actual base64 texture
            else -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDY2MzM5ZWQxYmEzOGY0Mzk5MWQzMDM3OTAyYzBhNWUzMjA0MzE1OGFkZDBjOTQ2MTZlYjMyZmVhYmZlNTc5YyJ9fX0=" // Replace with actual base64 texture
        }

        return CustomGui.createPlayerHeadButton(
            "SpawnTimeHead",
            Text.literal("Spawn Time").styled { it.withColor(Formatting.WHITE).withBold(true) },
            listOf(
                Text.literal("§aCurrent: ${spawnTime.capitalize()}"),
                Text.literal("§eClick to toggle spawn time")
            ),
            textureValue
        )
    }

    /**
     * Creates a Spawn Weather toggle Player Head.
     */
    private fun createSpawnWeatherHeadButton(spawnWeather: String): ItemStack {
        val textureValue = when (spawnWeather) {
            "CLEAR" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjUwZTI3NmZhMTc4NjVmNGZkZjI4MjMxZjBlNGQzODlhMDUyYjAzZTlhZjE0MzhkMzExMTk5ZGU3ODY3MmFjZSJ9fX0=" // Replace with actual base64 texture
            "RAIN" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTI5MmQxNzI2MTcxYWJhYmY3M2Y4NDQxMTU0Y2Y3YjcyZWUyZTBlNDY0NGQ2ZWUwODM4ZDc2MGRjMzQ4OWM5MiJ9fX0=" // Replace with actual base64 texture
            "THUNDER" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzNkNjlhNjBkOTcwYWQwYjhhYTE1ODk3OTE0ZjVhYWMyNjVlOTllNmY1MDE2YTdkOGFhN2JlOWFjMDNiNjE0OCJ9fX0=" // Replace with actual base64 texture
            else -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjVmMzc4MjYxNjFjNzkyZDdmNmM5MjBiMmZhMDZiODhlNjg0NTI4OGFiMDJhZDliNjVkNGJiZjVjYTJjZTFlMyJ9fX0=" // Replace with actual base64 texture
        }

        return CustomGui.createPlayerHeadButton(
            "SpawnWeatherHead",
            Text.literal("Spawn Weather").styled { it.withColor(Formatting.WHITE).withBold(true) },
            listOf(
                Text.literal("§aCurrent: ${spawnWeather.capitalize()}"),
                Text.literal("§eClick to toggle spawn weather")
            ),
            textureValue
        )
    }

    /**
     * Creates a Spawn Location toggle Player Head.
     */
    private fun createSpawnLocationHeadButton(spawnLocation: String): ItemStack {
        val textureValue = when (spawnLocation) {
            "SURFACE" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzAyNzU4YjJkZjU2ZTg1MGZmMTZhMDVhODExNTk2MmUyYmEyZTdiYWNhYjIwZjcwODVmMGQ0YjUzYmJiODA1YyJ9fX0=" // Replace with actual base64 texture
            "UNDERGROUND" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2M1ODNmNzE1MDlkMDI2MmUzZGMzZjFkMWE0YzZhMWZhNjA0ZWMxN2NjMjA4NjVkOGE2ZDdiOWM2YTQ4YWUwYSJ9fX0=" // Replace with actual base64 texture
            "WATER" -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzcyNWM4YWRiOWZlNmIzNGI0ODc0MGExMzBjZWM0NGIyODI1ZmUzMmRhZDE5ODU3MDA1MGVlNGI0ZWRhZGYzMyJ9fX0=" // Replace with actual base64 texture
            else -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDVlZDJlZjMyN2RkYTZmMmRlYmU3YzI0MWNmMjFjNWVmMGI3MzdiZjYxMTc4N2ZlNGJmNTM5YzhhNTcyMDM2In19fQ==" // Replace with actual base64 texture
        }

        return CustomGui.createPlayerHeadButton(
            "SpawnLocationHead",
            Text.literal("Spawn Location").styled { it.withColor(Formatting.WHITE).withBold(true) },
            listOf(
                Text.literal("§aCurrent: ${spawnLocation.capitalize()}"),
                Text.literal("§eClick to toggle spawn location")
            ),
            textureValue
        )
    }

    /**
     * Creates a Back button as a Player Head.
     */
    private fun createBackHeadButton(): ItemStack {
        val textureValue = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0=" // Replace with actual base64 texture

        return CustomGui.createPlayerHeadButton(
            "BackButton",
            Text.literal("Back").styled { it.withColor(Formatting.WHITE).withBold(false) },
            listOf(Text.literal("§eClick to return to the previous menu")),
            textureValue
        )
    }

    /**
     * Toggles the spawnLocation property of the selectedEntry.
     */
    private fun toggleSpawnLocation(
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        player: ServerPlayerEntity,
        locationSlot: Int
    ) {
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName) { selectedEntry ->
            selectedEntry.spawnSettings.spawnLocation = when (selectedEntry.spawnSettings.spawnLocation) {
                "SURFACE" -> "UNDERGROUND"
                "UNDERGROUND" -> "WATER"
                "WATER" -> "ALL"
                else -> "SURFACE"
            }
        } ?: run {
            player.sendMessage(Text.literal("Failed to toggle spawn location."), false)
            return
        }

        // Update the location head to reflect the new spawn location
        val updatedEntry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName)
        if (updatedEntry != null) {
            val locationHead = createSpawnLocationHeadButton(updatedEntry.spawnSettings.spawnLocation)

            // Update the GUI with the new location head without closing
            val screenHandler = player.currentScreenHandler
            if (locationSlot < screenHandler.slots.size) {
                screenHandler.slots[locationSlot].stack = locationHead
            }

            screenHandler.sendContentUpdates()

            logger.info(
                "Toggled spawn location for $pokemonName (${updatedEntry.formName ?: "Standard"}) at spawner $spawnerPos to ${updatedEntry.spawnSettings.spawnLocation}."
            )

            // Notify the player
            player.sendMessage(
                Text.literal("Set spawn location to ${updatedEntry.spawnSettings.spawnLocation} for $pokemonName."),
                false
            )
        }
    }

    /**
     * Toggles the spawnTime property of the selectedEntry.
     */
    private fun toggleSpawnTime(
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        player: ServerPlayerEntity,
        clockSlot: Int
    ) {
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName) { selectedEntry ->
            selectedEntry.spawnSettings.spawnTime = when (selectedEntry.spawnSettings.spawnTime) {
                "DAY" -> "NIGHT"
                "NIGHT" -> "ALL"
                else -> "DAY"
            }
        } ?: run {
            player.sendMessage(Text.literal("Failed to toggle spawn time."), false)
            return
        }

        // Update the spawn time head to reflect the new spawn time
        val updatedEntry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName)
        if (updatedEntry != null) {
            val spawnTimeHead = createSpawnTimeHeadButton(updatedEntry.spawnSettings.spawnTime)

            // Update the GUI with the new spawn time head without closing
            val screenHandler = player.currentScreenHandler
            if (clockSlot < screenHandler.slots.size) {
                screenHandler.slots[clockSlot].stack = spawnTimeHead
            }

            screenHandler.sendContentUpdates()

            logger.info(
                "Toggled spawn time for $pokemonName (${updatedEntry.formName ?: "Standard"}) at spawner $spawnerPos to ${updatedEntry.spawnSettings.spawnTime}."
            )

            // Notify the player
            player.sendMessage(
                Text.literal("Set spawn time to ${updatedEntry.spawnSettings.spawnTime} for $pokemonName."),
                false
            )
        }
    }

    /**
     * Toggles the spawnWeather property of the selectedEntry.
     */
    private fun toggleSpawnWeather(
        spawnerPos: BlockPos,
        pokemonName: String,
        formName: String?,
        player: ServerPlayerEntity,
        weatherSlot: Int
    ) {
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName) { selectedEntry ->
            selectedEntry.spawnSettings.spawnWeather = when (selectedEntry.spawnSettings.spawnWeather) {
                "CLEAR" -> "RAIN"
                "RAIN" -> "THUNDER"
                "THUNDER" -> "ALL"
                else -> "CLEAR"
            }
        } ?: run {
            player.sendMessage(Text.literal("Failed to toggle spawn weather."), false)
            return
        }

        // Update the spawn weather head to reflect the new spawn weather
        val updatedEntry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName)
        if (updatedEntry != null) {
            val spawnWeatherHead = createSpawnWeatherHeadButton(updatedEntry.spawnSettings.spawnWeather)

            // Update the GUI with the new spawn weather head without closing
            val screenHandler = player.currentScreenHandler
            if (weatherSlot < screenHandler.slots.size) {
                screenHandler.slots[weatherSlot].stack = spawnWeatherHead
            }

            screenHandler.sendContentUpdates()

            logger.info(
                "Toggled spawn weather for $pokemonName (${updatedEntry.formName ?: "Standard"}) at spawner $spawnerPos to ${updatedEntry.spawnSettings.spawnWeather}."
            )

            // Notify the player
            player.sendMessage(
                Text.literal("Set spawn weather to ${updatedEntry.spawnSettings.spawnWeather} for $pokemonName."),
                false
            )
        }
    }
}
