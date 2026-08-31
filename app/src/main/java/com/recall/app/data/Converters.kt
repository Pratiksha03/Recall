package com.recall.app.data

import androidx.room.TypeConverter
import com.recall.app.srs.Rating

/** Room only understands primitives, so teach it how to store our enum. */
class Converters {
    @TypeConverter
    fun answerTypeToString(value: AnswerType): String = value.name

    @TypeConverter
    fun stringToAnswerType(value: String): AnswerType =
        runCatching { AnswerType.valueOf(value) }.getOrDefault(AnswerType.TEXT)

    @TypeConverter
    fun ratingToString(value: Rating): String = value.name

    // A grade we cannot parse is still a grade that happened, so it reads back as
    // GOOD rather than throwing and taking the whole history query down with it.
    @TypeConverter
    fun stringToRating(value: String): Rating =
        runCatching { Rating.valueOf(value) }.getOrDefault(Rating.GOOD)
}
