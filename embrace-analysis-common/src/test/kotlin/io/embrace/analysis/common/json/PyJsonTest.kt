package io.embrace.analysis.common.json

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PyJson]'s loose `dict.get`-flavoured accessors and its two string-rendering paths: [PyJson.repr] /
 * [PyJson.reprList] (Python's `repr`, single-quoted unless the text itself has a single quote) and
 * [PyJson.dumps] / [PyJson.dumpsPretty] (proper double-quoted `json.dumps`).
 */
class PyJsonTest {

    private val record = JsonObject(
        linkedMapOf(
            "name" to JsonPrimitive("hello"),
            "emptyStr" to JsonPrimitive(""),
            "nullField" to JsonNull,
            "num" to JsonPrimitive(5),
            "numDouble" to JsonPrimitive(2.5),
            "zero" to JsonPrimitive(0),
            "flag" to JsonPrimitive(true),
            "flagFalse" to JsonPrimitive(false),
            "nested" to JsonObject(linkedMapOf("x" to JsonPrimitive(1))),
            "list" to JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(2))),
            "emptyList" to JsonArray(emptyList()),
            "emptyObj" to JsonObject(emptyMap()),
        ),
    )

    @Test
    fun `str falls back to the default for a missing key, a null value and an empty string`() {
        assertEquals("fallback", PyJson.str(record, "missingKey", "fallback"))
        assertEquals("fallback", PyJson.str(record, "nullField", "fallback"))
        assertEquals("fallback", PyJson.str(record, "emptyStr", "fallback"))
        assertEquals("hello", PyJson.str(record, "name", "fallback"))
    }

    @Test
    fun `str renders a non-primitive field with dumps rather than falling back`() {
        assertEquals("""{"x": 1}""", PyJson.str(record, "nested", "fallback"))
    }

    @Test
    fun `strOrNull is null for a missing key and a null value, but passes an empty string through`() {
        assertNull(PyJson.strOrNull(record, "missingKey"))
        assertNull(PyJson.strOrNull(record, "nullField"))
        assertEquals("", PyJson.strOrNull(record, "emptyStr"))
        assertEquals("hello", PyJson.strOrNull(record, "name"))
    }

    @Test
    fun `obj and arr cast to their own shape and null out otherwise`() {
        assertEquals(1, PyJson.obj(record, "nested")?.get("x")?.jsonPrimitive?.content?.toInt())
        assertNull(PyJson.obj(record, "list"))
        assertNull(PyJson.obj(record, "missingKey"))
        assertEquals(2, PyJson.arr(record, "list")?.size)
        assertNull(PyJson.arr(record, "nested"))
    }

    @Test
    fun `double parses a numeric primitive and nulls out for null, string, absent and non-primitive fields`() {
        assertEquals(5.0, PyJson.double(record, "num")!!, 0.0)
        assertEquals(2.5, PyJson.double(record, "numDouble")!!, 0.0)
        assertNull(PyJson.double(record, "nullField"))
        assertNull(PyJson.double(record, "name"))
        assertNull(PyJson.double(record, "missingKey"))
        assertNull(PyJson.double(record, "nested"))
    }

    @Test
    fun `truthy is Python truthiness for numeric, boolean, string, array and object fields`() {
        assertFalse(PyJson.truthy(record, "missingKey"))
        assertFalse(PyJson.truthy(record, "nullField"))
        assertFalse(PyJson.truthy(record, "emptyStr"))
        assertFalse(PyJson.truthy(record, "zero"))
        assertFalse(PyJson.truthy(record, "flagFalse"))
        assertFalse(PyJson.truthy(record, "emptyList"))
        assertFalse(PyJson.truthy(record, "emptyObj"))
        assertTrue(PyJson.truthy(record, "name"))
        assertTrue(PyJson.truthy(record, "num"))
        // a JSON boolean's content is the text "true", not a number - it must still read as truthy
        assertTrue(PyJson.truthy(record, "flag"))
        assertTrue(PyJson.truthy(record, "list"))
        assertTrue(PyJson.truthy(record, "nested"))
    }

    @Test
    fun `bool reads real JSON booleans and falls back to numeric truthiness or the default`() {
        assertEquals(true, PyJson.bool(record, "flag", false))
        assertEquals(false, PyJson.bool(record, "flagFalse", true))
        assertEquals(true, PyJson.bool(record, "num", false))
        assertEquals(false, PyJson.bool(record, "zero", true))
        assertEquals(false, PyJson.bool(record, "nullField", true))
        assertEquals(true, PyJson.bool(record, "missingKey", true))
    }

    @Test
    fun `repr uses single quotes normally and switches to double quotes for a lone single quote`() {
        assertEquals("'plain text'", PyJson.repr("plain text"))
        assertEquals("\"it's fine\"", PyJson.repr("it's fine"))
    }

    @Test
    fun `reprList renders each element through repr`() {
        assertEquals("['a', \"b's\"]", PyJson.reprList(listOf("a", "b's")))
    }

    @Test
    fun `dumps sorts keys by default and preserves insertion order when asked not to`() {
        val el = JsonObject(linkedMapOf("b" to JsonPrimitive(1), "a" to JsonPrimitive(2)))

        assertEquals("""{"a": 2, "b": 1}""", PyJson.dumps(el))
        assertEquals("""{"b": 1, "a": 2}""", PyJson.dumps(el, sortKeys = false))
    }

    @Test
    fun `dumps renders arrays and nulls the way json dumps does`() {
        val el = JsonObject(
            linkedMapOf(
                "list" to JsonArray(listOf(JsonPrimitive(1), JsonPrimitive("x"))),
                "missing" to JsonNull,
            ),
        )

        assertEquals("""{"list": [1, "x"], "missing": null}""", PyJson.dumps(el))
    }

    @Test
    fun `dumpsPretty indents one entry per line, sorted by key`() {
        val el = JsonObject(linkedMapOf("b" to JsonPrimitive(1), "a" to JsonArray(emptyList())))

        assertEquals("{\n  \"a\": [],\n  \"b\": 1\n}", PyJson.dumpsPretty(el))
    }
}
