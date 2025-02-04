// File: SortGui.kt
package com.cobblespawners.utils.gui


import com.blanketutils.gui.CustomGui
import com.blanketutils.gui.InteractionContext
import com.blanketutils.gui.setCustomName

import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos

object SortGui {

    private val alphabet = ('A'..'Z').toList()

    private val letterHeadTextures = mapOf(
        'A' to "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTdkZDM0OTI0ZDJiNmEyMTNhNWVkNDZhZTU3ODNmOTUzNzNhOWVmNWNlNWM4OGY5ZDczNjcwNTk4M2I5NyJ9fX0=",
        'B' to "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWVjYTk4YmVmZDBkN2VmY2E5YjExZWJmNGIyZGE0NTljYzE5YTM3ODExNGIzY2RkZTY3ZDQwNjdhZmI4OTYifX19",
        'C' to "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTZiMTQ4NmUxZjU3NmJjOTIxYjhmOWY1OWZlNjEyMmNlNmNlOWRkNzBkNzVlMmM5MmZkYjhhYjk4OTdiNSJ9fX0=",
        'D' to "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTlhYTY5MjI5ZmZkZmExODI4ODliZjMwOTdkMzIyMTVjMWIyMTU5ZDk4NzEwM2IxZDU4NDM2NDZmYWFjIn19fQ==",
        'E' to "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2VkOWY0MzFhOTk3ZmNlMGQ4YmUxODQ0ZjYyMDkwYjE3ODNhYzU2OWM5ZDI3OTc1MjgzNDlkMzdjMjE1ZmNjIn19fQ==",
        'F' to "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWQ3MTRiYWZiMGI1YWI5Y2ZhN2RiMDJlZmM4OTI3YWVkMWVmMjk3OTdhNTk1ZGEwNjZlZmM1YzNlZmRjOSJ9fX0=",
        'G' to "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNThjMzM2ZGVkZmUxOTdiNDM0YjVhYjY3OTg4Y2JlOWMyYzlmMjg1ZWMxODcxZmRkMWJhNDM0ODU1YiJ9fX0=",
        'H' to "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmRlNGE4OWJlMjE5N2Y4NmQyZTYxNjZhMGFjNTQxY2NjMjFkY2UyOGI3ODU0Yjc4OGQzMjlhMzlkYWVjMzIifX19",
        'I' to "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzE0OGE4ODY1YmM0YWZlMDc0N2YzNDE1MTM4Yjk2YmJiNGU4YmJiNzI2MWY0NWU1ZDExZDcyMTlmMzY4ZTQifX19",
        'J' to "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMThjOWRjM2QzOGE1NjI4MmUxZDkyMzM3MTk4ZmIxOWVhNjQxYjYxYThjNGU1N2ZiNGUyN2MxYmE2YTRiMjRjIn19fQ==",
        'K' to "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTJiZmViMjQ2ZjY0OWI4NmYyMTJmZWVhODdhOWMyMTZhNjU1NTY1ZDRiNzk5MmU4MDMyNmIzOTE4ZDkyM2JkIn19fQ==",
        'L' to "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2M1ODMyMWQ0YmZmYmVjMmRkZjY2YmYzOGNmMmY5ZTlkZGYzZmEyZjEzODdkYzdkMzBjNjJiNGQwMTBjOCJ9fX0=",
        'M' to "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTAzNzZkYzVlM2M5ODFiNTI5NjA1NzhhZmU0YmZjNDFjMTc3ODc4OWJjZDgwZWMyYzJkMmZkNDYwZTVhNTFhIn19fQ==",
        'N' to "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjEyYzdhZmVhNDhlNTMzMjVlNTEyOTAzOGE0NWFlYzUxYWZlMjU2YWJjYTk0MWI2YmM4MjA2ZmFlMWNlZiJ9fX0=",
        'O' to "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWMyNzIzNWRlM2E1NTQ2NmI2Mjc0NTlmMTIzMzU5NmFiNmEyMmM0MzVjZmM4OWE0NDU0YjQ3ZDMyYjE5OTQzMSJ9fX0=",
        'P' to "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzU4NGRjN2VjZjM2YjRmMDQ0ZjgyNjI1Mjc5ODU3MThiZjI0YTlkYWVmMDEyZGU5MmUxZTc2ZDQ1ODZkOTYifX19",
        'Q' to "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmY3MmNjZWI0YTU2NTQ3OGRlNWIwYjBlNzI3OTQ2ZTU0OTgzNGUzNmY2ZTBlYzhmN2RkN2Y2MzI3YjE1YSJ9fX0=",
        'R' to "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2NiODgyMjVlZTRhYjM5ZjdjYmY1ODFmMjJjYmYwOGJkY2MzMzg4NGYxZmY3NDc2ODkzMTI4NDE1MTZjMzQ1In19fQ==",
        'S' to "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWYyMmQ3Y2Q1M2Q1YmZlNjFlYWZiYzJmYjFhYzk0NDQzZWVjMjRmNDU1MjkyMTM5YWM5ZmJkYjgzZDBkMDkifX19",
        'T' to "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmMyZmNiYzI0ZTczODJhYzExMmJiMmMwZDVlY2EyN2U5ZjQ4ZmZjYTVhMTU3ZTUwMjYxN2E5NmQ2MzZmNWMzIn19fQ==",
        'U' to "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWZkYzRmMzIxYzc4ZDY3NDg0MTM1YWU0NjRhZjRmZDkyNWJkNTdkNDU5MzgzYTRmZTlkMmY2MGEzNDMxYTc5In19fQ==",
        'V' to "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmRkMDE0M2Q4ZTQ0OWFkMWJhOTdlMTk4MTcxMmNlZTBmM2ZjMjk3ZGJjMTdjODNiMDVlZWEzMzM4ZDY1OSJ9fX0=",
        'W' to "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzljYmM0NjU1MjVlMTZhODk0NDFkNzg5YjcyZjU1NGU4ZmY0ZWE1YjM5MzQ0N2FlZjNmZjE5M2YwNDY1MDU4In19fQ==",
        'X' to "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzM4YWIxNDU3NDdiNGJkMDljZTAzNTQzNTQ5NDhjZTY5ZmY2ZjQxZDllMDk4YzY4NDhiODBlMTg3ZTkxOSJ9fX0=",
        'Y' to "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTcxMDcxYmVmNzMzZjQ3NzAyMWIzMjkxZGMzZDQ3ZjBiZGYwYmUyZGExYjE2NWExMTlhOGZmMTU5NDU2NyJ9fX0=",
        'Z' to "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzk5MmM3NTNiZjljNjI1ODUzY2UyYTBiN2IxNzRiODlhNmVjMjZiYjVjM2NjYjQ3M2I2YTIwMTI0OTYzMTIifX19"
    )

