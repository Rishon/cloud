package systems.rishon.cloud.manager

import com.velocitypowered.api.proxy.server.ServerInfo
import systems.rishon.cloud.docker.DockerClientManager
import systems.rishon.cloud.handler.FileHandler
import systems.rishon.cloud.handler.MainHandler
import systems.rishon.cloud.utils.LoggerUtil
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ServerManager(private val handler: MainHandler) {

    // Executor
    private val executor: ExecutorService = Executors.newFixedThreadPool(10)

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
        CompletableFuture.runAsync({
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
        }, executor)
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
            CompletableFuture.runAsync(stopServerTask, executor)
        } else {
            stopServerTask()
        }
    }

    fun deleteDanglingContainers() {
        CompletableFuture.runAsync({
            val containers = this.dockerClient.listContainers()
            LoggerUtil.log("Checking for dangling containers...")
            for (container in containers) {
                if (this.images.contains(container.image) && !this.containerMap.containsValue(container.id)) {
                    LoggerUtil.error("Dangling container with id ${container.id} and image ${container.image} will be removed.")
                    this.dockerClient.stopContainer(container.id)
                    this.dockerClient.removeContainer(container.id)
                }
            }
            LoggerUtil.log("Dangling containers removed.")
        }, executor)
    }

    fun monitorAndScale() {
        CompletableFuture.runAsync({
            val proxy = this.handler.getPlugin().proxy
            val playerCount = proxy.playerCount
            val serverCount = this.containerMap.size
            val scaleDownBuffer = 0.8

            this.serverData.forEach { data ->
                if (serverCount < data.maxConcurrentServers) {
                    if (playerCount > serverCount * data.maxPlayers) {
                        startServer(data.dockerImage, data.serverName)
                    } else if (playerCount < serverCount * data.maxPlayers * scaleDownBuffer && serverCount > data.minConcurrentServers) {
                        val serversToStop = this.containerMap.filter { (name, _) ->
                            proxy.getServer(name).map { server ->
                                server.playersConnected.size <= data.maxPlayers / 2 && data.downScaleIfEmpty
                            }.orElse(false)
                        }

                        serversToStop.forEach { (name, _) ->
                            stopServer(name, true)
                        }
                    }
                }
            }
        }, executor)
    }

    fun autoHeal() {
        CompletableFuture.runAsync({
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
                    stopServer(name, true)
                    startServer(this.serverData.first { it.serverName == name }.dockerImage, name)
                    return@runAsync
                }
            }
        }, executor)
    }

    fun getContainerMap(): MutableMap<String, String> {
        return this.containerMap
    }
}