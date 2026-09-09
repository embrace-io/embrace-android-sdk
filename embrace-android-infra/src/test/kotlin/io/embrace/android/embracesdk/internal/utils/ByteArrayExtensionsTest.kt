package io.embrace.android.embracesdk.internal.utils

import org.junit.Assert.assertEquals
import org.junit.Test

internal class ByteArrayExtensionsTest {

    private val buffer = "abcabcabd".toByteArray()

    @Test
    fun `indexOf finds the first occurrence starting from fromIndex`() {
        assertEquals(0, buffer.indexOf("abc".toByteArray()))
        assertEquals(3, buffer.indexOf("abc".toByteArray(), 1))
        assertEquals(6, buffer.indexOf("abd".toByteArray()))
        assertEquals(8, buffer.indexOf("d".toByteArray()))
        assertEquals(0, buffer.indexOf("abc".toByteArray(), -5))
    }

    @Test
    fun `indexOf returns -1 for absent, empty, oversized, or exhausted searches`() {
        assertEquals(-1, buffer.indexOf("abe".toByteArray()))
        assertEquals(-1, buffer.indexOf(ByteArray(0)))
        assertEquals(-1, buffer.indexOf("abcabcabdx".toByteArray()))
        assertEquals(-1, buffer.indexOf("abd".toByteArray(), 7))
        assertEquals(-1, ByteArray(0).indexOf("a".toByteArray()))
        assertEquals(-1, ByteArray(0).indexOf(ByteArray(0)))
        // a partial match at the very end must not read past the array
        assertEquals(-1, buffer.indexOf("abdz".toByteArray()))
    }

    @Test
    fun `indexOf matches content that ends exactly at the end of the array`() {
        assertEquals(6, buffer.indexOf("abd".toByteArray()))
        assertEquals(8, buffer.indexOf("d".toByteArray()))
        assertEquals(0, buffer.indexOf(buffer.copyOf()))
        assertEquals(0, "a".toByteArray().indexOf("a".toByteArray()))
    }

    @Test
    fun `indexOf handles every fromIndex without throwing`() {
        val content = "abc".toByteArray()
        assertEquals(0, buffer.indexOf(content, Int.MIN_VALUE))
        assertEquals(0, buffer.indexOf(content, -1))
        assertEquals(0, buffer.indexOf(content, 0))
        assertEquals(3, buffer.indexOf(content, 3))
        assertEquals(-1, buffer.indexOf(content, 6))
        assertEquals(-1, buffer.indexOf(content, 7))
        assertEquals(-1, buffer.indexOf(content, buffer.size))
        assertEquals(-1, buffer.indexOf(content, Int.MAX_VALUE))
        assertEquals(-1, buffer.indexOf(content, Int.MAX_VALUE - 1))
    }

    @Test
    fun `indexOf is correct when nearly every offset is a candidate`() {
        val repetitive = ByteArray(64) { 'a'.code.toByte() }
        assertEquals(-1, repetitive.indexOf("aab".toByteArray()))
        assertEquals(0, repetitive.indexOf("aaa".toByteArray()))
        assertEquals(0, repetitive.indexOf(ByteArray(64) { 'a'.code.toByte() }))
        assertEquals(-1, repetitive.indexOf(ByteArray(65) { 'a'.code.toByte() }))

        repetitive[63] = 'b'.code.toByte()
        assertEquals(61, repetitive.indexOf("aab".toByteArray()))
        assertEquals(62, repetitive.indexOf("ab".toByteArray()))
        assertEquals(63, repetitive.indexOf("b".toByteArray()))

        // fromIndex landing exactly on the last offset the content can still fit at, where it does match
        assertEquals(62, repetitive.indexOf("ab".toByteArray(), 62))
        assertEquals(63, repetitive.indexOf("b".toByteArray(), 63))
    }

    @Test
    fun `indexOf handles content containing zero bytes`() {
        val withNuls = byteArrayOf(1, 0, 0, 2, 0, 3)
        assertEquals(1, withNuls.indexOf(byteArrayOf(0, 0)))
        assertEquals(3, withNuls.indexOf(byteArrayOf(2, 0, 3)))
        assertEquals(-1, withNuls.indexOf(byteArrayOf(0, 0, 0)))
        assertEquals(-1, withNuls.indexOf(byteArrayOf(0, 3, 0)))
    }
}
