package io.embrace.android.gradle.plugin.agp

import com.android.build.api.AndroidPluginVersion
import io.embrace.embrace_gradle_plugin.BuildConfig

sealed class AgpVersion(private val version: AndroidPluginVersion) : Comparable<AgpVersion> {

    class CURRENT(version: AndroidPluginVersion) : AgpVersion(version)
    object MIN_VERSION : AgpVersion(parse(BuildConfig.MIN_AGP_VERSION))

    override fun compareTo(other: AgpVersion): Int {
        return version.compareTo(other.version)
    }

    override fun toString(): String {
        return "${version.major}.${version.minor}.${version.micro}"
    }
}

private fun parse(version: String): AndroidPluginVersion {
    val (major, minor, micro) = version.split(".").map(String::toInt)
    return AndroidPluginVersion(major, minor, micro)
}
