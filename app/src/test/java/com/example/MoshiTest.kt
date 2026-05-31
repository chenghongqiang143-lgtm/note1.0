package com.example

import com.example.data.model.SyncPayload
import com.example.data.model.Note
import com.squareup.moshi.Moshi
import org.junit.Test
import org.junit.Assert.*

class MoshiTest {
    @Test
    fun testSerialization() {
        try {
            val moshi = Moshi.Builder().build()
            val adapter = moshi.adapter(SyncPayload::class.java)
            val payload = SyncPayload(notes = listOf(Note(title = "test", content = "content")))
            val json = adapter.toJson(payload)
            assertNotNull(json)
            println(json)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}
