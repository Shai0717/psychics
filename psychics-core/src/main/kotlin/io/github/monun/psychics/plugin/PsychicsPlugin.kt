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

package io.github.monun.psychics.plugin

import io.github.monun.psychics.PsychicManager
import io.github.monun.psychics.Psychics
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.command.PsychicsCommands
import io.github.monun.psychics.damage.Damage
import io.github.Shai0717.kolor.KolorDisplay
import io.github.Shai0717.kolor.KolorScheduler
import org.bukkit.Bukkit
import org.bukkit.configuration.serialization.ConfigurationSerialization
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/**
 * @author monun
 */
class PsychicsPlugin : JavaPlugin() {
    val kolorScheduler by lazy { KolorScheduler(this) }
    val kolorDisplay by lazy { KolorDisplay(this) }

    lateinit var psychicManager: PsychicManager
        private set

    override fun onLoad() {
        ConfigurationSerialization.registerClass(Damage::class.java)
        ConfigurationSerialization.registerClass(EsperStatistic::class.java)
    }

    override fun onEnable() {
        loadModules()
        setupCommands()
        registerPlayers()
        Psychics.initialize(this, logger, psychicManager)
    }

    private fun loadModules() {
        psychicManager = PsychicManager(
            this,
            logger,
            File(dataFolder, "abilities"),
            File(dataFolder, "psychics"),
            File(dataFolder, "espers")
        )

        psychicManager.run {
            updateAbilities()
            loadAbilities()
            loadPsychics()
        }

        server.apply {
            pluginManager.registerEvents(
                EventListener(psychicManager), this@PsychicsPlugin
            )
        }
    }

    private fun registerPlayers() {
        for (player in server.onlinePlayers) {
            psychicManager.addPlayer(player)
        }
    }

    private fun setupCommands() {
        PsychicsCommands(this, psychicManager).register()
    }

    override fun onDisable() {
        if (this::psychicManager.isInitialized) {
            psychicManager.unload()
        }
    }

    fun reloadPsychics() {
        psychicManager.reload()
    }
}