package io.embrace.android.gradle.plugin.tasks.registration

import com.android.build.api.variant.Variant
import io.embrace.android.gradle.plugin.extension.EmbraceExtensionInternal
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import io.embrace.android.gradle.plugin.network.NetworkService
import org.gradle.api.Project

class RegistrationParams(
    val project: Project,
    val variant: Variant,
    val data: AndroidCompactedVariantData,
    val networkService: NetworkService,
    val extension: EmbraceExtensionInternal,
    val baseUrl: String,
)
