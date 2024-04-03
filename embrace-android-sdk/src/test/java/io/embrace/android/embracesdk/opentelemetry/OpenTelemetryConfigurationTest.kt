package io.embrace.android.embracesdk.opentelemetry

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.spans.SpanSinkImpl
import io.opentelemetry.semconv.incubating.AndroidIncubatingAttributes
import io.opentelemetry.semconv.incubating.DeviceIncubatingAttributes
import io.opentelemetry.semconv.incubating.OsIncubatingAttributes
import io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes
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

        with(configuration) {
            assertEquals(serviceName, resource.getAttribute(ServiceIncubatingAttributes.SERVICE_NAME))
            assertEquals(serviceVersion, resource.getAttribute(ServiceIncubatingAttributes.SERVICE_VERSION))
            assertEquals(systemInfo.osName, resource.getAttribute(OsIncubatingAttributes.OS_NAME))
            assertEquals(systemInfo.osVersion, resource.getAttribute(OsIncubatingAttributes.OS_VERSION))
            assertEquals(systemInfo.osType, resource.getAttribute(OsIncubatingAttributes.OS_TYPE))
            assertEquals(systemInfo.osBuild, resource.getAttribute(OsIncubatingAttributes.OS_BUILD_ID))
            assertEquals(systemInfo.androidOsApiLevel, resource.getAttribute(AndroidIncubatingAttributes.ANDROID_OS_API_LEVEL))
            assertEquals(systemInfo.deviceManufacturer, resource.getAttribute(DeviceIncubatingAttributes.DEVICE_MANUFACTURER))
            assertEquals(systemInfo.deviceModel, resource.getAttribute(DeviceIncubatingAttributes.DEVICE_MODEL_IDENTIFIER))
            assertEquals(systemInfo.deviceModel, resource.getAttribute(DeviceIncubatingAttributes.DEVICE_MODEL_NAME))
        }
    }
}
