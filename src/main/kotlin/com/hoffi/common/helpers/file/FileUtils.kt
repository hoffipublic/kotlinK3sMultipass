package com.hoffi.common.helpers.file

import com.github.ajalt.clikt.output.TermUi.echo
import com.hoffi.infra.CONF
import koodies.exec.CommandLine
import koodies.io.path.appendLines
import koodies.io.path.copyTo
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

object FileUtils {
    val START_SENTINEL = "#BEGIN MARKER used for automation, DO NOT CHANGE this comment line"
    val END_SENTINEL = "#ENDIN MARKER used for automation, DO NOT CHANGE this comment line"

    fun mkdir(dirpath: String) = mkdirs(dirpath).first()
    fun mkdir(dirpath: File) = mkdirs(dirpath).first()
    fun mkdir(dirpath: Path) = mkdirs(dirpath).first()
    fun mkdirs(vararg dirpaths: String) = mkdirs(*(dirpaths.map { Paths.get(it) }.toTypedArray()))
    fun mkdirs(vararg dirfiles: File) = mkdirs(*(dirfiles.map { it.toPath() }.toTypedArray()))
    fun mkdirs(vararg dirpaths: Path): Array<out Path> {
        dirpaths.forEach { it.toFile().mkdirs() }
        return dirpaths
    }

    fun getBetween(origFilepath: Path, startSentinel: String, endSentinel: String): MutableList<String> {
        val lines = mutableListOf<String>()
        var betweenSentinels = false
        origFilepath.toFile().forEachLine { line ->
            when {
                endSentinel == line -> {
                    betweenSentinels = false
                }
                betweenSentinels -> {
                    lines.add(line)
                }
                startSentinel == line -> {
                    betweenSentinels = true
                }
            }
        }
        return lines
    }
    fun findBetween(origFilepath: Path, startSentinel: String, endSentinel: String, matchEntireLine: Regex): MutableList<MatchResult> {
        val matchResults = mutableListOf<MatchResult>()
        var betweenSentinels = false
        origFilepath.toFile().forEachLine { line ->
            when {
                endSentinel == line -> {
                    betweenSentinels = false
                }
                betweenSentinels -> {
                    val matchResult: MatchResult? = matchEntireLine.matchEntire(line)
                    if (matchResult != null) {
                        matchResults.add(matchResult)
                    }
                }
                startSentinel == line -> {
                    betweenSentinels = true
                }
            }
        }
        return matchResults
    }

    fun replaceBetween(origFilepath: Path, startSentinel: String, endSentinel: String, keepOriginal: Boolean = true, needsSudo: Boolean = false, replaceWith: (String) -> String) {
        val listOfSudoCmds = mutableListOf<String>()
        if (keepOriginal) {
            if (needsSudo) {
                listOfSudoCmds.add("cp -f \"${origFilepath.toAbsolutePath()}\" \"${origFilepath.toAbsolutePath()}.orig\"")
            } else {
                CommandLine("cp", "-f", "${origFilepath}", "${origFilepath}.orig").exec()
            }
        }
        val tmpTmpDir = FileUtils.mkdir("${CONF.DIR.TMPDIR}/tmp")
        val intermediateFile = Path.of("${tmpTmpDir}", "${origFilepath.fileName}.intermediate")
        //// maybe needs sudo
        //val whoami = CommandLine("whoami").exec().output
        //val userGroup = if(OSType.DETECTED == OSType.MacOS) {
        //    CommandLine("gstat", "--format", "%U:%G", "${origFilepath}").exec().output
        //} else {
        //    CommandLine("stat", "--format", "%U:%G", "${origFilepath}").exec().output
        //}
        var betweenSentinels = false
        var startSentinelFound = false
        var endSentinelFound = false
        intermediateFile.toFile().printWriter().use { writer ->
            origFilepath.toFile().forEachLine { line ->
                writer.print(when {
                    endSentinel == line -> {
                        endSentinelFound = true
                        betweenSentinels = false
                        line + "\n"
                    }
                    betweenSentinels -> {
                        replaceWith(line) + "\n"
                    }
                    startSentinel == line -> {
                        startSentinelFound = true
                        betweenSentinels = true
                        line + "\n"
                    }
                    else -> {
                        line + "\n"
                    }
                })
            }
        }
        if ( (! startSentinelFound) && ( ! endSentinelFound) ) {
            intermediateFile.appendLines(startSentinel, endSentinel)
        } else if ( (startSentinelFound && (! endSentinelFound)) || (endSentinelFound && (! startSentinelFound)) ) {
            throw Exception("in ${origFilepath}: only found one of startSentinel and endSentinel    ")
        }
        if (needsSudo) {
            listOfSudoCmds.add("cat ${intermediateFile.toAbsolutePath()} > ${origFilepath.toAbsolutePath()}")
        } else {
            intermediateFile.copyTo(origFilepath, overwrite = true)
        }
        if (needsSudo) {
            echo("")
            echo("sudo rights needed, please execute the following command(s) manually...")
            echo("")
            echo("sudo bash -c '")
            listOfSudoCmds.forEach { echo("  ${it}") }
            echo("'")
            echo("")
            echo("if done that (in another shell!), press Enter key")
            //System.`in`.readAllBytes() // throw away already typed stuff
            readLine()
        }
        // check(file.delete() && tempFile.renameTo(file)) { "failed to replace file" }
    }

