package com.cobblespawners.utils.gui

import com.cobblespawners.CobbleSpawners
import com.cobblespawners.utils.CobbleSpawnersConfig
import com.cobblespawners.utils.CommandRegistrarUtil
import com.cobblespawners.utils.SpawnRadius
import com.cobblespawners.utils.SpawnerData
import com.cobblespawners.utils.WanderingSettings
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.InteractionContext
import com.everlastingutils.gui.setCustomName
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.ClickType
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos

object SpawnerSettingsGui {

    private object Slots {
        const val SPAWN_WIDTH = 11
        const val SPAWN_TIMER = 13
        const val SPAWN_HEIGHT = 15
        const val SPAWN_LIMIT = 29
        const val VISIBILITY_TOGGLE = 31
        const val WANDER_TYPE_TOGGLE = 33
        const val WANDERING_ENABLED_TOGGLE = 34
        const val SPAWN_AMOUNT = 38
        const val WANDER_DISTANCE = 42
        const val BACK_BUTTON = 49
    }

    private object Textures {
        const val SPAWN_RADIUS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGVhMzUxZDZlMTJiMmNmMTUxYTk3NTZhYzdkNDE5OTA1OTdhYmQzMzcyNTg1MTNkMDY2ZTZkMDkxNGU5NTNiZiJ9fX0="
        const val SPAWN_TIMER = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjA2M2RmYTE1YzZkOGRhNTA2YTJkOTM0MTQ3NjNjYjFmODE5Mzg2ZDJjZjY1NDNjMDhlMjMyZjE2M2ZiMmMxYyJ9fX0="
        const val SPAWN_LIMIT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGVjNzZjNWY4NTcwOGU4NDg3NTQ2ZDlmZDcwZGM4Y2IwNWU0N2M1ZjU2ZmQyOGQ5NThkOGE0NjJhNGQ2MTUxZSJ9fX0="
        const val VISIBILITY = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODVmZmI1MjMzMmNiZmNiNWJlNTM1NTNkNjdjNzI2NDNiYTJiYjUxN2Y3ZTg5ZGVkNTNkNGE5MmIwMGNlYTczZSJ9fX0="
        const val WANDER_TYPE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjZmNDQ4ZTNhMzViZWRkYjI1MmVlN2IzMGZlZDY3MTUzYjhhMzI0NTA2NTU2YjRhNzFlYWZjZTVlYjg2YjQ5In19fQ=="
        const val WANDERING_ENABLED = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjlhMjhiYTNiYTc5YmUxOTU0NzEwZDRkYjJhM2ZkMjI3NzNmNjE5ZjE4ZmVjZjU5ODIzNTNmYjdhYzE4MzkzYSJ9fX0="
        const val SPAWN_AMOUNT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDMzZjVjYzlkZTM1ODVkOGY2NDMzMGY0NDY4ZDE1NmJhZjAzNGEyNWRjYjc3M2MwNDc5ZDdjYTUyNmExM2Q2MSJ9fX0="
        const val WANDER_DISTANCE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmMzZGQ2ZDgzNDBlY2M2NWIyY2I0OGYzNGQ5NTE0YjU2ZjczY2MyZDE1YTE1YWVhNWM3MTBiOTc2YTNjMDA4ZiJ9fX0="
        const val BACK_BUTTON = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
    }

    private object Setting {
        const val SPAWN_WIDTH = "Spawn Width"
        const val SPAWN_TIMER = "Spawn Timer"
        const val SPAWN_HEIGHT = "Spawn Height"
        const val SPAWN_LIMIT = "Spawn Limit"
        const val WANDER_TYPE = "Wander Type"
        const val SPAWN_AMOUNT = "Spawn Amount Per Spawn"
        const val WANDER_DISTANCE = "Max Distance"
    }

