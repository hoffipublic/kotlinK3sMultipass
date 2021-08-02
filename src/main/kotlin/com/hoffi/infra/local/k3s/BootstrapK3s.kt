package com.hoffi.infra.local.k3s

import com.github.ajalt.clikt.core.CliktCommand
import com.hoffi.common.log.LoggerDelegate

class BootstrapK3s: CliktCommand(printHelpOnEmptyArgs = true, help = """
    bootstrap K8s Rancher K3s flavor on an existing VM cluster    
""".trimIndent())
{
    private val log by LoggerDelegate()

    override fun run() {
        log.info("running BootStrapK3s.")
        log.info("ran BootStrapK3s.")
    }
}
