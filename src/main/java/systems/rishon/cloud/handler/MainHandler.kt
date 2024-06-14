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

        // Unregister schedulers
        this.plugin.proxy.scheduler.tasksByPlugin(this.plugin).forEach { it.cancel() }

        // Stop all servers
        LoggerUtil.log("Stopping all servers...")
        for (name in this.serverManager.getContainerMap().keys) {
            this.serverManager.stopServer(name)
            LoggerUtil.log("Server with name $name stopped.")
        }

        this.serverManager.deleteDanglingContainers()
    }

    private fun loadTasks() {
        this.serverManagerTask = ServerManagerTask(this)
        this.plugin.proxy.scheduler.buildTask(this.plugin, this.serverManagerTask)
            .repeat(FileHandler.handler.serverCheckInterval, TimeUnit.SECONDS).schedule()
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