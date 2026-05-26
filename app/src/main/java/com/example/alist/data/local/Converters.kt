package com.example.alist.data.local

import androidx.room.TypeConverter
import com.example.alist.data.remote.model.AListFile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromList(value: List<AListFile>?): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toList(value: String): List<AListFile>? {
        val type = object : TypeToken<List<AListFile>>() {}.type
        return gson.fromJson(value, type)
    }
}
