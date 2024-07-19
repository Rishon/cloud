package systems.rishon.cloud.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateContainerResponse
import com.github.dockerjava.api.model.Container
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Ports
import com.github.dockerjava.api.model.PruneType
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import systems.rishon.cloud.handler.FileHandler
import systems.rishon.cloud.utils.LoggerUtil
import java.util.Optional

class DockerClientManager() {

    private var client: DockerClient

    init {
        // Docker Client Config
        val config =
            DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost(FileHandler.handler.dockerHost)
                .withDockerTlsVerify(false).build()

        // HTTP Client
        val httpClient =
            ApacheDockerHttpClient.Builder().dockerHost(config.dockerHost).sslConfig(config.sslConfig).build()

        // Docker Client
        this.client = DockerClientImpl.getInstance(config, httpClient)
    }

    fun createContainer(image: String, name: String): Container {
        val fileHandler = FileHandler.handler

        val portRange = fileHandler.portRange.split(":").map { it.toInt() }
        val availablePorts = (portRange[0]..portRange[1]).toMutableList()

        // Check for available ports and create containers
        while (availablePorts.isNotEmpty()) {
            val port = availablePorts.removeAt(0)
            // If port exists, choose another port
            if (doesPortExist(port)) continue

            val exposedPort = ExposedPort.tcp(port)
            val portBindings = Ports().apply {
                bind(exposedPort, Ports.Binding.bindPort(port))
            }

            val hostConfig = HostConfig()
                .withPortBindings(portBindings)
                .withNetworkMode("bridge")
                .withAutoRemove(true)

            val containerResponse: CreateContainerResponse = this.client.createContainerCmd(image)
                .withName("${name}_$port")
                .withHostConfig(hostConfig)
                .withExposedPorts(exposedPort)
                .withHostName(fileHandler.dockerLocalIP)
                .exec()

            LoggerUtil.log("Assigned port $port to container ${containerResponse.id}.")

            return getContainer(containerResponse.id).orElseThrow()
        }

        throw IllegalArgumentException("No available ports in range ${fileHandler.portRange}")
    }

    private fun doesPortExist(port: Int): Boolean {
        return this.client.listContainersCmd().withShowAll(true).exec()
            .any { it.ports?.any { portBinding -> portBinding.publicPort == port } == true }
    }

    fun startContainer(containerId: String) {
        if (!doesContainerExist(containerId)) return
        this.client.startContainerCmd(containerId).exec()
    }

    fun stopContainer(containerId: String) {
        if (!doesContainerExist(containerId)) return
        this.client.stopContainerCmd(containerId).exec()
    }

    fun getContainer(containerId: String): Optional<Container> {
        return this.client.listContainersCmd().withShowAll(true).exec().stream().filter { it.id == containerId }
            .findFirst()
    }

//    fun getContainerAddress(container: Container): InetSocketAddress {
//        val port: Int = container.ports?.first()?.publicPort
//            ?: throw IllegalArgumentException("Container does not have any ports exposed.")
//        return InetSocketAddress(FileHandler.handler.dockerLocalIP, port)
//    }

    fun removeContainer(containerId: String, force: Boolean = false) {
        if (!doesContainerExist(containerId)) return
        this.client.removeContainerCmd(containerId).withForce(force).exec()
    }

    fun doesContainerExist(containerId: String): Boolean {
        return this.client.listContainersCmd().withShowAll(true).exec().any { it.id == containerId }
    }

    fun pruneContainers() {
        this.client.pruneCmd(PruneType.CONTAINERS).exec()
    }

    fun listContainers(): List<Container> {
        return this.client.listContainersCmd().withShowAll(true).exec()
    }

    fun getContainerState(containerId: String): String {
        return this.client.inspectContainerCmd(containerId).exec().state.status.toString()
    }

    fun getClient(): DockerClient {
        return this.client
    }
}