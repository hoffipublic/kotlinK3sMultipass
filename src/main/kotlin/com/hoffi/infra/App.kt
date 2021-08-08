package com.hoffi.infra

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.output.TermUi.echo
import com.hoffi.common.log.LoggerDelegate
import com.hoffi.conf.yaml.YamlConf
import com.hoffi.infra.hetzner.Hetzner
import com.hoffi.infra.local.Local
import com.hoffi.infra.local.k3s.BootstrapK3s
import com.hoffi.infra.local.k3s.ConfigureK3s
import com.hoffi.infra.local.multipass.BootstrapInfra
import com.hoffi.infra.local.scratch.Scratch
import com.hoffi.infra.local.scratch.Scratch2
import com.hoffi.yaml.YAML.callYttFiles
import koodies.exec.output
import koodies.exec.successful
import koodies.shell.ShellScript
import koodies.text.ANSI.ansiRemoved
import koodies.text.capitalize
import net.mamoe.yamlkt.Yaml
import java.io.File

enum class TargetEnvs { local, hetzner, scratch }

val subCommands = listOf(
    Local(),
    Hetzner(),
    BootstrapInfra(),
    BootstrapK3s(),
    ConfigureK3s(),
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
            "configure-k3s",
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
    echo("targetEnv: " + targetEnv.name)
    val (hostIp, hostNic) = hostIpAndNic()

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
        IP_HOST: ${hostIp}
        NIC_HOST: ${hostNic}
        
    """.trimIndent())
    yttConfTemplate.appendText(strippedYttFromOrigContents)
    val yttConfDataValues = File(GENDIRCONFytt, "yttConfData.yml")
    yttConfDataValues.writeText("""
        #@data/values
        ---
        HOME: ${System.getenv("HOME")}
        targetEnv: ${targetEnv.name}
        TargetENV: ${targetEnv.name.capitalize()}
        IP_HOST: ${hostIp}
        NIC_HOST: ${hostNic}
        
    """.trimIndent())
    strippedYttFromOrigContents = strippedYttFromOrigContents.replace(Regex("^(.+?): #@.*$", RegexOption.MULTILINE), "$1: \"ytt templating removed\"")
    yttConfDataValues.appendText(strippedYttFromOrigContents)

    val yttOut = callYttFiles(name = "${GENDIRCONF}/conf.yml", listOf(yttConfDataValues, yttConfTemplate), includeConf = false)
    val yttOutFilename = "${GENDIRCONF}/conf.yml"
    val yttOutFile = File(yttOutFilename)
//    yttOutFile.writeText("""
//        #! from conf/${targetEnv.name}/conf.yml
//        #@overlay/match-child-defaults missing_ok=True
//        #@data/values
//        ---
//
//        """.trimIndent())
//    yttOutFile.appendText(yttOut)
    yttOutFile.writeText(yttOut)

    // the "real" CONF: YamlConf that is used throughout this project
    CONF = Yaml.decodeFromString(YamlConf.serializer(), yttOutFile.readText())
    CONF.targetEnv = targetEnv.name
    CONF.TargetENV = targetEnv.name.capitalize()
    CONF.DIR.GENDIR = GENDIR
    // as yaml deserialize above cannot deal with ---
    yttOutFile.writeText("""
            #! from conf/${targetEnv.name}/conf.yml
            #@overlay/match-child-defaults missing_ok=True
            #@data/values
            ---
            
            """.trimIndent())
    yttOutFile.appendText(yttOut)

    // for dirs that are in CONF we make sure they exist
    val TMPDIR = File("tmp", targetEnv.name)       ; TMPDIR.mkdirs()        ; CONF.DIR.TMPDIR = TMPDIR

    echo(Yaml { encodeDefaultValues = true }.encodeToString(YamlConf.serializer(), CONF))
}

fun hostIpAndNic(): List<String> {
    val shellResult = ShellScript(name = "getting host's IP and NIC") { """
        set -Eeuo pipefail
        case $(uname) in
            'Linux')
            HOSTNIC=$(route | grep '^default' | grep -o '[^ ]*$')
            HOSTIP=$(ifconfig "${'$'}HOSTNIC}" | sed -n -E 's/^.*inet ([0-9.]+).*$/\1/p')
            ;;
            'FreeBSD')
            HOSTNIC=$(route | grep '^default' | grep -o '[^ ]*$')
            HOSTIP=$(ifconfig "${'$'}{HOSTNIC}" | sed -n -E 's/^.*inet ([0-9.]+).*$/\1/p')
            ;;
            'WindowsNT')
            HOSTNIC=unknown
            HOSTIP=unknown
            ;;
            'Darwin')
            HOSTNIC=$(route get example.com | sed -n -E 's/^ *interface: (.*)$/\1/p')
            HOSTIP=$(ifconfig "${'$'}{HOSTNIC}" | sed -n -E 's/^.*inet ([0-9.]+).*$/\1/p')
            ;;
            *) ;;
        esac
        printf "%s,%s" ${'$'}HOSTIP ${'$'}HOSTNIC
        """.trimIndent()
    }.exec.invoke()
    if ( shellResult.successful ) {
        return shellResult.output.ansiRemoved.split(',')
    } else {
        throw Exception("failed to get hostIpAndNic()")
    }
}
