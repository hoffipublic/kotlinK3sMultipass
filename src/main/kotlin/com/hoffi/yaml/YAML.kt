package com.hoffi.yaml

import com.hoffi.infra.CONF
import koodies.exec.successful
import koodies.shell.ShellScript
import net.mamoe.yamlkt.Yaml
import java.io.File

object YAML {

    // val shellResult = CommandLine("ytt", "-f", "${yttConfDataValues}", "-f", "${yttConfTemplate}").exec.logging()
    fun callYttFiles(name: String, yttFiles: List<File>, yttOpts: String = "", includeConf: Boolean = true)
            = callYtt(name, yttFiles.map { it.toString() }, yttOpts, includeConf)
    fun callYtt(name: String, yttFilenames: List<String>, yttOps: String = "", includeConf: Boolean = true): String {
        val theFilenames = if (includeConf) {
            mutableListOf("${CONF.DIR.GENDIR}/conf/conf.yml")
        } else {
            mutableListOf()
        }
        theFilenames.addAll(yttFilenames)
        val yttCmd = "ytt ${theFilenames.joinToString(" -f ", "-f ")} $yttOps"
        val shellResult = ShellScript(name = "ytt $name: $yttCmd") {
            yttCmd
        }.exec.logging()
        if ( shellResult.successful ) {
            return shellResult.io.toString() + "\n"
        } else {
            throw Exception("failed '${name}':  $yttCmd")
        }
    }

    data class LinuxPackage(val name: String, val versionCmd: String)
    fun linuxPackages(): Map<String, List<LinuxPackage>> {
        val yamlConf = Yaml.decodeMapFromString(File(CONF.DIR.CONFDIR, "common/linuxPackages.yml").readText())

        val theMap = mutableMapOf<String, List<LinuxPackage>>()
        yamlConf.forEach { k, v ->
            @Suppress("UNCHECKED_CAST")
            val packages = v as List<Map<*, *>>
            theMap[k.toString()] = packages.map { LinuxPackage(it["name"].toString(), it["versionCmd"].toString()) }
        }
        return theMap
    }
}
