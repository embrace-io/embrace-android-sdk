package io.embrace.android.embracesdk.testframework.assertions

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.session.getSessionSpan
import io.embrace.android.embracesdk.testframework.actions.EmbracePayloadAssertionInterface
import org.json.JSONObject
import org.junit.Assert
import org.junit.Assert.fail
import java.lang.reflect.Type

/**
 * Asserts that the session span extracted from [envelope] matches the golden JSON file [goldenFile].
 * Only the session [Span] is compared — not the whole envelope — so the golden file only needs
 * to describe session-span fields.
 */
internal fun EmbracePayloadAssertionInterface.assertSessionSpanMatchesGoldenFile(
    envelope: Envelope<SessionPartPayload>,
    goldenFile: String,
) {
    val sessionSpan = checkNotNull(envelope.getSessionSpan()) {
        "No session span found in envelope."
    }
    compareJsonToGoldenFile(sessionSpan, Span::class.java, goldenFile, serializer)
}

/**
 * Asserts that the [LogPayload] from [envelope] matches the golden JSON file [goldenFile].
 * Only the payload data (the `logs` list) is compared — not the whole envelope.
 */
internal fun EmbracePayloadAssertionInterface.assertLogPayloadMatchesGoldenFile(
    envelope: Envelope<LogPayload>,
    goldenFile: String,
) {
    compareJsonToGoldenFile(envelope.data, LogPayload::class.java, goldenFile, serializer)
}

private fun <T : Any> compareJsonToGoldenFile(
    observed: T,
    type: Type,
    goldenFile: String,
    serializer: PlatformSerializer,
) {
    val observedJson = serializer.toJson(observed, type)
    val expectedJson = ResourceReader.readResourceAsText(goldenFile)
    val mismatches = JsonComparator.compare(JSONObject(expectedJson), JSONObject(observedJson))
    if (mismatches.isNotEmpty()) {
        fail(
            "Payload differed from golden file '$goldenFile' due to: ${mismatches.joinToString("; ")}\n" +
                "Dump of full JSON: $observedJson"
        )
    }
}
