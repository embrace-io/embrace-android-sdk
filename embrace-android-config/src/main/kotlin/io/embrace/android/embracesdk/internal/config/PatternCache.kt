package io.embrace.android.embracesdk.internal.config

import java.util.regex.Pattern

internal class PatternCache {

    private val memo: MutableMap<Set<String>, Collection<Pattern>> = mutableMapOf()

    fun doesStringMatchesPatternInSet(string: String, patternSet: Set<String>): Boolean {
        val patterns = memo.getOrPut(patternSet) {
            patternSet.map(Pattern::compile)
        }
        return patterns.any { it.matcher(string).matches() }
    }
}
