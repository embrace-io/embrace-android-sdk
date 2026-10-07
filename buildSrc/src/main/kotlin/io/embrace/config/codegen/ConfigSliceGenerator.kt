package io.embrace.config.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.joinToCode

/**
 * Generates the SDK's resolved config: an interface, an inline factory taking one provider per field that
 * defaults to the field's default, and a precedence resolver function.
 */
fun configSliceFile(slice: ConfigSlice, packageName: String, sourceName: String): FileSpec {
    val iface = ClassName(packageName, slice.name)
    val impl = TypeSpec.anonymousClassBuilder()
        .addSuperinterface(iface)
        .addProperties(
            slice.fields.map {
                PropertySpec.builder(it.name, it.type.typeName, KModifier.OVERRIDE)
                    .initializer("%N()", it.name)
                    .build()
            },
        )
    val factory = FunSpec.builder(slice.name)
        .addModifiers(KModifier.INLINE)
        .addParameters(
            slice.fields.map {
                ParameterSpec.builder(it.name, LambdaTypeName.get(returnType = it.type.typeName), KModifier.CROSSINLINE)
                    .defaultValue("{ %L }", it.type.literal(it.default))
                    .build()
            },
        )
        .returns(iface)
        .addStatement("return %L", impl.build())
    val providers = slice.fields.map { field ->
        val remote = field.remote?.let {
            if (it.type == ConfigType.PCT) {
                CodeBlock.of("%M(remote?.%L, bucket)", MemberName(packageName, "rolloutEnabled"), it.sdk)
            } else {
                CodeBlock.of("remote?.%L", it.sdk)
            }
        }
        val fallback = field.local?.let { CodeBlock.of("local.%L", it.sdk) } ?: field.type.literal(field.default)
        CodeBlock.of("%N = { %L },\n", field.name, listOfNotNull(remote, fallback).joinToCode(" ?: "))
    }
    val resolver = FunSpec.builder(slice.resolver)
        .addParameters(slice.resolverParams)
        .returns(iface)
        .addCode("return %T(\n⇥%L⇤)\n", iface, providers.joinToCode(""))
    val type = TypeSpec.interfaceBuilder(iface)
        .addKdoc("%L", slice.doc)
        .addProperties(slice.fields.map { PropertySpec.builder(it.name, it.type.typeName).build() })
    return generatedFile(packageName, slice.name, sourceName)
        .addType(type.build())
        .addFunction(factory.build())
        .addFunction(resolver.build())
        .build()
}
