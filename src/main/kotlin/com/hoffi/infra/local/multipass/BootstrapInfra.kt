package com.hoffi.infra.local.multipass

import com.github.ajalt.clikt.core.CliktCommand
import com.hoffi.common.log.LoggerDelegate
import com.hoffi.conf.yaml.YamlConf
import com.hoffi.infra.CONF
import net.mamoe.yamlkt.Yaml

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

        echo(Yaml { encodeDefaultValues = true }.encodeToString(YamlConf.serializer(), CONF))
        echo("some processing...")
        echo(CONF.HOST_KUBECONFIG_FILE)

        log.info("ran BootStrapInfra.")
    }
}
