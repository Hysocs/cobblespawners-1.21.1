package com.cobblespawners.utils.gui

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.feature.ChoiceSpeciesFeatureProvider
import com.cobblemon.mod.common.api.pokemon.feature.SpeciesFeatures
import com.cobblemon.mod.common.item.PokemonItem
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import com.cobblespawners.utils.CobbleSpawnersConfig
import com.cobblespawners.utils.PokemonSpawnEntry
import com.cobblespawners.utils.gui.SpawnerPokemonSelectionGui.Constants.NORMAL_FORM
import com.cobblespawners.utils.gui.SpawnerPokemonSelectionGui.Constants.STANDARD_FORM
import com.everlastingutils.command.CommandManager
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
import org.joml.Vector4f
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

enum class SortMethod {
    ALPHABETICAL, TYPE, SELECTED, SEARCH
}

object SpawnerPokemonSelectionGui {

    var sortMethod = SortMethod.ALPHABETICAL
    var searchTerm = ""
    val playerPages = ConcurrentHashMap<ServerPlayerEntity, Int>()
    val spawnerGuisOpen = ConcurrentHashMap<BlockPos, ServerPlayerEntity>()

    private var cachedVariants: List<SpeciesFormVariant>? = null
    private var cachedSortMethod: SortMethod? = null
    private var cachedSearchTerm: String? = null
    private var cachedConfigKey: String? = null

    private val additionalAspectsCache = mutableMapOf<String, List<Set<String>>>()
    private val playerComputations = ConcurrentHashMap<ServerPlayerEntity, CompletableFuture<Void>>()

    private object Slots {
        const val PREV_PAGE = 45
        const val SORT_METHOD = 48
        const val SPAWNER_MENU = 49
        const val SPAWNER_SETTINGS = 50
        const val NEXT_PAGE = 53
    }

    private object Textures {
        const val PREV_PAGE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTMzYWQ1YzIyZGIxNjQzNWRhYWQ2MTU5MGFiYTUxZDkzNzkxNDJkZDU1NmQ2YzQyMmE3MTEwY2EzYWJlYTUwIn19fQ=="
        const val NEXT_PAGE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGU0MDNjYzdiYmFjNzM2NzBiZDU0M2Y2YjA5NTViYWU3YjhlOTEyM2Q4M2JkNzYwZjYyMDRjNWFmZDhiZTdlMSJ9fX0="
        const val SORT_METHOD = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWI1ZWU0MTlhZDljMDYwYzE2Y2I1M2IxZGNmZmFjOGJhY2EwYjJhMjI2NWIxYjZjN2U4ZTc4MGMzN2IxMDRjMCJ9fX0="
        const val SPAWNER_MENU = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGQ4YjUxZGM5NTljMzNjMjUxNWJhZDY1ODk5N2Y2Y2VlOWY4NmRmMGU3ODdiNmM2ZjhkNTA3MDY0N2JkYyJ9fX0="
        const val SPAWNER_SETTINGS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTdiMjE4OTMwMGYzMzliYTA1MGUwMWFlMmE1NDBiN2U4OWVmODk2YTU1Yzc5MTZkY2M5ZTU4NTFhZjg2NDExZSJ9fX0="
    }

    private object Constants {
        const val NORMAL_FORM = "Normal"
        const val STANDARD_FORM = "Standard"
        const val SHINY_ASPECT = "shiny"
    }

    data class SpeciesFormVariant(val species: Species, val form: FormData, val additionalAspects: Set<String>) {
        fun toKey(): String {
            val formName = if (form.name.equals(STANDARD_FORM, ignoreCase = true)) NORMAL_FORM else form.name
            return "${species.showdownId()}_${formName.lowercase()}_${additionalAspects.map { it.lowercase() }.sorted().joinToString(",")}"
        }
    }

    fun isSpawnerGuiOpen(spawnerPos: BlockPos): Boolean = spawnerGuisOpen.containsKey(spawnerPos)

