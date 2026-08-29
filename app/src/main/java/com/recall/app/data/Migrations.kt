package com.recall.app.data

import androidx.room.migration.Migration

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
    // Nothing here yet: the database is still at version 1.
    //
    // The next one you write will look like this — a new nullable column with a
    // default is the easy, common case:
    //
    // object : Migration(1, 2) {
    //     override fun migrate(db: SupportSQLiteDatabase) {
    //         db.execSQL("ALTER TABLE cards ADD COLUMN timesSeen INTEGER NOT NULL DEFAULT 0")
    //     }
    // }
    //
    // A whole new table is just as easy — copy the CREATE TABLE statement that
    // Room put in app/schemas/2.json so it matches byte for byte:
    //
    // object : Migration(2, 3) {
    //     override fun migrate(db: SupportSQLiteDatabase) {
    //         db.execSQL(
    //             "CREATE TABLE IF NOT EXISTS tags (" +
    //                 "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
    //                 "name TEXT NOT NULL)"
    //         )
    //     }
    // }
)
