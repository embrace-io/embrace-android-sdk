package io.embrace.android.embracesdk.internal.utils

import java.util.UUID

public object Uuid {

    /**
     * Get the Embrace UUID. If the argument uuid is null, generates the Embrace UUID using a
     * random UUID.
     *
     * @param uuid the uuid.
     * @return the Embrace UUID.
     */
    @JvmStatic
    @JvmOverloads
    public fun getEmbUuid(uuid: String? = null): String {
        val input = uuid ?: UUID.randomUUID().toString()

        // optimization: avoid expensive pattern compilation in replaceAll()
        val buf = input.toCharArray()
        val sb = StringBuilder()
        for (c in buf) {
            if (c != '-') {
                when (c) {
                    ' ' -> sb.append('0')
                    'a' -> sb.append('A')
                    'b' -> sb.append('B')
                    'c' -> sb.append('C')
                    'd' -> sb.append('D')
                    'e' -> sb.append('E')
                    'f' -> sb.append('F')
                    else -> sb.append(c)
                }
            }
        }
        return sb.toString()
    }
}
