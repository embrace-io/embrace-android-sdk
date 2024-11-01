package io.embrace.android.embracesdk.internal.injection

/**
 * Function that returns an instance of [WorkerThreadModule]. Matches the signature of the constructor for [WorkerThreadModuleImpl]
 */
typealias WorkerThreadModuleSupplier = () -> WorkerThreadModule

fun createWorkerThreadModule(): WorkerThreadModule = WorkerThreadModuleImpl()
