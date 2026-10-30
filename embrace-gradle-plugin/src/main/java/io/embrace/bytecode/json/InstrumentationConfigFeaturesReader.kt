@file:OptIn(ExperimentalSerializationApi::class)

package io.embrace.bytecode.json

import io.embrace.bytecode.strategy.ClassVisitStrategy
import io.embrace.bytecode.visitor.BytecodeClassInsertionParams
import io.embrace.bytecode.visitor.BytecodeInstrumentationFeature
import io.embrace.bytecode.visitor.BytecodeMethodInsertionParams
import io.embrace.bytecode.visitor.BytecodeMethodOverrideParams
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

private val json = Json {
    explicitNulls = false
    ignoreUnknownKeys = true
}

private val bytecodeInstrumentationFeatures: List<BytecodeInstrumentationFeature> by lazy {
    readBytecodeInstrumentationConfig().features.map(InstrumentationConfigFeature::convertInstrumentationConfigFeature)
}

fun readBytecodeInstrumentationFeatures(): List<BytecodeInstrumentationFeature> = bytecodeInstrumentationFeatures

private fun readBytecodeInstrumentationConfig(): InstrumentationConfigFeatures {
    val classLoader = InstrumentationConfigFeatures::class.java.classLoader
    val stream = classLoader.getResourceAsStream("bytecode_instrumentation_features.json")
        ?: error("Bytecode instrumentation config file not found")

    return stream.buffered().use { json.decodeFromStream(InstrumentationConfigFeatures.serializer(), it) }
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
