package io.embrace.android.gradle.plugin.tasks

import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import org.gradle.api.DefaultTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import javax.inject.Inject

abstract class EmbraceTaskImpl @Inject constructor(
    objectFactory: ObjectFactory
) : DefaultTask(), EmbraceTask {

    @get:Input
    override val variantData: Property<AndroidCompactedVariantData> =
        objectFactory.property(AndroidCompactedVariantData::class.java)
}
