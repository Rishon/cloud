package systems.rishon.cloud.manager

import com.velocitypowered.api.proxy.server.RegisteredServer
import com.velocitypowered.api.proxy.server.ServerInfo
import systems.rishon.cloud.docker.DockerClientManager
import systems.rishon.cloud.handler.FileHandler
import systems.rishon.cloud.handler.MainHandler
import systems.rishon.cloud.utils.LoggerUtil
import java.util.Optional
import java.util.UUID

class ServerManager(private val handler: MainHandler) {

    // DockerClient
    private var dockerClient: DockerClientManager = DockerClientManager()

    // Container Map
    private var containerMap: MutableMap<String, String> = mutableMapOf()

    // Registered Docker Images
    private var images: MutableSet<String> = mutableSetOf()

    // ServerData
    val serverData = FileHandler.handler.serverData

    init {
        // Start with minimum servers
        this.serverData.forEach { data ->
            for (i in 0 until data.minConcurrentServers) {
                // Register if new image
                this.images.add(data.dockerImage)
                // Start server
                startServer(data.dockerImage, data.serverName)
            }
        }

        // Prune containers
        if (FileHandler.handler.pruneContainers) this.dockerClient.pruneContainers()

        // Delete dangling containers
        deleteDanglingContainers()
    }

    fun startServer(imageName: String, serverName: String) {
        val uniqueId = UUID.randomUUID().toString().slice(0..4)
        val name = "$serverName-$uniqueId"
        LoggerUtil.log("Starting server $name with image $imageName...")
        val containerId = this.dockerClient.createContainer(imageName, name)
        this.containerMap[name] = containerId
        this.dockerClient.startContainer(containerId)

        val proxy = this.handler.getPlugin().proxy
        val serverInfo: ServerInfo = ServerInfo(
            name, this.dockerClient.getContainerAddress(this.dockerClient.getContainer(containerId).get())
        )

        if (FileHandler.handler.autoAddServers) proxy.registerServer(serverInfo)
        else proxy.createRawRegisteredServer(serverInfo)

        LoggerUtil.log("Server with name $name started.")
    }

    fun stopServer(serverName: String) {
        val containerId = containerMap[serverName]

        if (containerId == null) {
            LoggerUtil.error("Container with name $serverName not found.")
            return
        }

        this.dockerClient.stopContainer(containerId)
        this.dockerClient.removeContainer(containerId)
        this.containerMap.remove(serverName)

        val proxy = this.handler.getPlugin().proxy
        val registeredServer: Optional<RegisteredServer?>? = proxy.getServer(serverName)
        if (registeredServer == null) return
        val serverInfo: ServerInfo = registeredServer.get().serverInfo
        proxy.unregisterServer(serverInfo)
    }

    fun deleteDanglingContainers() {
        val containers = this.dockerClient.listContainers()
        LoggerUtil.log("Checking for dangling containers...")
        for (container in containers) {
            if (this.images.contains(container.image) && !this.containerMap.containsValue(container.id)) {
                LoggerUtil.error("Dangling container with id ${container.id} and image ${container.image} will be removed.")
                this.dockerClient.stopContainer(container.id)
                this.dockerClient.removeContainer(container.id)
            }
        }
    }

    fun monitorAndScale() {
        val proxy = this.handler.getPlugin().proxy
        val playerCount = proxy.playerCount
        val serverCount = this.containerMap.size

        this.serverData.forEach { data ->
            if (serverCount < data.maxConcurrentServers) {
                if (playerCount >= serverCount * data.maxPlayers) {
                    LoggerUtil.log("Scaling up servers...")
                    startServer(data.dockerImage, data.serverName)
                } else if (playerCount < serverCount * data.maxPlayers && serverCount > data.minConcurrentServers) {
                    val serversToStop = this.containerMap.filter { (name, _) ->
                        proxy.getServer(name).map { it.playersConnected.size <= data.maxPlayers / 2 }.orElse(false)
                    }

                    serversToStop.forEach { (name, _) ->
                        stopServer(name)
                        LoggerUtil.log("Scaling down servers...")
                    }
                }
            }
        }
    }

    fun autoHeal() {
        // Auto heal servers
        for ((name, containerId) in this.containerMap) {

            if (!this.dockerClient.doesContainerExist(containerId)) {
                LoggerUtil.error("Server with name $name does not exist and will be removed.")
                this.containerMap.values.remove(containerId)
                monitorAndScale()
                continue
            }

            val state = this.dockerClient.getContainerState(containerId)
            if (state == "exited") {
                LoggerUtil.error("Server with name $name is in state $state and will be removed.")
                this.dockerClient.removeContainer(containerId)
            } else if (state != "running") {
                LoggerUtil.error("Server with name $name is in state $state and will be restarted.")
                stopServer(name)
                startServer(this.serverData.first { it.serverName == name }.dockerImage, name)
                return
            }
        }
    }

    fun getContainerMap(): MutableMap<String, String> {
        return this.containerMap
    }
}