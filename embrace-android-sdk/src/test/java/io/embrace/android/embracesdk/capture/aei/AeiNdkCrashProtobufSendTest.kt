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
import java.util.Base64

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
     * Sends the protobuf file to the delivery service. This involves the protobuf file being read
     * and included into the payload. It is not a full test of how JSON is serialized & received
     * by our server.
     */
    @Test
    fun testDeliveredProtobufIsReadable() {
        val deliveryService = FakeDeliveryService()
        createAeiService(deliveryService)

        val obj = deliveryService.getAeiObject()
        val byteStream = obj.asStream()
        assertProtobufIsReadable(byteStream)
    }

    /**
     * Serializes then deserializes a protobuf in the payload. This ensures that information is
     * not lost when encoding the protobuf into JSON that is sent to the server.
     */
    @Test
    fun testReadProtobufFromPayload() {
        val deliveryService = FakeDeliveryService()
        createAeiService(deliveryService)

        // serialize then deserialize to/from JSON
        val obj = deliveryService.getAeiObject()
        val json = Gson().toJson(obj)
        val aei = Gson().fromJson(json, AppExitInfoData::class.java)
        val byteStream = aei.asStream()
        assertProtobufIsReadable(byteStream)
    }

    /**
     * Gets an inputstream of the protobuf file that was encoded in the AEI object
     */
    private fun AppExitInfoData.asStream(): InputStream {
        val decoder = Base64.getDecoder()
        val contents = decoder.decode(trace)
        return contents.inputStream()
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