    fun openSpawnerGui(player: ServerPlayerEntity, spawnerPos: BlockPos, page: Int = 0) {
        if (!CommandManager.hasPermissionOrOp(player.commandSource, "CobbleSpawners.Edit", 2, 2)) {
            player.sendMessage(Text.literal("You don't have permission to use this GUI."), false)
            return
        }

        invalidateCaches()

        val spawnerData = CobbleSpawnersConfig.spawners[spawnerPos] ?: run {
            player.sendMessage(Text.literal("Spawner data not found"), false)
            return
        }

        spawnerGuisOpen[spawnerPos] = player
        playerPages[player] = page

        CustomGui.openGui(
            player,
            "Select Pokémon for ${spawnerData.spawnerName}",
            generateFullGuiLayout(spawnerData.selectedPokemon, page),
            { context -> handleMainGuiInteraction(context, player, spawnerPos) },
            {
                spawnerGuisOpen.remove(spawnerPos)
                playerPages.remove(player)
            }
        )
    }

    private fun handleMainGuiInteraction(context: InteractionContext, player: ServerPlayerEntity, spawnerPos: BlockPos) {
        val currentPage = playerPages[player] ?: 0

        when (context.slotIndex) {
            Slots.PREV_PAGE -> if (currentPage > 0) {
                playerPages[player] = currentPage - 1
                refreshGuiItems(player, spawnerPos)
            }
            Slots.NEXT_PAGE -> {
                val spawnerData = CobbleSpawnersConfig.spawners[spawnerPos] ?: return
                val totalVariants = getTotalVariantsCount(spawnerData.selectedPokemon)
                if ((currentPage + 1) * 45 < totalVariants) {
                    playerPages[player] = currentPage + 1
                    refreshGuiItems(player, spawnerPos)
                }
            }
            Slots.SORT_METHOD -> handleSortInteraction(context, player, spawnerPos)
            Slots.SPAWNER_MENU -> handleMenuInteraction(context, player)
            Slots.SPAWNER_SETTINGS -> SpawnerSettingsGui.openSpawnerSettingsGui(player, spawnerPos)
            else -> handlePokemonItemClick(context, player, spawnerPos)
        }
    }

    private fun handleSortInteraction(context: InteractionContext, player: ServerPlayerEntity, spawnerPos: BlockPos) {
        when (context.clickType) {
            ClickType.LEFT -> {
                sortMethod = when (sortMethod) {
                    SortMethod.ALPHABETICAL -> SortMethod.TYPE
                    SortMethod.TYPE -> SortMethod.SELECTED
                    else -> SortMethod.ALPHABETICAL
                }
                if (sortMethod != SortMethod.SEARCH) searchTerm = ""
                if (sortMethod != SortMethod.SELECTED) invalidateCaches()

                refreshGuiItems(player, spawnerPos)
                player.sendMessage(Text.literal("Sort method changed to ${sortMethod.name}"), false)
            }
            ClickType.RIGHT -> SearchGui.openSearchGui(player, spawnerPos)
            else -> {}
        }
    }

    private fun handleMenuInteraction(context: InteractionContext, player: ServerPlayerEntity) {
        val spawnerPos = spawnerGuisOpen.filterValues { it == player }.keys.firstOrNull()
        when (context.clickType) {
            ClickType.LEFT -> GlobalSettingsGui.openGlobalSettingsGui(player, spawnerPos)
            ClickType.RIGHT -> SpawnerListGui.openSpawnerListGui(player)
            else -> {}
        }
    }

    private fun handlePokemonItemClick(context: InteractionContext, player: ServerPlayerEntity, spawnerPos: BlockPos) {
        val clickedItem = context.clickedStack
        if (clickedItem.item !is PokemonItem) return

        val (species, formName, aspects) = parsePokemonName(CustomGui.stripFormatting(clickedItem.name.string)) ?: return

        when (context.clickType) {
            ClickType.LEFT -> togglePokemonSelection(player, spawnerPos, species, formName, aspects)
            ClickType.RIGHT -> {
                if (CobbleSpawnersConfig.getPokemonSpawnEntry(spawnerPos, species.showdownId(), formName, aspects) != null) {
                    PokemonEditSubGui.openPokemonEditSubGui(player, spawnerPos, species.showdownId(), formName, aspects)
                }
            }
            else -> {}
        }
    }

