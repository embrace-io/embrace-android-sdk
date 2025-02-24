package io.embrace.android.gradle.plugin.tasks.registration

import com.android.build.api.variant.Variant
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import io.embrace.android.gradle.plugin.network.NetworkService
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty

class RegistrationParams(
    val project: Project,
    val variant: Variant,
    val data: AndroidCompactedVariantData,
    val networkService: NetworkService,
    val variantConfigurationsListProperty: ListProperty<VariantConfig>,
    val baseUrl: String,
)
