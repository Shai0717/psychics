package io.github.monun.psychics.ability.stormbreaker

import io.github.Shai0717.kolor.KolorTask
import io.github.monun.psychics.Ability
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.AbilityType
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.config.PsychicGroup
import io.github.monun.psychics.config.PsychicSetting
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.tooltip.TooltipBuilder
import io.github.monun.psychics.tooltip.stats
import io.github.monun.psychics.tooltip.template
import io.github.monun.psychics.util.TargetFilter
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.*
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Snowball
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.random.Random.Default.nextDouble
import kotlin.random.Random.Default.nextFloat

@PsychicGroup("storm-breaker")
class AbilityConceptStormBreaker : AbilityConcept() {
    @PsychicSetting(minimum = 0.0)
    var lightningRadius = 2.0

    @PsychicSetting
    var iron = Damage.of(DamageType.MELEE, EsperAttribute.ATTACK_DAMAGE to 2.0)

    @PsychicSetting
    var golden = Damage.of(DamageType.MELEE, EsperAttribute.ATTACK_DAMAGE to 2.5)

    @PsychicSetting
    var diamond = Damage.of(DamageType.MELEE, EsperAttribute.ATTACK_DAMAGE to 3.0)

    @PsychicSetting
    var netherite = Damage.of(DamageType.MELEE, EsperAttribute.ATTACK_DAMAGE to 4.0)

    @PsychicSetting(minimum = 0.0)
    var axeSpeed = 2.0

    @PsychicSetting(minimum = 0.0)
    var axeGravity = 0.04

    @PsychicSetting(minimum = 0.0)
    var slowAmplifier = 3

    @PsychicSetting(minimum = 0.0)
    var slowDurationTicks = 60

    init {
        type = AbilityType.ACTIVE
        range = 256.0
        cooldownTime = 10L * 1000L
        durationTime = 5L * 1000L
        wand = ItemStack(Material.LIGHTNING_ROD)
        displayName = "스톰브레이커"
        description = listOf(
            text("도끼를 우클릭 시 도끼를 던져 적중한 곳에"),
            text("번개를 내리쳐 피해를 입히고 둔화시킵니다."),
            text("도끼가 땅에 꽂힌 동안 다시 사용하면 위치로 순간이동합니다.")
        )
    }

    override fun onRenderTooltip(tooltip: TooltipBuilder, stats: (EsperStatistic) -> Double) {
        tooltip.stats(text("철").color(NamedTextColor.WHITE), iron) { NamedTextColor.WHITE to "iron" }
        tooltip.stats(text("금").color(NamedTextColor.WHITE), golden) { NamedTextColor.GOLD to "golden" }
        tooltip.stats(text("다이아몬드").color(NamedTextColor.WHITE), diamond) { NamedTextColor.AQUA to "diamond" }
        tooltip.stats(text("네더라이트").color(NamedTextColor.WHITE), netherite) { NamedTextColor.RED to "netherite" }
        tooltip.template("iron", stats(iron.stats))
        tooltip.template("golden", stats(golden.stats))
        tooltip.template("diamond", stats(diamond.stats))
        tooltip.template("netherite", stats(netherite.stats))
    }

    fun findDamage(type: Material): Damage? = when (type) {
        Material.IRON_AXE -> iron
        Material.GOLDEN_AXE -> golden
        Material.DIAMOND_AXE -> diamond
        Material.NETHERITE_AXE -> netherite
        else -> null
    }
}

class AbilityStormBreaker : Ability<AbilityConceptStormBreaker>(), Listener {
    private val flyingAxes = HashMap<UUID, Pair<Snowball, Damage>>()
    private val trailTasks = HashMap<UUID, KolorTask>()
    private var hitLocation: Location? = null
    private var axeDisplay: ItemDisplay? = null

    override fun onEnable() {
        psychic.registerEvents(this)
        psychic.runTaskTimer(this::tick, 0L, 1L)
        updateCooldown((cooldownTime / 50L).toInt())
    }

    override fun onDisable() {
        flyingAxes.values.forEach { (projectile, _) -> projectile.remove() }
        flyingAxes.clear()
        trailTasks.values.forEach(KolorTask::cancel)
        trailTasks.clear()
        axeDisplay?.let(psychic::removeDisplay)
        axeDisplay = null
        hitLocation = null
    }

