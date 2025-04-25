package io.embrace.android.gradle.plugin.instrumentation.json

import com.squareup.moshi.Moshi
import io.embrace.android.gradle.plugin.instrumentation.strategy.ClassVisitStrategy
import io.embrace.android.gradle.plugin.instrumentation.visitor.BytecodeClassInsertionParams
import io.embrace.android.gradle.plugin.instrumentation.visitor.BytecodeInstrumentationFeature
import io.embrace.android.gradle.plugin.instrumentation.visitor.BytecodeMethodInsertionParams
import okio.buffer
import okio.source

fun readBytecodeInstrumentationFeatures(): List<BytecodeInstrumentationFeature> {
    val configFeatures = readBytecodeInstrumentationConfig()
    return configFeatures.features.map(InstrumentationConfigFeature::convertInstrumentationConfigFeature)
}

private fun readBytecodeInstrumentationConfig(): InstrumentationConfigFeatures {
    val classLoader = InstrumentationConfigFeatures::class.java.classLoader
    val stream = classLoader.getResourceAsStream("bytecode_instrumentation_features.json")
        ?: error("Bytecode instrumentation config file not found")

    return stream.use {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(InstrumentationConfigFeatures::class.java)
        adapter.fromJson(it.source().buffer())
    } ?: error("Failed to parse bytecode instrumentation config file")
}

private fun InstrumentationConfigFeature.convertInstrumentationConfigFeature(): BytecodeInstrumentationFeature {
    return BytecodeInstrumentationFeature(
        name = name,
        targetParams = BytecodeClassInsertionParams(
            name = target.name,
            descriptor = target.descriptor
        ),
        insertionParams = BytecodeMethodInsertionParams(
            owner = insert.owner,
            name = insert.name,
            descriptor = insert.descriptor,
            operandStackIndices = insert.operandStackIndices
        ),
        visitStrategy = convertVisitStrategy()
    )
}

private fun InstrumentationConfigFeature.convertVisitStrategy(): ClassVisitStrategy {
    return when (visitStrategy.type) {
        "match_super_class_name" -> ClassVisitStrategy.MatchSuperClassName(checkNotNull(visitStrategy.value))
        "match_class_name" -> ClassVisitStrategy.MatchClassName(checkNotNull(visitStrategy.value))
        "exhaustive" -> ClassVisitStrategy.Exhaustive
        else -> error("Unsupported visit strategy type: ${visitStrategy.type}")
    }
}
