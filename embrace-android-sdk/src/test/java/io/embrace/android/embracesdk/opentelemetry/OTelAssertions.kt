package io.embrace.android.embracesdk.opentelemetry

import io.embrace.android.embracesdk.arch.schema.EmbraceAttributeKey
import io.embrace.android.embracesdk.internal.SystemInfo
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.data.SpanData
import org.junit.Assert.assertEquals

internal fun Resource.assertExpectedAttributes(
    expectedServiceName: String,
    expectedServiceVersion: String,
    systemInfo: SystemInfo
) {
    assertEquals(expectedServiceName, getAttribute(serviceName))
    assertEquals(expectedServiceVersion, getAttribute(serviceVersion))
    assertEquals(expectedServiceName, getAttribute(telemetryDistroName))
    assertEquals(expectedServiceVersion, getAttribute(telemetryDistroVersion))
    assertEquals(systemInfo.osName, getAttribute(osName))
    assertEquals(systemInfo.osVersion, getAttribute(osVersion))
    assertEquals(systemInfo.osType, getAttribute(osType))
    assertEquals(systemInfo.osBuild, getAttribute(osBuildId))
    assertEquals(systemInfo.androidOsApiLevel, getAttribute(androidApiLevel))
    assertEquals(systemInfo.deviceManufacturer, getAttribute(deviceManufacturer))
    assertEquals(systemInfo.deviceModel, getAttribute(deviceModelIdentifier))
    assertEquals(systemInfo.deviceModel, getAttribute(deviceModelName))
}

internal fun SpanData.assertHasEmbraceAttribute(key: EmbraceAttributeKey, value: String) {
    assertEquals(value, attributes.get(AttributeKey.stringKey(key.name)))
}
