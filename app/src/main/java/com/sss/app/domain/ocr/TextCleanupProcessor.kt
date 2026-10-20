package com.sss.app.domain.ocr

object TextCleanupProcessor {

    fun clean(text: String): String {
        return text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
            .joinToString("\n") { line ->
                line.trimEnd()
            }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}
