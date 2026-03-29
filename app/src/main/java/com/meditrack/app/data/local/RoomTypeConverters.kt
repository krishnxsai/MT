package com.meditrack.app.data.local

import androidx.room.TypeConverter
import java.util.Date

/**
 * Room type converters for handling non-primitive types.
 * Converts between Room-compatible types and complex Kotlin objects.
 */
class RoomTypeConverters {

    // ── Date Conversion ─────────────────────────────────────
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}

