package com.recall.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.OpenableColumns
import com.github.luben.zstd.ZstdInputStream
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

/**
 * Reads Anki's *package* exports — `.colpkg` (a whole collection) and `.apkg` (one deck).
 *
 * Unlike a text export these are not text at all: a package is a ZIP holding Anki's
 * SQLite collection plus every media file, so reading one means unzipping, possibly
 * zstd-decompressing, and then querying a database.
 *
 *     export.colpkg
 *       |-- meta                    format version; not needed here
 *       |-- collection.anki21b      SQLite, zstd-compressed — Anki 2.1.50 and later
 *       |     or collection.anki21  SQLite, plain — older 2.1.x
 *       |     or collection.anki2   SQLite, plain — legacy exports
 *       |-- media                   an index of the files below
 *       `-- 0, 1, 2, ...            the media files themselves
 *
 * Two collection schemas turn up in the wild and both are handled: the old one keeps
 * decks as a JSON blob in the single `col` row, the new one has real `decks` tables.
 * What this needs from either is small — each note's fields and tags, and the name of
 * the deck its cards live in.
 *
 * A note becomes one card: field 1 is the question, the first non-empty field after it
 * is the answer. Cloze notes are unfolded into a fill-in-the-blank pair rather than
 * dropped, since otherwise a cloze-heavy deck would import as nothing at all.
 *
 * Media is *not* imported. The files are right there in the ZIP, but this app stores
 * its own copies keyed by path, so pulling them in is a separate job. Notes that
 * referenced an image or a sound still import, minus the reference, and are counted in
 * a warning so the loss is never silent.
 */
object AnkiPackage {

    /** Anki separates a note's fields with this control character inside `notes.flds`. */
    private const val FIELD_SEP = '\u001F'

    private val COLLECTION_NAMES =
        listOf("collection.anki21b", "collection.anki21", "collection.anki2")

    /** `[sound:foo.mp3]` — a media reference, meaningless once the file is gone. */
    private val SOUND = Regex("\\[sound:[^\\]]*\\]")

    // `{{c1::hidden::hint}}`, the hint optional. Every brace and bracket is escaped
    // because Android's regex engine is ICU, which rejects the bare `}` and `]` that
    // java.util.regex on the JVM quietly accepts as literals.
    /** Matches one cloze deletion. */
    private val CLOZE = Regex("\\{\\{c\\d+::(.*?)(?:::(.*?))?\\}\\}", RegexOption.DOT_MATCHES_ALL)

    private const val ZSTD_MISSING =
        "This package is compressed with zstd and the decompressor could not load on " +
            "this device. Re-export from Anki with \"Support older Anki versions\" ticked."

    /**
     * Reads a picked file if it is an Anki package; returns null if it is not one, so
     * the caller can fall back to treating it as text. Never throws — a package that
     * cannot be read comes back as an empty result carrying the reason why.
     */
    fun read(context: Context, uri: Uri, sourceName: String? = null): ImportResult? {
        val stream = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
            ?: return null
        val input = BufferedInputStream(stream)
        if (!startsWithZipHeader(input)) {
            runCatching { input.close() }
            return null
        }

        var collection: File? = null
        return try {
            collection = input.use { extractCollection(context, it) }
                ?: return failure(
                    "No Anki collection inside this file. It is a ZIP, but not a " +
                        ".colpkg or .apkg export.",
                    sourceName
                )
            readCollection(collection, sourceName)
        } catch (e: UnsupportedPackageException) {
            failure(e.message.orEmpty(), sourceName)
        } catch (e: Exception) {
            failure(
                "Could not read this package: ${e.message ?: e.javaClass.simpleName}",
                sourceName
            )
        } finally {
            collection?.delete()
        }
    }

    /** The display name of a picked file, so the screen can say which one is loaded. */
    fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    private class UnsupportedPackageException(message: String) : Exception(message)

    private fun failure(message: String, sourceName: String?) =
        ImportResult(warnings = listOf(message), source = sourceName)

