package io.github.monun.psychics.ability.fangs

import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.ActiveAbility
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.util.Times
import io.github.monun.psychics.util.hostileFilter
import io.github.monun.psychics.config.PsychicGroup
import io.github.monun.psychics.config.PsychicSetting
import net.kyori.adventure.text.Component.text
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.LivingEntity
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.util.BoundingBox
import java.util.*

// 전방으로 가시를 발사
@PsychicGroup("fangs")
class AbilityConceptFangs : AbilityConcept() {
    @PsychicSetting
    var fangsSpace = 1.5

    @PsychicSetting
    var fangsHeight = 1.5

    @PsychicSetting
    var fangsWidth = 2.0

    @PsychicSetting
    var fangPerTime = 40L

    init {
        cooldownTime = 500L
        range = 24.0
        cost = 20.0
        damage = Damage.of(DamageType.RANGED, EsperAttribute.ATTACK_DAMAGE to 2.0)
        knockback = 0.5
        description = listOf(
            text("지정한 방향으로 송곳니를 소환합니다.")
        )
        wand = ItemStack(Material.STICK)
    }
}

class AbilityFangs : ActiveAbility<AbilityConceptFangs>(), Listener {

    private lateinit var effectQueue: PriorityQueue<Fang>
    private lateinit var damageQueue: PriorityQueue<Fang>
    private lateinit var removeQueue: PriorityQueue<Fang>

    override fun onEnable() {
        effectQueue = PriorityQueue()
        damageQueue = PriorityQueue()
        removeQueue = PriorityQueue()

        psychic.runTaskTimer(this::onUpdate, 0L, 1L)
    }

    override fun onDisable() {
        fun PriorityQueue<Fang>.destroy() {
            forEach { it.display?.let(psychic::removeDisplay) }
            clear()
        }

        effectQueue.destroy()
        damageQueue.destroy()
        removeQueue.destroy()
    }

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        val concept = concept
        val player = esper.player
        val location = player.location

        val vector = location.direction.multiply(concept.fangsSpace)
        val count = (concept.range / concept.fangsSpace).toInt()
        val currentTime = Times.current
        val damaged = HashSet<Entity>()

        repeat(count) {
            val loc = location.add(vector).clone()
            val effectTime = currentTime + concept.fangPerTime * it
            val fang = Fang(loc, effectTime, damaged = damaged)
            effectQueue.offer(fang)
            psychic.displayItem(loc.clone().apply { y -= 0.3 }, ItemStack(Material.BONE))
                .thenAccept { display ->
                    fang.display = display
                    display.itemDisplayTransform = org.bukkit.entity.ItemDisplay.ItemDisplayTransform.HEAD
                }
                .exceptionally { exception ->
                    psychic.plugin.logger.warning("Could not create fang display: ${exception.message}")
                    null
                }
        }

        exhaust()
    }

    private fun onUpdate() {
        val current = Times.current

        effectQueue.execute(current) { fang ->
            val location = fang.location
            location.world.spawnParticle(Particle.CRIT, location, 8, 0.2, 0.1, 0.2, 0.1)
            fang.display?.let { display ->
                psychic.plugin.kolorDisplay.update(display) { it.teleport(location.clone().add(0.0, 0.15, 0.0)) }
            }
            fang.nextRunTime += 250L
            damageQueue.offer(fang)
        }

        damageQueue.execute(current) { fang ->
            val r = concept.fangsWidth / 2.0
            val loc = fang.location
            val box = BoundingBox.of(loc, r, concept.fangsHeight, r)
            val hostiles = loc.world.getNearbyEntities(box, esper.player.hostileFilter())

            for (entity in hostiles) {
                if (fang.damaged.add(entity))
                    (entity as LivingEntity).psychicDamage()

            }

            fang.nextRunTime += 1000L
            removeQueue.offer(fang)
        }

        removeQueue.execute(current) {
            it.display?.let(psychic::removeDisplay)
        }
    }

    private class Fang(
        val location: Location,
        var nextRunTime: Long,
        val damaged: MutableSet<Entity>,
        var display: ItemDisplay? = null
    ) : Comparable<Fang> {
        override fun compareTo(other: Fang): Int {
            return nextRunTime.compareTo(other.nextRunTime)
        }
    }

    private fun PriorityQueue<Fang>.execute(current: Long, executor: (Fang) -> Unit) {
        while (isNotEmpty()) {
            val fang = peek()

            if (current < fang.nextRunTime)
                break

            remove()
            executor(fang)
        }
    }
}