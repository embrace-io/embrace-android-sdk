package io.embrace.android.gradle.plugin.gradle

import org.gradle.util.GradleVersion.version

sealed class GradleVersion(private val version: org.gradle.util.GradleVersion) :
    Comparable<GradleVersion> {

    object MIN_VERSION : GradleVersion(version("8.0.2"))
    object CURRENT : GradleVersion(org.gradle.util.GradleVersion.current())
    object GRADLE_8_5 : GradleVersion(version("8.5"))

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
        @JvmStatic
        fun isAtLeast(version: GradleVersion): Boolean = CURRENT >= version
    }
}