    /** "PK" — every ZIP starts with it. Peeks without consuming. */
    private fun startsWithZipHeader(input: BufferedInputStream): Boolean {
        input.mark(4)
        val head = ByteArray(4)
        var read = 0
        while (read < 4) {
            val n = input.read(head, read, 4 - read)
            if (n <= 0) break
            read += n
        }
        input.reset()
        return read == 4 &&
            head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() &&
            head[2] == 3.toByte() && head[3] == 4.toByte()
    }

    /**
     * Streams the ZIP and writes out only the collection database.
     *
     * Streaming rather than copying the package first matters: a real collection export
     * is mostly media, and the collection comes before it, so this normally stops after
     * a few megabytes of a file that can be gigabytes.
     */
    private fun extractCollection(context: Context, input: InputStream): File? {
        val dir = File(context.cacheDir, "anki_import").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        val found = mutableMapOf<String, File>()

        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.substringAfterLast('/')
                if (name !in COLLECTION_NAMES) {
                    zip.closeEntry()
                    continue
                }
                val target = File(dir, name)
                target.outputStream().use { out ->
                    if (name.endsWith("b")) decompressZstd(zip, out) else zip.copyTo(out)
                }
                found[name] = target
                zip.closeEntry()
                // The newest format present wins, and nothing better can follow these two.
                if (name == "collection.anki21b" || name == "collection.anki21") break
            }
        }

        val best = COLLECTION_NAMES.firstNotNullOfOrNull { found[it] }
        found.values.forEach { if (it != best) it.delete() }
        return best
    }

    /** Anki 2.1.50 and later zstd-compress the collection inside the package. */
    private fun decompressZstd(input: InputStream, out: OutputStream) {
        try {
            ZstdInputStream(NonClosingStream(input)).use { it.copyTo(out) }
        } catch (e: NoClassDefFoundError) {
            throw UnsupportedPackageException(ZSTD_MISSING)
        } catch (e: UnsatisfiedLinkError) {
            throw UnsupportedPackageException(ZSTD_MISSING)
        }
    }

    /** ZstdInputStream closes what it wraps; the ZipInputStream has to survive that. */
    private class NonClosingStream(private val delegate: InputStream) : InputStream() {
        override fun read(): Int = delegate.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)
        override fun available(): Int = delegate.available()
        override fun close() = Unit
    }

    // --- the collection database ---

    private fun readCollection(file: File, sourceName: String?): ImportResult {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            if (!hasTable(db, "notes")) {
                return failure("That file is not an Anki collection — it has no notes.", sourceName)
            }

            val deckNames = deckNames(db)
            val cards = mutableListOf<ImportedCard>()
            val deckCounts = mutableMapOf<String, Int>()
            var skipped = 0
            var withMedia = 0

            // odid is set while a card sits in a filtered deck: did is then the filtered
            // deck, and odid is where the card actually belongs.
            val sql = """
                SELECT n.flds, n.tags,
                       (SELECT CASE WHEN c.odid != 0 THEN c.odid ELSE c.did END
                          FROM cards c WHERE c.nid = n.id ORDER BY c.ord LIMIT 1)
                  FROM notes n
            """.trimIndent()

            db.rawQuery(sql, null).use { cursor ->
                while (cursor.moveToNext()) {
                    // One oversized row — a note with a big image inlined as base64 —
                    // throws rather than fitting the cursor window. Lose that note, not
                    // the whole import.
                    val row = runCatching {
                        Triple(
                            cursor.getString(0).orEmpty(),
                            cursor.getString(1).orEmpty(),
                            if (cursor.isNull(2)) null else cursor.getLong(2)
                        )
                    }.getOrNull()
                    if (row == null) {
                        skipped++
                        continue
                    }
                    val (flds, tags, deckId) = row
                    if (flds.contains("<img", ignoreCase = true) || SOUND.containsMatchIn(flds)) {
                        withMedia++
                    }

                    val card = toCard(flds, tags)
                    if (card == null) {
                        if (flds.isNotBlank()) skipped++
                        continue
                    }
                    cards += card
                    deckNames[deckId]?.let { deckCounts[it] = (deckCounts[it] ?: 0) + 1 }
                }
            }

            val warnings = mutableListOf<String>()
            if (deckCounts.size > 1) {
                warnings += "These notes came from ${deckCounts.size} decks. They will all be " +
                    "imported into the one deck chosen below."
            }
            if (withMedia > 0) {
                warnings += "$withMedia note${if (withMedia == 1) "" else "s"} referenced images " +
                    "or audio. The text is imported; the media is not."
            }
            if (skipped > 0) {
                warnings += "$skipped note${if (skipped == 1) "" else "s"} skipped — " +
                    "needs at least two non-empty fields."
            }
            if (cards.isEmpty() && warnings.isEmpty()) {
                warnings += "The collection opened, but there are no notes in it."
            }

            return ImportResult(
                cards = cards,
                deckName = deckCounts.maxByOrNull { it.value }?.key,
                skipped = skipped,
                warnings = warnings,
                source = sourceName
            )
        }
    }

    private fun hasTable(db: SQLiteDatabase, name: String): Boolean =
        db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(name))
            .use { it.moveToFirst() }

    /**
     * Deck id to name. The modern schema has a `decks` table, the old one a JSON map in
     * the single `col` row. Both nest with "::", except the new table, which nests with
     * the same field separator Anki uses everywhere else.
     */
    private fun deckNames(db: SQLiteDatabase): Map<Long, String> {
        val names = mutableMapOf<Long, String>()
        if (hasTable(db, "decks")) {
            runCatching {
                db.rawQuery("SELECT id, name FROM decks", null).use { c ->
                    while (c.moveToNext()) {
                        val name = c.getString(1).orEmpty().replace(FIELD_SEP.toString(), "::")
                        if (name.isNotBlank()) names[c.getLong(0)] = name
                    }
                }
            }
        }
        if (names.isEmpty() && hasTable(db, "col")) {
            runCatching {
                db.rawQuery("SELECT decks FROM col LIMIT 1", null).use { c ->
                    if (c.moveToFirst()) names.putAll(decksFromJson(c.getString(0).orEmpty()))
                }
            }
        }
        return names
    }

    /** `{"1": {"name": "Default", ...}, ...}`, as written into the old `col.decks`. */
    internal fun decksFromJson(json: String): Map<Long, String> {
        if (json.isBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(json)
            buildMap {
                obj.keys().forEach { key ->
                    val id = key.toLongOrNull() ?: return@forEach
                    val name = obj.optJSONObject(key)?.optString("name").orEmpty()
                    if (name.isNotBlank()) put(id, name)
                }
            }
        }.getOrDefault(emptyMap())
    }

    /**
     * One note's raw fields into one card, or null if there is nothing usable in it.
     * Field 1 is the question; the answer is the first non-empty field after it, because
     * plenty of note types leave field 2 blank and put the content further along.
     */
    internal fun toCard(flds: String, tags: String): ImportedCard? {
        val fields = flds.split(FIELD_SEP)
        val rawFront = fields.firstOrNull().orEmpty()
        if (clean(rawFront).isBlank()) return null

        if (CLOZE.containsMatchIn(rawFront)) return clozeCard(rawFront, tags)

        val rawBack = fields.drop(1).firstOrNull { clean(it).isNotBlank() } ?: return null

        return ImportedCard(
            question = clean(rawFront),
            answer = clean(rawBack),
            answerType = AnkiImport.detectType(rawBack, clean(rawBack)),
            tags = tags.trim()
        )
    }

    /**
     * A cloze note hides its answer inside the question — "Paris is in {{c1::France}}".
     * Blank the deletions out for the question and collect them as the answer, so the
     * card still tests what the note was written to test.
     */
    private fun clozeCard(rawFront: String, tags: String): ImportedCard? {
        val blanked = CLOZE.replace(rawFront) { m ->
            val hint = m.groupValues[2]
            if (hint.isNotBlank()) "[${hint.trim()}]" else "[...]"
        }
        val question = clean(blanked)
        val answer = CLOZE.findAll(rawFront)
            .map { clean(it.groupValues[1]) }
            .filter { it.isNotBlank() }
            .joinToString(" / ")
        if (question.isBlank() || answer.isBlank()) return null
        return ImportedCard(question, answer, AnswerType.TEXT, tags.trim())
    }

    private fun clean(field: String): String =
        AnkiImport.htmlToText(SOUND.replace(field, " ")).trim()
}
