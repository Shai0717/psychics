package io.github.monun.psychics.ability.shockwave

import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.ActiveAbility
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.util.hostileFilter
import io.github.monun.psychics.config.PsychicGroup
import net.kyori.adventure.text.Component.text
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack

@PsychicGroup("shockwave")
class AbilityConceptShockwave : AbilityConcept() {
    init {
        displayName = "충격파"
        description = listOf(text("주변의 적에게 피해를 입히고 밀어냅니다."))
        cooldownTime = 10000L
        cost = 20.0
        range = 5.0
        knockback = 1.0
        damage = Damage.of(DamageType.MELEE, EsperAttribute.ATTACK_DAMAGE to 2.0)
        wand = ItemStack(Material.BREEZE_ROD)
        supplyItems = listOf(ItemStack(Material.BREEZE_ROD))
    }
}

class AbilityShockwave : ActiveAbility<AbilityConceptShockwave>(WandAction.RIGHT_CLICK) {
    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        val player = esper.player
        val origin = player.location
        val radius = concept.range
        exhaust()
        origin.world.getNearbyEntities(origin, radius, radius, radius, player.hostileFilter())
            .filterIsInstance<LivingEntity>()
            .filter { it.location.distanceSquared(origin) <= radius * radius }
            .forEach { it.psychicDamage(knockbackLocation = origin) }
        origin.world.spawnParticle(Particle.EXPLOSION, origin.add(0.0, 1.0, 0.0), 4, 0.5, 0.3, 0.5, 0.0)
        origin.world.playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.5f)
    }
}
