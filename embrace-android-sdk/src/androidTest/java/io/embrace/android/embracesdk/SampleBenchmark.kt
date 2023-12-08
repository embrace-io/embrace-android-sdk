package io.embrace.android.embracesdk

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.GsonBuilder
import io.embrace.android.embracesdk.comms.api.EmbraceUrl
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.serialization.EmbraceUrlAdapter
import io.embrace.android.embracesdk.payload.NetworkCallV2
import io.embrace.android.embracesdk.payload.NetworkRequests
import io.embrace.android.embracesdk.payload.NetworkSessionV2
import io.embrace.android.embracesdk.payload.SessionMessage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.Random
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class SampleBenchmark {

    private val ctx = InstrumentationRegistry.getInstrumentation().context
    private val serializer = EmbraceSerializer()

    // representative session: generated from opening the android-test-suite & grabbing the session
    // from local storage
    private val fakeSession: String =
        ctx.assets.open("example-session.json").bufferedReader().readText()
    private val obj: SessionMessage = serializer.fromJson(fakeSession, SessionMessage::class.java)
    private val largeMessage = createLargeSessionMessage()
    private val largeFakeSession: String = serializer.toJson(largeMessage)

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Test
    fun libraryInitialization() {
        benchmarkRule.measureRepeated {
            GsonBuilder()
                .registerTypeAdapter(EmbraceUrl::class.java, EmbraceUrlAdapter())
                .create()
        }
    }

    @Test
    fun jsonDeserializationFromString() {
        benchmarkRule.measureRepeated {
            serializer.fromJson(fakeSession, SessionMessage::class.java)
        }
    }

    @Test
    fun jsonSerializationToString() {
        benchmarkRule.measureRepeated {
            serializer.toJson(obj)
        }
    }

    @Test
    fun largeJsonDeserializationFromString() {
        benchmarkRule.measureRepeated {
            serializer.fromJson(largeFakeSession, SessionMessage::class.java)
        }
    }

    @Test
    fun largeJsonSerializationToString() {
        benchmarkRule.measureRepeated {
            serializer.toJson(largeMessage)
        }
    }



    @Test
    fun largeJsonDeserializationFromStream() {
        benchmarkRule.measureRepeated {
            serializer.fromJson(largeFakeSession.byteInputStream(), SessionMessage::class.java)
        }
    }

    @Test
    fun largeJsonSerializationToStream() {
        benchmarkRule.measureRepeated {
            serializer.toJson(largeMessage, SessionMessage::class.java, ByteArrayOutputStream())
        }
    }

    private fun createLargeSessionMessage(): SessionMessage {
        val perfInfo = checkNotNull(obj.performanceInfo)
        val random = Random(0)
        val requests = (0 until 1000).map {
            NetworkCallV2(
                "https://www.google.com",
                "GET",
                200,
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                100L + random.nextInt(100),
            )
        }
        val networkRequests = NetworkRequests(NetworkSessionV2(requests, emptyMap()))
        return obj.copy(
            session = obj.session.copy(),
            performanceInfo = perfInfo.copy(networkRequests = networkRequests)
        )
    }
}
