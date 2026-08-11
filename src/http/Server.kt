package http

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * Thin wrapper around Java's built-in [HttpServer].
 *
 * @param httpModule request dispatcher for all incoming routes
 * @param port TCP port to bind; Render provides this through the PORT environment variable
 */
class Server(
    private val httpModule: HttpModule,
    private val port: Int = 8080
) {
    /**
     * Starts the HTTP server.
     *
     * @return the running [HttpServer] instance
     */
    fun start(): HttpServer {
        val server = HttpServer.create(InetSocketAddress(port), 0)
        server.createContext("/") { exchange -> httpModule.handle(exchange) }
        server.executor = Executors.newFixedThreadPool(4)
        server.start()
        return server
    }
}
