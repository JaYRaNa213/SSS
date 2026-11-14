package com.sss.app

import com.sss.app.data.ocr.OcrLine
import com.sss.app.data.ocr.OcrPage
import com.sss.app.domain.ocr.CleanupLevel
import com.sss.app.domain.ocr.Ignored
import com.sss.app.domain.ocr.NoteBuilder
import com.sss.app.domain.ocr.NoteFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteBuilderTest {

    private fun line(text: String, top: Int, h: Int, left: Int = 100, right: Int = 900, block: Int = 0) =
        OcrLine(text, left, top, right, top + h, block)

    private fun page(vararg l: OcrLine) = OcrPage(0, 1000, 2000, l.toList())

    @Test fun clockIsIgnoredTitleIsHeadingAndWrappedLinesMerge() {
        val p = page(
            line("11:57", 40, 40, 500, 600, 0),
            line("Computer Networks", 300, 80, 100, 700, 1),
            line("A network is a group of connected devices that", 420, 40, 100, 900, 2),
            line("share data and resources.", 470, 40, 100, 500, 2)
        )
        val (main, ignored) = NoteFormat.split(NoteBuilder.build(listOf(p), CleanupLevel.SMART))
        assertTrue(main.contains("# Computer Networks"))
        assertTrue(main.contains("A network is a group of connected devices that share data and resources."))
        assertFalse(main.contains("11:57"))
        assertEquals(listOf("11:57"), ignored.map { it.text })
    }

    @Test fun textRepeatedOnMostScreenshotsIsTreatedAsWatermark() {
        val pages = listOf("Alpha content line", "Beta content line", "Gamma content line").map { t ->
            page(line(t, 400, 40, block = 0), line("ExamPrep Live", 900, 40, block = 1))
        }
        val (main, ignored) = NoteFormat.split(NoteBuilder.build(pages, CleanupLevel.SMART))
        assertFalse(main.contains("ExamPrep"))
        assertEquals(listOf(1, 2, 3), ignored.map { it.page })
    }

    @Test fun bulletsBecomeAList() {
        val p = page(line("\u2022 First point", 400, 40, block = 0), line("\u2022 Second point", 460, 40, block = 1))
        val (main, _) = NoteFormat.split(NoteBuilder.build(listOf(p), CleanupLevel.SMART))
        assertTrue(main.contains("- First point\n- Second point"))
    }

    @Test fun levelOffKeepsEverything() {
        val p = page(line("11:57", 40, 40, 500, 600, 0), line("Some body text here", 400, 40, block = 1))
        val (main, ignored) = NoteFormat.split(NoteBuilder.build(listOf(p), CleanupLevel.OFF))
        assertTrue(main.contains("11:57"))
        assertTrue(ignored.isEmpty())
    }

    @Test fun addBackPutsTheLineInsideItsScreenshotSection() {
        val content = NoteFormat.join(
            "--- Screenshot 1 ---\n\nHello\n\n--- Screenshot 2 ---\n\nWorld",
            listOf(Ignored(1, "Sign up"))
        )
        val (main, ignored) = NoteFormat.split(NoteFormat.restore(content, Ignored(1, "Sign up")))
        assertTrue(main.contains("Hello\n\nSign up\n\n--- Screenshot 2 ---"))
        assertTrue(ignored.isEmpty())
    }
}