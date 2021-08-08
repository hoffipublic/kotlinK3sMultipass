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
            // alter own HOST's /etc/hosts and usr/local/etc/dnsmasq.conf
            val etchosts = Path.of("/etc/hosts")
            val dnsmasq = Path.of("/usr/local/etc/dnsmasq.conf")
            val linesBetweenEtcHosts = FileUtils.getBetween(etchosts, FileUtils.START_SENTINEL, FileUtils.END_SENTINEL)
            val linesBetweenDnsmasq = FileUtils.getBetween(dnsmasq, FileUtils.START_SENTINEL, FileUtils.END_SENTINEL)
            linesBetweenEtcHosts.removeIf { line -> line.contains(Regex(vmStates.map { it.name }.joinToString("|"))) }
            linesBetweenDnsmasq.removeIf { line -> line.contains(Regex(vmStates.map { it.name }.joinToString("|"))) }
            vmStates.forEach {
                linesBetweenEtcHosts.add("${it.IP} ${it.name}")
                linesBetweenDnsmasq.add("address=/${it.name}/${it.IP}")
            }
            linesBetweenDnsmasq.removeIf { line -> line.contains("address=/.${CONF.MYCLUSTER_DOMAIN}/") }
            linesBetweenDnsmasq.add(0, "address=/.${CONF.MYCLUSTER_DOMAIN}/${vmStates[0].IP}")
            FileUtils.replaceAllWith(
                dnsmasq, linesBetweenDnsmasq.joinToString("\n"),
                FileUtils.START_SENTINEL, FileUtils.END_SENTINEL, keepOriginal = true, needsSudo = false
            )
            FileUtils.replaceAllWith(
                etchosts, linesBetweenEtcHosts.joinToString("\n"),
                FileUtils.START_SENTINEL, FileUtils.END_SENTINEL, keepOriginal = true, needsSudo = true,
                additionalSudoCmds = listOf("brew services restart dnsmasq")
            )
            // FINALLY, DO THE FRESH INSTALL
            CommonNodes.freshInstall(vmStates.filter { it.formerState == "fresh" })
        }

        log.info("ran BootStrapInfra.")
    }
}

