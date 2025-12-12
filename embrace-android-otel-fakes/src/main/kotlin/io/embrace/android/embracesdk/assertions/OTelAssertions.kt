package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.resource.Resource
import io.embrace.opentelemetry.kotlin.semconv.AndroidAttributes
import io.embrace.opentelemetry.kotlin.semconv.DeviceAttributes
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi
import io.embrace.opentelemetry.kotlin.semconv.OsAttributes
import io.embrace.opentelemetry.kotlin.semconv.ServiceAttributes
import io.embrace.opentelemetry.kotlin.semconv.TelemetryAttributes
import org.junit.Assert.assertEquals

@OptIn(ExperimentalApi::class, IncubatingApi::class)
fun Resource.assertExpectedAttributes(
    expectedServiceName: String,
    expectedServiceVersion: String,
    systemInfo: SystemInfo,
    expectedDistroName: String = expectedServiceName,
    expectedDistroVersion: String = expectedServiceVersion,
) {
    assertEquals(expectedServiceName, attributes[ServiceAttributes.SERVICE_NAME])
    assertEquals(expectedServiceVersion, attributes[ServiceAttributes.SERVICE_VERSION])
    assertEquals(expectedDistroName, attributes[TelemetryAttributes.TELEMETRY_DISTRO_NAME])
    assertEquals(expectedDistroVersion, attributes[TelemetryAttributes.TELEMETRY_DISTRO_VERSION])
    assertEquals(systemInfo.osName, attributes[OsAttributes.OS_NAME])
    assertEquals(systemInfo.osVersion, attributes[OsAttributes.OS_VERSION])
    assertEquals(systemInfo.osType, attributes[OsAttributes.OS_TYPE])
    assertEquals(systemInfo.osBuild, attributes[OsAttributes.OS_BUILD_ID])
    assertEquals(systemInfo.androidOsApiLevel, attributes[AndroidAttributes.ANDROID_OS_API_LEVEL])
    assertEquals(systemInfo.deviceManufacturer, attributes[DeviceAttributes.DEVICE_MANUFACTURER])
    assertEquals(systemInfo.deviceModel, attributes[DeviceAttributes.DEVICE_MODEL_IDENTIFIER])
    assertEquals(systemInfo.deviceModel, attributes[DeviceAttributes.DEVICE_MODEL_NAME])
}
