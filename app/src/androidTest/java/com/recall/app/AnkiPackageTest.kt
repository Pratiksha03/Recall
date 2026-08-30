package com.recall.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.luben.zstd.ZstdOutputStream
import com.recall.app.data.AnkiPackage
import com.recall.app.data.AnswerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Runs on a device or emulator: ./gradlew connectedDebugAndroidTest
 *
 * There is no way to test the package importer on the JVM — unzipping is the easy
 * part, but zstd is a native library and the collection is a real SQLite database.
 * So these build actual .colpkg/.apkg files, in both of the layouts Anki produces,
 * and read them back through the same entry point the screen uses.
 */
@RunWith(AndroidJUnit4::class)
class AnkiPackageTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val sep = '\u001F'

    @Test
    fun modernColpkgWithZstdCompressedCollection() {
        val db = collection(newSchema = true) { db ->
            db.execSQL("INSERT INTO decks VALUES (1, 'Default')")
            db.execSQL("INSERT INTO decks VALUES (2, 'Spanish${sep}Verbs')")
            note(db, id = 1, deck = 2, flds = "hablar${sep}to speak")
            note(db, id = 2, deck = 2, flds = "comer${sep}<b>to eat</b><br>(regular)")
        }
        val pkg = zip("modern.colpkg") { zip ->
            entry(zip, "meta", byteArrayOf(2, 0))
            zstdEntry(zip, "collection.anki21b", db.readBytes())
            entry(zip, "0", "not really a picture".toByteArray())
        }

        val result = AnkiPackage.read(context, Uri.fromFile(pkg), "modern.colpkg")
        assertNotNull("a .colpkg must be recognised as a package", result)
        assertEquals(result!!.warnings.toString(), 2, result.cards.size)
        assertEquals("hablar", result.cards[0].question)
        assertEquals("to speak", result.cards[0].answer)
        assertEquals("to eat\n(regular)", result.cards[1].answer)
        assertEquals("Spanish::Verbs", result.deckName)
        assertEquals("modern.colpkg", result.source)
    }

    @Test
    fun legacyApkgWithPlainCollectionAndDecksAsJson() {
        val db = collection(newSchema = false) { db ->
            db.execSQL(
                "INSERT INTO col VALUES (1, ?)",
                arrayOf("""{"1":{"name":"Default"},"5":{"name":"Kanji"}}""")
            )
            note(db, id = 1, deck = 5, flds = "&#26085;${sep}sun, day", tags = " jlpt n5 ")
        }
        val pkg = zip("legacy.apkg") { zip ->
            entry(zip, "collection.anki2", db.readBytes())
            entry(zip, "media", "{}".toByteArray())
        }

        val result = AnkiPackage.read(context, Uri.fromFile(pkg), "legacy.apkg")!!
        assertEquals(result.warnings.toString(), 1, result.cards.size)
        assertEquals("日", result.cards[0].question)
        assertEquals("jlpt n5", result.cards[0].tags)
        assertEquals("Kanji", result.deckName)
    }

    /** The awkward notes: cloze, media, a code answer, and one with nothing on the back. */
    @Test
    fun clozeMediaAndEmptyBacksAreHandledNotDropped() {
        val db = collection(newSchema = true) { db ->
            db.execSQL("INSERT INTO decks VALUES (1, 'Mixed')")
            note(db, id = 1, deck = 1, flds = "Paris is in {{c1::France}}$sep")
            note(db, id = 2, deck = 1, flds = "Hear it[sound:a.mp3]${sep}bonjour")
            note(db, id = 3, deck = 1, flds = "Reverse a list$sep<pre>xs.reversed()</pre>")
            note(db, id = 4, deck = 1, flds = "no answer here$sep$sep")
            note(db, id = 5, deck = 1, flds = "A cat<img src=\"cat.jpg\">${sep}gato")
        }
        val pkg = zip("mixed.colpkg") { zip ->
            zstdEntry(zip, "collection.anki21b", db.readBytes())
        }

        val result = AnkiPackage.read(context, Uri.fromFile(pkg), "mixed.colpkg")!!
        val byQuestion = result.cards.associateBy { it.question }

        assertEquals(4, result.cards.size)
        assertEquals("France", byQuestion["Paris is in [...]"]?.answer)
        assertEquals("bonjour", byQuestion["Hear it"]?.answer)
        assertEquals(AnswerType.CODE, byQuestion["Reverse a list"]?.answerType)
        assertEquals("gato", byQuestion["A cat"]?.answer)
        assertEquals(1, result.skipped)
        assertTrue(
            result.warnings.toString(),
            result.warnings.any { it.contains("referenced images") }
        )
    }

    @Test
    fun aTextFileIsNotAPackage() {
        val txt = File(context.cacheDir, "notes.txt").apply {
            writeText("#separator:tab\nfront\tback\n")
        }
        assertNull(AnkiPackage.read(context, Uri.fromFile(txt), "notes.txt"))
    }

    @Test
    fun aZipWithoutACollectionSaysSoRatherThanCrashing() {
        val pkg = zip("random.zip") { zip -> entry(zip, "hello.txt", "hi".toByteArray()) }
        val result = AnkiPackage.read(context, Uri.fromFile(pkg), "random.zip")!!
        assertTrue(result.isEmpty)
        assertTrue(result.warnings.first().contains("No Anki collection"))
    }

    // --- fixtures ---

    /**
     * A minimal collection in either schema: the modern one has a `decks` table, the
     * old one keeps deck names as JSON in the single `col` row. Only the columns this
     * importer actually reads are here.
     */
    private fun collection(newSchema: Boolean, fill: (SQLiteDatabase) -> Unit): File {
        val file = File(context.cacheDir, "fixture-${System.nanoTime()}.anki2")
        file.delete()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.execSQL("CREATE TABLE col (id INTEGER PRIMARY KEY, decks TEXT)")
        db.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY, guid TEXT, mid INTEGER, tags TEXT, flds TEXT)")
        db.execSQL("CREATE TABLE cards (id INTEGER PRIMARY KEY, nid INTEGER, did INTEGER, ord INTEGER, odid INTEGER)")
        if (newSchema) db.execSQL("CREATE TABLE decks (id INTEGER PRIMARY KEY, name TEXT)")
        fill(db)
        db.close()
        return file
    }

    private fun note(db: SQLiteDatabase, id: Long, deck: Long, flds: String, tags: String = "") {
        db.execSQL("INSERT INTO notes VALUES (?, ?, 1, ?, ?)", arrayOf(id, "guid$id", tags, flds))
        db.execSQL("INSERT INTO cards VALUES (?, ?, ?, 0, 0)", arrayOf(id * 10, id, deck))
    }

    private fun zip(name: String, write: (ZipOutputStream) -> Unit): File {
        val file = File(context.cacheDir, name)
        ZipOutputStream(file.outputStream()).use(write)
        return file
    }

    private fun entry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    /** How Anki 2.1.50+ writes the collection: zstd inside the zip entry. */
    private fun zstdEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        val compressed = java.io.ByteArrayOutputStream()
        ZstdOutputStream(compressed).use { it.write(bytes) }
        zip.write(compressed.toByteArray())
        zip.closeEntry()
    }
}