    fun openSpawnerSettingsGui(player: ServerPlayerEntity, spawnerPos: BlockPos) {
        val spawnerData = CobbleSpawnersConfig.getSpawner(spawnerPos) ?: run {
            player.sendMessage(Text.literal("Spawner not found at position $spawnerPos."), false)
            return
        }

        SpawnerPokemonSelectionGui.spawnerGuisOpen[spawnerPos] = player

        if (spawnerData.spawnRadius == null) spawnerData.spawnRadius = SpawnRadius()
        if (spawnerData.wanderingSettings == null) spawnerData.wanderingSettings = WanderingSettings()
        CobbleSpawnersConfig.saveSpawnerData()

        val guiTitle = "Edit Settings for ${spawnerData.spawnerName}"
        val layout = generateSpawnerSettingsLayout(spawnerData)

        val onInteract: (InteractionContext) -> Unit = { context ->
            when (context.slotIndex) {
                Slots.SPAWN_WIDTH -> adjustSpawnerSetting(player, spawnerPos, Setting.SPAWN_WIDTH, context.clickType)
                Slots.SPAWN_TIMER -> adjustSpawnerSetting(player, spawnerPos, Setting.SPAWN_TIMER, context.clickType)
                Slots.SPAWN_HEIGHT -> adjustSpawnerSetting(player, spawnerPos, Setting.SPAWN_HEIGHT, context.clickType)
                Slots.SPAWN_LIMIT -> adjustSpawnerSetting(player, spawnerPos, Setting.SPAWN_LIMIT, context.clickType)
                Slots.VISIBILITY_TOGGLE -> toggleSpawnerVisibility(player, spawnerPos)
                Slots.WANDER_TYPE_TOGGLE -> adjustSpawnerSetting(player, spawnerPos, Setting.WANDER_TYPE, context.clickType)
                Slots.WANDERING_ENABLED_TOGGLE -> toggleWanderingEnabled(player, spawnerPos)
                Slots.SPAWN_AMOUNT -> adjustSpawnerSetting(player, spawnerPos, Setting.SPAWN_AMOUNT, context.clickType)
                Slots.WANDER_DISTANCE -> adjustSpawnerSetting(player, spawnerPos, Setting.WANDER_DISTANCE, context.clickType)
                Slots.BACK_BUTTON -> {
                    CustomGui.closeGui(player)
                    val page = SpawnerPokemonSelectionGui.playerPages[player] ?: 0
                    SpawnerPokemonSelectionGui.openSpawnerGui(player, spawnerPos, page)
                }
            }
        }

        val onClose: (Inventory) -> Unit = {
            SpawnerPokemonSelectionGui.spawnerGuisOpen.remove(spawnerPos)
            val world = player.server?.getWorld(CobbleSpawners.parseDimension(spawnerData.dimension))
            if (world is ServerWorld) {
                val newPositions = CobbleSpawners.computeValidSpawnPositions(world, spawnerData)
                CobbleSpawners.spawnerValidPositions[spawnerPos] = newPositions
            }
        }

        CustomGui.openGui(player, guiTitle, layout, onInteract, onClose)
    }

