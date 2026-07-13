package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.utils.RedactionPatterns
import java.util.regex.Pattern

private const val URL_REDACTION_PATTERNS_MAX_SIZE = 100
private const val URL_REDACTION_PATTERN_MAX_LENGTH = 512

class UrlRedactionBehaviorImpl(
    local: InstrumentedConfig,
) : UrlRedactionBehavior {
    private val redactionPatterns = RedactionPatterns(
        local.redaction.getUrlRedactionPatterns()
            .orEmpty()
            .take(URL_REDACTION_PATTERNS_MAX_SIZE)
            .filter { it.length <= URL_REDACTION_PATTERN_MAX_LENGTH }
            .mapNotNull {
                runCatching { Pattern.compile(it) }.getOrNull()
            },
    )

    override fun redactUrl(url: String): String {
        return redactionPatterns.redacted(url)
    }
}
