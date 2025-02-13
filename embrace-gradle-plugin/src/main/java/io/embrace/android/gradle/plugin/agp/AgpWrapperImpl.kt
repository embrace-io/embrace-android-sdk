package io.embrace.android.gradle.plugin.agp

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Project

class AgpWrapperImpl(project: Project) : AgpWrapper {

    private val extension: CommonExtension<*, *, *, *> = checkNotNull(
        project.extensions.findByType(CommonExtension::class.java)
    )

    private val componentsExtension: AndroidComponentsExtension<*, *, *> = checkNotNull(
        project.extensions.findByType(AndroidComponentsExtension::class.java)
    )

    override val version by lazy {
        AgpVersion.CURRENT(componentsExtension.pluginVersion)
    }

    override val isCoreLibraryDesugaringEnabled: Boolean by lazy {
        extension.compileOptions.isCoreLibraryDesugaringEnabled
    }

    override val usesCMake: Boolean by lazy {
        extension.externalNativeBuild.cmake.path != null
    }

    override val usesNdkBuild: Boolean by lazy {
        extension.externalNativeBuild.ndkBuild.path != null
    }

    override val minSdk: Int? by lazy {
        extension.defaultConfig.minSdk
    }
}
