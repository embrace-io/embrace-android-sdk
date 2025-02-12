package io.embrace.android.gradle.network

import okhttp3.MultipartReader
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class FormPart(
    val contentDisposition: String,
    val data: String?,
    val contentType: String? = null,
)

class MultipartFormReader {

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

fun FormPart.validateBodyAppId(expectedAppId: String) {
    assertEquals("form-data; name=\"app\"", contentDisposition)
    assertEquals(expectedAppId, data)
}

fun FormPart.validateBodyApiToken(expectedApiToken: String) {
    assertEquals("form-data; name=\"token\"", contentDisposition)
    assertEquals(expectedApiToken, data)
}

fun FormPart.validateBodyVariant(expectedVariantName: String) {
    assertEquals("form-data; name=\"variant\"", contentDisposition)
    assertEquals(expectedVariantName, data)
}

fun FormPart.validateBodyBuildId() {
    assertEquals("form-data; name=\"id\"", contentDisposition)
    assertTrue(data?.length == BUILD_ID_LENGTH)
}

fun FormPart.validateMappingFile(expectedFileName: String) {
    assertEquals(
        "form-data; name=\"file\"; filename=\"${expectedFileName}\"",
        contentDisposition
    )
    assertEquals("text/plain", contentType)
    assertTrue(checkNotNull(data?.length) > 0)
}

private const val BUILD_ID_LENGTH = 32
const val HEADER_APP_ID = "X-EM-AID"
