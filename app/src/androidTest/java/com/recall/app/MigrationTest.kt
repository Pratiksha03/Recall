package com.recall.app

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.recall.app.data.MIGRATIONS
import com.recall.app.data.RecallDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on a device or emulator: ./gradlew connectedDebugAndroidTest
 *
 * The one thing a migration must never do is lose someone's cards, and the one
 * thing it must do is leave a schema Room recognises. This creates a real version 1
 * database with real rows in it, runs the migration, and checks both.
 *
 * `runMigrationsAndValidate` compares the result against schemas/2.json, so a
 * migration whose SQL has drifted from the entity — a missing index, a column typed
 * INTEGER instead of REAL — fails here rather than on someone's phone.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val name = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RecallDatabase::class.java
    )

    @Test
    fun addingTheReviewJournalKeepsEveryCard() {
        helper.createDatabase(name, 1).use { db ->
            db.execSQL("INSERT INTO decks (id, name, colorIndex, createdAt) VALUES (1, 'Kotlin', 2, 1000)")
            db.execSQL(
                "INSERT INTO cards (id, deckId, question, answer, answerType, note, " +
                    "intervalDays, repetition, easeFactor, dueAt, createdAt, lapses) " +
                    "VALUES (1, 1, 'What is a sealed class?', 'A closed hierarchy', 'TEXT', '', " +
                    "17, 4, 2.36, 5000, 1000, 2)"
            )
        }

        val db = helper.runMigrationsAndValidate(name, 2, true, *MIGRATIONS)

        // The scheduling state is the part that took months to earn — it has to
        // arrive on the other side byte for byte.
        db.query("SELECT question, intervalDays, easeFactor, lapses FROM cards").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("What is a sealed class?", c.getString(0))
            assertEquals(17, c.getInt(1))
            assertEquals(2.36, c.getDouble(2), 0.0001)
            assertEquals(2, c.getInt(3))
        }

        // The journal exists and starts empty: there is no history to invent.
        db.query("SELECT COUNT(*) FROM reviews").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
    }

    @Test
    fun theNewTableTakesRowsAndIsIndexed() {
        helper.createDatabase(name, 1).close()
        val db = helper.runMigrationsAndValidate(name, 2, true, *MIGRATIONS)

        db.execSQL(
            "INSERT INTO reviews (cardId, deckId, reviewedAt, rating, remembered, " +
                "intervalBefore, intervalAfter, easeAfter) " +
                "VALUES (1, 1, 1700000000000, 'GOOD', 1, 10, 25, 2.5)"
        )

        db.query("SELECT rating, remembered FROM reviews WHERE reviewedAt >= 0").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("GOOD", c.getString(0))
            assertEquals(1, c.getInt(1))
        }

        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'reviews'")
            .use { c ->
                val names = buildList { while (c.moveToNext()) add(c.getString(0)) }
                assertTrue("index_reviews_reviewedAt" in names)
                assertTrue("index_reviews_cardId" in names)
                assertTrue("index_reviews_deckId" in names)
            }
    }
}
