package io.embrace.android.embracesdk.internal.otel.config

import io.embrace.android.embracesdk.fakes.FakeAttributeContainer
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.otel.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.otel.spans.SpanSinkImpl
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.semconv.ServiceAttributes
import io.opentelemetry.semconv.incubating.AndroidIncubatingAttributes
import io.opentelemetry.semconv.incubating.DeviceIncubatingAttributes
import io.opentelemetry.semconv.incubating.OsIncubatingAttributes
import io.opentelemetry.semconv.incubating.TelemetryIncubatingAttributes
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalApi::class)
internal class OtelSdkConfigTest {

    @Test
    fun `check resource attributes`() {
        val systemInfo = SystemInfo(
            osName = "testOS",
            osType = "testOsType",
            osBuild = "testBuild",
            osVersion = "testOsVersionName",
            androidOsApiLevel = "99",
            deviceManufacturer = "testManufacturer",
            deviceModel = "testModel"
        )

        val configuration = OtelSdkConfig(
            spanSink = SpanSinkImpl(),
            logSink = LogSinkImpl(),
            sdkName = "sdk",
            sdkVersion = "1.0",
            systemInfo = systemInfo
        )

        val attrs = FakeAttributeContainer().apply(configuration.resourceAction).attributes()
        val expected = mapOf(
            ServiceAttributes.SERVICE_NAME to configuration.sdkName,
            ServiceAttributes.SERVICE_VERSION to configuration.sdkVersion,
            TelemetryIncubatingAttributes.TELEMETRY_DISTRO_NAME to configuration.sdkName,
            TelemetryIncubatingAttributes.TELEMETRY_DISTRO_VERSION to configuration.sdkVersion,
            OsIncubatingAttributes.OS_NAME to systemInfo.osName,
            OsIncubatingAttributes.OS_VERSION to systemInfo.osVersion,
            OsIncubatingAttributes.OS_TYPE to systemInfo.osType,
            OsIncubatingAttributes.OS_BUILD_ID to systemInfo.osBuild,
            AndroidIncubatingAttributes.ANDROID_OS_API_LEVEL to systemInfo.androidOsApiLevel,
            DeviceIncubatingAttributes.DEVICE_MANUFACTURER to systemInfo.deviceManufacturer,
            DeviceIncubatingAttributes.DEVICE_MODEL_IDENTIFIER to systemInfo.deviceModel,
            DeviceIncubatingAttributes.DEVICE_MODEL_NAME to systemInfo.deviceModel
        ).mapKeys { it.key.key }
        assertEquals(expected, attrs)
    }
}
