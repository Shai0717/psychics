package io.github.monun.psychics.ability.dash

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

@PsychicGroup("dash")
class AbilityConceptDash : AbilityConcept() {
    @PsychicSetting(minimum = 0.0, maximum = 4.0)
    var speed = 1.4

    init {
        displayName = "돌진"
        description = listOf(text("바라보는 방향으로 빠르게 돌진합니다."))
        cooldownTime = 5000L
        cost = 8.0
        wand = ItemStack(Material.FEATHER)
        supplyItems = listOf(ItemStack(Material.FEATHER))
    }
}

class AbilityDash : ActiveAbility<AbilityConceptDash>(WandAction.RIGHT_CLICK) {
    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        val player = esper.player
        val direction = player.location.direction
        direction.y = 0.0
        if (direction.lengthSquared() < 0.0001) {
            player.sendMessage("수평 방향을 바라봐야 합니다.")
            return
        }
        exhaust()
        direction.normalize().multiply(concept.speed)
        direction.y = 0.2
        player.velocity = direction
        player.world.spawnParticle(Particle.CLOUD, player.location.add(0.0, 0.2, 0.0), 12, 0.3, 0.1, 0.3, 0.02)
        player.world.playSound(player.location, Sound.ENTITY_BREEZE_WIND_BURST, 0.8f, 1.3f)
    }
}