    private fun generateSpawnerSettingsLayout(spawnerData: SpawnerData): List<ItemStack> {
        val layout = MutableList(54) { ItemStack.EMPTY }

        val spawnRadius = spawnerData.spawnRadius ?: SpawnRadius()
        val wandering = spawnerData.wanderingSettings ?: WanderingSettings()

        layout[Slots.SPAWN_WIDTH] = createNumericSettingButton(
            title = Text.literal(Setting.SPAWN_WIDTH).formatted(Formatting.AQUA),
            currentValue = spawnRadius.width,
            description = listOf(
                "Sets the horizontal spawn range around the spawner.",
                "Larger values increase the spawn area width."
            ),
            texture = Textures.SPAWN_RADIUS
        )
        layout[Slots.SPAWN_TIMER] = createNumericSettingButton(
            title = Text.literal("Spawn Timer (Ticks)").formatted(Formatting.YELLOW),
            currentValue = spawnerData.spawnTimerTicks,
            description = listOf(
                "Controls how often Pokémon spawn (in ticks).",
                "Lower values mean faster spawns."
            ),
            texture = Textures.SPAWN_TIMER
        )
        layout[Slots.SPAWN_HEIGHT] = createNumericSettingButton(
            title = Text.literal(Setting.SPAWN_HEIGHT).formatted(Formatting.AQUA),
            currentValue = spawnRadius.height,
            description = listOf(
                "Sets the vertical spawn range above/below the spawner.",
                "Larger values increase the spawn area height."
            ),
            texture = Textures.SPAWN_RADIUS
        )
        layout[Slots.SPAWN_LIMIT] = createNumericSettingButton(
            title = Text.literal(Setting.SPAWN_LIMIT).formatted(Formatting.LIGHT_PURPLE),
            currentValue = spawnerData.spawnLimit,
            description = listOf(
                "Caps the total Pokémon alive at once.",
                "Higher values allow more mons to be alive at the same time."
            ),
            texture = Textures.SPAWN_LIMIT
        )
        layout[Slots.SPAWN_AMOUNT] = createNumericSettingButton(
            title = Text.literal(Setting.SPAWN_AMOUNT).formatted(Formatting.BLUE),
            currentValue = spawnerData.spawnAmountPerSpawn,
            description = listOf(
                "Sets how many Pokémon spawn each time.",
                "Higher values spawn more per cycle."
            ),
            texture = Textures.SPAWN_AMOUNT
        )
        layout[Slots.WANDER_DISTANCE] = createNumericSettingButton(
            title = Text.literal(Setting.WANDER_DISTANCE).formatted(Formatting.GOLD),
            currentValue = wandering.wanderDistance,
            description = listOf(
                "Sets how far Pokémon can wander.",
                "RADIUS: Adds blocks beyond spawn area edge.",
                "SPAWNER: Max blocks from spawner location.",
                "",
                "Larger values allow more roaming freedom."
            ),
            texture = Textures.WANDER_DISTANCE
        )

        layout[Slots.VISIBILITY_TOGGLE] = createToggleButton(
            baseName = "Spawner",
            enabled = spawnerData.visible,
            enabledText = "Visible",
            disabledText = "Hidden",
            description = listOf(
                "Toggles spawner block visibility.",
                "Visible: Shows the spawner block.",
                "Hidden: Makes it invisible in-game."
            ),
            texture = Textures.VISIBILITY
        )
        layout[Slots.WANDER_TYPE_TOGGLE] = createSettingButton(
            title = Text.literal(Setting.WANDER_TYPE).formatted(Formatting.BLUE),
            lore = listOf(
                Text.literal("§aCurrent: §f${wandering.wanderType}"),
                Text.literal("§7Defines how Pokémon wandering is limited."),
                Text.literal("§7RADIUS: Stays within spawn area plus extra blocks."),
                Text.literal("§7SPAWNER: Stays near the spawner block itself."),
                Text.literal(""),
                Text.literal("§7Choose based on your control needs."),
                Text.literal("§eClick to toggle")
            ),
            texture = Textures.WANDER_TYPE
        )
        layout[Slots.WANDERING_ENABLED_TOGGLE] = createToggleButton(
            baseName = "Wandering",
            enabled = wandering.enabled,
            description = listOf(
                "Controls if Pokémon can wander freely.",
                "ON: Limits them to a set distance.",
                "OFF: They roam without restriction.",
                "",
                "Helps keep Pokémon near for battling or farming.",
                "Prevents them from getting lost or stuck."
            ),
            texture = Textures.WANDERING_ENABLED
        )

        layout[Slots.BACK_BUTTON] = createSettingButton(
            title = Text.literal("Back").formatted(Formatting.WHITE),
            lore = listOf(Text.literal("§7Returns to the spawner Pokémon selection.")),
            texture = Textures.BACK_BUTTON
        )

        for (i in layout.indices) {
            if (layout[i] == ItemStack.EMPTY) {
                layout[i] = ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
                    setCustomName(Text.literal(" "))
                }
            }
        }
        return layout
    }

    private fun adjustSpawnerSetting(player: ServerPlayerEntity, spawnerPos: BlockPos, setting: String, clickType: ClickType) {
        CobbleSpawnersConfig.updateSpawner(spawnerPos) { spawnerData ->
            val spawnRadius = spawnerData.spawnRadius ?: SpawnRadius().also { spawnerData.spawnRadius = it }
            val wandering = spawnerData.wanderingSettings ?: WanderingSettings().also { spawnerData.wanderingSettings = it }
            val isLeftClick = clickType == ClickType.LEFT

            when (setting) {
                Setting.SPAWN_TIMER -> spawnerData.spawnTimerTicks = if (isLeftClick) (spawnerData.spawnTimerTicks - 10).coerceAtLeast(10) else spawnerData.spawnTimerTicks + 10
                Setting.SPAWN_WIDTH -> spawnRadius.width = if (isLeftClick) (spawnRadius.width - 1).coerceAtLeast(1) else (spawnRadius.width + 1).coerceAtMost(20000)
                Setting.SPAWN_HEIGHT -> spawnRadius.height = if (isLeftClick) (spawnRadius.height - 1).coerceAtLeast(1) else (spawnRadius.height + 1).coerceAtMost(20000)
                Setting.SPAWN_LIMIT -> spawnerData.spawnLimit = if (isLeftClick) (spawnerData.spawnLimit - 1).coerceAtLeast(1) else spawnerData.spawnLimit + 1
                Setting.SPAWN_AMOUNT -> spawnerData.spawnAmountPerSpawn = if (isLeftClick) (spawnerData.spawnAmountPerSpawn - 1).coerceAtLeast(1) else spawnerData.spawnAmountPerSpawn + 1
                Setting.WANDER_DISTANCE -> wandering.wanderDistance = if (isLeftClick) (wandering.wanderDistance - 1).coerceAtLeast(1) else wandering.wanderDistance + 1
                Setting.WANDER_TYPE -> wandering.wanderType = if (wandering.wanderType.equals("SPAWNER", ignoreCase = true)) "RADIUS" else "SPAWNER"
            }
        }
        CobbleSpawnersConfig.saveSpawnerData()
        refreshSpawnerGui(player, spawnerPos)
    }

    private fun toggleSpawnerVisibility(player: ServerPlayerEntity, spawnerPos: BlockPos) {
        player.server?.let {
            if (CommandRegistrarUtil.toggleSpawnerVisibility(it, spawnerPos)) {
                refreshSpawnerGui(player, spawnerPos)
            } else {
                player.sendMessage(Text.literal("Failed to toggle spawner visibility."), false)
            }
        }
    }

    private fun toggleWanderingEnabled(player: ServerPlayerEntity, spawnerPos: BlockPos) {
        CobbleSpawnersConfig.updateSpawner(spawnerPos) { spawnerData ->
            val wandering = spawnerData.wanderingSettings ?: WanderingSettings().also { spawnerData.wanderingSettings = it }
            wandering.enabled = !wandering.enabled
        }
        CobbleSpawnersConfig.saveSpawnerData()
        refreshSpawnerGui(player, spawnerPos)
    }

    private fun refreshSpawnerGui(player: ServerPlayerEntity, spawnerPos: BlockPos) {
        val spawnerData = CobbleSpawnersConfig.getSpawner(spawnerPos) ?: return
        val layout = generateSpawnerSettingsLayout(spawnerData)
        val screenHandler = player.currentScreenHandler
        layout.forEachIndexed { index, itemStack ->
            if (index < screenHandler.slots.size) {
                screenHandler.slots[index].stack = itemStack
            }
        }
        screenHandler.sendContentUpdates()
    }

    private fun createSettingButton(title: Text, lore: List<Text>, texture: String): ItemStack {
        return CustomGui.createPlayerHeadButton(
            textureName = title.string.filter { !it.isWhitespace() },
            title = title,
            lore = lore,
            textureValue = texture
        )
    }

    private fun createNumericSettingButton(title: Text, currentValue: Any, description: List<String>, texture: String): ItemStack {
        val lore = mutableListOf(
            Text.literal("§aCurrent Value: §f$currentValue")
        )
        description.forEach { lore.add(Text.literal("§7$it")) }
        lore.add(Text.literal(""))
        lore.add(Text.literal("§eLeft-click to decrease"))
        lore.add(Text.literal("§eRight-click to increase"))

        return createSettingButton(title, lore, texture)
    }

    private fun createToggleButton(baseName: String, enabled: Boolean, description: List<String>, texture: String, enabledText: String = "ON", disabledText: String = "OFF"): ItemStack {
        val statusText = if (enabled) enabledText else disabledText
        val statusColor = if (enabled) Formatting.GREEN else Formatting.RED
        val title = Text.literal("$baseName: $statusText").formatted(statusColor)

        val lore = mutableListOf<Text>()
        description.forEach { lore.add(Text.literal("§7$it")) }
        lore.add(Text.literal(""))
        lore.add(Text.literal("§eClick to toggle"))

        return createSettingButton(title, lore, texture)
    }
}