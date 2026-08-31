package com.recall.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * How to change the database without losing anyone's cards.
 *
 * Room compares the schema it generates from your @Entity classes against the
 * schema of the database already on the phone. If they differ, it needs a
 * Migration explaining how to get from the old one to the new one.
 *
 * The recipe, every time:
 *
 *  1. Change the @Entity (add a column, add a table, add an index).
 *  2. Bump `version` in the @Database annotation, 1 -> 2.
 *  3. Write a Migration(1, 2) below and add it to MIGRATIONS.
 *  4. Build once. Room writes app/schemas/2.json — commit it. Diffing 1.json
 *     against 2.json shows you exactly what SQL your migration has to produce.
 *  5. Install the new build OVER the old one (do not uninstall first) and check
 *     your data is still there.
 *
 * SQLite is limited: it can ALTER TABLE ADD COLUMN, but it cannot drop or retype
 * a column. For those you do the "create new table, copy rows, drop old, rename"
 * dance shown in the commented example.
 *
 * Adding a *value* to an enum like [AnswerType] needs none of this — those are
 * stored as text, so the schema does not change at all.
 */
val MIGRATIONS: Array<Migration> = arrayOf(

    /**
     * 1 -> 2: the review journal behind the Progress screen.
     *
     * Purely additive — no existing table is touched, so every card and its
     * scheduling state survives untouched. Anyone upgrading starts with an empty
     * history, because the old schema never recorded one: there is nothing to
     * backfill from, and inventing plausible-looking past reviews would make the
     * first retention figure a lie.
     *
     * The SQL below is copied from schemas/2.json. It has to match what Room
     * generates character for character, down to the backticks, or Room throws on
     * the first launch after the upgrade.
     */
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `reviews` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`cardId` INTEGER NOT NULL, " +
                    "`deckId` INTEGER NOT NULL, " +
                    "`reviewedAt` INTEGER NOT NULL, " +
                    "`rating` TEXT NOT NULL, " +
                    "`remembered` INTEGER NOT NULL, " +
                    "`intervalBefore` INTEGER NOT NULL, " +
                    "`intervalAfter` INTEGER NOT NULL, " +
                    "`easeAfter` REAL NOT NULL)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reviews_reviewedAt` ON `reviews` (`reviewedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reviews_cardId` ON `reviews` (`cardId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reviews_deckId` ON `reviews` (`deckId`)")
        }
    }
)
