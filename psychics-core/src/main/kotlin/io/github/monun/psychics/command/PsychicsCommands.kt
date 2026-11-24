package io.github.monun.psychics.command

import io.github.Shai0717.kolor.compat.kommand
import io.github.monun.psychics.PsychicManager
import io.github.monun.psychics.invfx.InvPsychic
import io.github.monun.psychics.item.addItemNonDuplicate
import io.github.monun.psychics.item.psionicsLevel
import io.github.monun.psychics.plugin.PsychicsPlugin
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

internal class PsychicsCommands(
    private val plugin: PsychicsPlugin,
    private val manager: PsychicManager
) {
    fun register() {
        plugin.kommand {
            register("psychics", "psy") {
                requires { it.hasPermission("psychics.commands") }
                executes { context ->
                    context.sender.sendMessage("사용법: /psychics <attach|detach|info|supply|enchant|reload>")
                    1
                }
                greedyStringArg("input") {
                    executes { context ->
                        execute(context.sender, context.string("input").trim().split(Regex("\\s+")))
                        1
                    }
                }
            }
            register("ability") {
                executesOnEntity { player, _ -> open(player) }
            }
        }
    }

    private fun execute(sender: CommandSender, args: List<String>) {
        when (args.firstOrNull()?.lowercase()) {
            "attach" -> attach(sender, args)
            "detach" -> detach(sender, args)
            "info" -> info(sender, args)
            "supply" -> withPlayer(sender) { player ->
                val psychic = manager.getEsper(player)?.psychic
                if (psychic == null) {
                    sender.sendMessage("능력이 없습니다.")
                } else if (args.size == 1) {
                    psychic.concept.abilityConcepts.forEach { player.inventory.addItemNonDuplicate(it.supplyItems) }
                    sender.sendMessage("능력 아이템을 지급했습니다.")
                } else {
                    val ability = psychic.abilities.find { it.concept.name == args[1] }
                    if (ability == null) sender.sendMessage("스킬을 찾을 수 없습니다: ${args[1]}")
                    else {
                        player.inventory.addItemNonDuplicate(ability.concept.supplyItems)
                        sender.sendMessage("능력 아이템을 지급했습니다.")
                    }
                }
            }
            "enchant" -> withPlayer(sender) { player ->
                val level = args.getOrNull(1)?.toIntOrNull()
                if (args.size != 2 || level == null || level !in 0..5) {
                    sender.sendMessage("사용법: /psychics enchant <0~5>")
                } else {
                    val item = player.inventory.itemInMainHand
                    if (item.type == Material.AIR) sender.sendMessage("인챈트할 아이템을 손에 들어주세요.")
                    else {
                        item.psionicsLevel = level
                        sender.sendMessage("아이템에 초능력 레벨 $level 을 부여했습니다.")
                    }
                }
            }
            "reload" -> {
                if (args.size != 1) sender.sendMessage("사용법: /psychics reload")
                else {
                    plugin.reloadPsychics()
                    sender.sendMessage("Psychics 설정을 다시 불러왔습니다.")
                }
            }
            else -> sender.sendMessage("사용법: /psychics <attach|detach|info|supply|enchant|reload>")
        }
    }

    private fun attach(sender: CommandSender, args: List<String>) {
        if (args.size != 3) {
            sender.sendMessage("사용법: /psychics attach <플레이어> <능력>")
            return
        }
        val target = Bukkit.getPlayerExact(args[1])
        val concept = manager.getPsychicConcept(args[2])
        if (target == null) {
            sender.sendMessage("접속 중인 플레이어를 찾을 수 없습니다.")
            return
        }
        if (concept == null) {
            sender.sendMessage("존재하지 않는 능력입니다: ${args[2]}")
            return
        }
        runOn(target, sender) {
            val esper = manager.getEsper(target)
            if (esper == null) sender.sendMessage("플레이어 데이터를 찾을 수 없습니다.")
            else {
                esper.attachPsychic(concept).isEnabled = true
                sender.sendMessage("${target.name}의 능력: ${concept.name}")
            }
        }
    }

    private fun detach(sender: CommandSender, args: List<String>) {
        if (args.size != 2) {
            sender.sendMessage("사용법: /psychics detach <플레이어>")
            return
        }
        val target = Bukkit.getPlayerExact(args[1])
        if (target == null) {
            sender.sendMessage("접속 중인 플레이어를 찾을 수 없습니다.")
            return
        }
        runOn(target, sender) {
            val esper = manager.getEsper(target)
            if (esper == null) sender.sendMessage("플레이어 데이터를 찾을 수 없습니다.")
            else {
                esper.detachPsychic()
                sender.sendMessage("${target.name}의 능력을 제거했습니다.")
            }
        }
    }

    private fun info(sender: CommandSender, args: List<String>) = withPlayer(sender) { player ->
        val esper = manager.getEsper(player)
        val concept = if (args.size == 2) manager.getPsychicConcept(args[1]) else esper?.psychic?.concept
        if (args.size > 2 || esper == null || concept == null) sender.sendMessage("능력을 찾을 수 없습니다.")
        else InvPsychic.open(player, concept, esper::getStatistic)
    }

    private fun open(player: Player) {
        val esper = manager.getEsper(player)
        val concept = esper?.psychic?.concept
        if (esper == null || concept == null) player.sendMessage("능력이 없습니다.")
        else InvPsychic.open(player, concept, esper::getStatistic)
    }

    private fun withPlayer(sender: CommandSender, action: (Player) -> Unit) {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.")
            return
        }
        runOn(player, sender) { action(player) }
    }

    private fun runOn(target: Player, sender: CommandSender, action: () -> Unit) {
        val task = with(plugin.kolorScheduler) {
            target.runOn(retiredCallback = {
                plugin.logger.warning("Psychics command cancelled because player retired: ${target.uniqueId}")
            }, task = action)
        }
        if (task == null) sender.sendMessage("플레이어 작업을 예약하지 못했습니다.")
    }
}
