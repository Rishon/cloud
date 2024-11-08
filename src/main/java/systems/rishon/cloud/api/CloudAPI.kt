package systems.rishon.cloud.api

import systems.rishon.cloud.docker.DockerClientManager
import systems.rishon.cloud.manager.ServerManager

class CloudAPI {

    init {
        instance = this
    }

    /**
     * Get all names of running servers
     * @return List<String>
     */
    fun getRunningServerNames(): List<String> {
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

    /**
     * Will stop the server and use the latest image to start it again
     * @param serverName String
     * @throws Exception
     */
    fun redeployServer(serverName: String) {
        val serverManager = ServerManager.getManager()
        serverManager.serverData
    }

    /**
     * Check if a server is related to the cloud system
     * @param serverName String
     * @return Boolean
     */
    fun isServerRelatedToCloud(serverName: String): Boolean {
        val serverManager = ServerManager.getManager()
        if (serverManager.getContainerMap().isEmpty()) return false
        return serverManager.getContainerMap().containsKey(serverName)
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