    private fun togglePokemonSelection(player: ServerPlayerEntity, spawnerPos: BlockPos, species: Species, formName: String, aspects: Set<String>) {
        val showdownId = species.showdownId()
        val displaySuffix = createDisplaySuffix(formName, aspects)

        val wasRemoved = CobbleSpawnersConfig.removeAndSavePokemonFromSpawner(spawnerPos, showdownId, formName, aspects)

        if (!wasRemoved) {
            CobbleSpawnersConfig.addDefaultPokemonToSpawner(spawnerPos, showdownId, formName, aspects)
            player.sendMessage(Text.literal("Added ${species.name}$displaySuffix to the spawner."), false)
        } else {
            player.sendMessage(Text.literal("Removed ${species.name}$displaySuffix from the spawner."), false)
        }

        refreshGuiItems(player, spawnerPos)
    }

    private fun refreshGuiItems(player: ServerPlayerEntity, spawnerPos: BlockPos) {
        val spawnerData = CobbleSpawnersConfig.spawners[spawnerPos] ?: return
        val page = playerPages[player] ?: 0

        playerComputations[player]?.cancel(true)

        val future = CompletableFuture.runAsync {
            val items = generateFullGuiLayout(spawnerData.selectedPokemon, page)
            player.server.execute {
                CustomGui.refreshGui(player, items)
                playerComputations.remove(player)
            }
        }.exceptionally {
            playerComputations.remove(player)
            null
        }
        playerComputations[player] = future
    }

    private fun generateFullGuiLayout(selectedPokemon: List<PokemonSpawnEntry>, page: Int): List<ItemStack> {
        val layout = generatePokemonItemsForGui(selectedPokemon, page).toMutableList()
        val totalVariants = getTotalVariantsCount(selectedPokemon)

        layout[Slots.PREV_PAGE] = if (page > 0) createNavButton("Previous", Textures.PREV_PAGE) else createFillerPane()
        layout[Slots.NEXT_PAGE] = if ((page + 1) * 45 < totalVariants) createNavButton("Next", Textures.NEXT_PAGE) else createFillerPane()

        layout[Slots.SORT_METHOD] = createSortButton()
        layout[Slots.SPAWNER_MENU] = createMenuButton()
        layout[Slots.SPAWNER_SETTINGS] = createSettingsButton()

        listOf(46, 47, 51, 52).forEach { layout[it] = createFillerPane() }
        return layout
    }

    private fun generatePokemonItemsForGui(selectedPokemon: List<PokemonSpawnEntry>, page: Int): List<ItemStack> {
        val layout = MutableList(54) { ItemStack.EMPTY }
        val pageSize = 45
        val variants = getVariantsForPage(selectedPokemon, page, pageSize)

        variants.forEachIndexed { i, variant ->
            val isSelected = isPokemonSelected(variant, selectedPokemon)
            layout[i] = createPokemonItem(variant, isSelected, selectedPokemon)
        }
        return layout
    }

    private fun createPokemonItem(variant: SpeciesFormVariant, isSelected: Boolean, selectedPokemon: List<PokemonSpawnEntry>): ItemStack {
        val showForms = CobbleSpawnersConfig.config.globalConfig.showFormsInGui
        val properties = PokemonProperties.parse(buildPropertiesString(variant.species, variant.form, variant.additionalAspects, showForms))
        val pokemon = properties.create()
        val tint = if (isSelected) Vector4f(1.0f, 1.0f, 1.0f, 1.0f) else Vector4f(0.3f, 0.3f, 0.3f, 1f)
        val item = PokemonItem.from(pokemon, tint = tint)

        val displayName = buildDisplayName(variant.species, variant.form, variant.additionalAspects, showForms)
        val nameColor = if (isSelected) "§f§n" else "§f"
        item.setCustomName(Text.literal("$nameColor$displayName"))

        if (isSelected) {
            CustomGui.addEnchantmentGlint(item)
            val entry = findSelectedEntry(variant, selectedPokemon)
            CustomGui.setItemLore(item, createSelectedLore(variant, entry))
        } else {
            CustomGui.setItemLore(item, createUnselectedLore(variant))
        }

        return item
    }

