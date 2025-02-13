package io.embrace.android.gradle.plugin.util

import java.util.Locale
import java.util.UUID

/**
 * General utility functions for dealing with UUIDs.
 *
 *
 * This class should be completely functional and should not contain any instance methods.
 */
object UuidUtils {

    /**
     * Generates a random UUID using Java Utils library, and formats it into an Embrace API
     * compatible build UUID.
     *
     * @return Embrace API compatible randomly generated UUID
     */
    @JvmOverloads
    fun generateEmbraceUuid(uuid: UUID = UUID.randomUUID()): String {
        return uuid.toString().replace("[^a-zA-Z0-9]".toRegex(), "").uppercase(Locale.getDefault())
    }
}
