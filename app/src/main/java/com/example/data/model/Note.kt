package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val content: String,
    val folderId: Long? = null,
    val tags: String = "", // comma separated values: work,travel,ideas
    val isFavorite: Boolean = false,
    val isTodo: Boolean = false,
    val isTodoDone: Boolean = false,
    val todoStatus: String = "未完成", // 未完成, 进行中, 已完成
    val todoDueDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
