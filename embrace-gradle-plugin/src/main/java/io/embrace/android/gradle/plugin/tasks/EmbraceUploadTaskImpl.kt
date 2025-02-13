package io.embrace.android.gradle.plugin.tasks

import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import io.embrace.android.gradle.plugin.tasks.common.RequestParams
import org.gradle.api.DefaultTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import javax.inject.Inject

abstract class EmbraceUploadTaskImpl @Inject constructor(
    objectFactory: ObjectFactory
) : DefaultTask(), EmbraceUploadTask {

    @get:Input
    override val requestParams: Property<RequestParams> =
        objectFactory.property(RequestParams::class.java)

    @get:Input
    override val variantData: Property<AndroidCompactedVariantData> =
        objectFactory.property(AndroidCompactedVariantData::class.java)
}
