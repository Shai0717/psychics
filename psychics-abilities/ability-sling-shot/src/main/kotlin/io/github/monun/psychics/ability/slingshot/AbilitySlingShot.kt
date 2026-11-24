package io.github.monun.psychics.ability.slingshot

import io.github.Shai0717.kolor.KolorTask
import io.github.monun.psychics.Ability
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.config.PsychicGroup
import io.github.monun.psychics.config.PsychicSetting
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import net.kyori.adventure.text.Component.text
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Snowball
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.joml.Vector3f
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@PsychicGroup("sling-shot")
class AbilityConceptSlingShot : AbilityConcept() {
    @PsychicSetting
    var stoneSpeed = 4.0

    @PsychicSetting
    var stoneGravity = 0.02

    @PsychicSetting(minimum = 0.01)
    var stoneSize = 2.0

    init {
        type = io.github.monun.psychics.AbilityType.ACTIVE
        range = 128.0
        cooldownTime = 80L
        knockback = 0.05
        damage = Damage.of(DamageType.RANGED, EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 0.25))
        wand = ItemStack(Material.COBBLESTONE)
        description = listOf(text("돌을 던져 피해를 입힙니다."))
    }
}

class AbilitySlingShot : Ability<AbilityConceptSlingShot>(), Listener {
    private data class ProjectileState(
        val projectile: Snowball,
        var task: KolorTask? = null,
        var display: ItemDisplay? = null
    )

    private val projectiles = ConcurrentHashMap<UUID, ProjectileState>()

    override fun onEnable() {
        psychic.registerEvents(this)
    }

    override fun onDisable() {
        for ((id, state) in projectiles) {
            state.task?.cancel()
            val removeTask = with(psychic.plugin.kolorScheduler) {
                state.projectile.runOn { state.projectile.remove() }
            }
            if (removeTask == null) {
                psychic.plugin.logger.warning("Could not remove sling-shot projectile $id: entity retired")
            }
            state.display?.let(psychic::removeDisplay)
        }
        projectiles.clear()
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.player !== esper.player || event.hand != EquipmentSlot.HAND) return
        if (event.action != Action.LEFT_CLICK_AIR && event.action != Action.LEFT_CLICK_BLOCK) return
        val item = event.item ?: return
        if (item.type != Material.COBBLESTONE) return

        val result = test()
        if (result !== io.github.monun.psychics.TestResult.Success) {
            result.message(this)?.let(event.player::sendActionBar)
            return
        }
        exhaust()
        if (event.player.gameMode != org.bukkit.GameMode.CREATIVE) item.amount--

        val start = event.player.eyeLocation
        val direction = start.direction.multiply(concept.stoneSpeed)
        val snowball = event.player.launchProjectile(Snowball::class.java, direction)
        snowball.item = ItemStack(Material.AIR)
        snowball.setGravity(false)
        val state = ProjectileState(snowball)
        projectiles[snowball.uniqueId] = state
        state.task = with(psychic.plugin.kolorScheduler) {
            snowball.runOn(
                delayTicks = 1L,
                periodTicks = 1L,
                retiredCallback = {
                    projectiles.remove(snowball.uniqueId, state)
                    state.display?.let(psychic.plugin.kolorDisplay::remove)
                }
            ) {
                if (!snowball.isValid) {
                    projectiles.remove(snowball.uniqueId, state)
                    state.display?.let(psychic::removeDisplay)
                    state.task?.cancel()
                    return@runOn
                }
                snowball.velocity = snowball.velocity.clone().apply { y -= concept.stoneGravity }
                state.display?.let { psychic.plugin.kolorDisplay.teleport(it, snowball.location) }
            }
        } ?: run {
            projectiles.remove(snowball.uniqueId, state)
            snowball.remove()
            error("Could not schedule sling-shot projectile update")
        }
        psychic.displayItem(
            snowball.location,
            ItemStack(Material.COBBLESTONE),
            Vector3f(concept.stoneSize.toFloat())
        ).whenComplete { display, failure ->
            if (failure != null) {
                psychic.plugin.logger.warning("Could not create sling-shot display: ${failure.message}")
            } else if (projectiles[snowball.uniqueId] !== state) {
                psychic.removeDisplay(display)
            } else {
                state.display = display
            }
        }
        event.player.world.playSound(event.player.location, Sound.ENTITY_SNOWBALL_THROW, 1.0F, 0.1F)
    }

    @EventHandler(ignoreCancelled = true)
    fun onProjectileHit(event: ProjectileHitEvent) {
        val snowball = event.entity as? Snowball ?: return
        val state = projectiles[snowball.uniqueId] ?: return
        if (snowball.shooter !== esper.player) return
        projectiles.remove(snowball.uniqueId, state)
        state.task?.cancel()
        state.display?.let(psychic::removeDisplay)
        val location = snowball.location
        val world = snowball.world
        world.spawnParticle(Particle.BLOCK, location, 24, 0.15, 0.15, 0.15, 0.1, Material.COBBLESTONE.createBlockData())
        world.playSound(location, Sound.BLOCK_STONE_BREAK, 1.0F, 1.5F)
        val target = event.hitEntity as? LivingEntity
        if (target != null) {
            this@AbilitySlingShot.run {
                target.psychicDamage(
                    concept.damage!!,
                    esper.player.location,
                    if (target.isOnGround) concept.knockback else 0.0
                )
            }
        }
        snowball.remove()
    }
}
