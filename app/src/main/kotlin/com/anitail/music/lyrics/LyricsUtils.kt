package com.anitail.music.lyrics

import android.text.format.DateUtils
import com.anitail.music.ui.component.ANIMATE_SCROLL_DURATION

@Suppress("RegExpRedundantEscape")
object LyricsUtils {
    val LINE_REGEX = "((\\[\\d\\d:\\d\\d\\.\\d{2,3}\\] ?)+)(.+)".toRegex()
    val TIME_REGEX = "\\[(\\d\\d):(\\d\\d)\\.(\\d{2,3})\\]".toRegex()

    fun parseLyrics(lyrics: String): List<LyricsEntry> =
        lyrics
            .lines()
            .flatMap { line ->
                parseLine(line).orEmpty()
            }.sorted()

    private fun parseLine(line: String): List<LyricsEntry>? {
        if (line.isEmpty()) {
            return null
        }
        val matchResult = LINE_REGEX.matchEntire(line.trim()) ?: return null
        val times = matchResult.groupValues[1]
        val text = matchResult.groupValues[3]
        val timeMatchResults = TIME_REGEX.findAll(times)

        return timeMatchResults
            .map { timeMatchResult ->
                val min = timeMatchResult.groupValues[1].toLong()
                val sec = timeMatchResult.groupValues[2].toLong()
                val milString = timeMatchResult.groupValues[3]
                var mil = milString.toLong()
                if (milString.length == 2) {
                    mil *= 10
                }
                val time = min * DateUtils.MINUTE_IN_MILLIS + sec * DateUtils.SECOND_IN_MILLIS + mil
                LyricsEntry(time, text)
            }.toList()
    }

    fun findCurrentLineIndex(
        lines: List<LyricsEntry>,
        position: Long,
    ): Int {
        for (index in lines.indices) {
            if (lines[index].time >= position + ANIMATE_SCROLL_DURATION) {
                return index - 1
            }
        }
        return lines.lastIndex
    }
    
    /**
     * Calculate the progress through the current line (for karaoke animations)
     * @return a value between 0.0 and 1.0 representing progress through current line
     */
    fun calculateLineProgress(
        lines: List<LyricsEntry>,
        currentLineIndex: Int,
        position: Long
    ): Float {
        if (currentLineIndex < 0 || currentLineIndex >= lines.size - 1) {
            return 1.0f
        }
        
        val currentLineTime = lines[currentLineIndex].time
        val nextLineTime = lines[currentLineIndex + 1].time
        val duration = nextLineTime - currentLineTime
        
        if (duration <= 0) return 1.0f
        
        val elapsed = position - currentLineTime
        return (elapsed.toFloat() / duration).coerceIn(0.0f, 1.0f)
    }
}
