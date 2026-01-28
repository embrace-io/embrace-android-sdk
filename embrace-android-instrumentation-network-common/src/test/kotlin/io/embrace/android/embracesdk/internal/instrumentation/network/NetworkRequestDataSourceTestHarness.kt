package io.embrace.android.embracesdk.internal.instrumentation.network

import androidx.test.core.app.ApplicationProvider
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDomainCountLimiter
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.FakeSpanToken
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.behavior.FakeNetworkBehavior
import io.embrace.android.embracesdk.fakes.behavior.FakeNetworkSpanForwardingBehavior
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import org.junit.Assert.assertEquals

internal class NetworkRequestDataSourceTestHarness {
    val domainCountLimiter: FakeDomainCountLimiter = FakeDomainCountLimiter()
    val telemetryService: FakeTelemetryService = FakeTelemetryService()
    val networkSpanForwardingBehavior = FakeNetworkSpanForwardingBehavior()
    val args: FakeInstrumentationArgs = FakeInstrumentationArgs(
        application = ApplicationProvider.getApplicationContext(),
        configService = FakeConfigService(
            networkBehavior = FakeNetworkBehavior(domainCountLimiter = domainCountLimiter),
            networkSpanForwardingBehavior = networkSpanForwardingBehavior
        ),
        telemetryService = telemetryService
    )
    val dataSource: NetworkRequestDataSource = NetworkRequestDataSourceImpl(args)

    fun getNetworkSpans(): List<FakeSpanToken> {
        return args.destination.createdSpans.filter { it.type == EmbType.Performance.Network }
    }

    fun assertNetworkRequest(
        spanToken: FakeSpanToken?,
        expectedStartTimeMs: Long,
        expectedEndTimeMs: Long,
        expectedErrorCode: ErrorCodeAttribute? = null,
        expectedAttributes: Map<String, String> = emptyMap()
    ) {
        with(checkNotNull(spanToken)) {
            assertEquals(expectedStartTimeMs, startTimeMs)
            assertEquals(expectedEndTimeMs, endTimeMs)
            assertEquals(expectedErrorCode, errorCode)
            expectedAttributes.forEach {
                assertEquals(it.value, attributes[it.key])
            }
        }
    }
}
