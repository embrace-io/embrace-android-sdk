package io.embrace.android.embracesdk.internal.perfetto

import org.junit.Assert.assertEquals
import org.junit.Test

internal class AtracePayloadParserTest {

    @Test
    fun `a begin carries the name atrace wrote, without the tgid`() {
        assertEquals(AtracePayload.Begin("emb-sdk-start"), parseAtracePayload("B|$TGID|emb-sdk-start"))
    }

    @Test
    fun `both forms of end are recognised, since neither names what it closes`() {
        assertEquals(AtracePayload.End, parseAtracePayload("E"))
        assertEquals(AtracePayload.End, parseAtracePayload("E|$TGID"))
    }

    @Test
    fun `the terminator atrace writes is not part of the payload`() {
        assertEquals(AtracePayload.Begin("emb-sdk-start"), parseAtracePayload("B|$TGID|emb-sdk-start\n"))
        assertEquals(AtracePayload.End, parseAtracePayload("E|$TGID\n"))
        assertEquals(AtracePayload.End, parseAtracePayload("E\n"))
    }

    @Test
    fun `a name is everything past the tgid, so one containing a separator survives`() {
        assertEquals(AtracePayload.Begin("a|b|c"), parseAtracePayload("B|$TGID|a|b|c"))
    }

    @Test
    fun `payloads that are not synchronous slices are recognised rather than discarded`() {
        assertEquals(AtracePayload.Unsupported.AsyncBegin("S|$TGID|work|7"), parseAtracePayload("S|$TGID|work|7\n"))
        assertEquals(AtracePayload.Unsupported.AsyncEnd("F|$TGID|work|7"), parseAtracePayload("F|$TGID|work|7"))
        assertEquals(AtracePayload.Unsupported.Counter("C|$TGID|queue|3"), parseAtracePayload("C|$TGID|queue|3"))
    }

    @Test
    fun `an unsupported payload is classified by its kind alone, since its fields are never read`() {
        assertEquals(AtracePayload.Unsupported.AsyncBegin("S|$TGID"), parseAtracePayload("S|$TGID"))
    }

    @Test
    fun `a payload that does not parse is unrecognised rather than an exception`() {
        listOf(
            "",
            "\n",
            "B",
            "B|$TGID",
            "B|$TGID|",
            "Elephant",
            "X|$TGID|something",
        ).forEach { payload ->
            assertEquals(
                payload,
                AtracePayload.Unsupported.Unrecognised(payload.substringBefore('\n')),
                parseAtracePayload(payload),
            )
        }
    }

    private companion object {
        const val TGID = 9874
    }
}
