package http

import java.io.ByteArrayInputStream
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream

fun HttpResponse<ByteArray>.bodyText(): String =
    if (headers().firstValue("Content-Encoding").orElse("").lowercase().contains("gzip")) {
        GZIPInputStream(ByteArrayInputStream(body())).use { gzip ->
            String(gzip.readAllBytes(), StandardCharsets.UTF_8)
        }
    } else {
        String(body(), StandardCharsets.UTF_8)
    }
