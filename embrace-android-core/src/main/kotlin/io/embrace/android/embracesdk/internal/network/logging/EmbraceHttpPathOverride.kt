package io.embrace.android.embracesdk.internal.network.logging

import io.embrace.android.embracesdk.annotation.InternalApi
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/**
 * Header used to override the URL's relative path
 */
const val PATH_OVERRIDE: String = "x-emb-path"

/**
 * Max length of relative path override
 */
private const val RELATIVE_PATH_MAX_LENGTH = 1024

/**
 * Allowed characters in relative path
 * As per https://tools.ietf.org/html/rfc3986#section-2 and https://stackoverflow.com/a/1547940.
 * Removed certain characters we do not want present: ?#
 */
private val RELATIVE_PATH_PATTERN: Pattern = Pattern.compile("[A-Za-z0-9-._~:/\\[\\]@!$&'()*+,;=]+")

@JvmOverloads
@InternalApi
fun getOverriddenURLString(
    request: HttpPathOverrideRequest,
    pathOverride: String? = request.getHeaderByName(PATH_OVERRIDE)
): String {
    val url = try {
        if (pathOverride != null && validatePathOverride(pathOverride)) {
            request.getOverriddenURL(pathOverride)
        } else {
            request.getURLString()
        }
    } catch (_: Exception) {
        request.getURLString()
    }
    return url
}

private fun validatePathOverride(path: String?): Boolean {
    if (path == null) {
        return false
    }
    if (path.isEmpty()) {
        return false
    }
    if (path.length > RELATIVE_PATH_MAX_LENGTH) {
        return false
    }
    if (!StandardCharsets.US_ASCII.newEncoder().canEncode(path)) {
        return false
    }
    if (!path.startsWith("/")) {
        return false
    }
    if (!RELATIVE_PATH_PATTERN.matcher(path).matches()) {
        return false
    }

    return true
}
