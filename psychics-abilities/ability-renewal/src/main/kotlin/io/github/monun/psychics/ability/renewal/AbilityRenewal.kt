package io.github.monun.psychics.ability.renewal

import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.ActiveAbility
import io.github.monun.psychics.config.PsychicGroup
import io.github.monun.psychics.config.PsychicSetting
import net.kyori.adventure.text.Component.text
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack

@PsychicGroup("renewal")
class AbilityConceptRenewal : AbilityConcept() {
    @PsychicSetting(minimum = 0.0)
    var healingAmount = 6.0

    init {
        displayName = "회복"
        description = listOf(text("자신의 체력을 회복합니다."))
        cooldownTime = 12000L
        cost = 15.0
        wand = ItemStack(Material.GHAST_TEAR)
        supplyItems = listOf(ItemStack(Material.GHAST_TEAR))
    }
}

class AbilityRenewal : ActiveAbility<AbilityConceptRenewal>(WandAction.RIGHT_CLICK) {
    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        val player = esper.player
        exhaust()
        player.psychicHeal(concept.healingAmount)
        player.world.spawnParticle(Particle.HEART, player.location.add(0.0, 1.0, 0.0), 8, 0.5, 0.5, 0.5, 0.0)
        player.world.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f)
    }
}
