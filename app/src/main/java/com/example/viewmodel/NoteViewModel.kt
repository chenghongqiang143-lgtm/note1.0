package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiService
import com.example.data.local.NoteDatabase
import com.example.data.model.Diary
import com.example.data.model.Folder
import com.example.data.model.Note
import com.example.data.repository.NoteRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.*

enum class SelectedTab {
    NOTES,  // 笔记
    DIARY,  // 日记
    TODO,   // 待办
    TAG,    // 标签
    SETTINGS // 设置
}

data class ChatMessage(
    val role: String, // "user" or "model"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

class NoteViewModel(application: Application, private val repository: NoteRepository) : androidx.lifecycle.AndroidViewModel(application) {
    
    val networkSyncManager = com.example.sync.NetworkSyncManager(application, repository)

    // --- Core Database Flows ---
    val allFolders = repository.allFolders.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allNotes = repository.allNotes.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val favoriteNotes = repository.favoriteNotes.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val todoNotes = repository.todoNotes.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allDiaries = repository.allDiaries.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // --- Selected Navigation States ---
    private val _selectedTab = MutableStateFlow(SelectedTab.DIARY)
    val selectedTab: StateFlow<SelectedTab> = _selectedTab.asStateFlow()
    private var _previousTab = SelectedTab.DIARY

    private val _currentFolderId = MutableStateFlow<Long?>(null)
    val currentFolderId: StateFlow<Long?> = _currentFolderId.asStateFlow()

    private val _selectedNote = MutableStateFlow<Note?>(null)
    val selectedNote: StateFlow<Note?> = _selectedNote.asStateFlow()

    private val _recentOpenedNoteIds = MutableStateFlow<List<Long>>(emptyList())
    val recentOpenedNotes: StateFlow<List<Note>> = combine(_recentOpenedNoteIds, repository.allNotes) { ids, notes ->
        ids.mapNotNull { id -> notes.find { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _isDrawerOpen = MutableStateFlow(false)
    val isDrawerOpen: StateFlow<Boolean> = _isDrawerOpen.asStateFlow()

    private val _selectedDate = MutableStateFlow("") // format: "YYYY-MM-DD"
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    private val _isDiaryEditMode = MutableStateFlow(false)
    val isDiaryEditMode: StateFlow<Boolean> = _isDiaryEditMode.asStateFlow()

    fun setDiaryEditMode(mode: Boolean) {
        _isDiaryEditMode.value = mode
    }

    // --- Tag Categorisation Engine ---
    val allTags: StateFlow<Map<String, Int>> = allNotes.map { notes ->
        val tagsMap = mutableMapOf<String, Int>()
        val inlineTagRegex = Regex("#([a-zA-Z0-9_\\u4e00-\\u9fa5]+)")
        notes.forEach { note ->
            val noteTags = mutableSetOf<String>()
            // Tags from metadata
            if (note.tags.isNotEmpty()) {
                val values = note.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                noteTags.addAll(values)
            }
            // Tags from inline content
            inlineTagRegex.findAll(note.content).forEach { match ->
                noteTags.add(match.groupValues[1])
            }
            
            noteTags.forEach { tag ->
                tagsMap[tag] = (tagsMap[tag] ?: 0) + 1
            }
        }
        tagsMap
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    private val _selectedTagFilter = MutableStateFlow<Set<String>>(emptySet())
    val selectedTagFilter: StateFlow<Set<String>> = _selectedTagFilter.asStateFlow()

    // --- AI Chat States ---
    private val _aiChatLog = MutableStateFlow<List<ChatMessage>>(emptyList())
    val aiChatLog: StateFlow<List<ChatMessage>> = _aiChatLog.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    init {
        // Default select today
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        _selectedDate.value = today

        viewModelScope.launch {
            // Check if folders are empty
            if (repository.getFolderCount() == 0) {
                prepopulateDatabase()
            } else {
                // For existing users, ensure "日记" folder exists at root and is synced
                val folders = repository.allFolders.first()
                var diaryRoot = folders.find { it.name == "日记" && it.parentId == null }
                if (diaryRoot == null) {
                    val id = repository.insertFolder(Folder(name = "日记", parentId = null))
                    diaryRoot = Folder(id = id, name = "日记", parentId = null)
                }
                
                // Sync diaries to notes
                val diaries = repository.allDiaries.first()
                val notes = repository.allNotes.first()

                diaries.forEach { diary ->
                    val targetFolderId = getDiaryTargetFolderId(diary.date)
                    val existingNote = notes.find { it.title == diary.date && it.folderId == targetFolderId }
                    
                    if (existingNote == null) {
                        repository.insertNote(Note(
                            title = diary.date,
                            content = diary.content,
                            folderId = targetFolderId,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        ))
                    } else {
                        if (existingNote.content != diary.content) {
                            repository.updateNote(existingNote.copy(
                                content = diary.content,
                                updatedAt = System.currentTimeMillis()
                            ))
                        }
                    }
                }
            }
        }
    }

    private val diaryFolderMutex = Mutex()
    private suspend fun getDiaryTargetFolderId(date: String): Long? = diaryFolderMutex.withLock {
        var root = repository.getFolderByNameAndParent("日记", null)
        if (root == null) {
            root = repository.getFolderByNameAndParent("日记文件夹", null)
        }
        if (root == null) {
            val folders = repository.allFolders.first()
            root = folders.find { it.parentId == null && (it.name == "日记" || it.name == "日记文件夹" || it.name.startsWith("日记")) }
        }
        if (root == null) {
            val id = repository.insertFolder(Folder(name = "日记", parentId = null))
            root = Folder(id = id, name = "日记", parentId = null)
        }
        
        val parts = date.split("-")
        if (parts.size < 2) return root.id
        val year = parts[0] + "年"
        val month = (parts[1].toIntOrNull() ?: parts[1]).toString() + "月"
        
        val yearFolderId = repository.getFolderByNameAndParent(year, root.id)?.id 
            ?: repository.insertFolder(Folder(name = year, parentId = root.id))
            
        val monthFolderId = repository.getFolderByNameAndParent(month, yearFolderId)?.id
            ?: repository.insertFolder(Folder(name = month, parentId = yearFolderId))
            
        return monthFolderId
    }

    private fun isInsideDiaryFolder(folderId: Long?): Boolean {
        if (folderId == null) return false
        val folders = allFolders.value
        var current = folders.find { it.id == folderId }
        var count = 0
        while (current != null && count < 100) { // Safety break
            if (current.parentId == null && (current.name == "日记" || current.name == "日记文件夹" || current.name.startsWith("日记"))) return true
            current = folders.find { it.id == current?.parentId }
            count++
        }
        return false
    }

    // --- Database Population (Reproducing screenshot exactly) ---
    private suspend fun prepopulateDatabase() {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        
        // Root Folder "项目"
        val projId = repository.insertFolder(
            Folder(name = "项目", parentId = null, createdAt = sdf.parse("2026-05-01 10:00")?.time ?: System.currentTimeMillis())
        )

        // Root Folder "日记"
        val diaryFolderId = repository.insertFolder(
            Folder(name = "日记", parentId = null, createdAt = System.currentTimeMillis())
        )

        // Subfolders under "项目" as shown in screenshot is 绘画, app, 电气, 学习, 相亲, 减肥
        val f1 = repository.insertFolder(Folder(name = "绘画", parentId = projId, createdAt = sdf.parse("2026-05-03 16:54")?.time ?: System.currentTimeMillis()))
        val f2 = repository.insertFolder(Folder(name = "app", parentId = projId, createdAt = sdf.parse("2026-05-03 17:26")?.time ?: System.currentTimeMillis()))
        val f3 = repository.insertFolder(Folder(name = "电气", parentId = projId, createdAt = sdf.parse("2026-05-03 23:23")?.time ?: System.currentTimeMillis()))
        val f4 = repository.insertFolder(Folder(name = "学习", parentId = projId, createdAt = sdf.parse("2026-05-04 01:24")?.time ?: System.currentTimeMillis()))
        val f5 = repository.insertFolder(Folder(name = "相亲", parentId = projId, createdAt = sdf.parse("2026-05-08 15:15")?.time ?: System.currentTimeMillis()))
        val f6 = repository.insertFolder(Folder(name = "减肥", parentId = projId, createdAt = sdf.parse("2026-05-15 07:59")?.time ?: System.currentTimeMillis()))

        // Set current folder to projId on launch so it opens the directory list from Screenshot 1
        _currentFolderId.value = projId

        // Insert some sub-pages/sub-notes under "减肥" (Screenshot 2 shows these bullet items)
        repository.insertNote(Note(title = "早餐方案", content = "- 燕麦粥 + 水煮蛋\n- 黑咖啡一杯\n- 复合维生素 1 粒\n\n#减重 #营养早餐", folderId = f6, tags = "减肥,早餐", isFavorite = true, isTodo = false))
        repository.insertNote(Note(title = "午餐搭配", content = "- 香煎鸡胸肉 150g\n- 清炒西兰花 100g\n- 糙米饭半碗\n\n#低脂 #高蛋白", folderId = f6, tags = "减肥,午餐", isFavorite = false, isTodo = false))
        repository.insertNote(Note(title = "晚餐计划", content = "- 清蒸鲈鱼 100g\n- 小番茄及生菜沙拉\n- 无米蛋炒饭\n\n注意控制碳水化合物摄入！", folderId = f6, tags = "减肥,晚餐", isFavorite = false, isTodo = false))
        repository.insertNote(Note(title = "体脂与体重追踪", content = "- 5月15日：82.3 kg\n- 5月18日：81.5 kg\n- 5月21日：80.8 kg\n- 5月24日：80.1 kg\n\n继续加油，目标体脂 15%！", folderId = f6, tags = "减肥,体重", isFavorite = true, isTodo = false))
        repository.insertNote(Note(title = "减肥方案分析", content = "结合低碳饮食和中等强度有氧运动。每周慢跑3次，每次不少于30分钟。每日热量缺口控制在500卡路里左右。", folderId = f6, tags = "减肥,分析", isFavorite = false, isTodo = false))

        // Create some todos to make the tab alive
        repository.insertNote(Note(title = "搭建项目核心骨架", content = "完成 Jetpack Compose Edge-to-Edge 沉浸底栏和 Room 数据库的基础交互封装。", folderId = f2, tags = "app,开发", isFavorite = true, isTodo = true, isTodoDone = true))
        repository.insertNote(Note(title = "设计知识树图谱交互", content = "在 Canvas 画布上设计力导向拓扑关系网，节点支持拖拽与连线联动渲染。", folderId = f2, tags = "app,设计", isFavorite = false, isTodo = true, isTodoDone = false))
        repository.insertNote(Note(title = "整理电气工控PLC基础知识", content = "针对西门子 S7-1200 常用通信协议（Modbus TCP、S7Comm）抓包分析并建档总结。", folderId = f3, tags = "电气,自主学习", isFavorite = false, isTodo = true, isTodoDone = false))

        // Generate full diaries
        val diaryContents = listOf(
            "2026-05-24" to "· 减肥\n\t· 早餐: 燕麦粥 + 水煮蛋 (250 kcal)\n\t· 午餐: 香煎鸡胸肉配糙米饭 (450 kcal)\n\t· 晚餐: 水煮生菜牛肉片 (320 kcal)\n\t· 分析: 晚间增加了一次半小时慢跑，状态极佳\n\t· 方案: 维持当前轻度低碳生酮循环，间歇性断食\n\t· 体重: 80.1kg (-0.7kg)\n· 项目\n\t· app: 已经完成侧滑菜单的画板和历史卡片交互\n\t· 学习: 电动汽车电气化结构模块第三课时",
            "2026-05-23" to "· 项目\n\t· app: 优化了 Room 本地数据库和 KSP 触发机制\n\t· 减肥: 正常饮食，体重80.8kg",
            "2026-05-22" to "· 学习\n\t· 电气: 读完了 PLC 通信链路教程\n· 相亲\n\t· 聊天回应热烈，周末约在咖啡馆见面！",
            "2026-05-20" to "· 绘画\n\t· 素描五五比例练习 2.5 小时",
            "2026-05-18" to "· 减肥\n\t· 晚餐聚餐，略微超量，体重81.5kg",
            "2026-05-15" to "· 减肥\n\t· 开始第一天断食，体重82.3kg。\n· 绘画\n\t· 人物速写3张"
        )

        diaryContents.forEach { (date, content) ->
            repository.insertDiary(Diary(date = date, content = content))
            val targetFolderId = getDiaryTargetFolderId(date)
            repository.insertNote(Note(
                title = date,
                content = content,
                folderId = targetFolderId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            ))
        }
    }

    // --- State Toggles & Operations ---
    fun selectTab(tab: SelectedTab) {
        if (_selectedTab.value != tab) {
            _previousTab = _selectedTab.value
            _selectedTab.value = tab
        }
        // Always clear selected note when switching tabs or re-selecting current tab to return to root list
        _selectedNote.value = null
    }

    fun goBackTab() {
        _selectedTab.value = _previousTab
    }

    fun exitNote() {
        _selectedNote.value = null
        _selectedTab.value = _previousTab
    }

    fun navigateToFolder(folderId: Long?) {
        _currentFolderId.value = folderId
    }

    fun selectNote(note: Note?) {
        _selectedNote.value = note
        if (note != null) {
            val list = _recentOpenedNoteIds.value.toMutableList()
            list.remove(note.id)
            list.add(0, note.id)
            _recentOpenedNoteIds.value = list.take(3)
        }
    }

    fun closeRecentNote(noteId: Long) {
        val list = _recentOpenedNoteIds.value.filter { it != noteId }
        _recentOpenedNoteIds.value = list
        if (_selectedNote.value?.id == noteId) {
            val notes = allNotes.value
            val nextId = list.firstOrNull()
            _selectedNote.value = nextId?.let { id -> notes.find { it.id == id } }
        }
    }

    fun selectTagFilter(tag: String?) {
        if (tag == null) {
            _selectedTagFilter.value = emptySet()
        } else {
            _selectedTagFilter.value = setOf(tag)
        }
    }

    fun toggleTagFilter(tag: String) {
        val current = _selectedTagFilter.value
        if (current.contains(tag)) {
            _selectedTagFilter.value = current - tag
        } else {
            _selectedTagFilter.value = current + tag
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
    }

    fun toggleDrawer(open: Boolean) {
        _isDrawerOpen.value = open
    }

    fun selectDate(date: String) {
        _selectedDate.value = date
    }

    // --- Directory & Sub-item Creation Actions ---
    fun createFolder(name: String, parentId: Long? = _currentFolderId.value) {
        viewModelScope.launch {
            repository.insertFolder(Folder(name = name, parentId = parentId))
        }
    }

    fun deleteFolder(folderId: Long) {
        viewModelScope.launch {
            repository.deleteFolderById(folderId)
            // If deleting active folder, go up
            if (_currentFolderId.value == folderId) {
                _currentFolderId.value = null
            }
        }
    }

    fun renameFolder(folderId: Long, newName: String) {
        viewModelScope.launch {
            val folder = repository.allFolders.first().find { it.id == folderId }
            if (folder != null) {
                repository.updateFolder(folder.copy(name = newName))
            }
        }
    }

    fun moveFolder(folderId: Long, newParentId: Long?) {
        viewModelScope.launch {
            val folder = repository.allFolders.first().find { it.id == folderId }
            if (folder != null) {
                repository.updateFolder(folder.copy(parentId = newParentId))
            }
        }
    }

    fun moveNote(noteId: Long, newFolderId: Long?) {
        viewModelScope.launch {
            val note = repository.allNotes.first().find { it.id == noteId }
            if (note != null) {
                val updated = note.copy(folderId = newFolderId)
                repository.updateNote(updated)

                // Sync to Diary if moved into "日记" folder hierarchy
                if (isInsideDiaryFolder(updated.folderId)) {
                    if (updated.title.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                        repository.insertDiary(Diary(date = updated.title, content = updated.content))
                    }
                }
            }
        }
    }

    fun createNote(title: String, content: String, folderId: Long? = _currentFolderId.value, tags: String = "", isTodo: Boolean = false) {
        viewModelScope.launch {
            var finalFolderId = folderId
            // If it's a diary (matches date pattern) and is inside the diary hierarchy, ensure it's in the correct month folder
            if (isInsideDiaryFolder(folderId) && title.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                val targetId = getDiaryTargetFolderId(title)
                if (targetId != null) {
                    finalFolderId = targetId
                }
            }

            val note = Note(
                title = title,
                content = content,
                folderId = finalFolderId,
                tags = tags,
                isTodo = isTodo,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val id = repository.insertNote(note)
            
            // Sync to Diary if created in "日记" folder hierarchy
            if (isInsideDiaryFolder(finalFolderId)) {
                if (title.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                    repository.insertDiary(Diary(date = title, content = content))
                }
            }

            // Automatically select the newly created note to edit it
            val newNote = repository.getNoteById(id)
            if (newNote != null) {
                _selectedNote.value = newNote
            }
        }
    }

    fun updateNoteContent(
        note: Note,
        newTitle: String,
        newContent: String,
        newTags: String = note.tags,
        isTodo: Boolean = note.isTodo,
        isTodoDone: Boolean = note.isTodoDone,
        todoStatus: String = note.todoStatus
    ) {
        viewModelScope.launch {
            var status = todoStatus
            var done = isTodoDone

            if (todoStatus != note.todoStatus) {
                status = todoStatus
                done = (todoStatus == "已完成")
            } else if (isTodoDone != note.isTodoDone) {
                done = isTodoDone
                status = if (isTodoDone) "已完成" else "未完成"
            }

            var finalFolderId = note.folderId
            // If it's a diary (matches date pattern) and is inside the diary hierarchy, ensure it's in the correct month folder
            if (isInsideDiaryFolder(note.folderId) && newTitle.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                val targetId = getDiaryTargetFolderId(newTitle)
                if (targetId != null) {
                    finalFolderId = targetId
                }
            }

            val updated = note.copy(
                title = newTitle,
                content = newContent,
                tags = newTags,
                isTodo = isTodo,
                isTodoDone = done,
                todoStatus = status,
                folderId = finalFolderId,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateNote(updated)
            // Update selected note state if it is currently selected
            if (_selectedNote.value?.id == note.id) {
                _selectedNote.value = updated
            }

            // Sync back to Diary if in "日记" folder hierarchy and title is a date
            if (isInsideDiaryFolder(updated.folderId)) {
                if (newTitle.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                    repository.insertDiary(Diary(date = newTitle, content = newContent))
                }
            }
        }
    }

    fun toggleNoteFavorite(note: Note) {
        viewModelScope.launch {
            val updated = note.copy(isFavorite = !note.isFavorite, updatedAt = System.currentTimeMillis())
            repository.updateNote(updated)
            if (_selectedNote.value?.id == note.id) {
                _selectedNote.value = updated
            }
        }
    }

    fun toggleNoteTodoState(note: Note) {
        viewModelScope.launch {
            val updated = note.copy(isTodo = !note.isTodo, updatedAt = System.currentTimeMillis())
            repository.updateNote(updated)
            if (_selectedNote.value?.id == note.id) {
                _selectedNote.value = updated
            }
        }
    }

    fun toggleTodoDone(note: Note) {
        viewModelScope.launch {
            val nextStatus = when (note.todoStatus) {
                "进行中" -> "已完成"
                "已完成" -> "未完成"
                else -> "进行中"
            }
            val nextDone = (nextStatus == "已完成")
            val updated = note.copy(
                isTodoDone = nextDone,
                todoStatus = nextStatus,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateNote(updated)
            if (_selectedNote.value?.id == note.id) {
                _selectedNote.value = updated
            }
        }
    }

    fun toggleContentLineTodo(note: Note, lineIndex: Int, currentStatus: String) {
        viewModelScope.launch {
            val lines = note.content.split("\n").toMutableList()
            if (lineIndex in lines.indices) {
                val line = lines[lineIndex]
                val indent = line.takeWhile { it == '\t' || it == ' ' }
                val clean = line.trimStart()
                
                var bulletPrefix = ""
                var textToProcess = clean
                if (clean.startsWith("- ")) {
                    bulletPrefix = "- "
                    textToProcess = clean.substring(2)
                } else if (clean.startsWith("* ")) {
                    bulletPrefix = "* "
                    textToProcess = clean.substring(2)
                }

                val nextStatus = when (currentStatus) {
                    "进行中" -> "已完成"
                    "已完成" -> "未完成"
                    else -> "进行中"
                }
                
                val newTodoMarker = when (nextStatus) {
                    "已完成" -> "[x]"
                    "进行中" -> "[-]"
                    else -> "[ ]"
                }
                
                val oldMarkerLength = when {
                    textToProcess.startsWith("[ ]") -> 3
                    textToProcess.startsWith("[-]") -> 3
                    textToProcess.startsWith("[/]") -> 3
                    textToProcess.startsWith("[x]", ignoreCase = true) -> 3
                    else -> 0
                }
                
                val finalClean = if (oldMarkerLength > 0) {
                    textToProcess.substring(oldMarkerLength).trimStart()
                } else {
                    textToProcess.trimStart()
                }
                
                lines[lineIndex] = "$indent$bulletPrefix$newTodoMarker $finalClean"
                val newContent = lines.joinToString("\n")
                
                val updated = note.copy(content = newContent, updatedAt = System.currentTimeMillis())
                repository.updateNote(updated)
                if (_selectedNote.value?.id == note.id) {
                    _selectedNote.value = updated
                }

                // Sync back to Diary if in "日记" folder hierarchy and title is a date
                if (isInsideDiaryFolder(updated.folderId)) {
                    if (updated.title.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                        repository.insertDiary(Diary(date = updated.title, content = newContent))
                    }
                }
            }
        }
    }

    fun toggleDiaryLineTodo(diary: Diary, lineIndex: Int, currentStatus: String) {
        viewModelScope.launch {
            val lines = diary.content.split("\n").toMutableList()
            if (lineIndex in lines.indices) {
                val line = lines[lineIndex]
                val indent = line.takeWhile { it == '\t' || it == ' ' }
                val clean = line.trimStart()
                
                var bulletPrefix = ""
                var textToProcess = clean
                if (clean.startsWith("- ")) {
                    bulletPrefix = "- "
                    textToProcess = clean.substring(2)
                } else if (clean.startsWith("* ")) {
                    bulletPrefix = "* "
                    textToProcess = clean.substring(2)
                }

                val nextStatus = when (currentStatus) {
                    "进行中" -> "已完成"
                    "已完成" -> "未完成"
                    else -> "进行中"
                }
                
                val newTodoMarker = when (nextStatus) {
                    "已完成" -> "[x]"
                    "进行中" -> "[-]"
                    else -> "[ ]"
                }
                
                val oldMarkerLength = when {
                    textToProcess.startsWith("[ ]") -> 3
                    textToProcess.startsWith("[-]") -> 3
                    textToProcess.startsWith("[/]") -> 3
                    textToProcess.startsWith("[x]", ignoreCase = true) -> 3
                    else -> 0
                }
                
                val finalClean = if (oldMarkerLength > 0) {
                    textToProcess.substring(oldMarkerLength).trimStart()
                } else {
                    textToProcess.trimStart()
                }
                
                lines[lineIndex] = "$indent$bulletPrefix$newTodoMarker $finalClean"
                val newContent = lines.joinToString("\n")
                
                saveDiary(diary.date, newContent)
            }
        }
    }

    fun updateTodoStatus(note: Note, newStatus: String) {
        viewModelScope.launch {
            val isDone = (newStatus == "已完成")
            val updated = note.copy(
                todoStatus = newStatus,
                isTodoDone = isDone,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateNote(updated)
            if (_selectedNote.value?.id == note.id) {
                _selectedNote.value = updated
            }
        }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            repository.deleteNoteById(noteId)
            if (_selectedNote.value?.id == noteId) {
                _selectedNote.value = null
            }
        }
    }

    // --- Diary Operations ---
    private var diarySaveJob: Job? = null
    fun saveDiary(date: String, content: String) {
        diarySaveJob?.cancel()
        diarySaveJob = viewModelScope.launch {
            delay(500) // Debounce 500ms
            repository.insertDiary(Diary(date = date, content = content))
            
            // Also save as a note in the "日记/Year/Month" hierarchy
            val targetFolderId = getDiaryTargetFolderId(date)
            if (targetFolderId != null) {
                val notes = allNotes.value
                // Search for any note with this title (date) that is in the diary hierarchy
                val existingNote = notes.find { 
                    it.title == date && isInsideDiaryFolder(it.folderId)
                }
                
                if (existingNote != null) {
                    if (existingNote.content != content || existingNote.folderId != targetFolderId) {
                        repository.updateNote(existingNote.copy(
                            content = content,
                            folderId = targetFolderId,
                            updatedAt = System.currentTimeMillis()
                        ))
                    }
                } else {
                    repository.insertNote(Note(
                        title = date,
                        content = content,
                        folderId = targetFolderId,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    ))
                }
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
            _selectedNote.value = null
            _currentFolderId.value = null
            _recentOpenedNoteIds.value = emptyList()
            _searchQuery.value = ""
            _selectedTagFilter.value = emptySet()
            _aiChatLog.value = emptyList()
            _isAiLoading.value = false
            _isDiaryEditMode.value = false
            _isDrawerOpen.value = false
            _selectedNoteIds.value = emptySet()
            _isInSelectionMode.value = false
            
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            _selectedDate.value = today
        }
    }

    // --- Gemini AI Assistant Chat Operations ---
    fun sendAiMessage(message: String) {
        if (message.isBlank()) return

        val userMsg = ChatMessage(role = "user", content = message)
        _aiChatLog.value = _aiChatLog.value + userMsg
        _isAiLoading.value = true

        viewModelScope.launch {
            val systemInstruction = """
                You are "AI 助手", an expert knowledge architect and personal assistant within a high-concept note application ("项目笔记").
                Help users categorize their notes, create hierarchical bullet journal outlines (子弹日记), summarize content, structure mindmaps, or manage task todos.
                Keep your answers brief, modern, beautiful, structured in bullet points or codeblocks where applicable. Rely heavily on structured thinking and helpful productivity tips.
                Respond in Chinese. Keep response under 300 words.
            """.trimIndent()

            val activeNoteContext = _selectedNote.value?.let {
                "【当前选中的笔记】标题: ${it.title}\n内容: ${it.content}\n标签: ${it.tags}\n"
            } ?: "当前未选中任何笔记。"

            val folderDetails = allFolders.value.joinToString("\n") { "文件夹: ${it.name} (ID: ${it.id}, 父级ID: ${it.parentId})" }
            val noteDetails = allNotes.value.take(10).joinToString("\n") { "笔记: ${it.title} (标签: ${it.tags})" }

            val fullPrompt = """
                $activeNoteContext
                【当前知识库大纲结构】
                $folderDetails
                $noteDetails
                
                【用户提问/请求】
                $message
            """.trimIndent()

            val reply = GeminiService.generateResponse(fullPrompt, systemInstruction)
            val modelMsg = ChatMessage(role = "model", content = reply)
            _aiChatLog.value = _aiChatLog.value + modelMsg
            _isAiLoading.value = false
        }
    }

    fun clearAiChat() {
        _aiChatLog.value = emptyList()
    }

    // --- Multi-Selection States & Helpers ---
    private val _selectedNoteIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedNoteIds: StateFlow<Set<Long>> = _selectedNoteIds.asStateFlow()

    private val _isInSelectionMode = MutableStateFlow(false)
    val isInSelectionMode: StateFlow<Boolean> = _isInSelectionMode.asStateFlow()

    fun toggleNoteSelection(noteId: Long) {
        val current = _selectedNoteIds.value
        if (current.contains(noteId)) {
            val next = current - noteId
            _selectedNoteIds.value = next
            if (next.isEmpty()) {
                _isInSelectionMode.value = false
            }
        } else {
            _selectedNoteIds.value = current + noteId
            _isInSelectionMode.value = true
        }
    }

    fun startSelectionMode(noteId: Long) {
        _selectedNoteIds.value = setOf(noteId)
        _isInSelectionMode.value = true
    }

    fun clearSelection() {
        _selectedNoteIds.value = emptySet()
        _isInSelectionMode.value = false
    }

    fun deleteSelectedNotes() {
        viewModelScope.launch {
            _selectedNoteIds.value.forEach { id ->
                repository.deleteNoteById(id)
            }
            clearSelection()
        }
    }

    fun copySelectedNotesText(clipboardManager: androidx.compose.ui.platform.ClipboardManager) {
        val selectedIds = _selectedNoteIds.value
        val notesToCopy = allNotes.value.filter { it.id in selectedIds }
        if (notesToCopy.isEmpty()) return
        
        val sb = StringBuilder()
        notesToCopy.forEach { note ->
            sb.append("【").append(note.title).append("】\n")
            sb.append(note.content).append("\n\n")
        }
        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(sb.toString().trim()))
    }

    fun cutSelectedNotesText(clipboardManager: androidx.compose.ui.platform.ClipboardManager) {
        copySelectedNotesText(clipboardManager)
        deleteSelectedNotes()
    }
    override fun onCleared() {
        super.onCleared()
        networkSyncManager.cleanup()
    }
}

class NoteViewModelFactory(private val application: Application, private val repository: NoteRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NoteViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NoteViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
