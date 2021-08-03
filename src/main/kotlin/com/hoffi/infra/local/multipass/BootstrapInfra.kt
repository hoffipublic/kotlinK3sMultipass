package com.hoffi.infra.local.multipass

import com.github.ajalt.clikt.core.CliktCommand
import com.hoffi.common.helpers.file.FileUtils
import com.hoffi.common.log.LoggerDelegate
import com.hoffi.infra.CONF
import java.nio.file.Path

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

        if (vmStates.any { it.formerState == "fresh" }) {
            // alter own HOST's /etc/hosts
            val file = Path.of("/etc/hosts")
            val linesBetween = FileUtils.getBetween(file, FileUtils.START_SENTINEL, FileUtils.END_SENTINEL)
            linesBetween.removeIf { line -> line.contains(Regex(vmStates.map { it.name }.joinToString("|"))) }
            vmStates.forEach {
                linesBetween.add("${it.IP} ${it.name}")
            }
            FileUtils.replaceAllWith(
                file, linesBetween.joinToString("\n"),
                FileUtils.START_SENTINEL, FileUtils.END_SENTINEL, keepOriginal = true, needsSudo = true,
                additionalSudoCmds = listOf("brew services restart dnsmasq")
            )

            // fresh install
            CommonNodes.freshInstall(vmStates.filter { it.formerState == "fresh" })
        }

        log.info("ran BootStrapInfra.")
    }
}
