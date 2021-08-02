package com.hoffi.yaml

import com.hoffi.infra.CONF
import koodies.exec.successful
import koodies.shell.ShellScript
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
}
