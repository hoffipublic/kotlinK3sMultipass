package com.hoffi.common.helpers.os

enum class OSType {
    Windows, MacOS, Linux, Other;

    companion object {
        var DETECTED: OSType = Other

        init {
            val OS = System.getProperty("os.name", "generic").lowercase()
            if (OS.contains("mac") || OS.contains("darwin")) {
                DETECTED = MacOS
            } else if (OS.contains("win")) {
                DETECTED = Windows
            } else if (OS.contains("nux")) {
                DETECTED = Linux
            } else {
                DETECTED = Other
            }
        }
    }
}