    private fun createSelectedLore(variant: SpeciesFormVariant, entry: PokemonSpawnEntry?): List<String> {
        return mutableListOf<String>().apply {
            addAll(createBaseLore(variant, "§2", "§a"))
            add("----------------")
            add("§6Spawn Chance: §e${entry?.spawnChance ?: 50.0}%")
            add("§dMin Level: §f${entry?.minLevel ?: 1}")
            add("§dMax Level: §f${entry?.maxLevel ?: 100}")
            add("§9Spawn Time: §b${entry?.spawnSettings?.spawnTime ?: "ALL"}")
            add("§3Spawn Weather: §b${entry?.spawnSettings?.spawnWeather ?: "ALL"}")
            add("----------------")
            add("§e§lLeft-click§r to §cDeselect")
            add("§e§lRight-click§r to §aEdit")
        }.filter { it.isNotEmpty() }
    }

    private fun createUnselectedLore(variant: SpeciesFormVariant): List<String> {
        return mutableListOf<String>().apply {
            addAll(createBaseLore(variant, "§a", "§f"))
            add("----------------")
            add("§e§lLeft-click§r to §aSelect")
        }.filter { it.isNotEmpty() }
    }

    private fun createBaseLore(variant: SpeciesFormVariant, prefix: String, valuePrefix: String): List<String> {
        val lore = mutableListOf(
            "${prefix}Type: $valuePrefix${variant.species.primaryType.name}"
        )
        variant.species.secondaryType?.let { lore.add("${prefix}Secondary Type: $valuePrefix${it.name}") }

        val displayFormName = if (variant.form.name == Constants.STANDARD_FORM) Constants.NORMAL_FORM else variant.form.name
        val showForms = CobbleSpawnersConfig.config.globalConfig.showFormsInGui || variant.form.name != Constants.STANDARD_FORM
        if (showForms) lore.add("${prefix}Form: $valuePrefix$displayFormName")

        val aspects = getDisplayableAspects(variant.additionalAspects)
        if (aspects.isNotEmpty()) {
            lore.add("${prefix}Aspects: $valuePrefix${aspects.joinToString(", ")}")
        }

        return lore
    }

    private fun buildPropertiesString(species: Species, form: FormData, aspects: Set<String>, showForms: Boolean): String {
        return buildString {
            append(species.showdownId())
            if (showForms && form.name != Constants.STANDARD_FORM) {
                if (form.aspects.isNotEmpty()) {
                    form.aspects.forEach { append(" aspect=${it.lowercase()}") }
                } else {
                    append(" form=${form.formOnlyShowdownId()}")
                }
            }
            aspects.forEach { aspect ->
                if (aspect.contains("=")) append(" ${aspect.lowercase()}")
                else append(" aspect=${aspect.lowercase()}")
            }
        }
    }

    private fun buildDisplayName(species: Species, form: FormData, aspects: Set<String>, showForms: Boolean): String {
        val parts = mutableListOf<String>()
        val displayFormName = if (form.name == Constants.STANDARD_FORM) Constants.NORMAL_FORM else form.name

        if (showForms || form.name != Constants.STANDARD_FORM) {
            parts.add(displayFormName)
        }
        parts.addAll(getDisplayableAspects(aspects))

        return if (parts.isNotEmpty()) "${species.name} (${parts.joinToString(", ")})" else species.name
    }

    private fun getDisplayableAspects(aspects: Set<String>): List<String> {
        val showAllAspects = CobbleSpawnersConfig.config.globalConfig.showAspectsInGui
        return when {
            showAllAspects -> aspects.map { it.replaceFirstChar(Char::titlecase) }
            aspects.any { it.equals(Constants.SHINY_ASPECT, true) } -> listOf("Shiny")
            else -> emptyList()
        }
    }