    override fun test(): io.github.monun.psychics.TestResult {
        if (psychic.channeling != null) return io.github.monun.psychics.TestResult.FailedChannel
        return super.test()
    }

    private fun tick() {
        if (durationTime <= 0L && hitLocation != null) {
            hitLocation = null
            axeDisplay?.let(psychic::removeDisplay)
            axeDisplay = null
            val location = esper.player.location
            location.world.spawnParticle(Particle.CLOUD, location.clone().add(0.0, 1.0, 0.0), 16, 0.2, 0.2, 0.2, 0.05)
        }
    }

    private fun updateCooldown(cooldownTicks: Int) {
        listOf(Material.IRON_AXE, Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE)
            .forEach { esper.player.setCooldown(it, cooldownTicks) }
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.player !== esper.player || event.hand != EquipmentSlot.HAND) return
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        val item = event.item ?: return
        val damage = concept.findDamage(item.type) ?: return

        hitLocation?.let { location ->
            hitLocation = null
            axeDisplay?.let(psychic::removeDisplay)
            axeDisplay = null
            durationTime = 0L
            val destination = location.clone().apply {
                yaw = event.player.location.yaw
                pitch = event.player.location.pitch
                y += 0.5
            }
            val previous = event.player.location
            event.player.teleportAsync(destination)
            event.player.world.spawnParticle(Particle.CLOUD, previous.add(0.0, 1.0, 0.0), 24, 0.2, 0.2, 0.2, 0.1)
            event.player.world.playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 0.4F + nextFloat() * 0.2F)
            return
        }

        val result = test()
        if (result !== io.github.monun.psychics.TestResult.Success) {
            result.message(this)?.let(event.player::sendActionBar)
            return
        }
        exhaust()
        updateCooldown((concept.cooldownTime / 50L).toInt())

        val start = event.player.eyeLocation
        val projectile = event.player.launchProjectile(
            Snowball::class.java,
            start.direction.multiply(concept.axeSpeed)
        )
        projectile.item = item.clone()
        projectile.setGravity(false)
        flyingAxes[projectile.uniqueId] = projectile to damage
        val trail = psychic.runTaskTimer(Runnable {
            if (!projectile.isValid) {
                trailTasks.remove(projectile.uniqueId)?.cancel()
                return@Runnable
            }
            projectile.world.spawnParticle(Particle.END_ROD, projectile.location, 1, 0.0, 0.0, 0.0, 0.025)
            projectile.velocity = projectile.velocity.apply { y -= concept.axeGravity }
        }, 0L, 1L)
        trailTasks[projectile.uniqueId] = trail
        event.player.swingMainHand()
    }

    @EventHandler(ignoreCancelled = true)
    fun onProjectileHit(event: ProjectileHitEvent) {
        val projectile = event.entity as? Snowball ?: return
        if (projectile.shooter !== esper.player) return
        val entry = flyingAxes.remove(projectile.uniqueId) ?: return
        trailTasks.remove(projectile.uniqueId)?.cancel()

        val hit = event.hitEntity?.location ?: projectile.location
        val location = hit.clone()
        val world = location.world
        durationTime = concept.durationTime
        hitLocation = location
        psychic.displayItem(location.clone().apply { y -= 0.35 }, projectile.item.clone()).thenAccept { display ->
            if (durationTime <= 0L || hitLocation == null) {
                psychic.removeDisplay(display)
            } else {
                axeDisplay = display
            }
        }
            .exceptionally { exception ->
                psychic.plugin.logger.warning("Could not create storm-breaker display: ${exception.message}")
                null
            }

        world.strikeLightningEffect(location)
        val radius = concept.lightningRadius
        val effect = PotionEffect(PotionEffectType.SLOWNESS, concept.slowDurationTicks, concept.slowAmplifier, false, false, false)
        world.getNearbyEntities(location, radius, radius, radius, TargetFilter(esper.player)).forEach { target ->
            if (target is LivingEntity) {
                target.addPotionEffect(effect)
                target.psychicDamage(entry.second, location, concept.knockback)
            }
        }
        val direction = Vector()
        repeat(32) {
            direction.setX(nextDouble() - 0.5).setY(nextDouble()).setZ(nextDouble() - 0.5)
            world.spawnParticle(Particle.ITEM, location, 0, direction.x, direction.y, direction.z, 1.0, projectile.item)
        }
        projectile.remove()
    }
}
