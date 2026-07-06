package io.embrace.android.embracesdk.testframework.assertions

import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource

/**
 * Test-only shims that read the new flat [EnvelopeResource.attributes] map using the explicit keys in the old format.
 * Added so existing tests that assert the envelope won't have to change
 */

internal val EnvelopeResource.appFramework: AppFramework?
    get() = attributes["app_framework"]?.intValue?.let(AppFramework::fromInt)

internal val EnvelopeResource.appVersion: String?
    get() = attributes["service.version"]?.stringValue

internal val EnvelopeResource.hostedSdkVersion: String?
    get() = attributes["hosted_sdk_version"]?.stringValue

internal val EnvelopeResource.hostedPlatformVersion: String?
    get() = attributes["hosted_platform_version"]?.stringValue

internal val EnvelopeResource.javascriptPatchNumber: String?
    get() = attributes["javascript_patch_number"]?.stringValue

internal val EnvelopeResource.unityBuildId: String?
    get() = attributes["unity_build_id"]?.stringValue

internal val EnvelopeResource.reactNativeBundleId: String?
    get() = attributes["react_native_bundle_id"]?.stringValue

internal val EnvelopeResource.sdkVersion: String?
    get() = attributes["telemetry.distro.version"]?.stringValue

internal val EnvelopeResource.osVersion: String?
    get() = attributes["os.version"]?.stringValue

internal val EnvelopeResource.osName: String?
    get() = attributes["os.name"]?.stringValue

internal val EnvelopeResource.deviceModel: String?
    get() = attributes["device.model.identifier"]?.stringValue
