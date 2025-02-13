package io.embrace.android.gradle.plugin.agp

import com.android.build.api.AndroidPluginVersion

sealed class AgpVersion(private val version: AndroidPluginVersion) : Comparable<AgpVersion> {

    class CURRENT(version: AndroidPluginVersion) : AgpVersion(version)
    object AGP_8_3_0 : AgpVersion(AndroidPluginVersion(8, 3, 0))
    object AGP_8_0_0 : AgpVersion(AndroidPluginVersion(8, 0, 0))

    override fun compareTo(other: AgpVersion): Int {
        return version.compareTo(other.version)
    }

    override fun toString(): String {
        return "${version.major}.${version.minor}.${version.micro}"
    }
}
