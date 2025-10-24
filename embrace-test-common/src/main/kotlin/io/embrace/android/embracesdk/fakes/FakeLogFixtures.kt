package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.Envelope.Companion.createLogEnvelope
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.LogPayload

fun fakeEmptyLogEnvelope(
    resource: EnvelopeResource = fakeEnvelopeResource,
    metadata: EnvelopeMetadata = fakeEnvelopeMetadata,
) = LogPayload().createLogEnvelope(resource = resource, metadata = metadata)

val fakeEnvelopeResource = EnvelopeResource(
    appVersion = "1.5",
    appFramework = AppFramework.NATIVE,
    buildId = "555",
    appEcosystemId = "com.my.app",
    sdkVersion = "6.14.0",
    deviceManufacturer = "Google",
    deviceModel = "Pixel 5",
    osType = "linux",
    osName = "Android",
    osVersion = "15",
)

val fakeLaterEnvelopeResource = fakeEnvelopeResource.copy(appVersion = "1.6")

val fakeEnvelopeMetadata = EnvelopeMetadata(
    userId = "abcde",
    email = "me@mycompany.com",
    username = "admin",
    personas = setOf("stuff-doer"),
)

val fakeLaterEnvelopeMetadata = fakeEnvelopeMetadata.copy(username = "new-admin")
