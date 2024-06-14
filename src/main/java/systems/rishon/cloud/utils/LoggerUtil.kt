package systems.rishon.cloud.utils

import systems.rishon.cloud.Cloud

object LoggerUtil {

    private val plugin = Cloud.plugin

    @JvmStatic
    fun log(message: String) {
        this.plugin.logger.info(message)
    }

    @JvmStatic
    fun warn(message: String) {
        this.plugin.logger.warning(message)
    }

    @JvmStatic
    fun error(message: String) {
        this.plugin.logger.severe(message)
    }

}