    fun openSortGui(player: ServerPlayerEntity, spawnerPos: BlockPos) {
        val layout = generateAlphabetGuiLayout()

        SpawnerPokemonSelectionGui.spawnerGuisOpen[spawnerPos] = player

        val onInteract: (InteractionContext) -> Unit = { context ->
            if (context.slotIndex == 53) {
                goBackToPreviousGui(player, spawnerPos)
            } else {
                handleLetterSelection(context, player, spawnerPos)
            }
        }

        val onClose: (Inventory) -> Unit = {
            SpawnerPokemonSelectionGui.spawnerGuisOpen.remove(spawnerPos)
            player.sendMessage(Text.literal("Sorting letter selection closed."), false)
        }

        CustomGui.openGui(
            player,
            "Select Sorting Letter",
            layout,
            onInteract,
            onClose
        )
    }

    private fun generateAlphabetGuiLayout(): List<ItemStack> {
        val layout = MutableList(54) { ItemStack.EMPTY }

        alphabet.forEachIndexed { index, letter ->
            layout[index] = createLetterHeadItem(letter)
        }

        layout[49] = createBackButton()

        for (i in layout.indices) {
            if (layout[i] == ItemStack.EMPTY) {
                layout[i] = ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
                    setCustomName(Text.literal(" "))
                }
            }
        }

        return layout
    }

    private fun createLetterHeadItem(letter: Char): ItemStack {
        val texture = letterHeadTextures[letter]!!
        return CustomGui.createPlayerHeadButton(
            "LetterHead_$letter",
            Text.literal("§f§l$letter").styled { it.withBold(true).withItalic(false) },
            listOf(
                Text.literal("§7Click to sort by letter '$letter'").styled { it.withItalic(false) }
            ),
            texture
        )
    }

    private fun createBackButton(): ItemStack {
        return CustomGui.createPlayerHeadButton(
            "BackButtonHead",
            Text.literal("§cBack").styled { it.withBold(true).withItalic(false) },
            listOf(
                Text.literal("§7Click to go back").styled { it.withItalic(false) }
            ),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
        )
    }

    private fun handleLetterSelection(context: InteractionContext, player: ServerPlayerEntity, spawnerPos: BlockPos) {
        val clickedItem = context.clickedStack
        val letter = CustomGui.stripFormatting(clickedItem.name.string).trim()

        player.sendMessage(Text.literal("You selected the letter '$letter' for sorting."), false)

        try {
            val selectedSortMethod = SortMethod.valueOf("LETTER_${letter.uppercase()}")
            SpawnerPokemonSelectionGui.sortMethod = selectedSortMethod

            player.sendMessage(Text.literal("Sort method set to start with '$letter'"), false)
        } catch (e: IllegalArgumentException) {
            player.sendMessage(Text.literal("Invalid letter selected for sorting."), false)
        }

        goBackToPreviousGui(player, spawnerPos)
    }

    private fun goBackToPreviousGui(player: ServerPlayerEntity, spawnerPos: BlockPos) {
        SpawnerPokemonSelectionGui.openSpawnerGui(player, spawnerPos)
    }
}
