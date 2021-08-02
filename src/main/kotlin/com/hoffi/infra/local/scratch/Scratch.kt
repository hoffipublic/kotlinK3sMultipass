package com.hoffi.infra.local.scratch

import com.github.ajalt.clikt.core.CliktCommand
import com.hoffi.common.log.LoggerDelegate
import koodies.exec.CommandLine
import koodies.shell.ShellScript
import koodies.text.styling.wrapWithBorder
import koodies.time.Now
import koodies.tracing.Key
import koodies.tracing.spanning

class Scratch: CliktCommand() {
    private val log by LoggerDelegate()
    //val greetings: List<String> by argument().multiple()

    override fun run() {
//        if (greetings.isNotEmpty()) {
//            log.info("should have a greeting from ${greetings.joinToString(separator = "', '", prefix = "'", postfix = "'")}")
//        } else {
            log.info("should have a greeting.")
//        }

        spanning("span name") {
            event("test event", Key.stringKey("test attribute") to "test value")
            log("description") // = event("log", RenderingAttributes.DESCRIPTION to description)
            log.info("description") // = event("log", RenderingAttributes.DESCRIPTION to description)

            ShellScript { "printenv | grep HOME" }
                .exec.logging() // .exec.processing { io -> … }
            CommandLine

            Thread.sleep(5_000)
            42 // = return value
        }

        print(Now.emoji)

        println("some message".wrapWithBorder())

    }
}
