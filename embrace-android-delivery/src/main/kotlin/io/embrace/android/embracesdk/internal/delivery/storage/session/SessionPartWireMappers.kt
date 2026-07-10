package io.embrace.android.embracesdk.internal.delivery.storage.session

import io.embrace.android.embracesdk.internal.delivery.storage.session.proto.AttributeProto
import io.embrace.android.embracesdk.internal.delivery.storage.session.proto.EnvelopeMetadataProto
import io.embrace.android.embracesdk.internal.delivery.storage.session.proto.EnvelopeResourceProto
import io.embrace.android.embracesdk.internal.delivery.storage.session.proto.LinkProto
import io.embrace.android.embracesdk.internal.delivery.storage.session.proto.SpanEventProto
import io.embrace.android.embracesdk.internal.delivery.storage.session.proto.SpanProto
import io.embrace.android.embracesdk.internal.delivery.storage.session.proto.SpanStatusProto
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.Link
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent

/**
 * Mappers between the kotlinx-serialization payload data classes and the Wire-generated proto
 * messages. Wire represents absent repeated fields as empty lists, so on the way back we collapse
 * empty collections to null to round-trip cleanly with the mostly-nullable payload types.
 */

private fun <T> List<T>.orNullIfEmpty(): List<T>? = ifEmpty { null }

internal fun Attribute.toProto(): AttributeProto = AttributeProto(key = key, value_ = data)

internal fun AttributeProto.toPayload(): Attribute = Attribute(key = key, data = value_)

internal fun SpanEvent.toProto(): SpanEventProto = SpanEventProto(
    name = name,
    time_unix_nano = timestampNanos,
    attributes = attributes?.map(Attribute::toProto).orEmpty(),
)

internal fun SpanEventProto.toPayload(): SpanEvent = SpanEvent(
    name = name,
    timestampNanos = time_unix_nano,
    attributes = attributes.map(AttributeProto::toPayload).orNullIfEmpty(),
)

internal fun Link.toProto(): LinkProto = LinkProto(
    span_id = spanId,
    trace_id = traceId,
    attributes = attributes?.map(Attribute::toProto).orEmpty(),
    is_remote = isRemote,
)

internal fun LinkProto.toPayload(): Link = Link(
    spanId = span_id,
    traceId = trace_id,
    attributes = attributes.map(AttributeProto::toPayload).orNullIfEmpty(),
    isRemote = is_remote,
)

internal fun Span.Status.toProto(): SpanStatusProto = when (this) {
    Span.Status.UNSET -> SpanStatusProto.UNSET
    Span.Status.ERROR -> SpanStatusProto.ERROR
    Span.Status.OK -> SpanStatusProto.OK
}

internal fun SpanStatusProto.toPayload(): Span.Status = when (this) {
    SpanStatusProto.UNSET -> Span.Status.UNSET
    SpanStatusProto.ERROR -> Span.Status.ERROR
    SpanStatusProto.OK -> Span.Status.OK
}

internal fun Span.toProto(): SpanProto = SpanProto(
    trace_id = traceId,
    span_id = spanId,
    parent_span_id = parentSpanId,
    name = name,
    start_time_unix_nano = startTimeNanos,
    end_time_unix_nano = endTimeNanos,
    status = status?.toProto(),
    events = events?.map(SpanEvent::toProto).orEmpty(),
    attributes = attributes?.map(Attribute::toProto).orEmpty(),
    links = links?.map(Link::toProto).orEmpty(),
)

internal fun SpanProto.toPayload(): Span = Span(
    traceId = trace_id,
    spanId = span_id,
    parentSpanId = parent_span_id,
    name = name,
    startTimeNanos = start_time_unix_nano,
    endTimeNanos = end_time_unix_nano,
    status = status?.toPayload(),
    events = events.map(SpanEventProto::toPayload).orNullIfEmpty(),
    attributes = attributes.map(AttributeProto::toPayload).orNullIfEmpty(),
    links = links.map(LinkProto::toPayload).orNullIfEmpty(),
)

internal fun EnvelopeResource.toProto(): EnvelopeResourceProto = EnvelopeResourceProto(
    app_version = appVersion,
    app_framework = appFramework?.value,
    build_id = buildId,
    app_ecosystem_id = appEcosystemId,
    build_type = buildType,
    build_flavor = buildFlavor,
    environment = environment,
    bundle_version = bundleVersion,
    sdk_version = sdkVersion,
    sdk_simple_version = sdkSimpleVersion,
    react_native_bundle_id = reactNativeBundleId,
    react_native_version = reactNativeVersion,
    javascript_patch_number = javascriptPatchNumber,
    hosted_platform_version = hostedPlatformVersion,
    hosted_sdk_version = hostedSdkVersion,
    unity_build_id = unityBuildId,
    device_manufacturer = deviceManufacturer,
    device_model = deviceModel,
    device_architecture = deviceArchitecture,
    jailbroken = jailbroken,
    disk_total_capacity = diskTotalCapacity,
    os_type = osType,
    os_name = osName,
    os_version = osVersion,
    os_code = osCode,
    screen_resolution = screenResolution,
    num_cores = numCores,
    extras = extras,
)

internal fun EnvelopeResourceProto.toPayload(): EnvelopeResource = EnvelopeResource(
    appVersion = app_version,
    appFramework = app_framework?.let(AppFramework::fromInt),
    buildId = build_id,
    appEcosystemId = app_ecosystem_id,
    buildType = build_type,
    buildFlavor = build_flavor,
    environment = environment,
    bundleVersion = bundle_version,
    sdkVersion = sdk_version,
    sdkSimpleVersion = sdk_simple_version,
    reactNativeBundleId = react_native_bundle_id,
    reactNativeVersion = react_native_version,
    javascriptPatchNumber = javascript_patch_number,
    hostedPlatformVersion = hosted_platform_version,
    hostedSdkVersion = hosted_sdk_version,
    unityBuildId = unity_build_id,
    deviceManufacturer = device_manufacturer,
    deviceModel = device_model,
    deviceArchitecture = device_architecture,
    jailbroken = jailbroken,
    diskTotalCapacity = disk_total_capacity,
    osType = os_type,
    osName = os_name,
    osVersion = os_version,
    osCode = os_code,
    screenResolution = screen_resolution,
    numCores = num_cores,
    extras = extras,
)

internal fun EnvelopeMetadata.toProto(): EnvelopeMetadataProto = EnvelopeMetadataProto(
    user_id = userId,
    email = email,
    username = username,
    personas = personas?.toList().orEmpty(),
    timezone_description = timezoneDescription,
    locale = locale,
)

internal fun EnvelopeMetadataProto.toPayload(): EnvelopeMetadata = EnvelopeMetadata(
    userId = user_id,
    email = email,
    username = username,
    personas = personas.orNullIfEmpty()?.toSet(),
    timezoneDescription = timezone_description,
    locale = locale,
)
