package io.embrace.android.embracesdk.opentelemetry

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.spans.SpanSinkImpl
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
            systemInfo = systemInfo,
            processIdentifier = "fakeProcessIdentifier"
        )

        assertEquals(configuration.embraceServiceName, configuration.resource.getAttribute(serviceName))
        assertEquals(configuration.embraceVersionName, configuration.resource.getAttribute(serviceVersion))
        assertEquals(systemInfo.osName, configuration.resource.getAttribute(osName))
        assertEquals(systemInfo.osVersion, configuration.resource.getAttribute(osVersion))
        assertEquals(systemInfo.osType, configuration.resource.getAttribute(osType))
        assertEquals(systemInfo.osBuild, configuration.resource.getAttribute(osBuildId))
        assertEquals(systemInfo.androidOsApiLevel, configuration.resource.getAttribute(androidApiLevel))
        assertEquals(systemInfo.deviceManufacturer, configuration.resource.getAttribute(deviceManufacturer))
        assertEquals(systemInfo.deviceModel, configuration.resource.getAttribute(deviceModelIdentifier))
        assertEquals(systemInfo.deviceModel, configuration.resource.getAttribute(deviceModelName))
    }
}
