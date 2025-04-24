package io.embrace.android.gradle.plugin.instrumentation.visitor

import io.embrace.android.gradle.plugin.instrumentation.strategy.ClassVisitStrategy

internal val fcmFeature = BytecodeInstrumentationFeature(
    targetParams = BytecodeClassInsertionParams(
        name = "onMessageReceived",
        descriptor = "(Lcom/google/firebase/messaging/RemoteMessage;)V",
    ),
    insertionParams = BytecodeMethodInsertionParams(
        owner = "io/embrace/android/embracesdk/internal/instrumentation/bytecode/FcmBytecodeEntrypoint",
        name = "onMessageReceived",
        descriptor = "(Lcom/google/firebase/messaging/RemoteMessage;)V",
        operandStackIndices = listOf(1),
    ),
    visitStrategy = ClassVisitStrategy.MatchSuperClassName("com.google.firebase.messaging.FirebaseMessagingService")
)

internal val okhttpFeature = BytecodeInstrumentationFeature(
    targetParams = BytecodeClassInsertionParams(
        name = "build",
        descriptor = "()Lokhttp3/OkHttpClient;",
    ),
    insertionParams = BytecodeMethodInsertionParams(
        owner = "io/embrace/android/embracesdk/internal/instrumentation/bytecode/OkHttpBytecodeEntrypoint",
        name = "build",
        descriptor = "(Lokhttp3/OkHttpClient\$Builder;)V",
        operandStackIndices = listOf(0)
    ),
    visitStrategy = ClassVisitStrategy.MatchClassName("okhttp3.OkHttpClient\$Builder")
)
