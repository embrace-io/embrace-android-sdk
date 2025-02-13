package io.embrace.android.gradle.plugin.gradle

import io.embrace.android.gradle.plugin.gradle.GradleVersion.Companion.isAtLeast
import org.gradle.api.provider.HasMultipleValues
import org.gradle.api.provider.Provider
import java.util.Collections

object GradleCompatibilityHelper {

    /**
     * Utility method that adds a provider to a multiple values property.
     * This is needed specifically because of a Gradle issue.
     */
    fun <T> add(self: HasMultipleValues<T>, provider: Provider<out T>) {
        if (isGradleAffectedByIssue22331()) {
            self.addAll(
                provider.map {
                    Collections.singletonList(it)
                }
            )
        } else {
            self.add(provider)
        }
    }

    // this issue is still active, so we do not know when this will get fixed. Update this method when Gradle fix
    // is in place
    // issue link: https://github.com/gradle/gradle/issues/22331
    private fun isGradleAffectedByIssue22331() = !isAtLeast(GradleVersion.GRADLE_8_0)
}
