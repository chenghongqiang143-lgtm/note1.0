package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val parentId: Long? = null, // for nested directory support
    val createdAt: Long = System.currentTimeMillis()
)
