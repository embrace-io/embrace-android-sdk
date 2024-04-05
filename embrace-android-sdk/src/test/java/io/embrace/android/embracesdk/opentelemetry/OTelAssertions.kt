package io.embrace.android.embracesdk.opentelemetry

import io.embrace.android.embracesdk.internal.SystemInfo
import io.opentelemetry.sdk.resources.Resource
import org.junit.Assert.assertEquals

internal fun Resource.assertExpectedAttributes(
    expectedServiceName: String,
    expectedServiceVersion: String,
    systemInfo: SystemInfo
) {
    assertEquals(expectedServiceName, getAttribute(serviceName))
    assertEquals(expectedServiceVersion, getAttribute(serviceVersion))
    assertEquals(systemInfo.osName, getAttribute(osName))
    assertEquals(systemInfo.osVersion, getAttribute(osVersion))
    assertEquals(systemInfo.osType, getAttribute(osType))
    assertEquals(systemInfo.osBuild, getAttribute(osBuildId))
    assertEquals(systemInfo.androidOsApiLevel, getAttribute(androidApiLevel))
    assertEquals(systemInfo.deviceManufacturer, getAttribute(deviceManufacturer))
    assertEquals(systemInfo.deviceModel, getAttribute(deviceModelIdentifier))
    assertEquals(systemInfo.deviceModel, getAttribute(deviceModelName))
}
