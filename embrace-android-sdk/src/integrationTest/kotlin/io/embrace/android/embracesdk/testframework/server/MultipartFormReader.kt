package io.embrace.android.embracesdk.testframework.server

import okhttp3.MultipartReader
import okhttp3.mockwebserver.RecordedRequest

internal class FormPart(
    val contentDisposition: String,
    val data: String?,
    val contentType: String? = null,
)

internal class MultipartFormReader {

    fun read(request: RecordedRequest): List<FormPart> {
        val boundary = checkNotNull(request.headers["Content-Type"]).substringAfter("boundary=")
        val reader = MultipartReader(request.body, boundary)
        val parts = mutableListOf<FormPart>()

        reader.use {
            while (true) {
                val part = it.nextPart() ?: break
                parts.add(
                    FormPart(
                        contentDisposition = part.headers["Content-Disposition"]
                            ?: error("Missing Content-Disposition"),
                        data = part.body.readUtf8(),
                        contentType = part.headers["Content-Type"]
                    )
                )
            }
        }
        return parts
    }
}
