package com.hoffi.infra.local.scratch

import com.github.ajalt.clikt.core.CliktCommand
import com.hoffi.common.log.LoggerDelegate
import com.hoffi.conf.yaml.YamlConf
import com.hoffi.infra.CONF
import net.mamoe.yamlkt.Yaml
import java.io.File


class Scratch2: CliktCommand() {
    private val log by LoggerDelegate()
    val yaml = Yaml()

    override fun run() {
        log.info("should have a greeting.")

        val yamlConf = Yaml.decodeFromString(YamlConf.serializer(), File("conf/${CONF.targetEnv}/conf.yml").readText())

        echo("HOST_KUBECONFIG_FILE: ${yamlConf.HOST_KUBECONFIG_FILE}")
        yamlConf.K3SNODES.forEach { echo(it) }
        echo("---")
        echo(yamlConf)
        echo("---")
        echo(yaml.encodeToString(YamlConf.serializer(), yamlConf))

        log.info("read conf/${CONF.targetEnv}/conf.yml")
    }
}
