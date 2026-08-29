package com.recall.app.data

/**
 * What kind of answer a card holds. Room stores enums as their name string
 * (see [Converters]), so adding a value here needs no database migration.
 *
 * The order is the order the buttons appear on the add screen.
 */
enum class AnswerType {
    /** Plain prose. Reflows to the screen width. */
    TEXT,

    /**
     * A code snippet. Kept separate from TEXT on purpose: prose is rendered in a
     * proportional font and reflowed, which silently destroys indentation and
     * column alignment. Code needs a monospace font, literal whitespace, and
     * horizontal scrolling instead of wrapping.
     */
    CODE,

    /** A URL. Tapping it opens the browser. */
    LINK,

    /** A picture copied into the app's private storage. */
    IMAGE,

    /** An audio clip copied into the app's private storage. */
    AUDIO;

    /** Answers that are a file on disk rather than text typed into the app. */
    val isFile: Boolean get() = this == IMAGE || this == AUDIO
}
