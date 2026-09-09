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
 * Whether the bytes starting at [offset] equal [contentToFind]
 */
fun ByteArray.regionMatches(offset: Int, contentToFind: ByteArray): Boolean {
    if (offset < 0 || offset + contentToFind.size > size) {
        return false
    }
    var j = 0
    while (j < contentToFind.size) {
        if (this[offset + j] != contentToFind[j]) {
            return false
        }
        j++
    }
    return true
}
