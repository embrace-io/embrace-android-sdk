package io.embrace.android.embracesdk.internal.opentelemetry

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.spans.SpanSinkImpl
import io.opentelemetry.semconv.ServiceAttributes
import io.opentelemetry.semconv.incubating.AndroidIncubatingAttributes
import io.opentelemetry.semconv.incubating.DeviceIncubatingAttributes
import io.opentelemetry.semconv.incubating.OsIncubatingAttributes
import io.opentelemetry.semconv.incubating.TelemetryIncubatingAttributes
import org.junit.Assert.assertEquals
import org.junit.Test

internal class OpenTelemetryConfigurationTest {

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

        val configuration = OpenTelemetryConfiguration(
            spanSink = SpanSinkImpl(),
            logSink = LogSinkImpl(),
            systemInfo = systemInfo
        )

        assertEquals(configuration.embraceSdkName, configuration.resource.getAttribute(ServiceAttributes.SERVICE_NAME))
        assertEquals(
            configuration.embraceSdkVersion,
            configuration.resource.getAttribute(ServiceAttributes.SERVICE_VERSION)
        )
        assertEquals(
            configuration.embraceSdkName,
            configuration.resource.getAttribute(TelemetryIncubatingAttributes.TELEMETRY_DISTRO_NAME)
        )
        assertEquals(
            configuration.embraceSdkVersion,
            configuration.resource.getAttribute(TelemetryIncubatingAttributes.TELEMETRY_DISTRO_VERSION)
        )
        assertEquals(systemInfo.osName, configuration.resource.getAttribute(OsIncubatingAttributes.OS_NAME))
        assertEquals(systemInfo.osVersion, configuration.resource.getAttribute(OsIncubatingAttributes.OS_VERSION))
        assertEquals(systemInfo.osType, configuration.resource.getAttribute(OsIncubatingAttributes.OS_TYPE))
        assertEquals(systemInfo.osBuild, configuration.resource.getAttribute(OsIncubatingAttributes.OS_BUILD_ID))
        assertEquals(
            systemInfo.androidOsApiLevel,
            configuration.resource.getAttribute(AndroidIncubatingAttributes.ANDROID_OS_API_LEVEL)
        )
        assertEquals(
            systemInfo.deviceManufacturer,
            configuration.resource.getAttribute(DeviceIncubatingAttributes.DEVICE_MANUFACTURER)
        )
        assertEquals(
            systemInfo.deviceModel,
            configuration.resource.getAttribute(DeviceIncubatingAttributes.DEVICE_MODEL_IDENTIFIER)
        )
        assertEquals(
            systemInfo.deviceModel,
            configuration.resource.getAttribute(DeviceIncubatingAttributes.DEVICE_MODEL_NAME)
        )
    }
}
