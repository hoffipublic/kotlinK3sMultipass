package com.hoffi.shell

import com.hoffi.common.log.LoggerDelegate
import koodies.exec.successful
import koodies.shell.ShellScript

object Shell {
    private val log by LoggerDelegate()

    fun callShell(name: String, cmd: String, noLogging: Boolean = false): String {
        val shellScript = ShellScript(name = "bash $name: bash -c '$cmd'") {
            cmd
        }
        val shellResult = if (noLogging) {
            shellScript.exec()
        } else {
            shellScript.exec.logging()
        }
        if ( shellResult.successful ) {
            return shellResult.io.toString()
        } else {
            throw Exception("failed '${name}':  bash -c '$cmd'")
        }
    }
}
