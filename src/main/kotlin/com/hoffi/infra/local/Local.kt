package com.hoffi.infra.local

import com.github.ajalt.clikt.core.CliktCommand
import com.hoffi.common.log.LoggerDelegate

class Local: CliktCommand(printHelpOnEmptyArgs = true, help = """
    set targetEnv to localhost
""".trimIndent())
{
    private val log by LoggerDelegate()

    override fun run() {
        log.info("targetEnv=local")
    }
}
