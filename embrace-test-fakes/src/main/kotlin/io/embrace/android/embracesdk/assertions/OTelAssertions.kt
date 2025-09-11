package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.resource.Resource
import io.opentelemetry.semconv.ServiceAttributes
import io.opentelemetry.semconv.incubating.AndroidIncubatingAttributes
import io.opentelemetry.semconv.incubating.DeviceIncubatingAttributes
import io.opentelemetry.semconv.incubating.OsIncubatingAttributes
import io.opentelemetry.semconv.incubating.TelemetryIncubatingAttributes
import org.junit.Assert.assertEquals

@OptIn(ExperimentalApi::class)
fun Resource.assertExpectedAttributes(
    expectedServiceName: String,
    expectedServiceVersion: String,
    systemInfo: SystemInfo,
) {
    assertEquals(expectedServiceName, attributes[ServiceAttributes.SERVICE_NAME.toString()])
    assertEquals(expectedServiceVersion, attributes[ServiceAttributes.SERVICE_VERSION.toString()])
    assertEquals(expectedServiceName, attributes[TelemetryIncubatingAttributes.TELEMETRY_DISTRO_NAME.toString()])
    assertEquals(expectedServiceVersion, attributes[TelemetryIncubatingAttributes.TELEMETRY_DISTRO_VERSION.toString()])
    assertEquals(systemInfo.osName, attributes[OsIncubatingAttributes.OS_NAME.toString()])
    assertEquals(systemInfo.osVersion, attributes[OsIncubatingAttributes.OS_VERSION.toString()])
    assertEquals(systemInfo.osType, attributes[OsIncubatingAttributes.OS_TYPE.toString()])
    assertEquals(systemInfo.osBuild, attributes[OsIncubatingAttributes.OS_BUILD_ID.toString()])
    assertEquals(systemInfo.androidOsApiLevel, attributes[AndroidIncubatingAttributes.ANDROID_OS_API_LEVEL.toString()])
    assertEquals(systemInfo.deviceManufacturer, attributes[DeviceIncubatingAttributes.DEVICE_MANUFACTURER.toString()])
    assertEquals(systemInfo.deviceModel, attributes[DeviceIncubatingAttributes.DEVICE_MODEL_IDENTIFIER.toString()])
    assertEquals(systemInfo.deviceModel, attributes[DeviceIncubatingAttributes.DEVICE_MODEL_NAME.toString()])
}