    fun replaceAllWith(origFilepath: Path, replaceWith: String, startSentinel: String, endSentinel: String, keepOriginal: Boolean = true, needsSudo: Boolean = false, additionalSudoCmds: List<String> = emptyList()) {
        val listOfSudoCmds = mutableListOf<String>()
        if (keepOriginal) {
            if (needsSudo) {
                listOfSudoCmds.add("cp -f \"${origFilepath.toAbsolutePath()}\" \"${origFilepath.toAbsolutePath()}.orig\"")
            } else {
                CommandLine("cp", "-f", "${origFilepath}", "${origFilepath}.orig").exec()
            }
        }
        val tmpTmpDir = FileUtils.mkdir("${CONF.DIR.TMPDIR}/tmp")
        val intermediateFile = Path.of("${tmpTmpDir}", "${origFilepath.fileName}.intermediate")
        //// maybe needs sudo
        //val whoami = CommandLine("whoami").exec().output
        //val userGroup = if(OSType.DETECTED == OSType.MacOS) {
        //    CommandLine("gstat", "--format", "%U:%G", "${origFilepath}").exec().output
        //} else {
        //    CommandLine("stat", "--format", "%U:%G", "${origFilepath}").exec().output
        //}
        var betweenSentinels = false
        var startSentinelFound = false
        var endSentinelFound = false
        intermediateFile.toFile().printWriter().use { writer ->
            origFilepath.toFile().forEachLine { line ->
                writer.print(when {
                    endSentinel == line -> {
                        endSentinelFound = true
                        betweenSentinels = false
                        line + "\n"
                    }
                    betweenSentinels -> {
                        ""
                    }
                    startSentinel == line -> {
                        startSentinelFound = true
                        betweenSentinels = true
                        line + "\n" + replaceWith + "\n"
                    }
                    else -> {
                        line + "\n"
                    }
                })
            }
        }
        if ( (! startSentinelFound) && ( ! endSentinelFound) ) {
            intermediateFile.appendLines(startSentinel, replaceWith, endSentinel)
        } else if ( (startSentinelFound && (! endSentinelFound)) || (endSentinelFound && (! startSentinelFound)) ) {
            throw Exception("in ${origFilepath}: only found one of startSentinel and endSentinel    ")
        }
        if (needsSudo) {
            listOfSudoCmds.add("cat ${intermediateFile.toAbsolutePath()} > ${origFilepath.toAbsolutePath()}")
        } else {
            intermediateFile.copyTo(origFilepath, overwrite = true)
        }
        if (needsSudo) {
            echo("")
            echo("sudo rights needed, please execute the following command(s) manually...")
            echo("")
            echo("sudo bash -c '")
            listOfSudoCmds.forEach { echo("  ${it}") }
            additionalSudoCmds.forEach { echo("  ${it}") }
            echo("'")
            echo("")
            echo("if done that (in another shell!), press Enter key")
            //System.`in`.readAllBytes() // throw away already typed stuff
            readLine()
        }
        // check(file.delete() && tempFile.renameTo(file)) { "failed to replace file" }
    }
}
