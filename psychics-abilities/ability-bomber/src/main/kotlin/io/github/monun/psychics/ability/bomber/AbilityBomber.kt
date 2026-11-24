package io.github.monun.psychics.ability.bomber

import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.ActiveAbility
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.damage.psychicDamage
import io.github.monun.psychics.tooltip.TooltipBuilder
import io.github.monun.psychics.util.TargetFilter
import io.github.monun.psychics.config.PsychicGroup
import io.github.monun.psychics.config.PsychicSetting
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.ItemDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.BoundingBox
import kotlin.math.max

// 지속시간 이후 폭발하여 주위에 피해를 입힘
// 사망시 두배 데미지
@PsychicGroup("bomber")
class AbilityConceptBomber : AbilityConcept() {
    @PsychicSetting
    var selfDamage = 0.5

    @PsychicSetting
    var speedAmplifier = 3

    @PsychicSetting
    var deathDamage = 2.0

    init {
        range = 12.0
        wand = ItemStack(Material.GUNPOWDER)
        durationTime = 5000L
        cooldownTime = 40000L
        knockback = 3.0
        damage = Damage.of(DamageType.BLAST, EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 8.0))
        description = listOf(
            text("스킬 사용 시 지속시간 이후 폭발하는 폭탄을 머리위에 소환합니다.")
        )
    }

    override fun onRenderTooltip(tooltip: TooltipBuilder, stats: (EsperStatistic) -> Double) {
        tooltip.header(
            text().content("자기 피해량 ").color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
                .append(text().content((selfDamage * 100.0).toInt().toString())).append(text().content("%")).build()
        )
        if (speedAmplifier > 0) {
            tooltip.header(
                text().content("사망시 피해량 ").color(NamedTextColor.DARK_RED).decoration(TextDecoration.ITALIC, false)
                    .append(text().content((deathDamage * 100.0).toInt().toString())).append(text().content("%")).build()
            )
        }

        // 빨라보이게 일부러 이태릭체
        tooltip.header(
            text().content("신속 LV.").color(NamedTextColor.AQUA)
                .append(text().content(speedAmplifier.toString())).build()
        )
    }
}

class AbilityBomber : ActiveAbility<AbilityConceptBomber>(), Listener {
    private var tntDisplay: ItemDisplay? = null
    private var armed = false

    override fun onEnable() {
        psychic.runTaskTimer(this::update, 0L, 1L)
        psychic.registerEvents(this)
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        if (event.player !== esper.player) return
        durationTime = 0L
        destroy()
    }

    private fun update() {
        val remainDurationTime = durationTime

        if (remainDurationTime > 0L) {
            tntDisplay?.let { psychic.plugin.kolorDisplay.teleport(it, tntLocation()) }
            tntLocation().world.spawnParticle(Particle.FLAME, tntLocation(), 1, 0.0, 0.0, 0.0, 0.025)
        } else {
            destroy()
        }
    }

    private fun destroy() {
        if (!armed) return
        armed = false
        tntDisplay?.let(psychic::removeDisplay)
        tntDisplay = null

        val location = tntLocation()
        val world = location.world

        val r = max(1.0, concept.range - 2.0)
        world.spawnParticle(Particle.EXPLOSION_EMITTER, location, (r * r).toInt(), r, r, r, 0.0, null, true)
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 2.0F, 1.0F)

        val damage = concept.damage!!
        var amount = esper.getStatistic(damage.stats)
        val dead = esper.player.health <= 0.0
        if (dead) amount *= concept.deathDamage
        val knockback = concept.knockback

        val box = BoundingBox.of(location, r, r, r)
        world.getNearbyEntities(box, TargetFilter(esper.player)).forEach { enemy ->
            if (enemy is LivingEntity) {
                enemy.psychicDamage(this@AbilityBomber, damage.type, amount, esper.player, location, knockback)
            }
        }

        if (!dead)
            esper.player.psychicDamage(
                this@AbilityBomber,
                damage.type,
                amount * concept.selfDamage,
                esper.player,
                location
            )
    }

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        psychic.consumeMana(concept.cost)
        cooldownTime = concept.cooldownTime
        durationTime = concept.durationTime
        armed = true
        psychic.displayItem(tntLocation(), ItemStack(Material.TNT))
            .thenAccept { tntDisplay = it }
            .exceptionally { exception ->
                psychic.plugin.logger.warning("Could not create bomber display: ${exception.message}")
                null
            }

        val speedLevel = concept.speedAmplifier

        if (speedLevel > 0) {
            val amplifier = speedLevel - 1
            val speedTicks = (concept.durationTime / 50L).toInt()
            val potionEffect = PotionEffect(PotionEffectType.SPEED, speedTicks, amplifier, true, false, true)
            esper.player.addPotionEffect(potionEffect)
        }
    }

    /**
     * 플레이어 위치 기반 TNT 위치
     */
    private fun tntLocation() = esper.player.location.apply { y += 3.0 }

}