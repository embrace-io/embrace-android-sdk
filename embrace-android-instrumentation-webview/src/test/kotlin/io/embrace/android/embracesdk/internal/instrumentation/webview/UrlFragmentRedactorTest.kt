package io.embrace.android.embracesdk.internal.instrumentation.webview

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The vectors here are shared with the iOS SDK and both platforms must produce identical output for
 * each one, so a change to this list is a change to both repos.
 */
internal class UrlFragmentRedactorTest {

    private fun assertRedacted(expected: String, input: String) {
        assertEquals("redacting '$input'", expected, UrlFragmentRedactor.redact(input))
    }

    // rule 1 - key=value: keep the key and the '=', drop the value

    @Test
    fun `key value pairs lose their values`() {
        assertRedacted("access_token=", "access_token=eyJ0eXAiOiJKV1Qi")
        assertRedacted(
            "access_token=&token_type=&expires_in=",
            "access_token=eyJ0eXAiOiJKV1Qi&token_type=Bearer&expires_in=3600",
        )
        assertRedacted("id_token=&state=", "id_token=eyJ0eXAiOiJKV1Qi&state=abc")
    }

    @Test
    fun `punctuation in a key name is allowed`() {
        assertRedacted("mids[]=&layout=", "mids[]=6&layout=webview")
        assertRedacted("filter:name=", "filter:name=bob")
    }

    @Test
    fun `a segment with no value is kept alongside pairs`() {
        assertRedacted("flag&other=", "flag&other=1")
    }

    @Test
    fun `an empty key does not qualify as a pair`() {
        assertRedacted("=value", "=value")
    }

    @Test
    fun `a key over the length limit does not qualify as a pair`() {
        val key = "a".repeat(65)
        assertRedacted("", "$key=x")
    }

    @Test
    fun `a key at the length limit still qualifies as a pair`() {
        val key = "a".repeat(64)
        assertRedacted("$key=", "$key=x")
    }

    // rule 2 - routes: keep the path, recurse past an embedded '?'

    @Test
    fun `routes are kept whole`() {
        assertRedacted("/orders/123", "/orders/123")
        assertRedacted("!/checkout/step-2", "!/checkout/step-2")
        assertRedacted("/2/", "/2/")
    }

    @Test
    fun `a route recurses past an embedded question mark`() {
        assertRedacted("/search?q=", "/search?q=shoes")
    }

    @Test
    fun `a leading slash beats the length test`() {
        val route = "/a/very/long/route/" + "segment/".repeat(10)
        assertRedacted(route, route)
    }

    @Test
    fun `a route followed by pairs redacts only the pairs`() {
        assertRedacted(
            "/survey-form/abc&state=&session_state=&iss=",
            "/survey-form/abc&state=6f1a&session_state=90b2&iss=x",
        )
    }

    // rule 3 - short and unstructured: keep verbatim

    @Test
    fun `short unstructured segments are kept verbatim`() {
        assertRedacted("terms-and-conditions", "terms-and-conditions")
        assertRedacted("accordion-a5c7d862a4-item-7712f03666", "accordion-a5c7d862a4-item-7712f03666")
    }

    @Test
    fun `a bare identifier survives`() {
        // an accepted gap: no threshold separates a keyless identifier from a legitimate anchor
        assertRedacted("550c8890cc23ed42", "550c8890cc23ed42")
    }

    @Test
    fun `a segment at the opaque threshold is kept`() {
        val segment = "a".repeat(64)
        assertRedacted(segment, segment)
    }

    // rule 4 - long and unstructured: drop

    @Test
    fun `long unstructured segments are dropped`() {
        assertRedacted("", "eyJhbGciOiJIUzI1NiJ9." + "a".repeat(70))
    }

    @Test
    fun `a segment one over the opaque threshold is dropped`() {
        assertRedacted("", "a".repeat(65))
    }

    // trap 1 - base64 '=' padding is not a key separator

    @Test
    fun `base64 padding is not treated as a key separator`() {
        val padded = "eyJsYXlvdXQiOns" + "A".repeat(1605) + "fX0="
        assertEquals(1624, padded.length)
        assertRedacted("", padded)
    }

    @Test
    fun `an unpadded base64 blob matches the padded case`() {
        // a length that emits no padding must be handled identically to one that does
        assertRedacted("", "eyJsYXlvdXQiOns" + "A".repeat(1608))
    }

    // trap 2 - parse the encoded form; an encoded separator is not a separator

    @Test
    fun `an encoded separator is not a separator`() {
        assertRedacted("a=", "a=x%26evil=y")
    }

    @Test
    fun `multiple encoding layers are left encoded`() {
        assertRedacted("eid=&format=", "eid=1%25252C2&format=320x50_as")
    }

    // separators

    @Test
    fun `a semicolon separator is preserved rather than normalised`() {
        assertRedacted("a=;b=", "a=1;b=2")
    }

    @Test
    fun `mixed separators are each preserved in place`() {
        assertRedacted("a=;b=&c=", "a=1;b=2&c=3")
    }

    @Test
    fun `a bare separator is preserved`() {
        assertRedacted(";", ";")
        assertRedacted("&", "&")
    }

    @Test
    fun `a matrix parameter in a route splits on the semicolon`() {
        assertRedacted("/orders;id=", "/orders;id=123/detail")
    }

    @Test
    fun `empty segments and their separators are preserved`() {
        assertRedacted("a=&&b=", "a=1&&b=2")
    }

    @Test
    fun `an empty fragment redacts to an empty fragment`() {
        assertRedacted("", "")
    }

    // '?' past the '#' is an ordinary character, not a query

    @Test
    fun `a question mark after a valid key drops the rest of the segment`() {
        assertRedacted("a=", "a=1?b=2")
        assertRedacted("user_id=&utm_medium=", "user_id=6f1a?utm_source=foo&utm_medium=push")
    }

    @Test
    fun `a question mark before any equals disqualifies the key`() {
        // 'frag?x' is not a plausible key and this is not a route, so the length rule keeps it
        // verbatim, value included
        assertRedacted("frag?x=1", "frag?x=1")
    }

    // recursion bound

    @Test
    fun `deep nesting terminates`() {
        // four route hops are followed, then the remainder busts the length rule and is dropped
        assertRedacted("/?/?/?/?", "/?".repeat(50_000))
    }

    @Test
    fun `a short segment past the recursion bound falls through to the length rule`() {
        assertRedacted("/a?/b?/c?/d?/e?f", "/a?/b?/c?/d?/e?f")
    }
}
