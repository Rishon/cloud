package systems.rishon.cloud.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Container
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.PruneType
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import systems.rishon.cloud.handler.FileHandler
import java.net.InetSocketAddress
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

    fun createContainer(image: String, name: String): String {
        val hostConfig = HostConfig()
        hostConfig.withPublishAllPorts(true)
        hostConfig.withNetworkMode("bridge")

        val containerResponse = this.client.createContainerCmd(image).withName(name).withHostConfig(hostConfig).exec()
        return containerResponse.id
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

    fun getContainerAddress(container: Container): InetSocketAddress {
        val port: Int = container.ports?.first()?.publicPort
            ?: throw IllegalArgumentException("Container does not have any ports exposed.")
        return InetSocketAddress(FileHandler.handler.dockerLocalIP, port)
    }

    fun removeContainer(containerId: String) {
        if (!doesContainerExist(containerId)) return
        this.client.removeContainerCmd(containerId).exec()
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
}