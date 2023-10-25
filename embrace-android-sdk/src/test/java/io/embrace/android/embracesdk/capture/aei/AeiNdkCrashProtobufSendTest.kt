package io.embrace.android.embracesdk.capture.aei

import android.app.ActivityManager
import com.android.server.os.TombstoneProtos
import com.google.common.util.concurrent.MoreExecutors
import com.google.gson.Gson
import io.embrace.android.embracesdk.FakeDeliveryService
import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.config.local.AppExitInfoLocalConfig
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.fakeAppExitInfoBehavior
import io.embrace.android.embracesdk.payload.AppExitInfoData
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.InputStream

/**
 * Verifies that the protobuf file containing the NDK crash details can be sent by our SDK.
 */
internal class AeiNdkCrashProtobufSendTest {

    companion object {
        private const val FILE_NAME = "aei_ndk_crash.protobuf"
    }

    /**
     * Sanity check that the protobuf file can be read from the schema defined as part
     * of ApplicationExitInfo docs
     */
    @Test
    fun testLocalProtobufIsReadable() {
        val stream = ResourceReader.readResource(FILE_NAME)
        assertProtobufIsReadable(stream)
    }

    /**
     * Serializes then deserializes a protobuf in the payload. This ensures that information is
     * not lost when encoding the protobuf into JSON that is sent to the server.
     */
    @Test
    fun testReadProtobufFromPayload() {
        val deliveryService = FakeDeliveryService()
        createAeiService(deliveryService)

        // sending through the delivery service does not corrupt the protobuf
        val obj = deliveryService.getAeiObject()
        assertProtobufIsReadable(obj.asStream())

        // JSON serialization does not corrupt the protobuf
        val json = Gson().toJson(obj)
        val aei = Gson().fromJson(json, AppExitInfoData::class.java)
        val byteStream = aei.asStream()
        assertProtobufIsReadable(byteStream)
    }

    /**
     * Gets an inputstream of the protobuf file that was encoded in the AEI object
     */
    private fun AppExitInfoData.asStream(): InputStream {
        val contents = trace ?: error("No trace found")
        return utf8StringToBytes(contents).inputStream()
    }

    /**
     * Decodes a UTF-8 string with arbitrary binary data encoded in \Uxxxx characters.
     */
    private fun utf8StringToBytes(utf8String: String): ByteArray {
        val encoded = utf8String.toByteArray(Charsets.UTF_8)
        val decoded = ByteArray(encoded.size)

        var decodedIndex = 0
        var k = 0
        while (k < encoded.size) {
            val u = encoded[k].toInt() and 0xFF
            if (u < 128) {
                decoded[decodedIndex++] = u.toByte()
                k++
            } else {
                val a = u and 0x1F
                k++
                if (k >= encoded.size) {
                    error("Invalid UTF-8 encoding")
                }
                val b = encoded[k].toInt() and 0x3F
                k++
                decoded[decodedIndex++] = ((a shl 6) or b).toByte()
            }
        }
        return decoded.copyOf(decodedIndex)
    }

    /**
     * Gets the AEI object that was sent to the delivery service
     */
    private fun FakeDeliveryService.getAeiObject(): AppExitInfoData {
        val requests: List<AppExitInfoData> = appExitInfoRequests.single()
        return requests.single()
    }

    private fun assertProtobufIsReadable(stream: InputStream) {
        val tombstone = TombstoneProtos.Tombstone.parseFrom(stream)
        checkNotNull(tombstone)
        assertEquals("2023-10-18 17:10:28.838542153+0100", tombstone.timestamp)
        assertEquals("SIGSEGV", tombstone.signalInfo.name)
    }

    private fun createAeiService(deliveryService: FakeDeliveryService) {
        val stream = ResourceReader.readResource(FILE_NAME)
        val activityManager = createMockActivityManager(stream)
        EmbraceApplicationExitInfoService(
            MoreExecutors.newDirectExecutorService(),
            FakeConfigService(
                appExitInfoBehavior = fakeAppExitInfoBehavior(localCfg = {
                    AppExitInfoLocalConfig(
                        aeiCaptureEnabled = true
                    )
                })
            ),
            activityManager,
            FakePreferenceService(),
            deliveryService
        )
    }

    private fun createMockActivityManager(stream: InputStream): ActivityManager {
        return mockk(relaxed = true) {
            every {
                getHistoricalProcessExitReasons(
                    any(),
                    any(),
                    any()
                )
            } returns listOf(
                mockk(relaxed = true) {
                    every { traceInputStream } returns stream
                }
            )
        }
    }
}
