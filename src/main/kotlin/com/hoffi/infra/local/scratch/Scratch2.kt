package com.hoffi.infra.local.scratch

import com.github.ajalt.clikt.core.CliktCommand
import com.hoffi.common.helpers.file.FileUtils
import com.hoffi.common.helpers.os.Sudo
import com.hoffi.common.log.LoggerDelegate
import com.hoffi.infra.local.multipass.vmState
import net.mamoe.yamlkt.Yaml
import java.io.File
import java.nio.file.Path


class Scratch2: CliktCommand() {
    private val log by LoggerDelegate()
    val yaml = Yaml()

    override fun run() {
        log.info("should have a greeting.")

        //val yamlConf = Yaml.decodeMapFromString(File(CONF.DIR.CONFDIR, "common/linuxPackages.yml").readText())

        log.info("sudo: ${Sudo.check()}")
        File("tmp/erase.me").writeText("as sudo")

//        FileUtils.replaceBetween(
//            Paths.get("/etc/hosts"),
//            "#BEGIN MARKER used for automation, DO NOT CHANGE this comment line",
//            "#ENDIN MARKER used for automation, DO NOT CHANGE this comment line",
//            keepOriginal = true,
//            needsSudo = true
//        ) {
//            it.replace(Regex("new"), "superhot")
//        }

        val listVMs = listOf(
            vmState("k3snode01", "192.168.64.15", "ok", "ok"),
            vmState("k3snode02", "192.168.64.16", "ok", "ok")
        )

        val file = Path.of("tmp/tmphosts")
        val linesBetween = FileUtils.getBetween(file, FileUtils.START_SENTINEL, FileUtils.END_SENTINEL)
        linesBetween.removeIf { line -> line.contains(Regex(listVMs.map { it.name }.joinToString("|")))  }
        listVMs.forEach {
            linesBetween.add("${it.IP} ${it.name}")
        }
        FileUtils.replaceAllWith(file, linesBetween.joinToString("\n"), FileUtils.START_SENTINEL, FileUtils.END_SENTINEL)

        log.info("done.")
    }
}
