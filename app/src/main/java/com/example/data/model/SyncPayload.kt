package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SyncPayload(
    val type: String = "SYNC_DATA", // e.g. "REQUEST", "SYNC_DATA"
    val notes: List<Note> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val diaries: List<Diary> = emptyList(),
    val deviceName: String = ""
)
