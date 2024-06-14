package systems.rishon.cloud.handler

import com.moandjiezana.toml.Toml
import systems.rishon.cloud.Cloud
import systems.rishon.cloud.manager.ServerData
import systems.rishon.cloud.utils.LoggerUtil
import java.io.File
import java.io.IOException
import java.nio.file.Files

class FileHandler(private val plugin: Cloud) : IHandler {

    // Config
    private lateinit var config: Toml

    // Docker Settings
    var dockerHost: String? = null
    var dockerLocalIP: String? = null
    var serverCheckInterval: Long = 60

    // Scaling
    var serverData: MutableList<ServerData> = mutableListOf()

    init {
        handler = this
    }

    override fun init() {
        LoggerUtil.log("Initializing FileHandler...")
        loadConfigurations()
        LoggerUtil.log("FileHandler initialized!")
    }

    override fun end() {
        LoggerUtil.log("Shutting down FileHandler...")
    }

    private fun loadConfigurations() {
        val dir = this.plugin.directory.toFile()
        dir.mkdirs()
        val configFile = File(dir, "config.toml")
        if (!configFile.exists()) {
            try {
                val stream = javaClass.classLoader.getResourceAsStream("config.toml")
                if (stream != null) Files.copy(stream, configFile.toPath())
                else configFile.createNewFile()
            } catch (e: IOException) {
                e.printStackTrace()
                this.plugin.logger.warning("Error while copying default configuration file.")
            }
        }

        this.config = Toml().read(configFile)
        loadDockerSettings()
    }

    private fun loadDockerSettings() {
        this.dockerHost = this.config.getString("docker.host") ?: "tcp://127.0.0.1:2375"
        this.dockerLocalIP = this.config.getString("docker.local-ip") ?: "127.0.0.1"
        this.serverCheckInterval = this.config.getLong("docker.server_check_interval")

        // Load servers data
        val serverDataList = this.config.getTables("server") ?: emptyList()

        for (serverTable in serverDataList) {
            val server = ServerData()
            server.serverName = serverTable.getString("prefix-name") ?: "server"
            server.dockerImage = serverTable.getString("docker-image") ?: ""
            server.dockerTag = serverTable.getString("docker-tag") ?: "latest"
            server.minConcurrentServers = serverTable.getLong("min-concurrent-servers")?.toInt() ?: 1
            server.maxConcurrentServers = serverTable.getLong("max-concurrent-servers")?.toInt() ?: 1
            server.maxPlayers = serverTable.getLong("max-players")?.toInt() ?: 10
            this.serverData.add(server)
            LoggerUtil.log("Loaded server ${server.serverName} with image ${server.dockerImage}:${server.dockerTag}")
        }
    }

    companion object {
        // Static Access
        lateinit var handler: FileHandler
    }
}