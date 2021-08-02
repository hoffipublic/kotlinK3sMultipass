package com.hoffi.infra

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.output.TermUi.echo
import com.hoffi.common.log.LoggerDelegate
import com.hoffi.conf.yaml.YamlConf
import com.hoffi.infra.hetzner.Hetzner
import com.hoffi.infra.local.Local
import com.hoffi.infra.local.k3s.BootstrapK3s
import com.hoffi.infra.local.multipass.BootstrapInfra
import com.hoffi.infra.local.scratch.Scratch
import com.hoffi.infra.local.scratch.Scratch2
import koodies.exec.successful
import koodies.shell.ShellScript
import koodies.text.capitalize
import net.mamoe.yamlkt.Yaml
import java.io.File

enum class TargetEnvs { local, hetzner, scratch }

val subCommands = listOf(
    Local(),
    Hetzner(),
    BootstrapInfra(),
    BootstrapK3s(),
    Scratch(),
    Scratch2(),
)

var CONF = YamlConf() // dummy YamlConf ... real one generated in init()

fun main(args: Array<String>) {
    echo("originalArgs: ${args.joinToString("', '", "'", "'")}")
    if(args.isNotEmpty()) {
        val targetEnv = TargetEnvs.valueOf(args[0]) // assert that first arg is a valid TargetEnv
        init(targetEnv)
        App().subcommands(subCommands).main(args)

    } else {
        // default on no args
        init(TargetEnvs.local)
        App().subcommands(subCommands).main(arrayOf(
            "local",
            "bootstrap-infra",
            "bootstrap-k3s",
            "--"
        ))
    }
}

class App: CliktCommand(allowMultipleSubcommands = true, help = """
    bootstrap a VM cluster with Rancher's K3s Kubernetes, configure K8s and deploy some apps to it    
""".trimIndent())
{
    private val log by LoggerDelegate()

    override fun aliases(): Map<String, List<String>> = mapOf(
        "infra" to listOf("bootstrap-infra"),
        "k3s" to listOf("bootstrap-k3s"),
        "s" to listOf("scratch"),
    )

    override fun run() {
        log.info("App.run()")
    }
}

fun init(targetEnv: TargetEnvs) {
    val GENDIR = File("generated", targetEnv.name) ; GENDIR.mkdirs()
    val GENDIRCONF = File(GENDIR, "conf")           ; GENDIRCONF.mkdirs()
    val GENDIRCONFytt = File(GENDIRCONF, "ytt")     ; GENDIRCONFytt.mkdirs()

    // ytt templating conf.yml with itself (but remove all templated values from #@data/values
    val confFileOrig = File("conf/${targetEnv.name}/conf.yml")
    val confFileOrigContents = confFileOrig.readText()
    var strippedYttFromOrigContents = confFileOrigContents.replace(Regex("^(.*?)#!.*$", RegexOption.MULTILINE), "$1")

    val yttConfTemplate = File(GENDIRCONFytt, "yttConfTemplate.yml")
    yttConfTemplate.writeText("""
        #@ load("@ytt:data", "data")

        ---
        HOME: ${System.getenv("HOME")}
        targetEnv: ${targetEnv.name}
        TargetENV: ${targetEnv.name.capitalize()}
        
    """.trimIndent())
    yttConfTemplate.appendText(strippedYttFromOrigContents)
    val yttConfDataValues = File(GENDIRCONFytt, "yttConfData.yml")
    yttConfDataValues.writeText("""
        #@data/values
        ---
        HOME: ${System.getenv("HOME")}
        targetEnv: ${targetEnv.name}
        TargetENV: ${targetEnv.name.capitalize()}
        
    """.trimIndent())
    strippedYttFromOrigContents = strippedYttFromOrigContents.replace(Regex("^(.+?): #@.*$", RegexOption.MULTILINE), "$1: \"ytt templating removed\"")
    yttConfDataValues.appendText(strippedYttFromOrigContents)

    val yttCmd = "ytt -f ${yttConfDataValues} -f ${yttConfTemplate}"
    val yttCmdOut = "${GENDIRCONF}/conf.yml"
    // val shellResult = CommandLine("ytt", "-f", "${yttConfDataValues}", "-f", "${yttConfTemplate}").exec.logging()
    val shellResult = ShellScript(name = "generating './${yttCmdOut}' with '${yttCmd}'") {
        yttCmd
    }.exec.logging()
    if ( shellResult.successful ) {
        File(yttCmdOut).writeText(shellResult.io.toString() + "\n")
    } else {
        throw Exception("failed to generate ${GENDIRCONF}/conf.yml}")
    }

    // the "real" CONF: YamlConf that is used throughout this project
    CONF = Yaml.decodeFromString(YamlConf.serializer(), File(yttCmdOut).readText())
    CONF.targetEnv = targetEnv.name
    CONF.TargetENV = targetEnv.name.capitalize()
    CONF.DIR.GENDIR = GENDIR
    // for dirs that are in CONF we make sure they exist
    val TMPDIR = File("tmp", targetEnv.name)       ; TMPDIR.mkdirs()        ; CONF.DIR.TMPDIR = TMPDIR
}
