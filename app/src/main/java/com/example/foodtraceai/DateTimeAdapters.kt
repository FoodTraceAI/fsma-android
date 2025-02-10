package com.example.foodtraceai

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.io.IOException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

// Custom LocalDate adapter
class LocalDateAdapter : TypeAdapter<LocalDate>() {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    @Throws(IOException::class)
    override fun write(out: JsonWriter, value: LocalDate?) {
        if (value == null) {
            out.nullValue()
        } else {
            out.value(formatter.format(value))
        }
    }

    @Throws(IOException::class)
    override fun read(reader: JsonReader): LocalDate? {
        return reader.nextString()?.let {
            LocalDate.parse(it, formatter)
        }
    }
}

// Custom OffsetDateTime adapter
class OffsetDateTimeAdapter : TypeAdapter<OffsetDateTime>() {
    private val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    @Throws(IOException::class)
    override fun write(out: JsonWriter, value: OffsetDateTime?) {
        if (value == null) {
            out.nullValue()
        } else {
            out.value(formatter.format(value))
        }
    }

    @Throws(IOException::class)
    override fun read(reader: JsonReader): OffsetDateTime? {
        return reader.nextString()?.let {
            OffsetDateTime.parse(it, formatter)
        }
    }
}