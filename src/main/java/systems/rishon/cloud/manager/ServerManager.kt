package systems.rishon.cloud.manager

import com.github.dockerjava.api.command.InspectContainerResponse
import com.github.dockerjava.api.model.Ports
import com.velocitypowered.api.proxy.server.ServerInfo
import systems.rishon.cloud.docker.DockerClientManager
import systems.rishon.cloud.handler.FileHandler
import systems.rishon.cloud.handler.MainHandler
import systems.rishon.cloud.utils.LoggerUtil
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

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
        instance = this

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

    fun startServer(imageName: String, serverName: String): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()

        val uniqueId = UUID.randomUUID().toString().slice(0..4)
        val name = "$serverName-$uniqueId"
        LoggerUtil.log("Starting server $name with image $imageName...")

        val container = this.dockerClient.createContainer(imageName, name)
        val containerId = container.id
        LoggerUtil.log("Container with name $name created with id ${containerId}.")

        this.containerMap[name] = containerId

        try {
            this.dockerClient.startContainer(containerId)
            LoggerUtil.log("Container with name $name started.")

            val proxy = this.handler.getPlugin().proxy

            this.handler.getPlugin().proxy.scheduler.buildTask(this.handler.getPlugin()) { task ->
                val containerInfo: InspectContainerResponse =
                    this.dockerClient.getClient().inspectContainerCmd(containerId).exec()
                val portBindings: Ports? = containerInfo.networkSettings.ports
                val port = portBindings?.getBindings()?.keys?.first()?.port
                val socketAddress: InetSocketAddress =
                    InetSocketAddress(FileHandler.handler.dockerLocalIP, Integer.parseInt(port.toString()))
                val serverInfo = ServerInfo(
                    name, socketAddress
                )

                LoggerUtil.log("Registering server with name $name...")

                if (FileHandler.handler.autoAddServers) {
                    proxy.registerServer(serverInfo)
                } else {
                    proxy.createRawRegisteredServer(serverInfo)
                }

                LoggerUtil.log("Server with name $name started.")
            }.delay(2, TimeUnit.SECONDS).schedule()

        } catch (e: Exception) {
            LoggerUtil.error("Failed to register server with name $name.")
            e.printStackTrace()
        }

        return future
    }

    fun stopServer(serverName: String, async: Boolean) {
        val stopServerTask = {
            val containerId = containerMap[serverName]

            if (containerId != null) {
                containerMap.remove(serverName)
                dockerClient.stopContainer(containerId)
                dockerClient.removeContainer(containerId)

                val proxy = handler.getPlugin().proxy
                val registeredServer = proxy.getServer(serverName)
                if (registeredServer != null && registeredServer.isPresent) {
                    val serverInfo: ServerInfo = registeredServer.get().serverInfo
                    proxy.unregisterServer(serverInfo)
                }
            } else {
                LoggerUtil.error("Container with name $serverName not found.")
            }
        }

        if (async) {
            CompletableFuture.runAsync(stopServerTask)
        } else {
            stopServerTask()
        }
    }

    fun deleteContainersWithImages() {
        val containers = this.dockerClient.listContainers()
        LoggerUtil.log("Checking for containers with images...")

        for (container in containers) {
            if (this.images.contains(container.image)) {
                LoggerUtil.error("Container with id ${container.id} and image ${container.image} will be removed.")
                this.dockerClient.removeContainer(container.id, true)
            }
        }
        LoggerUtil.log("Containers with images removed.")
    }

    fun deleteDanglingContainers(): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()

        val containers = this.dockerClient.listContainers()
        LoggerUtil.log("Checking for dangling containers...")

        for (container in containers) {
            if (this.images.contains(container.image) && !this.containerMap.containsValue(container.id)) {
                LoggerUtil.error("Dangling container with id ${container.id} and image ${container.image} will be removed.")
                this.dockerClient.removeContainer(container.id, true)
            }
        }
        LoggerUtil.log("Dangling containers removed.")

        return future
    }

    /**
     * Monitor and scale servers based on the load
     */
    fun monitorAndScale() {
        val proxy = this.handler.getPlugin().proxy

        this.serverData.forEach { data ->
            val serverName = data.serverName
            val maxPlayers = data.maxPlayers
            val proxyServers = proxy.allServers

            val playerCount = proxyServers.stream().filter { it.serverInfo.name.startsWith(serverName) }
                .mapToInt { it.playersConnected.size }.sum()

            val serverCount = this.containerMap.entries.count { it.key.startsWith(serverName) }

            if (serverCount < data.maxConcurrentServers) {
                if (playerCount > serverCount * maxPlayers) {
                    startServer(data.dockerImage, serverName)
                }
            } else if (serverCount > data.minConcurrentServers) {
                val serversToStop = this.containerMap.filter { (name, _) ->
                    proxy.getServer(name).map { server ->
                        server.playersConnected.size < maxPlayers / 2
                    }.orElse(false)
                }

                serversToStop.forEach { (name, _) ->
                    stopServer(name, true)
                    LoggerUtil.log("Server with name $name stopped.")
                }
            }
        }
    }

    /**
     * Auto heal servers
     * Remove servers that are not running or in exited state
     * Restart servers that are not running
     * Scale servers based on the load
     */
    fun autoHeal(): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()
        // Auto heal servers

        for ((name, containerId) in this.containerMap) {

            if (!this.dockerClient.doesContainerExist(containerId)) {
                LoggerUtil.error("Container with name $name does not exist and will be removed.")
                this.containerMap.remove(name)

                // Unregister server
                val opt = this.handler.getPlugin().proxy.getServer(name)
                if (opt.isPresent) {
                    val serverInfo: ServerInfo = opt.get().serverInfo
                    this.handler.getPlugin().proxy.unregisterServer(serverInfo)
                }

                monitorAndScale()
                continue
            }

            val state = this.dockerClient.getContainerState(containerId)
            if (state == "exited") {
                LoggerUtil.error("Container with name $name is in state $state and will be removed.")
                this.dockerClient.removeContainer(containerId)
            } else if (state != "running") {
                LoggerUtil.error("Container with name $name is in state $state and will be restarted.")
                stopServer(name, true)
                startServer(this.serverData.first { it.serverName == name }.dockerImage, name)
                return future
            }
        }

        return future
    }

    /**
     * Get the container map
     *
     * @return MutableMap<String, String>
     */
    fun getContainerMap(): MutableMap<String, String> {
        return this.containerMap
    }

    // Access
    companion object {
        lateinit var instance: ServerManager

        @JvmStatic
        fun getManager(): ServerManager {
            return instance
        }
    }
}