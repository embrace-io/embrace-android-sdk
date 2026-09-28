package io.embrace.android.gradle.plugin.instrumentation.json

import io.embrace.android.gradle.plugin.instrumentation.strategy.ClassVisitStrategy
import io.embrace.android.gradle.plugin.instrumentation.visitor.BytecodeClassInsertionParams
import io.embrace.android.gradle.plugin.instrumentation.visitor.BytecodeInstrumentationFeature
import io.embrace.android.gradle.plugin.instrumentation.visitor.BytecodeMethodInsertionParams
import io.embrace.android.gradle.plugin.instrumentation.visitor.BytecodeMethodOverrideParams
import io.embrace.android.gradle.plugin.util.serialization.JsonSerializer

fun readBytecodeInstrumentationFeatures(): List<BytecodeInstrumentationFeature> {
    val configFeatures = readBytecodeInstrumentationConfig()
    return configFeatures.features.map(InstrumentationConfigFeature::convertInstrumentationConfigFeature)
}

private fun readBytecodeInstrumentationConfig(): InstrumentationConfigFeatures {
    val classLoader = InstrumentationConfigFeatures::class.java.classLoader
    val stream = classLoader.getResourceAsStream("bytecode_instrumentation_features.json")
        ?: error("Bytecode instrumentation config file not found")

    return JsonSerializer().fromJson(stream, InstrumentationConfigFeatures.serializer())
}

private fun InstrumentationConfigFeature.convertInstrumentationConfigFeature(): BytecodeInstrumentationFeature =
    BytecodeInstrumentationFeature(
        name = name,
        targetParams = BytecodeClassInsertionParams(
            name = target.name,
            descriptor = target.descriptor,
        ),
        insertionParams = BytecodeMethodInsertionParams(
            owner = insert.owner,
            name = insert.name,
            descriptor = insert.descriptor,
            operandStackIndices = insert.operandStackIndices,
            insertAtEnd = insert.insertAtEnd,
        ),
        visitStrategy = convertVisitStrategy(),
        addOverrideParams = addOverride?.let {
            BytecodeMethodOverrideParams(
                owner = it.owner,
                name = it.name,
                descriptor = it.descriptor,
            )
        },
    )

private fun InstrumentationConfigFeature.convertVisitStrategy(): ClassVisitStrategy {
    return when (visitStrategy.type) {
        "match_super_class_name" -> ClassVisitStrategy.MatchSuperClassName(checkNotNull(visitStrategy.value))
        "match_class_name" -> ClassVisitStrategy.MatchClassName(checkNotNull(visitStrategy.value))
        "exhaustive" -> ClassVisitStrategy.Exhaustive
        else -> error("Unsupported visit strategy type: ${visitStrategy.type}")
    }
}
