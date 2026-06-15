package io.embrace.android.embracesdk.internal.utils

import java.util.UUID
import kotlin.random.Random

class UuidSourceImpl(private val random: Random = Random.Default) : UuidSource {

    override fun createUuid(): String {
        val input = UUID(random.nextLong(), random.nextLong()).toString()

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
