package com.hoffi.infra.local.k3s

import com.github.ajalt.clikt.core.CliktCommand
import com.hoffi.common.log.LoggerDelegate
import com.hoffi.infra.CONF
import com.hoffi.infra.local.longhorn.Longhorn

class ConfigureK3s: CliktCommand(printHelpOnEmptyArgs = false, help = """
    configure the K3S cluster
""".trimIndent())
{
    private val log by LoggerDelegate()

    override fun run() {
        log.info("running ConfigureK3s.")

        val longHornVMs = Longhorn.cluster(CONF.K3SNODENAMEPREFIX, "%s%02d", CONF.K3SNODECOUNT)

        //CONF.tmpMap["Longhorn"] = vmStates // TODO define CONF constant
        longHornVMs.forEach {
            echo(it)
        }


        log.info("ran ConfigureK3s.")
    }

}
