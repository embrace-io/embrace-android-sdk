package io.embrace.android.embracesdk.internal.otel.config

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.otel.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.otel.spans.SpanSinkImpl
import io.opentelemetry.semconv.ServiceAttributes
import io.opentelemetry.semconv.incubating.AndroidIncubatingAttributes
import io.opentelemetry.semconv.incubating.DeviceIncubatingAttributes
import io.opentelemetry.semconv.incubating.OsIncubatingAttributes
import io.opentelemetry.semconv.incubating.TelemetryIncubatingAttributes
import org.junit.Assert.assertEquals
import org.junit.Test

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

        val resource = configuration.resourceBuilder.build()

        assertEquals(configuration.sdkName, resource.getAttribute(ServiceAttributes.SERVICE_NAME))
        assertEquals(
            configuration.sdkVersion,
            resource.getAttribute(ServiceAttributes.SERVICE_VERSION)
        )
        assertEquals(
            configuration.sdkName,
            resource.getAttribute(TelemetryIncubatingAttributes.TELEMETRY_DISTRO_NAME)
        )
        assertEquals(
            configuration.sdkVersion,
            resource.getAttribute(TelemetryIncubatingAttributes.TELEMETRY_DISTRO_VERSION)
        )
        assertEquals(systemInfo.osName, resource.getAttribute(OsIncubatingAttributes.OS_NAME))
        assertEquals(systemInfo.osVersion, resource.getAttribute(OsIncubatingAttributes.OS_VERSION))
        assertEquals(systemInfo.osType, resource.getAttribute(OsIncubatingAttributes.OS_TYPE))
        assertEquals(systemInfo.osBuild, resource.getAttribute(OsIncubatingAttributes.OS_BUILD_ID))
        assertEquals(
            systemInfo.androidOsApiLevel,
            resource.getAttribute(AndroidIncubatingAttributes.ANDROID_OS_API_LEVEL)
        )
        assertEquals(
            systemInfo.deviceManufacturer,
            resource.getAttribute(DeviceIncubatingAttributes.DEVICE_MANUFACTURER)
        )
        assertEquals(
            systemInfo.deviceModel,
            resource.getAttribute(DeviceIncubatingAttributes.DEVICE_MODEL_IDENTIFIER)
        )
        assertEquals(
            systemInfo.deviceModel,
            resource.getAttribute(DeviceIncubatingAttributes.DEVICE_MODEL_NAME)
        )
    }
}
