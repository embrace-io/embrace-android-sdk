package io.embrace.config.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.joinToCode

/**
 * The generated tests for one field of a [ConfigSlice].
 */
internal class ConfigTestCases(
    private val slice: ConfigSlice,
    private val field: ConfigField,
    private val packageName: String,
) {
    private val type = field.type
    private val unreadBucket = CodeBlock.of("%M", MemberName(packageName, "unreadBucket"))

    init {
        require(field.remote == null || field.remote.sdk.matches(Regex("\\w+"))) {
            "generated tests only support top-level RemoteConfig properties"
        }
    }

    fun tests(): List<FunSpec> = listOfNotNull(
        defaults(),
        fallback(),
        field.remote?.let { remoteOverrides(it) },
        field.remote?.takeIf { it.type == ConfigType.PCT }?.let { partialRollout(it) },
    )

    private fun defaults(): FunSpec = test("default") {
        val default = type.literal(field.default)
        assertEquals(default, CodeBlock.of("%T().%N.%N", ClassName(packageName, "EmbraceConfig"), slice.property, field.name))
        assertEquals(
            default,
            CodeBlock.of(
                "%M(%T, null, %L).%N.%N",
                MemberName(packageName, "resolveConfig"),
                ConfigClassNames.INSTRUMENTED_CONFIG_IMPL,
                unreadBucket,
                slice.property,
                field.name,
            ),
        )
    }

    private fun fallback(): FunSpec {
        val local = field.local
        return test(if (local != null) "local used when remote absent" else "default used when remote absent") {
            val expected = if (local != null) type.samples else listOf(field.default)
            val remotes = listOfNotNull(
                CodeBlock.of("null"),
                CodeBlock.of("%T()", ConfigClassNames.REMOTE_CONFIG).takeIf { field.remote != null },
            )
            expected.forEach { value ->
                remotes.forEach { remote ->
                    assertEquals(type.literal(value), resolve(local = localWith(value), remote = remote))
                }
            }
        }
    }

    private fun remoteOverrides(remote: ConfigProperty): FunSpec =
        test(if (field.local != null) "remote overrides local" else "remote overrides default") {
            remote.type.samples.forEachIndexed { index, value ->
                assertEquals(
                    type.literal(type.samples[index]),
                    resolve(local = localWith(type.samples[1 - index]), remote = remoteWith(remote, remote.type.literal(value))),
                )
            }
        }

    private fun partialRollout(remote: ConfigProperty): FunSpec = test("partial rollout reads bucket") {
        mapOf("49" to "true", "51" to "false").forEach { (bucket, expected) ->
            assertEquals(
                CodeBlock.of(expected),
                resolve(
                    local = localWith(type.samples.single { it != expected }),
                    remote = remoteWith(remote, CodeBlock.of("50f")),
                    bucket = CodeBlock.of("lazy { %Lf }", bucket),
                ),
            )
        }
    }

    private fun localWith(value: String): CodeBlock? = field.local?.let {
        CodeBlock.of("%M(%S, %L)", MemberName(packageName, "overrideLocal"), it.sdk, it.type.literal(value))
    }

    private fun remoteWith(remote: ConfigProperty, value: CodeBlock): CodeBlock =
        CodeBlock.of("%T(%N = %L)", ConfigClassNames.REMOTE_CONFIG, remote.sdk, value)

    private fun resolve(local: CodeBlock?, remote: CodeBlock, bucket: CodeBlock = unreadBucket): CodeBlock {
        val args = mapOf(
            "local" to (local ?: CodeBlock.of("%T", ConfigClassNames.INSTRUMENTED_CONFIG_IMPL)),
            "remote" to remote,
            "bucket" to bucket,
        )
        val params = slice.resolverParams.map { CodeBlock.of("%N = %L", it.name, args.getValue(it.name)) }
        return CodeBlock.of("%M(%L).%N", MemberName(packageName, slice.resolver), params.joinToCode(), field.name)
    }

    private fun test(name: String, body: FunSpec.Builder.() -> Unit): FunSpec =
        FunSpec.builder("${field.name} $name")
            .addAnnotation(ConfigClassNames.TEST)
            .apply(body)
            .build()

    /**
     * Two distinct values of this type. Each resolves to the sample at the same index of [ConfigType.resolvedType],
     * so Pct's 100 and 0 resolve to true and false.
     */
    private val ConfigType.samples: List<String>
        get() = when (this) {
            ConfigType.BOOLEAN -> listOf("true", "false")
            ConfigType.INT, ConfigType.LONG -> listOf("1", "2")
            ConfigType.STRING -> listOf("a", "b")
            ConfigType.PCT -> listOf("100", "0")
        }

    private fun FunSpec.Builder.assertEquals(expected: CodeBlock, actual: CodeBlock) {
        addStatement("%M(%L, %L)", ConfigClassNames.ASSERT_EQUALS, expected, actual)
    }
}
