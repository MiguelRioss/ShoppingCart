package http

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors

class Server(
    private val httpModule: HttpModule,
    private val port: Int = 8080
) {
    fun start(): HttpServer {
        val server = HttpServer.create(InetSocketAddress(port), 0)
        server.createContext("/") { exchange -> httpModule.handle(exchange) }
        server.executor = Executors.newFixedThreadPool(4)
        server.start()
        return server
    }
}
