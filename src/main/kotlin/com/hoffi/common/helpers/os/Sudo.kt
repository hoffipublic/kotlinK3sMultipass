package com.hoffi.common.helpers.os

import java.io.BufferedReader
import java.io.InputStreamReader


object Sudo {
    /** only for single line results */
    fun check(): Boolean {
        val pb = ProcessBuilder("id", "-u")
        pb.redirectErrorStream(true)
        var output: String = ""
        val process = pb.start()
        val inputStream =  BufferedReader(InputStreamReader(process.getInputStream()))
        @Suppress("ControlFlowWithEmptyBody")
        while ( inputStream.readLine()?.also { output = it } != null) {

        }
        inputStream.close()
        process.waitFor()
        if (output == "0") {
            return true
        }
        return false
    }
}
