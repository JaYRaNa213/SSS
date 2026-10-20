package com.sss.app.domain.ocr

enum class TextBlockType {
    HEADING,
    PARAGRAPH
}

data class ClassifiedLine(
    val text: String,
    val type: TextBlockType
)

object HeadingDetector {

    private val numberedHeading =
        Regex("""^\d+[.)]\s+\S.{0,80}$""")

    private val chapterHeading =
        Regex(
            """^(CHAPTER|UNIT|MODULE|SECTION|PART)\s+[\wIVXLCDM-]+.*$""",
            RegexOption.IGNORE_CASE
        )

    fun classify(text: String): List<ClassifiedLine> {
        return text
            .lines()
            .mapNotNull { rawLine ->
                val line = rawLine.trim()
                if (line.isBlank()) {
                    return@mapNotNull null
                }

                val isHeading =
                    numberedHeading.matches(line) ||
                            chapterHeading.matches(line) ||
                            (
                                    line.length in 3..55 &&
                                            line.endsWith(":")
                                    )

                ClassifiedLine(
                    text = line,
                    type = if (isHeading) {
                        TextBlockType.HEADING
                    } else {
                        TextBlockType.PARAGRAPH
                    }
                )
            }
    }
}
