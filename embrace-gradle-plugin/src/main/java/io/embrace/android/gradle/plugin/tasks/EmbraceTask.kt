package io.embrace.android.gradle.plugin.tasks

import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import org.gradle.api.Task
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input

interface EmbraceTask : Task {

    @get:Input
    val variantData: Property<AndroidCompactedVariantData>
}
