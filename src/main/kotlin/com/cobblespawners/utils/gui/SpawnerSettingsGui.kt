// File: SpawnerSettingsGui.kt
package com.cobblespawners.utils.gui

import com.blanketutils.gui.CustomGui
import com.blanketutils.gui.InteractionContext
import com.blanketutils.gui.setCustomName
import com.cobblespawners.CobbleSpawners
import com.cobblespawners.utils.*
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.LoreComponent
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

    fun openSpawnerSettingsGui(player: ServerPlayerEntity, spawnerPos: BlockPos) {
        val spawnerData = CobbleSpawnersConfig.getSpawner(spawnerPos)

        SpawnerPokemonSelectionGui.spawnerGuisOpen[spawnerPos] = player

        if (spawnerData == null) {
            player.sendMessage(Text.literal("Spawner not found at position $spawnerPos."), false)
            return
        }

        val layout = generateSpawnerSettingsLayout(spawnerData)

        val onInteract: (InteractionContext) -> Unit = { context ->
            when (context.slotIndex) {
                13 -> adjustSpawnerSetting(player, spawnerPos, "Spawn Timer", spawnerData.spawnTimerTicks, context.clickType)
                21 -> adjustSpawnerSetting(player, spawnerPos, "Spawn Width", spawnerData.spawnRadius.width, context.clickType)
                23 -> adjustSpawnerSetting(player, spawnerPos, "Spawn Height", spawnerData.spawnRadius.height, context.clickType)
                29 -> adjustSpawnerSetting(player, spawnerPos, "Spawn Limit", spawnerData.spawnLimit, context.clickType)
                31 -> toggleSpawnerVisibility(context, player, spawnerPos, spawnerData.visible)
                33 -> toggleShowParticles(context, player, spawnerPos, spawnerData.showParticles)
                39 -> adjustSpawnerSetting(player, spawnerPos, "Spawn Amount Per Spawn", spawnerData.spawnAmountPerSpawn, context.clickType) // New Button
                49 -> {
                    // Close the current GUI and reopen the Spawner list GUI
                    CustomGui.closeGui(player)
                    SpawnerPokemonSelectionGui.openSpawnerGui(player, spawnerPos, SpawnerPokemonSelectionGui.playerPages[player] ?: 0) // Reopen the Spawner list GUI
                }
                else -> player.sendMessage(Text.literal("Unknown setting clicked at slot ${context.slotIndex}"), false)
            }
        }

        val onClose: (Inventory) -> Unit = {
            SpawnerPokemonSelectionGui.spawnerGuisOpen.remove(spawnerPos)
            player.sendMessage(Text.literal("Spawner settings GUI closed"), false)

            // Recalculate spawn positions for the updated width/height
            val world = player.server?.getWorld(CobbleSpawners.parseDimension(spawnerData.dimension))
            if (world is ServerWorld) {
                val newPositions = CobbleSpawners.computeValidSpawnPositions(world, spawnerData)
                CobbleSpawners.spawnerValidPositions[spawnerPos] = newPositions
            }
        }

        CustomGui.openGui(
            player,
            "Edit Spawner Settings",
            layout,
            onInteract,
            onClose
        )
    }

    private fun generateSpawnerSettingsLayout(spawnerData: SpawnerData): List<ItemStack> {
        val layout = MutableList(54) { ItemStack.EMPTY }

        // Row 2: General settings like spawn timer
        layout[13] = CustomGui.createPlayerHeadButton(
            "spawn_timer",
            Text.literal("Spawn Timer (Ticks)").styled { it.withColor(Formatting.YELLOW) },
            listOf(
                Text.literal("§aCurrent Value: §f${spawnerData.spawnTimerTicks}"),
                Text.literal("§eLeft-click to decrease"),
                Text.literal("§eRight-click to increase")
            ),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjA2M2RmYTE1YzZkOGRhNTA2YTJkOTM0MTQ3NjNjYjFmODE5Mzg2ZDJjZjY1NDNjMDhlMjMyZjE2M2ZiMmMxYyJ9fX0="
        )

        // Row 3: Spawn radius width and height
        layout[21] = CustomGui.createPlayerHeadButton(
            "spawn_width",
            Text.literal("Spawn Width").styled { it.withColor(Formatting.AQUA) },
            listOf(
                Text.literal("§aCurrent Value: §f${spawnerData.spawnRadius.width}"),
                Text.literal("§eLeft-click to decrease"),
                Text.literal("§eRight-click to increase")
            ),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGVhMzUxZDZlMTJiMmNmMTUxYTk3NTZhYzdkNDE5OTA1OTdhYmQzMzcyNTg1MTNkMDY2ZTZkMDkxNGU5NTNiZiJ9fX0="
        )

        layout[23] = CustomGui.createPlayerHeadButton(
            "spawn_height",
            Text.literal("Spawn Height").styled { it.withColor(Formatting.AQUA) },
            listOf(
                Text.literal("§aCurrent Value: §f${spawnerData.spawnRadius.height}"),
                Text.literal("§eLeft-click to decrease"),
                Text.literal("§eRight-click to increase")
            ),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGVhMzUxZDZlMTJiMmNmMTUxYTk3NTZhYzdkNDE5OTA1OTdhYmQzMzcyNTg1MTNkMDY2ZTZkMDkxNGU5NTNiZiJ9fX0="
        )

        // Row 4: Spawn limit, visibility, and particles
        layout[29] = CustomGui.createPlayerHeadButton(
            "spawn_limit",
            Text.literal("Spawn Limit").styled { it.withColor(Formatting.LIGHT_PURPLE) },
            listOf(
                Text.literal("§aCurrent Value: §f${spawnerData.spawnLimit}"),
                Text.literal("§eLeft-click to decrease"),
                Text.literal("§eRight-click to increase")
            ),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDBiZjgwMjhjZWM1OTBiOWNiNTJlYTE1MzM3NzNiMzVhMWFhOWM5NjJjMDQzZDA0NjE3ZGNjMTg3ODA3Y2RmNCJ9fX0="
        )

        layout[31] = CustomGui.createPlayerHeadButton(
            "spawner_visible",
            Text.literal("Spawner Visibility").styled {
                it.withColor(Formatting.GREEN)
            },
            listOf(
                Text.literal("Toggle Spawner Visibility").styled { it.withColor(Formatting.GRAY).withItalic(false) },
                Text.literal("Status: ${if (spawnerData.visible) "ON" else "OFF"}").styled {
                    it.withColor(if (spawnerData.visible) Formatting.GREEN else Formatting.GRAY).withItalic(false)
                }
            ),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODVmZmI1MjMzMmNiZmNiNWJlNTM1NTNkNjdjNzI2NDNiYTJiYjUxN2Y3ZTg5ZGVkNTNkNGE5MmIwMGNlYTczZSJ9fX0="
        )

        layout[33] = CustomGui.createPlayerHeadButton(
            "show_particles",
            Text.literal("Show Particles").styled {
                it.withColor(Formatting.GREEN)
            },
            listOf(
                Text.literal("Toggle Show Particles").styled { it.withColor(Formatting.GRAY).withItalic(false) },
                Text.literal("Status: ${if (spawnerData.showParticles) "ON" else "OFF"}").styled {
                    it.withColor(if (spawnerData.showParticles) Formatting.GREEN else Formatting.GRAY).withItalic(false)
                }
            ),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDQ2MWQ5ZDA2YzBiZjRhN2FmNGIxNmZkMTI4MzFlMmJlMGNmNDJlNmU1NWU5YzBkMzExYTJhODk2NWEyM2IzNCJ9fX0="
        )

        // Row 5: New Spawn Amount Per Spawn button
        layout[39] = CustomGui.createPlayerHeadButton(
            "spawn_amount_per_spawn",
            Text.literal("Spawn Amount Per Spawn").styled { it.withColor(Formatting.BLUE) },
            listOf(
                Text.literal("§aCurrent Value: §f${spawnerData.spawnAmountPerSpawn}"),
                Text.literal("§eLeft-click to decrease"),
                Text.literal("§eRight-click to increase")
            ),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjFjYmY5NDdmYmNhZGIwZTFlOTQ2NGM1ZjU3ZGE3OTBkMzNkYzFkNWRjMGE3NzM4NTIxMTY2MGI1ZDRiY2RmYSJ9fX0="
        )

        // Fill empty slots with gray stained glass panes for a clean layout
        for (i in layout.indices) {
            if (layout[i] == ItemStack.EMPTY && i != 49 && i != 39) { // Exclude back and new button slots
                layout[i] = ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
                    CustomGui.setItemLore(this, listOf(" "))
                    setCustomName(Text.literal(" "))
                }
            }
        }

        // Row 6: Back button
        layout[49] = CustomGui.createPlayerHeadButton(
            "back_button",
            Text.literal("Back").styled { it.withColor(Formatting.WHITE) },
            listOf(Text.literal("Return to Spawner List").styled { it.withColor(Formatting.GRAY) }),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
        )

        return layout
    }

    private fun adjustSpawnerSetting(
        player: ServerPlayerEntity,
        spawnerPos: BlockPos,
        setting: String,
        currentValue: Any,
        clickType: ClickType
    ) {
        CobbleSpawnersConfig.updateSpawner(spawnerPos) { spawnerData ->
            when (setting) {
                "Spawn Timer" -> {
                    spawnerData.spawnTimerTicks = if (clickType == ClickType.LEFT) {
                        (spawnerData.spawnTimerTicks - 10).coerceAtLeast(10)
                    } else {
                        spawnerData.spawnTimerTicks + 10
                    }
                }
                "Spawn Width" -> {
                    spawnerData.spawnRadius.width = if (clickType == ClickType.LEFT) {
                        (spawnerData.spawnRadius.width - 1).coerceAtLeast(1)
                    } else {
                        (spawnerData.spawnRadius.width + 1).coerceAtMost(20000)
                    }
                }
                "Spawn Height" -> {
                    spawnerData.spawnRadius.height = if (clickType == ClickType.LEFT) {
                        (spawnerData.spawnRadius.height - 1).coerceAtLeast(1)
                    } else {
                        (spawnerData.spawnRadius.height + 1).coerceAtMost(20000)
                    }
                }
                "Spawn Limit" -> {
                    spawnerData.spawnLimit = if (clickType == ClickType.LEFT) {
                        (spawnerData.spawnLimit - 1).coerceAtLeast(1)
                    } else {
                        spawnerData.spawnLimit + 1
                    }
                }
                "Spawn Amount Per Spawn" -> { // New Setting
                    spawnerData.spawnAmountPerSpawn = if (clickType == ClickType.LEFT) {
                        (spawnerData.spawnAmountPerSpawn - 1).coerceAtLeast(1)
                    } else {
                        (spawnerData.spawnAmountPerSpawn + 1).coerceAtMost(100000)
                    }
                }
            }
        }
        CobbleSpawnersConfig.saveSpawnerData()
        player.sendMessage(Text.literal("$setting adjusted"), false)
        refreshSpawnerGui(player, spawnerPos) // Refresh the GUI to reflect the changes
    }

    private fun toggleSpawnerVisibility(context: InteractionContext, player: ServerPlayerEntity, spawnerPos: BlockPos, visible: Boolean) {
        // Call the CommandRegistrar's toggleSpawnerVisibility function
        val server = player.server ?: return

        val success = CommandRegistrarUtil.toggleSpawnerVisibility(server, spawnerPos)
        if (success) {
            // Update the local spawnerData's visibility
            val spawnerData = CobbleSpawnersConfig.getSpawner(spawnerPos)
            if (spawnerData != null) {
                // Update the GUI item to reflect the new status
                updateGuiItem(context, player, "Spawner Visibility", spawnerData.visible, if (spawnerData.visible) Formatting.GREEN else Formatting.RED)
                player.sendMessage(Text.literal("Spawner visibility has been toggled."), false)
            }
        } else {
            player.sendMessage(Text.literal("Failed to toggle spawner visibility."), false)
        }
    }

    private fun toggleShowParticles(context: InteractionContext, player: ServerPlayerEntity, spawnerPos: BlockPos, showParticles: Boolean) {
        CobbleSpawnersConfig.updateSpawner(spawnerPos) { spawnerData ->
            spawnerData.showParticles = !showParticles
        }
        CobbleSpawnersConfig.saveSpawnerData()
        updateGuiItem(context, player, "Show Particles", !showParticles, if (!showParticles) Formatting.GREEN else Formatting.RED)
    }

    private fun updateGuiItem(context: InteractionContext, player: ServerPlayerEntity, settingName: String, newValue: Boolean, color: Formatting) {
        val itemStack = context.clickedStack

        // Set the custom name using DataComponents instead of NBT
        itemStack.setCustomName(
            Text.literal(settingName).styled {
                it.withColor(color).withBold(false).withItalic(false)
            }
        )

        // Retrieve current lore from DataComponents
        val oldLoreComponent = itemStack.getOrDefault(DataComponentTypes.LORE, LoreComponent(emptyList()))
        val oldLore = oldLoreComponent.lines.toMutableList()

        // Attempt to find and update the "Status:" line
        var updatedStatus = false
        for (i in oldLore.indices) {
            val line = oldLore[i]
            if (line.string.contains("Status:", ignoreCase = true)) {
                oldLore[i] = Text.literal("Status: ${if (newValue) "ON" else "OFF"}").styled { s ->
                    s.withColor(if (newValue) Formatting.GREEN else Formatting.GRAY).withItalic(false)
                }
                updatedStatus = true
                break
            }
        }

        // If no "Status:" line was found, add a new line
        if (!updatedStatus) {
            oldLore.add(
                Text.literal("Status: ${if (newValue) "ON" else "OFF"}").styled { s ->
                    s.withColor(if (newValue) Formatting.GREEN else Formatting.GRAY).withItalic(false)
                }
            )
        }

        // Update the lore using DataComponents
        itemStack.set(DataComponentTypes.LORE, LoreComponent(oldLore))

        // Update the item in the player's screen
        player.currentScreenHandler.slots[context.slotIndex].stack = itemStack
        player.currentScreenHandler.sendContentUpdates()
    }


    private fun refreshSpawnerGui(player: ServerPlayerEntity, spawnerPos: BlockPos) {
        val spawnerData = CobbleSpawnersConfig.getSpawner(spawnerPos)
        if (spawnerData != null) {
            val layout = generateSpawnerSettingsLayout(spawnerData)

            val screenHandler = player.currentScreenHandler
            layout.forEachIndexed { index, itemStack ->
                if (index < screenHandler.slots.size) {
                    screenHandler.slots[index].stack = itemStack
                }
            }

            // Update the screen
            screenHandler.sendContentUpdates()
        }
    }
}
