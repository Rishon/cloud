package systems.rishon.cloud

import com.google.inject.Inject
import com.velocitypowered.api.event.PostOrder
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import lombok.Getter
import systems.rishon.cloud.handler.FileHandler
import systems.rishon.cloud.handler.IHandler
import systems.rishon.cloud.handler.MainHandler
import java.nio.file.Path
import java.util.logging.Logger

@Getter
class Cloud @Inject constructor(
    @field:Getter val logger: Logger, @field:Getter val proxy: ProxyServer, @param:DataDirectory val directory: Path
) {

    // Handlers
    private lateinit var handlers: List<IHandler>

    init {
        plugin = this
    }

    @Subscribe(order = PostOrder.FIRST)
    fun onProxyInitialization(event: ProxyInitializeEvent) {
        this.logger.info("Initializing Cloud...")
        addHandlers()
        this.handlers.forEach { it.init() }
    }

    @Subscribe(order = PostOrder.LAST)
    fun onProxyShutdown(event: ProxyShutdownEvent) {
        this.logger.info("Shutting down Cloud...")
        this.handlers.forEach { it.end() }
    }

    private fun addHandlers() {
        this.handlers = listOf(
            FileHandler(this),
            MainHandler(this),
        )
    }

    companion object {
        // Static Access
        lateinit var plugin: Cloud
    }

}