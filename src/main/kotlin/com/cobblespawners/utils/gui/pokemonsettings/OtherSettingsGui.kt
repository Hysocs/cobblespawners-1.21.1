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
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos

object OtherSettingsGui {

    private object Slots {
        const val SPAWN_WEATHER = 20
        const val SPAWN_TIME = 24
        const val SPAWN_LOCATION = 31
        const val BACK_BUTTON = 49
    }

    private object Textures {
        const val TIME_DAY = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWQyZmYwOWNmNmU3OTNjYTg4NzFiNDYwNzBkMWE1ODJmZGMxNmU3YjlmYmE2N2QzYzA4ZjE1YzZlNDdlYjY0NSJ9fX0="
        const val TIME_NIGHT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTk1MTRiMGY2N2E4YTFhMGJmODNjMmY4ODE3NTViNjA1MWIyYmQ0MmVlMzMwMjM0NGM1MzE1YWI3ZWQzNjk2ZSJ9fX0="
        const val TIME_ALL = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDY2MzM5ZWQxYmEzOGY0Mzk5MWQzMDM3OTAyYzBhNWUzMjA0MzE1OGFkZDBjOTQ2MTZlYjMyZmVhYmZlNTc5YyJ9fX0="
        const val WEATHER_CLEAR = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjUwZTI3NmZhMTc4NjVmNGZkZjI4MjMxZjBlNGQzODlhMDUyYjAzZTlhZjE0MzhkMzExMTk5ZGU3ODY3MmFjZSJ9fX0="
        const val WEATHER_RAIN = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTI5MmQxNzI2MTcxYWJhYmY3M2Y4NDQxMTU0Y2Y3YjcyZWUyZTBlNDY0NGQ2ZWUwODM4ZDc2MGRjMzQ4OWM5MiJ9fX0="
        const val WEATHER_THUNDER = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzNkNjlhNjBkOTcwYWQwYjhhYTE1ODk3OTE0ZjVhYWMyNjVlOTllNmY1MDE2YTdkOGFhN2JlOWFjMDNiNjE0OCJ9fX0="
        const val WEATHER_ALL = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjVmMzc4MjYxNjFjNzkyZDdmNmM5MjBiMmZhMDZiODhlNjg0NTI4OGFiMDJhZDliNjVkNGJiZjVjYTJjZTFlMyJ9fX0="
        const val LOCATION_SURFACE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzAyNzU4YjJkZjU2ZTg1MGZmMTZhMDVhODExNTk2MmUyYmEyZTdiYWNhYjIwZjcwODVmMGQ0YjUzYmJiODA1YyJ9fX0="
        const val LOCATION_UNDERGROUND = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2M1ODNmNzE1MDlkMDI2MmUzZGMzZjFkMWE0YzZhMWZhNjA0ZWMxN2NjMjA4NjVkOGE2ZDdiOWM2YTQ4YWUwYSJ9fX0="
        const val LOCATION_WATER = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzcyNWM4YWRiOWZlNmIzNGI0ODc0MGExMzBjZWM0NGIyODI1ZmUzMmRhZDE5ODU3MDA1MGVlNGI0ZWRhZGYzMyJ9fX0="
        const val LOCATION_ALL = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDVlZDJlZjMyN2RkYTZmMmRlYmU3YzI0MWNmMjFjNWVmMGI3MzdiZjYxMTc4N2ZlNGJmNTM5YzhhNTcyMDM2In19fQ=="
        const val BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
    }

    private val timeCycle = listOf("ALL", "DAY", "NIGHT")
    private val weatherCycle = listOf("ALL", "CLEAR", "RAIN", "THUNDER")
    private val locationCycle = listOf("ALL", "SURFACE", "UNDERGROUND", "WATER")

    fun openOtherEditableGui(player: ServerPlayerEntity, spawnerPos: BlockPos, pokemonName: String, formName: String?, additionalAspects: Set<String>) {
        val entry = CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", additionalAspects)
        if (entry == null) {
            player.sendMessage(Text.literal("Error: Could not find the specified Pokémon in this spawner."), false)
            return
        }
        spawnerGuisOpen[spawnerPos] = player

        val aspectsDisplay = if (additionalAspects.isNotEmpty()) ", ${additionalAspects.joinToString(", ")}" else ""
        val guiTitle = "Edit Other: ${entry.pokemonName} (${entry.formName ?: "Standard"}$aspectsDisplay)"

        CustomGui.openGui(
            player,
            guiTitle,
            generateLayout(entry),
            { context -> handleInteraction(context, player, spawnerPos, pokemonName, formName, additionalAspects) },
            { spawnerGuisOpen.remove(spawnerPos) }
        )
    }

