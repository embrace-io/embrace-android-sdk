package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.fakes.FakeIntakeService
import io.embrace.android.embracesdk.fakes.FakePayloadCachingService
import io.embrace.android.embracesdk.fakes.FakePayloadIntake
import io.embrace.android.embracesdk.fakes.FakePayloadResurrectionService
import io.embrace.android.embracesdk.fakes.FakeRequestExecutionService
import io.embrace.android.embracesdk.fakes.FakeSchedulingService
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.injection.DeliveryModule
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import org.junit.Assert.assertEquals

internal fun IntegrationTestRule.getIntakeService(): FakeIntakeService =
    getService { intakeService }

internal fun IntegrationTestRule.getPayloadCachingService(): FakePayloadCachingService =
    getService { payloadCachingService }

internal fun IntegrationTestRule.getPayloadResurrectionService(): FakePayloadResurrectionService =
    getService { payloadResurrectionService }

internal fun IntegrationTestRule.getRequestExecutionService(): FakeRequestExecutionService =
    getService { requestExecutionService }

internal fun IntegrationTestRule.getSchedulingService(): FakeSchedulingService = getService {
    schedulingService
}

/**
 * Checks that the intake service received a payload of the specified type & performs a few basic
 * assertions.
 */
@Suppress("UNCHECKED_CAST")
internal inline fun <reified T> FakePayloadIntake<T>.assertPayloadIntake(
    envelopeAssertion: (Envelope<T>) -> Unit
) {
    val expectedType = when (T::class) {
        SessionPayload::class -> SupportedEnvelopeType.SESSION
        LogPayload::class -> SupportedEnvelopeType.LOG
        else -> error("Unsupported type: ${T::class}")
    }
    assertEquals(expectedType, metadata.envelopeType)
    assertEquals("", metadata.filename) // TODO: assert filename
    envelopeAssertion(envelope)
}

private inline fun <reified T> IntegrationTestRule.getService(noinline fieldProvider: DeliveryModule.() -> Any?): T =
    fieldProvider(bootstrapper.deliveryModule) as? T
        ?: error("Intake service is not of type ${T::class.simpleName}")
