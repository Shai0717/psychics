package io.github.monun.psychics

import org.bukkit.Location
import org.bukkit.util.Vector

data class ProjectileMovement(val from: Location, val to: Location)
data class ProjectileTrail(val from: Location, val velocity: Vector?)

abstract class PsychicProjectile(
    private val maxTicks: Int,
    private val maxRange: Double
) {
    lateinit var psychic: Psychic
        internal set

    lateinit var location: Location
        private set

    var velocity: Vector = Vector()

    private var ticks = 0
    private var travelled = 0.0
    private var removed = false

    internal fun launch(start: Location) {
        location = start.clone()
    }

    internal fun update() {
        if (removed) return
        if (ticks >= maxTicks || travelled >= maxRange) {
            remove()
            return
        }

        onPreUpdate()
        val from = location.clone()
        val to = from.clone().add(velocity)
        onMove(ProjectileMovement(from, to))
        onTrail(ProjectileTrail(from, velocity.clone()))
        if (removed) return
        travelled += from.distance(to)
        location = to
        ticks++
    }

    fun remove() {
        if (removed) return
        removed = true
        onRemove()
        psychic.removeProjectile(this)
    }

    protected open fun onPreUpdate() {}
    protected open fun onMove(movement: ProjectileMovement) {}
    protected open fun onTrail(trail: ProjectileTrail) {}
    protected open fun onRemove() {}
}
