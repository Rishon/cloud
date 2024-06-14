package systems.rishon.cloud.tasks

import systems.rishon.cloud.handler.MainHandler

class ServerManagerTask(private val handler: MainHandler) : Runnable {

    override fun run() {
        this.handler.getServerManager().monitorAndScale()
        this.handler.getServerManager().autoHeal()
    }

}