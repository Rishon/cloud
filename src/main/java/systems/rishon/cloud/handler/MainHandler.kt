package systems.rishon.cloud.handler

import systems.rishon.cloud.Cloud
import systems.rishon.cloud.manager.ServerManager
import systems.rishon.cloud.tasks.ServerManagerTask
import systems.rishon.cloud.utils.LoggerUtil
import java.util.concurrent.TimeUnit

class MainHandler(private val plugin: Cloud) : IHandler {

    // Manager
    private lateinit var serverManager: ServerManager

    // Tasks
    private lateinit var serverManagerTask: ServerManagerTask

    init {
        handler = this
    }

    override fun init() {
        LoggerUtil.log("Initializing MainHandler...")

        this.serverManager = ServerManager(this)
        loadTasks()
        LoggerUtil.log("MainHandler initialized!")
    }

    override fun end() {
        LoggerUtil.log("Shutting down MainHandler...")
        // Stop all servers
        for (name in this.serverManager.getContainerMap().keys) this.serverManager.stopServer(name)
    }

    private fun loadTasks() {
        this.serverManagerTask = ServerManagerTask(this)
        this.plugin.proxy.scheduler.buildTask(this.plugin, this.serverManagerTask)
            .repeat(FileHandler.handler.serverCheckInterval, TimeUnit.MINUTES)
            .schedule()
    }

    fun getServerManager(): ServerManager {
        return this.serverManager
    }

    fun getPlugin(): Cloud {
        return this.plugin
    }

    companion object {
        // Static Access
        lateinit var handler: MainHandler
    }
}