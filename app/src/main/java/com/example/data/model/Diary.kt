package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(tableName = "diaries")
data class Diary(
    @PrimaryKey val date: String, // format: YYYY-MM-DD
    val content: String = "", // multiple lines, can use tabs/spaces to indent bullet hierarchy
    val createdAt: Long = System.currentTimeMillis()
)