    private fun getAllVariants(selectedPokemon: List<PokemonSpawnEntry>): List<SpeciesFormVariant> {
        val config = CobbleSpawnersConfig.config.globalConfig
        val configKey = "${config.showUnimplementedPokemonInGui}_${config.showFormsInGui}_${config.showAspectsInGui}"

        if (sortMethod != SortMethod.SELECTED && cachedVariants != null && cachedSortMethod == sortMethod && cachedSearchTerm == searchTerm && cachedConfigKey == configKey) {
            return cachedVariants!!
        }

        val speciesList = getSortedSpeciesList()

        val variantsList = speciesList.flatMap { species ->
            val forms = if (config.showFormsInGui && species.forms.isNotEmpty()) species.forms else listOf(species.standardForm)
            val aspectSets = if (config.showAspectsInGui) getAdditionalAspectSets(species) else emptyList()

            forms.flatMap { form ->
                val baseVariants = listOf(
                    SpeciesFormVariant(species, form, emptySet()),
                    SpeciesFormVariant(species, form, setOf(Constants.SHINY_ASPECT))
                )
                if (config.showAspectsInGui) {
                    (baseVariants + aspectSets.map { SpeciesFormVariant(species, form, it) }).distinctBy { it.toKey() }
                } else {
                    baseVariants
                }
            }
        }

        val result = if (sortMethod == SortMethod.SELECTED) {
            variantsList.filter { isPokemonSelected(it, selectedPokemon) }
        } else {
            variantsList
        }

        if (sortMethod != SortMethod.SELECTED) {
            cachedVariants = result
            cachedSortMethod = sortMethod
            cachedSearchTerm = searchTerm
            cachedConfigKey = configKey
        }
        return result
    }

    private fun getSortedSpeciesList(): List<Species> {
        val showUnimplemented = CobbleSpawnersConfig.config.globalConfig.showUnimplementedPokemonInGui
        val allSpecies = PokemonSpecies.species.filter { showUnimplemented || it.implemented }

        return when (sortMethod) {
            SortMethod.ALPHABETICAL -> allSpecies.sortedBy { it.name }
            SortMethod.TYPE -> allSpecies.sortedBy { it.primaryType.name }
            SortMethod.SELECTED -> allSpecies.sortedBy { it.name }
            SortMethod.SEARCH -> {
                if (searchTerm.isBlank()) allSpecies.sortedBy { it.name }
                else {
                    val term = searchTerm.lowercase()
                    allSpecies.filter { it.name.lowercase().contains(term) }.sortedBy { it.name }
                }
            }
        }
    }

    private fun getAdditionalAspectSets(species: Species): List<Set<String>> {
        return additionalAspectsCache.getOrPut(species.name.lowercase()) {
            val aspectSets = mutableSetOf(setOf(Constants.SHINY_ASPECT))
            val speciesSpecificAspects = mutableSetOf<String>()

            species.forms.forEach { it.aspects.forEach(speciesSpecificAspects::add) }

            SpeciesFeatures.getFeaturesFor(species)
                .filterIsInstance<ChoiceSpeciesFeatureProvider>()
                .forEach { provider -> provider.getAllAspects().forEach(speciesSpecificAspects::add) }

            speciesSpecificAspects.forEach { aspect ->
                aspectSets.add(setOf(aspect))
                aspectSets.add(setOf(aspect, Constants.SHINY_ASPECT))
            }
            aspectSets.distinctBy { it.toSortedSet().joinToString(",") }
        }
    }

    private fun getVariantsForPage(selected: List<PokemonSpawnEntry>, page: Int, pageSize: Int): List<SpeciesFormVariant> {
        val all = getAllVariants(selected)
        val start = page * pageSize
        val end = minOf(start + pageSize, all.size)
        return if (start < all.size) all.subList(start, end) else emptyList()
    }

    private fun getTotalVariantsCount(selected: List<PokemonSpawnEntry>): Int = getAllVariants(selected).size

    private fun invalidateCaches() { cachedVariants = null }

