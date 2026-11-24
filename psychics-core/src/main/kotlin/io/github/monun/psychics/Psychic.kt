/*
 * Copyright (c) 2020 monun
 *
 *  Licensed under the General Public License, Version 3.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/gpl-3.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.github.monun.psychics

import com.google.common.collect.ImmutableList
import io.github.Shai0717.kolor.KolorTask
import io.github.monun.psychics.plugin.PsychicsPlugin
import io.github.monun.psychics.util.Times
import net.md_5.bungee.api.ChatColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.attribute.Attribute
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.event.Listener
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import org.joml.Vector3f
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CompletableFuture
import kotlin.math.max

class Psychic internal constructor(
    val concept: PsychicConcept
) {
    lateinit var plugin: PsychicsPlugin
        private set

    lateinit var manager: PsychicManager
        private set

    /**
     * 마나량입니다.
     *
     * @exception IllegalArgumentException 유효하지 않은 객체일때 발생
     */
    var mana = 0.0
        set(value) {
            checkState()

            if (value != field) {
                val max = concept.mana
                field = value.coerceIn(0.0, max)
                updateManaBar()
            }
        }

    /**
     * 능력이 활성화된 시간입니다.
     *
     * 단위 = millis
     */
    var times = 0L
        private set

    /**
     * 능력 목록입니다.
     */
    val abilities: List<Ability<*>>

    /**
     * 현재 시전중인 채널입니다.
     *
     * @see Channel
     */
    var channeling: Channel? = null
        private set

    /**
     * 능력 활성화 여부입니다.
     *
     * @exception IllegalArgumentException 유효하지 않은 객체일때 발생
     */
    var isEnabled = false
        set(value) {
            checkState()

            if (field != value) {
                field = value

                if (value) {
                    onEnable()
                } else {
                    onDisable()
                }
            }
        }

    /**
     * 능력 유효상태입니다.
     */
    var valid = true
        private set

    /**
     * 능력이 부여된 [Esper]입니다.
     */
    lateinit var esper: Esper
        private set

    private var manaBar: BossBar? = null

    private lateinit var castingBar: BossBar

    private val tasks = ConcurrentHashMap.newKeySet<KolorTask>()
    private val projectiles = ConcurrentHashMap.newKeySet<PsychicProjectile>()
    private val listeners = Collections.newSetFromMap(WeakHashMap<Listener, Boolean>())
    private val displays = ConcurrentHashMap.newKeySet<Display>()
    private var updateTask: KolorTask? = null

    private var prevUpdateTime = 0L

    init {
        abilities = ImmutableList.copyOf(concept.abilityConcepts.map { concept ->
            concept.createAbilityInstance()
        })
    }

    internal fun initialize(plugin: PsychicsPlugin, manager: PsychicManager) {
        this.plugin = plugin
        this.manager = manager

        for (ability in abilities) {
            ability.initPsychic(this)
            ability.runCatching { onInitialize() }.onFailure { it.printStackTrace() }
        }
    }

    internal fun attach(esper: Esper) {
        require(!this::esper.isInitialized) { "Cannot redefine epser" }

        val player = esper.player

        this.esper = esper
        castingBar = Bukkit.createBossBar(null, BarColor.WHITE, BarStyle.SOLID).apply {
            addPlayer(player)
            isVisible = false
        }
        val playerTask = with(plugin.kolorScheduler) {
            player.runOn(periodTicks = 1, retiredCallback = {
                manager.logger.fine("Psychic player task retired: ${player.uniqueId}")
            }) {
                if (valid && isEnabled) update()
            }
        }
        checkNotNull(playerTask) { "Could not schedule Psychic update for ${player.uniqueId}" }
        updateTask = playerTask

        if (concept.mana > 0.0) {
            manaBar = Bukkit.createBossBar(null, concept.manaColor, BarStyle.SEGMENTED_10).apply {
                addPlayer(player)
                isVisible = true
            }
        }

        updateManaBar()

        for (ability in abilities) {
            ability.runCatching { onAttach() }.onFailure { it.printStackTrace() }
        }
    }

    private fun detach() {
        manaBar?.removeAll()
        castingBar.removeAll()

        for (ability in abilities) {
            ability.runCatching {
                cooldownTime = 0L
                onDetach()
            }.onFailure(Throwable::printStackTrace)
        }
    }

    private fun onEnable() {
        isEnabled = true
        prevUpdateTime = Times.current

        for (ability in abilities) {
            ability.runCatching { onEnable() }.onFailure(Throwable::printStackTrace)
        }
    }

    private fun onDisable() {
        castingBar.isVisible = false
        tasks.forEach(KolorTask::cancel)
        tasks.clear()
        projectiles.toList().forEach(PsychicProjectile::remove)
        listeners.toList().forEach(HandlerList::unregisterAll)
        listeners.clear()
        displays.forEach(plugin.kolorDisplay::remove)
        displays.clear()
        for (ability in abilities) {
            ability.runCatching { onDisable() }.onFailure(Throwable::printStackTrace)
        }
    }

    private fun updateManaBar() {
        manaBar?.let { bar ->
            val current = mana
            val max = concept.mana

            bar.progress = current / max
            bar.setTitle("${current.toInt()} / ${max.toInt()}")
        }
    }

    companion object {
        internal const val NAME = "name"
        private const val MANA = "mana"
        private const val TIMES = "time"
        private const val ENABLED = "enabled"
        private const val ABILITIES = "abilities"
    }

    internal fun save(config: ConfigurationSection) {
        config[NAME] = concept.name
        config[MANA] = mana
        config[TIMES] = times
        config[ENABLED] = isEnabled

        val abilitiesSection = config.createSection(ABILITIES)

        for (ability in abilities) {
            val abilityName = ability.concept.name
            val abilitySection = abilitiesSection.createSection(abilityName)

            ability.save(abilitySection)
        }
    }

    internal fun load(config: ConfigurationSection) {
        mana = config.getDouble(MANA).coerceIn(0.0, concept.mana)
        times = max(0L, config.getLong(TIMES))

        config.getConfigurationSection(ABILITIES)?.let { abilitiesSection ->
            for (ability in abilities) {
                val abilityName = ability.concept.name
                val abilitySection = abilitiesSection.getConfigurationSection(abilityName)

                if (abilitySection != null)
                    ability.load(abilitySection)
            }
        }

        isEnabled = config.getBoolean(ENABLED)
    }

    /**
     * [AbilityConcept.wand] 속성이 같은 [Ability]를 반환합니다.
     */
    fun getAbilityByWand(item: ItemStack): Ability<*>? {
        for (ability in abilities) {
            val wand = ability.concept.internalWand

            if (wand != null && wand.isSimilar(item))
                return ability
        }

        return null
    }

    /**
     * 태스크를 delay만큼 후에 실행합니다.
     *
     * 능력이 비활성화 될 때 취소됩니다.
     *
     * @exception IllegalArgumentException 유효하지 않은 객체일때 발생
     * @exception IllegalArgumentException 활성화되지 않은 객체일때 발생
     */
    fun runTask(runnable: Runnable, delay: Long): KolorTask {
        checkState()
        checkEnabled()

        return scheduleTask(runnable, delay, 0)
    }

    /**
     * 태스크를 delay만큼 후 period마다 주기적으로 실행합니다.
     *
     * 능력이 비활성화 될 때 취소됩니다.
     *
     * @exception IllegalArgumentException 유효하지 않은 객체일때 발생
     * @exception IllegalArgumentException 활성화되지 않은 객체일때 발생
     */
    fun runTaskTimer(runnable: Runnable, delay: Long, period: Long): KolorTask {
        checkState()
        checkEnabled()

        return scheduleTask(runnable, delay, period)
    }

    private fun scheduleTask(runnable: Runnable, delay: Long, period: Long): KolorTask {
        val player = esper.player
        val task = with(plugin.kolorScheduler) {
            player.runOn(delay, period, retiredCallback = {
                plugin.logger.fine("Psychic task owner retired: ${player.uniqueId}")
            }) {
                if (isEnabled && valid) runnable.run()
            }
        }
        return requireNotNull(task) { "Could not schedule Psychic task for ${player.uniqueId}" }
            .also(tasks::add)
    }

    /**
     * 이벤트를 등록합니다.
     *
     * 범위는 해당 [Psychic]이 부여된 [org.bukkit.entity.Player]객체로 국한됩니다.
     *
     * 능력이 비활성화 될 때 해제됩니다.
     *
     * @exception IllegalArgumentException 유효하지 않은 객체일때 발생
     * @exception IllegalArgumentException 활성화되지 않은 객체일때 발생
     */
    fun registerEvents(listener: Listener) {
        checkState()
        checkEnabled()

        plugin.server.pluginManager.registerEvents(listener, plugin)
        listeners.add(listener)
    }

    /**
     * 발사체를 발사합니다.
     *
     * 능력이 비활성화 될 때 제거됩니다.
     *
     * @exception IllegalArgumentException 유효하지 않은 객체일때 발생
     * @exception IllegalArgumentException 활성화되지 않은 객체일때 발생
     */
    fun launchProjectile(location: Location, projectile: PsychicProjectile) {
        checkState()
        checkEnabled()

        projectile.psychic = this
        projectile.launch(location)
        projectiles.add(projectile)
    }

    internal fun removeProjectile(projectile: PsychicProjectile) {
        projectiles.remove(projectile)
    }

    /**
     * 가상 [Entity]를 생성합니다.
     *
     * 능력이 비활성화 될 때 제거됩니다.
     *
     * @exception IllegalArgumentException 유효하지 않은 객체일때 발생
     * @exception IllegalArgumentException 활성화되지 않은 객체일때 발생
     */
    fun displayItem(location: Location, item: ItemStack): CompletableFuture<ItemDisplay> {
        return displayItem(location, item, Vector3f(1f, 1f, 1f))
    }

    fun displayItem(location: Location, item: ItemStack, scale: Vector3f): CompletableFuture<ItemDisplay> {
        checkState()
        checkEnabled()
        return plugin.kolorDisplay.item(esper.player, location, item, scale).thenApply { display ->
            if (!valid || !isEnabled) {
                plugin.kolorDisplay.remove(display)
                throw IllegalStateException("Psychic was disabled before its display spawned")
            }
            displays.add(display)
            display
        }
    }

    fun removeDisplay(display: Display) {
        displays.remove(display)
        plugin.kolorDisplay.remove(display)
    }

    /**
     * 마나를 소모합니다.
     *
     * 전달한 양보다 적다면 소모되지 않습니다.
     *
     * @return 소모 성공 여부
     */
    fun consumeMana(amount: Double): Boolean {
        if (mana >= amount) {
            mana -= amount

            return true
        }

        return false
    }

    internal fun startChannel(
        ability: ActiveAbility<*>,
        event: PlayerEvent,
        wandAction: ActiveAbility.WandAction,
        castingTime: Long,
        target: Any?
    ) {
        interruptChannel()

        channeling = Channel(ability, event, wandAction, castingTime, target)
        castingBar.apply {
            val abilityConcept = ability.concept
            setTitle("${ChatColor.BOLD}${concept.displayName}")
            color = abilityConcept.castingBarColor ?: BarColor.YELLOW
            progress = 0.0
            isVisible = true
        }
    }

    /**
     * 현재 실행중인 [Channel]을 중지시킵니다.
     */
    fun interruptChannel() {
        channeling?.let { channel ->
            channel.interrupt()
            channeling = null
        }
    }

    internal fun destroy() {
        isEnabled = false
        updateTask?.cancel()
        updateTask = null
        detach()
        valid = false
    }

    internal fun update() {
        val currentTime = Times.current
        val elapsedTime = currentTime - prevUpdateTime
        prevUpdateTime = currentTime
        times += elapsedTime

        regenMana(elapsedTime)
        regenHealth(elapsedTime)

        tasks.removeIf(KolorTask::isCancelled)
        projectiles.toList().forEach(PsychicProjectile::update)

        channeling?.let { channel ->
            val remainingTime = channel.remainingTime
            // 시간을 -150 하여 서버 <-> 클라이언트 사이의 딜레이 최소화
            castingBar.progress =
                (1.0 - max(0, remainingTime - 150).toDouble() / channel.ability.concept.castingTime.toDouble())

            if (remainingTime > 0L) {
                channel.channel()
            } else {
                castingBar.setTitle(null)
                castingBar.isVisible = false
                channeling = null
                channel.cast()
            }
        }
    }

    private fun regenMana(elapsedTime: Long) {
        mana = (mana + concept.manaRegenPerSecond * (elapsedTime / 1000.0)).coerceIn(0.0, concept.mana)
    }

    private fun regenHealth(elapsedTime: Long) {
        val player = esper.player
        val health = player.health

        if (health > 0.0) {
            val maxHealth = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
            player.health =
                (player.health + concept.healthRegenPerSecond * (elapsedTime / 1000.0)).coerceIn(
                    0.0,
                    maxHealth
                )
        }
    }

    /**
     * 능력 유효 여부를 체크합니다.
     *
     * @exception IllegalArgumentException 유효하지 않은 객체일때 발생
     */
    fun checkState() {
        require(valid) { "Invalid Psychic@${System.identityHashCode(this).toString(16)}" }
    }

    /**
     * 능력 활성화 여부를 체크합니다.
     *
     * @exception IllegalArgumentException 활성화되지 않은 객체일때 발생
     */
    fun checkEnabled() {
        require(isEnabled) { "Disabled Psychic@${System.identityHashCode(this).toString(16)}" }
    }
}

/**
 * 시전중인 스킬정보를 담고있는 클래스입니다.
 */
class Channel internal constructor(
    val ability: ActiveAbility<*>,
    val event: PlayerEvent,
    val action: ActiveAbility.WandAction,
    castingTime: Long,
    val target: Any? = null
) {
    private val channelTime = Times.current + castingTime

    /**
     * 시전까지 남은시간
     */
    val remainingTime
        get() = max(0, channelTime - Times.current)

    internal fun channel() {
        ability.runCatching { onChannel(this@Channel) }.onFailure(Throwable::printStackTrace)
    }

    internal fun cast() {
        ability.runCatching { onCast(event, action, target) }.onFailure(Throwable::printStackTrace)
    }

    internal fun interrupt() {
        ability.runCatching { onInterrupt(this@Channel) }.onFailure(Throwable::printStackTrace)
    }
}