package com.example.data.repository

import com.example.data.local.NoteDao
import com.example.data.model.Folder
import com.example.data.model.Note
import com.example.data.model.Diary
import kotlinx.coroutines.flow.Flow

class NoteRepository(private val dao: NoteDao) {

    // Folders
    val allFolders: Flow<List<Folder>> = dao.getAllFolders()
    suspend fun getFolderCount(): Int = dao.getFolderCount()
    suspend fun getFolderByNameAndParent(name: String, parentId: Long?): Folder? = dao.getFolderByNameAndParent(name, parentId)
    suspend fun insertFolder(folder: Folder): Long = dao.insertFolder(folder)
    suspend fun updateFolder(folder: Folder) = dao.updateFolder(folder)
    suspend fun deleteFolderById(folderId: Long) = dao.deleteFolderById(folderId)

    // Notes
    val allNotes: Flow<List<Note>> = dao.getAllNotes()
    val rootNotes: Flow<List<Note>> = dao.getRootNotes()
    val favoriteNotes: Flow<List<Note>> = dao.getFavoriteNotes()
    val todoNotes: Flow<List<Note>> = dao.getTodoNotes()

    fun getNotesByFolder(folderId: Long): Flow<List<Note>> = dao.getNotesByFolder(folderId)
    suspend fun getNoteById(id: Long): Note? = dao.getNoteById(id)
    suspend fun insertNote(note: Note): Long = dao.insertNote(note)
    suspend fun updateNote(note: Note) = dao.updateNote(note)
    suspend fun deleteNoteById(noteId: Long) = dao.deleteNoteById(noteId)

    // Diaries
    val allDiaries: Flow<List<Diary>> = dao.getAllDiaries()
    suspend fun getDiaryByDate(date: String): Diary? = dao.getDiaryByDate(date)
    fun observeDiaryByDate(date: String): Flow<Diary?> = dao.observeDiaryByDate(date)
    suspend fun insertDiary(diary: Diary) = dao.insertDiary(diary)
    suspend fun deleteDiaryByDate(date: String) = dao.deleteDiaryByDate(date)

    suspend fun clearAllData() {
        dao.clearAllNotes()
        dao.clearAllFolders()
        dao.clearAllDiaries()
    }
}
