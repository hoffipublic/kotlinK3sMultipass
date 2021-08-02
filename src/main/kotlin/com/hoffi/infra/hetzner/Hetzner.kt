package com.hoffi.infra.hetzner

import com.github.ajalt.clikt.core.CliktCommand
import com.hoffi.common.log.LoggerDelegate

class Hetzner: CliktCommand(printHelpOnEmptyArgs = true, help = """
    set targetEnv to Hetzner
""".trimIndent())
{
    private val log by LoggerDelegate()

    override fun run() {
        log.info("targetEnv=Hetzner")
    }
}
