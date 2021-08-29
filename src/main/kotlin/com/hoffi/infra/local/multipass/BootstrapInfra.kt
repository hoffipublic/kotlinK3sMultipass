package com.hoffi.infra.local.multipass

import com.github.ajalt.clikt.core.CliktCommand
import com.hoffi.common.log.LoggerDelegate
import com.hoffi.infra.CONF

class BootstrapInfra: CliktCommand(printHelpOnEmptyArgs = true, help = """
    bootstrap and start multipass hyperV VMs
""".trimIndent())
{
    private val log by LoggerDelegate()
//    val arg1: String by argument()
//    val arg2: String? by argument().optional()

    override fun run() {
        log.info("running BootStrapInfra.")
        //log.info("ran BootStrapInfra: arg1='${arg1}', arg2='${arg2}'")

        val vmStates = CommonNodes.cluster(CONF.K3SNODENAMEPREFIX, "%s%02d", CONF.K3SNODECOUNT)

        CONF.tmpMap[CONF.K3SNODENAMEPREFIX] = vmStates
        vmStates.forEach {
            echo(it)
        }

        log.info("ran BootStrapInfra.")
    }
}

