package com.recall.app

import com.recall.app.data.AnkiImport
import com.recall.app.data.AnswerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs on the JVM, no emulator needed: ./gradlew test
 * These are the cases that actually appear in real Anki exports.
 */
class AnkiImportTest {

    @Test
    fun `plain tab separated pair`() {
        val r = AnkiImport.parse("What is 2+2?\t4")
        assertEquals(1, r.cards.size)
        assertEquals("What is 2+2?", r.cards[0].question)
        assertEquals("4", r.cards[0].answer)
    }

    @Test
    fun `reads anki headers`() {
        val r = AnkiImport.parse(
            "#separator:tab\n#html:false\n#deck:Spanish\n#tags:vocab\nhello\thola\n"
        )
        assertEquals("Spanish", r.deckName)
        assertEquals(1, r.cards.size)
        assertTrue(r.cards[0].tags.contains("vocab"))
    }

    @Test
    fun `quoted field containing the separator and a newline`() {
        val r = AnkiImport.parse("\"a, question\",\"line one\nline two\"")
        assertEquals(1, r.cards.size)
        assertEquals("a, question", r.cards[0].question)
        assertEquals("line one\nline two", r.cards[0].answer)
    }

    @Test
    fun `doubled quotes mean a literal quote`() {
        val r = AnkiImport.parse("\"say \"\"hi\"\"\"\tgreeting")
        assertEquals("say \"hi\"", r.cards[0].question)
    }

    @Test
    fun `html is converted to text`() {
        val r = AnkiImport.parse("#html:true\nQ&amp;A\tone<br>two &lt;tag&gt;")
        assertEquals("Q&A", r.cards[0].question)
        assertEquals("one\ntwo <tag>", r.cards[0].answer)
    }

    @Test
    fun `ampersand entity is decoded last`() {
        // &amp;lt; must become "&lt;", not "<"
        assertEquals("&lt;", AnkiImport.htmlToText("&amp;lt;"))
    }

    @Test
    fun `pre or code becomes a CODE card`() {
        val r = AnkiImport.parse("#html:true\nHow to loop?\t<pre>for (i in 1..3)</pre>")
        assertEquals(AnswerType.CODE, r.cards[0].answerType)
    }

    @Test
    fun `a bare url becomes a LINK card`() {
        val r = AnkiImport.parse("Docs?\thttps://developer.android.com")
        assertEquals(AnswerType.LINK, r.cards[0].answerType)
    }

    @Test
    fun `separator is guessed when no header says`() {
        val r = AnkiImport.parse("a;b\nc;d")
        assertEquals("semicolon", r.separatorName)
        assertEquals(2, r.cards.size)
    }

    @Test
    fun `comment lines and blank lines are ignored`() {
        val r = AnkiImport.parse("a\tb\n\n# a comment\nc\td\n")
        assertEquals(2, r.cards.size)
    }

    @Test
    fun `incomplete rows are skipped and counted not fatal`() {
        val r = AnkiImport.parse("good\tcard\nlonely\ngood2\tcard2")
        assertEquals(2, r.cards.size)
        assertEquals(1, r.skipped)
    }

    @Test
    fun `empty input is handled`() {
        assertTrue(AnkiImport.parse("").isEmpty)
        assertTrue(AnkiImport.parse("   \n  ").isEmpty)
    }

    @Test
    fun `extra columns beyond front and back are not fatal`() {
        val r = AnkiImport.parse("front\tback\ttag1 tag2\tguid123")
        assertEquals(1, r.cards.size)
        assertEquals("back", r.cards[0].answer)
    }

    @Test
    fun `entities do not fool the separator guess`() {
        // One real tab, three semicolons from HTML entities. Must still split on tab.
        val r = AnkiImport.parse("Q&amp;A\tone &lt;x&gt; two")
        assertEquals("tab", r.separatorName)
        assertEquals(1, r.cards.size)
        assertEquals("Q&A", r.cards[0].question)
    }
}
