package io.embrace.android.gradle.plugin.gradle

import io.embrace.embrace_gradle_plugin.BuildConfig
import org.gradle.util.GradleVersion.version

sealed class GradleVersion(private val version: org.gradle.util.GradleVersion) :
    Comparable<GradleVersion> {

    object MIN_VERSION : GradleVersion(version(BuildConfig.MIN_GRADLE_VERSION))
    object CURRENT : GradleVersion(org.gradle.util.GradleVersion.current())

    override fun compareTo(other: GradleVersion): Int {
        return version.compareTo(other.version)
    }

    override fun toString(): String {
        return version.version
    }

    companion object {

        /**
         * Returns true if the current AGP version exceeds the specified version.
         */
        fun isAtLeast(version: GradleVersion): Boolean = CURRENT >= version
    }
}