    private fun isPokemonSelected(variant: SpeciesFormVariant, selected: List<PokemonSpawnEntry>): Boolean {
        return selected.any {
            val formName = it.formName ?: Constants.NORMAL_FORM
            it.pokemonName.equals(variant.species.showdownId(), true) &&
                    (formName.equals(variant.form.name, true) || (formName == Constants.NORMAL_FORM && variant.form.name == Constants.STANDARD_FORM)) &&
                    it.aspects.map(String::lowercase).toSet() == variant.additionalAspects.map(String::lowercase).toSet()
        }
    }

    private fun findSelectedEntry(variant: SpeciesFormVariant, selected: List<PokemonSpawnEntry>): PokemonSpawnEntry? {
        return selected.find {
            val formName = it.formName ?: Constants.NORMAL_FORM
            it.pokemonName.equals(variant.species.showdownId(), true) &&
                    (formName.equals(variant.form.name, true) || (formName == Constants.NORMAL_FORM && variant.form.name == Constants.STANDARD_FORM)) &&
                    it.aspects.map(String::lowercase).toSet() == variant.additionalAspects.map(String::lowercase).toSet()
        }
    }

    private fun parsePokemonName(name: String): Triple<Species, String, Set<String>>? {
        val regex = Regex("(.*) \\((.*)\\)")
        val match = regex.find(name)

        val (speciesName, details) = if (match != null) match.groupValues[1] to match.groupValues[2] else name to ""
        val species = PokemonSpecies.species.find { it.name.equals(speciesName, true) } ?: return null

        val parts = details.split(", ").map { it.trim() }.filter { it.isNotEmpty() }
        val form = species.forms.find { f -> parts.any { p -> p.equals(f.name, true) } } ?: species.standardForm
        val formName = if (form.name == Constants.STANDARD_FORM) Constants.NORMAL_FORM else form.name

        val aspectParts = parts.toMutableSet()
        aspectParts.remove(formName)

        return Triple(species, formName, aspectParts)
    }

    private fun createDisplaySuffix(formName: String, aspects: Set<String>): String {
        val parts = mutableListOf<String>()
        if (formName != Constants.NORMAL_FORM) parts.add(formName)
        parts.addAll(aspects)
        return if (parts.isNotEmpty()) " (${parts.joinToString(", ")})" else ""
    }

    private fun createButton(text: String, color: Formatting, lore: List<String>, texture: String): ItemStack {
        val formattedLore = lore.map {
            if (it.startsWith("§")) Text.literal(it)
            else Text.literal(it).styled { s -> s.withColor(Formatting.GRAY).withItalic(false) }
        }
        return CustomGui.createPlayerHeadButton(
            text.replace(" ", ""),
            Text.literal(text).styled { it.withColor(color).withBold(false).withItalic(false) },
            formattedLore,
            texture
        )
    }

    private fun createSortButton(): ItemStack {
        val text = if (sortMethod == SortMethod.SEARCH) "Searching: ${if (searchTerm.length > 10) searchTerm.take(7) + "..." else searchTerm}" else "Sort Method"
        val lore = if (sortMethod == SortMethod.SEARCH) {
            listOf("Current Search: \"$searchTerm\"", "Left-click to clear search", "Right-click to search again")
        } else {
            listOf("Current Sort: ${sortMethod.name.replaceFirstChar(Char::titlecase)}", "Left-click to cycle", "Right-click to search")
        }
        return createButton(text, Formatting.AQUA, lore, Textures.SORT_METHOD)
    }

    private fun createMenuButton(): ItemStack {
        return createButton(
            "Global Settings", Formatting.GREEN,
            listOf("Left-click for Global Settings", "Right-click for Spawner List"), Textures.SPAWNER_MENU
        )
    }

    private fun createSettingsButton(): ItemStack {
        return createButton(
            "Edit Spawner Settings", Formatting.BLUE,
            listOf("Click to edit this spawner’s settings"), Textures.SPAWNER_SETTINGS
        )
    }

    private fun createNavButton(text: String, texture: String): ItemStack {
        return createButton(text, Formatting.GREEN, listOf("Click to go to the ${text.lowercase()} page"), texture)
    }

    private fun createFillerPane(): ItemStack {
        return ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
            setCustomName(Text.literal(" "))
        }
    }
}