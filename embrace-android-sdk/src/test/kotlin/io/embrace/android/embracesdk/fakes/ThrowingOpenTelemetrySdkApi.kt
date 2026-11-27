package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.api.SdkApi
import io.embrace.android.embracesdk.internal.instance.NoopSdkInstance
import io.opentelemetry.kotlin.OpenTelemetry

internal class ThrowingOpenTelemetrySdkApi : SdkApi by NoopSdkInstance(FakeClock(), FakeErrorHandler(), "") {
    override fun getOpenTelemetryKotlin(): OpenTelemetry = error("getOpenTelemetryKotlin")
}
