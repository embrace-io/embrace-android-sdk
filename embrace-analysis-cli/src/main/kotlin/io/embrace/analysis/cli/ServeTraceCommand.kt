package io.embrace.analysis.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.Path

/**
 * `serve-trace`: serve a directory over HTTP on 127.0.0.1 with the CORS header ui.perfetto.dev
 * needs to fetch a local trace via its `?url=` deep link (a plain static server lacks it and the
 * fetch hangs in a private-network preflight).
 *
 * Then open `https://ui.perfetto.dev/#!/?url=http://127.0.0.1:<port>/<trace-filename>`. The first
 * fetch can take ~30 s; stop the server once the UI shows a `local_cache_key` URL.
 */
class ServeTraceCommand : CliktCommand(name = "serve-trace") {

    private val directory by argument("directory", help = "directory to serve").path(mustExist = true, canBeFile = false)
    private val port by argument("port", help = "TCP port on 127.0.0.1 (default $DEFAULT_PORT)").int().default(DEFAULT_PORT)

    override fun help(context: Context): String =
        "Serve a directory to ui.perfetto.dev with the CORS header its ?url= deep link needs."

    override fun run() {
        val root = directory.toAbsolutePath().normalize()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
        server.createContext("/") { exchange -> serve(root, exchange) }
        server.start()
        echo("serving $root on http://127.0.0.1:$port/  (open https://ui.perfetto.dev/#!/?url=http://127.0.0.1:$port/<trace>)")
        echo("Ctrl-C to stop")
        Thread.currentThread().join()
    }

    private fun serve(root: Path, exchange: HttpExchange) {
        exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
        val rel = URLDecoder.decode(exchange.requestURI.path.trimStart('/'), Charsets.UTF_8)
        val file = root.resolve(rel).normalize()
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            exchange.sendResponseHeaders(HTTP_NOT_FOUND, -1)
            exchange.close()
            return
        }
        exchange.responseHeaders.add("Content-Type", "application/octet-stream")
        if (exchange.requestMethod == "HEAD") {
            exchange.sendResponseHeaders(HTTP_OK, -1)
            exchange.close()
            return
        }
        exchange.sendResponseHeaders(HTTP_OK, Files.size(file))
        exchange.responseBody.use { out -> Files.newInputStream(file).use { it.copyTo(out) } }
    }

    private companion object {
        const val DEFAULT_PORT = 9001
        const val HTTP_OK = 200
        const val HTTP_NOT_FOUND = 404
    }
}