    private fun handleInteraction(context: InteractionContext, player: ServerPlayerEntity, spawnerPos: BlockPos, pokemonName: String, formName: String?, additionalAspects: Set<String>) {
        var changed = true
        when (context.slotIndex) {
            Slots.SPAWN_TIME -> toggleValue(spawnerPos, pokemonName, formName, additionalAspects, timeCycle) { it.spawnSettings::spawnTime }
            Slots.SPAWN_WEATHER -> toggleValue(spawnerPos, pokemonName, formName, additionalAspects, weatherCycle) { it.spawnSettings::spawnWeather }
            Slots.SPAWN_LOCATION -> toggleValue(spawnerPos, pokemonName, formName, additionalAspects, locationCycle) { it.spawnSettings::spawnLocation }
            Slots.BACK_BUTTON -> {
                CustomGui.closeGui(player)
                PokemonEditSubGui.openPokemonEditSubGui(player, spawnerPos, pokemonName, formName, additionalAspects)
                changed = false
            }
            else -> changed = false
        }
        if (changed) {
            refreshGui(player, spawnerPos, pokemonName, formName, additionalAspects)
        }
    }

    private fun generateLayout(entry: PokemonSpawnEntry): List<ItemStack> {
        val layout = MutableList(54) { createFillerPane() }
        val settings = entry.spawnSettings

        val timeTextures = mapOf("DAY" to Textures.TIME_DAY, "NIGHT" to Textures.TIME_NIGHT, "ALL" to Textures.TIME_ALL)
        val weatherTextures = mapOf("CLEAR" to Textures.WEATHER_CLEAR, "RAIN" to Textures.WEATHER_RAIN, "THUNDER" to Textures.WEATHER_THUNDER, "ALL" to Textures.WEATHER_ALL)
        val locationTextures = mapOf("SURFACE" to Textures.LOCATION_SURFACE, "UNDERGROUND" to Textures.LOCATION_UNDERGROUND, "WATER" to Textures.LOCATION_WATER, "ALL" to Textures.LOCATION_ALL)

        layout[Slots.SPAWN_TIME] = createCycleButton("Spawn Time", settings.spawnTime, timeTextures)
        layout[Slots.SPAWN_WEATHER] = createCycleButton("Spawn Weather", settings.spawnWeather, weatherTextures)
        layout[Slots.SPAWN_LOCATION] = createCycleButton("Spawn Location", settings.spawnLocation, locationTextures)
        layout[Slots.BACK_BUTTON] = createBackButton()

        return layout
    }

    private fun toggleValue(spawnerPos: BlockPos, pokemonName: String, formName: String?, additionalAspects: Set<String>, cycle: List<String>, property: (PokemonSpawnEntry) -> kotlin.reflect.KMutableProperty0<String>) {
        CobbleSpawnersConfig.updatePokemonSpawnEntry(spawnerPos, pokemonName, formName ?: "Standard", additionalAspects) { entry ->
            val prop = property(entry)
            val currentIndex = cycle.indexOf(prop.get())
            val nextIndex = (currentIndex + 1) % cycle.size
            prop.set(cycle[nextIndex])
        }
        CobbleSpawnersConfig.saveSpawnerData()
    }

    private fun createCycleButton(title: String, currentValue: String, textures: Map<String, String>): ItemStack {
        return createButton(
            title = Text.literal(title).formatted(Formatting.WHITE),
            lore = listOf(
                Text.literal("§aCurrent: §f${currentValue.replaceFirstChar { it.uppercase() }}"),
                Text.literal(""),
                Text.literal("§eClick to cycle")
            ),
            texture = textures[currentValue] ?: ""
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
        val layout = generateLayout(entry)
        layout.forEachIndexed { index, itemStack ->
            if (index < screenHandler.slots.size) {
                screenHandler.slots[index].stack = itemStack
            }
        }
        screenHandler.sendContentUpdates()
    }
}