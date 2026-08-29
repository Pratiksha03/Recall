package com.recall.app.data

import androidx.room.TypeConverter

/** Room only understands primitives, so teach it how to store our enum. */
class Converters {
    @TypeConverter
    fun answerTypeToString(value: AnswerType): String = value.name

    @TypeConverter
    fun stringToAnswerType(value: String): AnswerType =
        runCatching { AnswerType.valueOf(value) }.getOrDefault(AnswerType.TEXT)
}
