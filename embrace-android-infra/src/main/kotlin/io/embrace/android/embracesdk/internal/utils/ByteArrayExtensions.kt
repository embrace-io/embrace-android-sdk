package io.embrace.android.embracesdk.internal.utils

/**
 * Index of the first occurrence of a non-empty [contentToFind] at or after [fromIndex], or -1 if it is not found.
 * Intended for use with small [ByteArray]. Does not contain optimizations that would be required to work with a
 * large [ByteArray] when doing full scans repeatedly incur material runtime costs.
 */
fun ByteArray.indexOf(contentToFind: ByteArray, fromIndex: Int = 0): Int {
    if (contentToFind.isEmpty() || size < contentToFind.size) {
        return -1
    }
    val first = contentToFind[0]
    val last = size - contentToFind.size
    var i = maxOf(fromIndex, 0)
    while (i <= last) {
        if (this[i] == first && regionMatches(i, contentToFind)) {
            return i
        }
        i++
    }
    return -1
}

/**
 * Whether the bytes starting at [offset] equal [contentToFind], deferring to Kotlin's [contentEquals], which is
 * an inline alias for `java.util.Arrays.equals(byte[], byte[])`.
 *
 * Note: the eventual use of the two-argument form is deliberate. The vectorized look up that exists in API 33+
 * is removed by D8 if minSdk is below 35, so and is not reachable in production. It's toss-up whether taking
 * a copy of array section to conform to the simpler API actually improve performance, but not having to maintain
 * additional code as well as runtime costs not actually being that material at reasonable input values means
 * deferring to it is preferred over having a hand-coded comparison that does this by scanning the byte array.
 */
private fun ByteArray.regionMatches(offset: Int, contentToFind: ByteArray): Boolean {
    // Fail fast if a match isn't possible given the offset and the size of the content to be matched
    if (offset < 0 || contentToFind.size > size - offset) {
        return false
    }
    return copyOfRange(offset, offset + contentToFind.size).contentEquals(contentToFind)
}
