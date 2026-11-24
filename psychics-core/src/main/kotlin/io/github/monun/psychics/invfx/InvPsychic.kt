package io.github.monun.psychics.invfx

import io.github.Shai0717.kolor.KolorInventory
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.PsychicConcept
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.item.addItemNonDuplicate
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

object InvPsychic {
    private const val PAGE_SIZE = 5

    fun open(player: Player, concept: PsychicConcept, stats: (EsperStatistic) -> Double) {
        val holder = PsychicHolder(concept, stats)
        holder.gui = KolorInventory.create(holder, 9, text("PSYCHICS"))
        render(holder)
        player.openInventory(holder.gui)
    }

    fun handleClick(event: InventoryClickEvent): Boolean {
        val holder = event.view.topInventory.holder as? PsychicHolder ?: return false
        event.isCancelled = true
        if (event.clickedInventory !== holder.gui) return true

        when (event.rawSlot) {
            1 -> holder.page = (holder.page - 1).coerceAtLeast(0)
            7 -> holder.page = (holder.page + 1).coerceAtMost(maxPage(holder))
            in 2..6 -> {
                val ability = holder.concept.abilityConcepts.getOrNull(holder.page * PAGE_SIZE + event.rawSlot - 2)
                if (ability != null && event.whoClicked is Player) {
                    event.whoClicked.inventory.addItemNonDuplicate(ability.supplyItems)
                }
                return true
            }
            else -> return true
        }
        render(holder)
        return true
    }

    private fun render(holder: PsychicHolder) {
        val inventory = holder.gui
        inventory.clear()
        inventory.setItem(0, holder.concept.renderTooltip().applyTo(ItemStack(Material.ENCHANTED_BOOK)))
        inventory.setItem(8, KolorInventory.item(Material.DARK_OAK_SIGN, text("도움말"), listOf(
            text("← → 버튼으로 스킬 목록을 넘깁니다."),
            text("스킬 아이콘을 클릭하면 지급 아이템을 받습니다.")
        )))
        inventory.setItem(1, KolorInventory.item(Material.END_CRYSTAL, text("←")))
        inventory.setItem(7, KolorInventory.item(Material.END_CRYSTAL, text("→")))
        val start = holder.page * PAGE_SIZE
        holder.concept.abilityConcepts.drop(start).take(PAGE_SIZE).forEachIndexed { index, ability ->
            val icon = ability.wand ?: ItemStack(Material.BOOK)
            inventory.setItem(index + 2, ability.renderTooltip(holder.stats).applyTo(icon))
        }
    }

    private fun maxPage(holder: PsychicHolder): Int =
        ((holder.concept.abilityConcepts.size - 1).coerceAtLeast(0)) / PAGE_SIZE

    private class PsychicHolder(
        val concept: PsychicConcept,
        val stats: (EsperStatistic) -> Double
    ) : InventoryHolder {
        lateinit var gui: Inventory
        var page = 0
        override fun getInventory(): Inventory = gui
    }
}
