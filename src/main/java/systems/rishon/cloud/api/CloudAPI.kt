package systems.rishon.cloud.api

import systems.rishon.cloud.docker.DockerClientManager
import systems.rishon.cloud.manager.ServerManager

class CloudAPI {

    init {
        instance = this
    }

    /**
     * Get all running servers names
     * @return List<String>
     */
    fun getRunningServersNames(): List<String> {
        val serverManager = ServerManager.getManager()
        return serverManager.getContainerMap().keys.toList()
    }

    /**
     * Restart a server
     * @param serverName String
     * @throws Exception
     */
    fun restartServer(serverName: String) {
        val dockerClient = DockerClientManager.getManager()
        if (!dockerClient.restartContainer(serverName)) throw Exception("Failed to restart server $serverName")
    }

    companion object {
        // Static-Access
        lateinit var instance: CloudAPI

        @JvmStatic
        fun getAPI(): CloudAPI {
            return instance
        }
    }
}