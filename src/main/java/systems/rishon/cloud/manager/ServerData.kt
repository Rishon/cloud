package systems.rishon.cloud.manager

import lombok.Data

@Data
class ServerData {
    var serverName: String = ""
    var dockerImage: String = ""
    var dockerTag = "latest"
    var minConcurrentServers = 1
    var maxConcurrentServers = 1
    var maxPlayers = 10
    var downScaleIfEmpty = true
}