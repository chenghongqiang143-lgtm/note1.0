package com.example.data.local

import androidx.room.*
import com.example.data.model.Folder
import com.example.data.model.Note
import com.example.data.model.Diary
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    // --- Folders ---
    @Query("SELECT * FROM folders ORDER BY name ASC")
    fun getAllFolders(): Flow<List<Folder>>

    @Query("SELECT COUNT(*) FROM folders")
    suspend fun getFolderCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: Folder): Long

    @Update
    suspend fun updateFolder(folder: Folder)

    @Query("SELECT * FROM folders WHERE name = :name AND (parentId = :parentId OR (parentId IS NULL AND :parentId IS NULL)) LIMIT 1")
    suspend fun getFolderByNameAndParent(name: String, parentId: Long?): Folder?

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteFolderById(folderId: Long)

    // --- Notes ---
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun getAllNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE folderId = :folderId ORDER BY updatedAt DESC")
    fun getNotesByFolder(folderId: Long): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE folderId IS NULL ORDER BY updatedAt DESC")
    fun getRootNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Long): Note?

    @Query("SELECT * FROM notes WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavoriteNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isTodo = 1 ORDER BY updatedAt DESC")
    fun getTodoNotes(): Flow<List<Note>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Update
    suspend fun updateNote(note: Note)

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteNoteById(noteId: Long)

    // --- Diaries ---
    @Query("SELECT * FROM diaries WHERE date = :date LIMIT 1")
    suspend fun getDiaryByDate(date: String): Diary?

    @Query("SELECT * FROM diaries WHERE date = :date LIMIT 1")
    fun observeDiaryByDate(date: String): Flow<Diary?>

    @Query("SELECT * FROM diaries ORDER BY date DESC")
    fun getAllDiaries(): Flow<List<Diary>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiary(diary: Diary)

    @Query("DELETE FROM diaries WHERE date = :date")
    suspend fun deleteDiaryByDate(date: String)

    @Query("DELETE FROM folders")
    suspend fun clearAllFolders()

    @Query("DELETE FROM notes")
    suspend fun clearAllNotes()

    @Query("DELETE FROM diaries")
    suspend fun clearAllDiaries()
}
