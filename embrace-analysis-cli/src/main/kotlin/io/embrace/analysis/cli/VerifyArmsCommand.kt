package io.embrace.analysis.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.types.path
import io.embrace.analysis.campaign.ArmVerifier
import java.io.IOException

/** `verify-arms`: dex-level pre-flight that two A/B APKs differ at all. */
class VerifyArmsCommand : CliktCommand(name = "verify-arms") {

    private val armA by argument("arm-a.apk").path(mustExist = true)
    private val armB by argument("arm-b.apk").path(mustExist = true)
    private val control by argument("arm-a-rebuilt.apk", help = "CONTROL: a second build of arm A's own tree, for the noise floor")
        .path(mustExist = true).optional()

    override fun help(context: Context): String =
        "Prove two A/B arms actually differ before spending device time: identical dex is a conclusive failure; a " +
            "difference is only conclusive against a control rebuild's noise floor. Runtime (the propagation gate) is the gold standard."

    override fun run() {
        val verdict = try {
            ArmVerifier.verify(armA, armB, control)
        } catch (e: IOException) {
            echo("cannot read the APKs: ${e.message}", err = true)
            throw ProgramResult(2)
        }
        echo(verdict.text, trailingNewline = false)
        if (verdict.exitCode != 0) throw ProgramResult(verdict.exitCode)
    }
}
