package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.ripple.rememberRipple
import com.example.data.model.Diary
import com.example.data.model.Folder
import com.example.data.model.Note
import com.example.viewmodel.ChatMessage
import com.example.viewmodel.NoteViewModel
import com.example.viewmodel.SelectedTab
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin



// Define Node data class for graph visualizer
data class GraphNode(
    val id: String,
    val label: String,
    val type: String, // "folder", "note", "tag"
    var x: Float,
    var y: Float,
    val color: Color
)

@Composable
fun AdaptiveNavigationLayout(
    isTabletLandscape: Boolean,
    drawerState: DrawerState,
    drawerContent: @Composable () -> Unit,
    mainContent: @Composable () -> Unit
) {
    if (isTabletLandscape) {
        Row(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 1.dp
            ) {
                drawerContent()
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                mainContent()
            }
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.width(400.dp),
                    drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                    drawerContainerColor = MaterialTheme.colorScheme.background
                ) {
                    drawerContent()
                }
            },
            content = mainContent
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(
    viewModel: NoteViewModel,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val isDrawerOpen by viewModel.isDrawerOpen.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val selectedNote by viewModel.selectedNote.collectAsState()
    val allNotes by viewModel.allNotes.collectAsState()
    val recentNotes = remember(allNotes) {
        allNotes.sortedByDescending { it.updatedAt }
    }
    val targetNote = recentNotes.firstOrNull()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isDiaryEditMode by viewModel.isDiaryEditMode.collectAsState()
    val isInSelectionMode by viewModel.isInSelectionMode.collectAsState()
    val selectedNoteIds by viewModel.selectedNoteIds.collectAsState()
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    androidx.activity.compose.BackHandler(enabled = selectedNote != null) {
        if (selectedTab == SelectedTab.NOTES) {
            viewModel.exitNote()
        } else {
            viewModel.selectNote(null)
        }
    }

    // Dialog state controllers
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderDialogParentId by remember { mutableStateOf<Long?>(null) }
    var showCreateNoteDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showSearchOverlay by remember { mutableStateOf(false) }

    // Drawer state configuration
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isTabletLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE && configuration.screenWidthDp >= 600

    val isKeyboardOpen = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp

    LaunchedEffect(isDrawerOpen, isTabletLandscape) {
        if (!isTabletLandscape) {
            try {
                if (isDrawerOpen) {
                    drawerState.open()
                } else {
                    drawerState.close()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Ignore initialization state exceptions
            }
        }
    }

    LaunchedEffect(drawerState.isOpen, isTabletLandscape) {
        if (!isTabletLandscape) {
            viewModel.toggleDrawer(drawerState.isOpen)
        }
    }

    val drawerContentComposable: @Composable () -> Unit = {
        SidebarDrawerContent(
            viewModel = viewModel,
            onCreateFolder = { parentId ->
                folderDialogParentId = parentId
                showCreateFolderDialog = true
            },
            onCreateNote = {
                showCreateNoteDialog = true
            },
            onCloseDrawer = {
                if (!isTabletLandscape) {
                    coroutineScope.launch {
                        try {
                            drawerState.close()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {}
                    }
                }
            }
        )
    }

    AdaptiveNavigationLayout(
        isTabletLandscape = isTabletLandscape,
        drawerState = drawerState,
        drawerContent = drawerContentComposable
    ) {
        Scaffold(
            topBar = {
                if (selectedNote == null) {
                    if (isInSelectionMode) {
                        TopAppBar(
                            title = {
                                Text("已选择 ${selectedNoteIds.size} 项", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            },
                            navigationIcon = {
                                IconButton(onClick = { viewModel.clearSelection() }) {
                                    Icon(Icons.Default.Close, contentDescription = "取消选择")
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = {
                                        viewModel.copySelectedNotesText(clipboardManager)
                                        viewModel.clearSelection()
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "复制文本")
                                }
                                IconButton(
                                    onClick = {
                                        viewModel.cutSelectedNotesText(clipboardManager)
                                        viewModel.clearSelection()
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCut, contentDescription = "剪切文本")
                                }
                                IconButton(
                                    onClick = {
                                        viewModel.deleteSelectedNotes()
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除单位项目", tint = MaterialTheme.colorScheme.error)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    } else {
                        TopAppBar(
                            title = {
                                val activeFolderId by viewModel.currentFolderId.collectAsState()
                                val folders by viewModel.allFolders.collectAsState()
                                
                                val titleText = when (selectedTab) {
                                    SelectedTab.NOTES -> "所有笔记"
                                    SelectedTab.DIARY -> "日记"
                                    SelectedTab.TODO -> "待办"
                                    SelectedTab.TAG -> "标签"
                                    SelectedTab.SETTINGS -> "设置"
                                }
                                Text(
                                    text = titleText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            navigationIcon = {
                                if (!isTabletLandscape) {
                                    IconButton(onClick = { viewModel.toggleDrawer(true) }) {
                                        Icon(Icons.Default.Menu, contentDescription = "打开菜单")
                                    }
                                }
                            },
                            actions = {
                                if (selectedTab == SelectedTab.NOTES && targetNote != null) {
                                    IconButton(onClick = { viewModel.toggleNoteFavorite(targetNote) }) {
                                        Icon(
                                            imageVector = if (targetNote.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = "收藏",
                                            tint = if (targetNote.isFavorite) Color(0xFFFFB000) else Color.Gray
                                        )
                                    }
                                }
                                IconButton(onClick = { showSearchOverlay = !showSearchOverlay }) {
                                    Icon(Icons.Default.Search, contentDescription = "搜索")
                                }
                                IconButton(onClick = { viewModel.toggleDarkMode() }) {
                                    Icon(
                                        imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                        contentDescription = "切换暗色"
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        )
                    }
                }
            },
            bottomBar = {
                if (!isDiaryEditMode && !isKeyboardOpen) {
                    val diaryScale by animateFloatAsState(targetValue = if (selectedTab == SelectedTab.DIARY) 1.15f else 1.0f, label = "diary_scale")
                    val todoScale by animateFloatAsState(targetValue = if (selectedTab == SelectedTab.TODO) 1.15f else 1.0f, label = "todo_scale")
                    val tagScale by animateFloatAsState(targetValue = if (selectedTab == SelectedTab.TAG) 1.15f else 1.0f, label = "tag_scale")

                    val itemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        indicatorColor = Color.Transparent
                    )

                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        NavigationBarItem(
                            icon = { 
                                Icon(
                                    imageVector = if (selectedTab == SelectedTab.DIARY) Icons.Default.Book else Icons.Outlined.Book, 
                                    contentDescription = "日记",
                                    modifier = Modifier.scale(diaryScale)
                                ) 
                            },
                            label = { 
                                Text(
                                    text = "日记",
                                    fontWeight = if (selectedTab == SelectedTab.DIARY) FontWeight.ExtraBold else FontWeight.Medium,
                                    fontSize = if (selectedTab == SelectedTab.DIARY) 13.sp else 12.sp
                                ) 
                            },
                            selected = selectedTab == SelectedTab.DIARY,
                            colors = itemColors,
                            onClick = { viewModel.selectTab(SelectedTab.DIARY) }
                        )
                        NavigationBarItem(
                            icon = { 
                                Icon(
                                    imageVector = if (selectedTab == SelectedTab.NOTES) Icons.Default.Description else Icons.Outlined.Description, 
                                    contentDescription = "笔记",
                                ) 
                            },
                            selected = selectedTab == SelectedTab.NOTES,
                            label = { 
                                Text(
                                    text = "笔记",
                                    fontWeight = if (selectedTab == SelectedTab.NOTES) FontWeight.ExtraBold else FontWeight.Medium,
                                    fontSize = if (selectedTab == SelectedTab.NOTES) 13.sp else 12.sp
                                ) 
                            },
                            colors = itemColors,
                            onClick = { viewModel.selectTab(SelectedTab.NOTES) }
                        )
                        NavigationBarItem(
                            icon = { 
                                Icon(
                                    imageVector = if (selectedTab == SelectedTab.TODO) Icons.Default.TaskAlt else Icons.Outlined.TaskAlt, 
                                    contentDescription = "待办",
                                    modifier = Modifier.scale(todoScale)
                                ) 
                            },
                            label = { 
                                Text(
                                    text = "待办",
                                    fontWeight = if (selectedTab == SelectedTab.TODO) FontWeight.ExtraBold else FontWeight.Medium,
                                    fontSize = if (selectedTab == SelectedTab.TODO) 13.sp else 12.sp
                                ) 
                            },
                            selected = selectedTab == SelectedTab.TODO,
                            colors = itemColors,
                            onClick = { viewModel.selectTab(SelectedTab.TODO) }
                        )
                        NavigationBarItem(
                            icon = { 
                                Icon(
                                    imageVector = if (selectedTab == SelectedTab.TAG) Icons.Default.Tag else Icons.Outlined.Tag, 
                                    contentDescription = "标签",
                                    modifier = Modifier.scale(tagScale)
                                ) 
                            },
                            selected = selectedTab == SelectedTab.TAG,
                            label = { 
                                Text(
                                    text = "标签",
                                    fontWeight = if (selectedTab == SelectedTab.TAG) FontWeight.ExtraBold else FontWeight.Medium,
                                    fontSize = if (selectedTab == SelectedTab.TAG) 13.sp else 12.sp
                                ) 
                            },
                            colors = itemColors,
                            onClick = { viewModel.selectTab(SelectedTab.TAG) }
                        )

                    }
                }
            },
            floatingActionButton = {
                if (selectedNote == null && !isDiaryEditMode && selectedTab != SelectedTab.DIARY && selectedTab != SelectedTab.TODO) {
                    FloatingActionButton(
                        onClick = {
                            // Default Note Addition
                            showCreateNoteDialog = true
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "添加", modifier = Modifier.size(28.dp))
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = innerPadding.calculateTopPadding(),
                        bottom = if (isKeyboardOpen) 0.dp else innerPadding.calculateBottomPadding(),
                        start = innerPadding.calculateStartPadding(androidx.compose.ui.platform.LocalLayoutDirection.current),
                        end = innerPadding.calculateEndPadding(androidx.compose.ui.platform.LocalLayoutDirection.current)
                    )
                    .imePadding()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Search bar overlay toggle
                    if (showSearchOverlay) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            placeholder = { Text("关键词过滤内容...") },
                            trailingIcon = {
                                IconButton(onClick = {
                                    viewModel.setSearchQuery("")
                                    showSearchOverlay = false
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "清除")
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }

                    // Master View Switcher
                    val isEditing = selectedNote != null && selectedTab != SelectedTab.NOTES
                    AnimatedContent(
                        targetState = isEditing,
                        transitionSpec = {
                            if (targetState && !initialState) {
                                slideInHorizontally { width -> width } + fadeIn() togetherWith slideOutHorizontally { width -> -width } + fadeOut()
                            } else if (!targetState && initialState) {
                                slideInHorizontally { width -> -width } + fadeIn() togetherWith slideOutHorizontally { width -> width } + fadeOut()
                            } else {
                                fadeIn() togetherWith fadeOut()
                            }
                        },
                        label = "master_note_transition"
                    ) { editing ->
                        if (editing) {
                            selectedNote?.let { targetNote ->
                                NoteEditorView(
                                    note = targetNote,
                                    viewModel = viewModel,
                                    onBack = {
                                        viewModel.selectNote(null)
                                    }
                                )
                            }
                        } else {
                            AnimatedContent(
                                targetState = selectedTab,
                                transitionSpec = {
                                    fadeIn() togetherWith fadeOut()
                                },
                                label = "tab_transition"
                            ) { currTab ->
                                when (currTab) {
                                    SelectedTab.NOTES -> NotesTabView(viewModel = viewModel)
                                    SelectedTab.DIARY -> DiaryTabView(viewModel = viewModel)
                                    SelectedTab.TODO -> TodoTabView(viewModel = viewModel)
                                    SelectedTab.TAG -> TagCategorisationView(viewModel = viewModel)
                                    SelectedTab.SETTINGS -> SettingsTabView(viewModel = viewModel)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Dynamic Dialogs ---
    if (showCreateFolderDialog) {
        var folderNameInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("新建目录", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = folderNameInput,
                    onValueChange = { folderNameInput = it },
                    placeholder = { Text("例如：个人小计、app开发") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderNameInput.isNotBlank()) {
                            viewModel.createFolder(folderNameInput, folderDialogParentId)
                            showCreateFolderDialog = false
                            folderNameInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("新增", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showCreateNoteDialog) {
        val folders by viewModel.allFolders.collectAsState()
        val currentFolderId by viewModel.currentFolderId.collectAsState()
        
        var noteTitleInput by remember { mutableStateOf("") }
        var selectedFolderId by remember(currentFolderId) { mutableStateOf(currentFolderId) }
        var dropdownExpanded by remember { mutableStateOf(false) }

        val selectedFolderText = remember(selectedFolderId, folders) {
            if (selectedFolderId == null) {
                "根目录"
            } else {
                folders.find { it.id == selectedFolderId }?.name ?: "根目录"
            }
        }

        AlertDialog(
            onDismissRequest = { showCreateNoteDialog = false },
            title = {
                Text(
                    text = "新建页面",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Page Name Input Field
                    OutlinedTextField(
                        value = noteTitleInput,
                        onValueChange = { noteTitleInput = it },
                        label = { Text("页面名称") },
                        placeholder = { Text("例: 读书笔记, 周会纪要...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Folder selection section
                    Column {
                        Text(
                            text = "所在文件夹",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                .clickable { dropdownExpanded = true }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedFolderText,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "展开文件夹列表",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.75f)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("根目录") },
                                    onClick = {
                                        selectedFolderId = null
                                        dropdownExpanded = false
                                    }
                                )
                                folders.forEach { folder ->
                                    DropdownMenuItem(
                                        text = { Text(folder.name) },
                                        onClick = {
                                            selectedFolderId = folder.id
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (noteTitleInput.isNotBlank()) {
                            viewModel.createNote(
                                title = noteTitleInput,
                                content = "",
                                folderId = selectedFolderId,
                                tags = ""
                            )
                            showCreateNoteDialog = false
                            noteTitleInput = ""
                        }
                    },
                    enabled = noteTitleInput.isNotBlank()
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateNoteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("关于 项目笔记 app", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("版本：2026.1.0-Emerald", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("模仿高级知识管理工具（Obsidian / Logseq），为移动端提供流畅的本地 Room 离线双链管理与子弹日记追踪。")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("【主要特色】", fontWeight = FontWeight.Bold)
                    Text("1. 双链图谱：在 Canvas 上构建力导向文件夹及标签联系拓扑图。")
                    Text("2. 子弹日记：按照 Screenshot 2 完美缩进及高亮子节点。")
                    Text("3. 热力图：侧滑菜单展示日常打卡活跃矩阵，点击日期一键转写。")
                    Text("4. Gemini AI：对笔记智能提取并关联建议。")
                }
            },
            confirmButton = {
                Button(onClick = { showSettingsDialog = false }) {
                    Text("明白")
                }
            }
        )
    }
}

@Composable
fun SidebarFolderNode(
    folder: com.example.data.model.Folder,
    allFolders: List<com.example.data.model.Folder>,
    level: Int,
    viewModel: NoteViewModel,
    onCloseDrawer: () -> Unit,
    onCreateFolder: (Long?) -> Unit,
    onRenameFolder: (Long, String) -> Unit,
    onDeleteFolder: (Long) -> Unit
) {
    val subFolders = allFolders.filter { it.parentId == folder.id }
    var isExpanded by remember { mutableStateOf(level == 0) }
    var showRenameDialog by remember { mutableStateOf(false) }

    if (showRenameDialog) {
        var newName by remember { mutableStateOf(folder.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("重命名目录") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotBlank()) {
                        onRenameFolder(folder.id, newName)
                    }
                    showRenameDialog = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("取消") }
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = (level * 24).dp,
                top = if (level == 0) 8.dp else 6.dp, 
                bottom = if (level == 0) 8.dp else 6.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (subFolders.isNotEmpty()) {
            IconButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Default.KeyboardArrowRight,
                    contentDescription = "展开/折叠",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Spacer(modifier = Modifier.width(24.dp))
        }

        Spacer(modifier = Modifier.width(2.dp))
        Icon(
            if (level == 0) Icons.Default.FolderOpen else Icons.Default.Folder, 
            contentDescription = null, 
            modifier = Modifier.size(if (level == 0) 24.dp else 22.dp), 
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = folder.name,
            fontWeight = if (level == 0) FontWeight.Bold else FontWeight.Medium,
            fontSize = if (level == 0) 18.sp else 16.sp,
            color = if (level == 0) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            modifier = Modifier
                .weight(1f)
                .clickable {
                    viewModel.navigateToFolder(folder.id)
                    viewModel.selectTab(SelectedTab.NOTES)
                    onCloseDrawer()
                }
        )
        
        var showMenu by remember { mutableStateOf(false) }
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = "更多选项", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("新建子目录") },
                    onClick = {
                        onCreateFolder(folder.id)
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("重命名") },
                    onClick = {
                        showRenameDialog = true
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("删除目录") },
                    onClick = {
                        onDeleteFolder(folder.id)
                        showMenu = false
                    }
                )
            }
        }
    }

    if (isExpanded && subFolders.isNotEmpty()) {
        subFolders.forEach { sub ->
            SidebarFolderNode(
                folder = sub,
                allFolders = allFolders,
                level = level + 1,
                viewModel = viewModel,
                onCloseDrawer = onCloseDrawer,
                onCreateFolder = onCreateFolder,
                onRenameFolder = onRenameFolder,
                onDeleteFolder = onDeleteFolder
            )
        }
    }
}

sealed class SidebarSelection {
    object Unclassified : SidebarSelection()
    object Favorites : SidebarSelection()
    object Recents : SidebarSelection()
    object Settings : SidebarSelection()
    data class FolderSelect(val folderId: Long) : SidebarSelection()
}

fun getNoteCountRecursive(
    folderId: Long,
    allFolders: List<com.example.data.model.Folder>,
    allNotes: List<com.example.data.model.Note>,
    visited: Set<Long> = emptySet()
): Int {
    if (folderId in visited) return 0
    val newVisited = visited + folderId
    var count = allNotes.count { it.folderId == folderId }
    val childFolders = allFolders.filter { it.parentId == folderId }
    childFolders.forEach { child ->
        count += getNoteCountRecursive(child.id, allFolders, allNotes, newVisited)
    }
    return count
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SidebarFolderTreeItem(
    folder: com.example.data.model.Folder,
    allFolders: List<com.example.data.model.Folder>,
    allNotes: List<com.example.data.model.Note>,
    selectedSelection: SidebarSelection,
    level: Int,
    isDark: Boolean,
    onSelectFolder: (Long) -> Unit,
    onCreateFolder: (Long?) -> Unit,
    onRenameFolder: (Long, String) -> Unit,
    onDeleteFolder: (Long) -> Unit,
    onMoveFolder: (Long, Long?) -> Unit,
    expandedFolderIds: Set<Long>,
    onToggleExpand: (Long) -> Unit
) {
    val subFolders = allFolders.filter { it.parentId == folder.id }
    val isExpanded = expandedFolderIds.contains(folder.id)
    var showRenameDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val isDescendant = remember(allFolders) {
        { targetId: Long, parentId: Long? ->
            var curr = parentId
            var isChild = false
            var count = 0
            while (curr != null && count < 100) {
                if (curr == targetId) {
                    isChild = true
                    break
                }
                curr = allFolders.find { it.id == curr }?.parentId
                count++
            }
            isChild
        }
    }

    val isActive = selectedSelection is SidebarSelection.FolderSelect && selectedSelection.folderId == folder.id
    val noteCount = remember(folder.id, allNotes, allFolders) {
        getNoteCountRecursive(folder.id, allFolders, allNotes)
    }

    if (showRenameDialog) {
        var newName by remember { mutableStateOf(folder.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("重命名目录") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotBlank()) {
                        onRenameFolder(folder.id, newName)
                    }
                    showRenameDialog = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("取消") }
            }
        )
    }

    if (showMoveDialog) {
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text("移动目录") },
            text = {
                val eligibleFolders = remember(folder.id, allFolders) {
                    allFolders.filter { f ->
                        f.id != folder.id && !isDescendant(folder.id, f.id)
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onMoveFolder(folder.id, null)
                                    showMoveDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("根目录 (无父目录)", fontSize = 13.sp)
                        }
                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                    }
                    items(eligibleFolders) { targetFolder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onMoveFolder(folder.id, targetFolder.id)
                                    showMoveDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(targetFolder.name, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoveDialog = false }) { Text("取消") }
            }
        )
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (isActive) {
                        if (isDark) Color(0xFF2C2C2E) else Color(0xFFE8E8E8)
                    } else {
                        Color.Transparent
                    }
                )
                .combinedClickable(
                    onClick = { onSelectFolder(folder.id) },
                    onLongClick = { showMenu = true }
                )
                .padding(vertical = 9.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Indent based on level (mimics hierarchy beautifully)
            Spacer(modifier = Modifier.width((level * 12).dp))

            // Expand/Collapse Toggle Arrow Button
            if (subFolders.isNotEmpty()) {
                IconButton(
                    onClick = { onToggleExpand(folder.id) },
                    modifier = Modifier.size(18.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Default.KeyboardArrowRight,
                        contentDescription = "展开折叠",
                        modifier = Modifier.size(14.dp),
                        tint = if (isDark) Color(0xFFBFBFBF) else Color(0xFF666666)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(18.dp))
            }

            Spacer(modifier = Modifier.width(4.dp))
            
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = Color(0xFF10B981)
            )
            Spacer(modifier = Modifier.width(6.dp))
            
            Text(
                text = folder.name,
                fontSize = 14.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = if (isActive) MaterialTheme.colorScheme.primary else (if (isDark) Color(0xFFECECEC) else Color(0xFF333333)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = "($noteCount)",
                fontSize = 11.sp,
                color = if (isDark) Color(0xFF666666) else Color(0xFFBFBFBF),
                modifier = Modifier.padding(end = 4.dp)
            )

            // Options 3-dots
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "选项",
                        modifier = Modifier.size(12.dp),
                        tint = if (isDark) Color(0xFF666666) else Color(0xFFADADAD)
                    )
                }

                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("新建子目录", fontSize = 12.sp) },
                        onClick = {
                            onCreateFolder(folder.id)
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("重命名", fontSize = 12.sp) },
                        onClick = {
                            showRenameDialog = true
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("移动目录", fontSize = 12.sp) },
                        onClick = {
                            showMoveDialog = true
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("删除目录", fontSize = 12.sp) },
                        onClick = {
                            onDeleteFolder(folder.id)
                            showMenu = false
                        }
                    )
                }
            }
        }

        // Recursive child folders
        if (isExpanded && subFolders.isNotEmpty()) {
            subFolders.forEach { child ->
                SidebarFolderTreeItem(
                    folder = child,
                    allFolders = allFolders,
                    allNotes = allNotes,
                    selectedSelection = selectedSelection,
                    level = level + 1,
                    isDark = isDark,
                    onSelectFolder = onSelectFolder,
                    onCreateFolder = onCreateFolder,
                    onRenameFolder = onRenameFolder,
                    onDeleteFolder = onDeleteFolder,
                    onMoveFolder = onMoveFolder,
                    expandedFolderIds = expandedFolderIds,
                    onToggleExpand = onToggleExpand
                )
            }
        }
    }
}

@Composable
fun SidebarLeftItem(
    title: String,
    count: Int?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) {
        if (isDark) Color(0xFF2C2C2E) else Color(0xFFE8E8E8)
    } else {
        Color.Transparent
    }
    val textColor = if (bg != Color.Transparent) {
        MaterialTheme.colorScheme.primary
    } else {
        if (isDark) Color(0xFFECECEC) else Color(0xFF333333)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (isSelected) MaterialTheme.colorScheme.primary else (if (isDark) Color(0xFF999999) else Color(0xFF666666))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
            modifier = Modifier.weight(1f)
        )
        if (count != null) {
            Text(
                text = "($count)",
                fontSize = 12.sp,
                color = if (isDark) Color(0xFF666666) else Color(0xFFBFBFBF),
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
fun MonthlyHeatmapWidget(
    diaries: List<com.example.data.model.Diary>,
    isDark: Boolean,
    onDateClicked: (String) -> Unit
) {
    val calendar = remember { Calendar.getInstance() }
    val currentYear = remember { calendar.get(Calendar.YEAR) }
    val currentMonthIdx = remember { calendar.get(Calendar.MONTH) } // 0-indexed
    val monthDisplayName = remember { 
        SimpleDateFormat("yyyy年 M月", Locale.getDefault()).format(Date())
    }

    // Days in current month
    val daysInMonth = remember(currentYear, currentMonthIdx) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, currentYear)
        cal.set(Calendar.MONTH, currentMonthIdx)
        cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    // Day of week for the 1st date of this month (1 = Sunday, 2 = Monday, ...)
    val firstDayOfWeek = remember(currentYear, currentMonthIdx) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, currentYear)
        cal.set(Calendar.MONTH, currentMonthIdx)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.get(Calendar.DAY_OF_WEEK)
    }

    val diaryLengthMap = remember(diaries) {
        diaries.associate { it.date to it.content.length }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isDark) Color(0xFF2C2C2E) else Color(0xFFEEEEEE))
            .padding(12.dp)
    ) {
        // Calendar Title / Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = monthDisplayName,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color(0xFFECECEC) else Color(0xFF333333)
            )
            Text(
                text = "日记活跃度",
                fontSize = 11.sp,
                color = if (isDark) Color(0xFF888888) else Color(0xFF999999)
            )
        }

        // Weekdays Header Row: Sun, Mon, Tue, Wed, Thu, Fri, Sat
        val weekdays = listOf("日", "一", "二", "三", "四", "五", "六")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            weekdays.forEach { dayLabel ->
                Text(
                    text = dayLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDark) Color(0xFF777777) else Color(0xFF888888),
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Create the matrix flow rows for calendar
        // Since calendar starts on Sunday (day 1), we have `firstDayOfWeek - 1` empty cells
        val totalCells = (firstDayOfWeek - 1) + daysInMonth
        val rowsCount = (totalCells + 6) / 7

        val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            for (rowIndex in 0 until rowsCount) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (colIndex in 0 until 7) {
                        val cellIndex = rowIndex * 7 + colIndex
                        val dayNumber = cellIndex - (firstDayOfWeek - 2)

                        if (dayNumber in 1..daysInMonth) {
                            // Date string construction
                            val dateString = remember(dayNumber) {
                                val cal = Calendar.getInstance()
                                cal.set(Calendar.YEAR, currentYear)
                                cal.set(Calendar.MONTH, currentMonthIdx)
                                cal.set(Calendar.DAY_OF_MONTH, dayNumber)
                                sdf.format(cal.time)
                            }

                            val contentLength = diaryLengthMap[dateString]
                            val activeLevel = when {
                                contentLength == null -> 0
                                contentLength > 200 -> 4
                                contentLength > 100 -> 3
                                contentLength > 30 -> 2
                                else -> 1
                            }

                            val tileColor = if (isDark) {
                                when (activeLevel) {
                                    1 -> Color(0xFF064E3B)
                                    2 -> Color(0xFF065F46)
                                    3 -> Color(0xFF047857)
                                    4 -> Color(0xFF10B981)
                                    else -> Color(0xFF3A3A3C) // Unactive dark tile matching apple's list
                                }
                            } else {
                                when (activeLevel) {
                                    1 -> Color(0xFF6EE7B7)
                                    2 -> Color(0xFF34D399)
                                    3 -> Color(0xFF10B981)
                                    4 -> Color(0xFF059669)
                                    else -> Color(0xFFE5E5EA) // Clean calendar background cell light grey
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 2.dp) // even spaces
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(tileColor)
                                    .clickable {
                                        onDateClicked(dateString)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dayNumber.toString(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (activeLevel > 0) {
                                        Color.White
                                    } else {
                                        if (isDark) Color(0xFF8E8E93) else Color(0xFF8E8E93)
                                    },
                                    style = androidx.compose.ui.text.TextStyle(
                                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                )
                            }
                        } else {
                            // Empty placeholder box
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 2.dp)
                                    .aspectRatio(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SidebarRightFolderItem(
    subFolder: com.example.data.model.Folder,
    allFolders: List<com.example.data.model.Folder>,
    allNotes: List<com.example.data.model.Note>,
    isAppDarkMode: Boolean,
    isSubFolderExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onEnterFolder: (Long) -> Unit,
    onCreateSubFolder: (Long) -> Unit,
    onRenameFolder: (Long, String) -> Unit,
    onDeleteFolder: (Long) -> Unit,
    onMoveFolder: (Long, Long?) -> Unit,
    viewModel: NoteViewModel,
    currentFolderId: Long?,
    onCloseDrawer: () -> Unit
) {
    val selectedNote by viewModel.selectedNote.collectAsState()

    val subFolderNotes = remember(allNotes, subFolder.id) {
        allNotes.filter { it.folderId == subFolder.id }
    }

    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }

    val isDescendant = remember(allFolders) {
        { targetId: Long, parentId: Long? ->
            var curr = parentId
            var isChild = false
            var count = 0
            while (curr != null && count < 100) {
                if (curr == targetId) {
                    isChild = true
                    break
                }
                curr = allFolders.find { it.id == curr }?.parentId
                count++
            }
            isChild
        }
    }

    if (showRenameDialog) {
        var newName by remember { mutableStateOf(subFolder.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("重命名目录") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotBlank()) {
                        onRenameFolder(subFolder.id, newName)
                    }
                    showRenameDialog = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("取消") }
            }
        )
    }

    if (showMoveDialog) {
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text("移动目录") },
            text = {
                val eligibleFolders = remember(subFolder.id, allFolders) {
                    allFolders.filter { f ->
                        f.id != subFolder.id && !isDescendant(subFolder.id, f.id)
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onMoveFolder(subFolder.id, null)
                                    showMoveDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("根目录 (无父目录)", fontSize = 13.sp)
                        }
                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                    }
                    items(eligibleFolders) { targetFolder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onMoveFolder(subFolder.id, targetFolder.id)
                                    showMoveDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(targetFolder.name, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoveDialog = false }) { Text("取消") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isAppDarkMode) Color(0xFF222224) else Color(0xFFF9F9F9))
            .padding(vertical = 4.dp, horizontal = 4.dp)
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .combinedClickable(
                        onClick = { onEnterFolder(subFolder.id) },
                        onLongClick = { showMenu = true }
                    )
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onToggleExpand() }
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = if (isSubFolderExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Default.KeyboardArrowRight,
                        contentDescription = "展开折叠",
                        modifier = Modifier.size(16.dp),
                        tint = if (isAppDarkMode) Color(0xFF999999) else Color(0xFF666666)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFF10B981)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = subFolder.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isAppDarkMode) Color(0xFFECECEC) else Color(0xFF333333),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "(${subFolderNotes.size})",
                    fontSize = 11.sp,
                    color = if (isAppDarkMode) Color(0xFF666666) else Color(0xFF999999)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("新建子目录", fontSize = 12.sp) },
                    onClick = {
                        onCreateSubFolder(subFolder.id)
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("重命名", fontSize = 12.sp) },
                    onClick = {
                        showRenameDialog = true
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("移动目录", fontSize = 12.sp) },
                    onClick = {
                        showMoveDialog = true
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("删除目录", fontSize = 12.sp) },
                    onClick = {
                        onDeleteFolder(subFolder.id)
                        showMenu = false
                    }
                )
            }
        }

        if (isSubFolderExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 2.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (subFolderNotes.isEmpty()) {
                    Text(
                        text = "无页面",
                        fontSize = 11.sp,
                        color = if (isAppDarkMode) Color(0xFF555555) else Color(0xFFC0C0C0),
                        modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp)
                    )
                } else {
                    subFolderNotes.forEach { snote ->
                        SidebarNoteItem(
                            note = snote,
                            isNoteSelected = selectedNote?.id == snote.id,
                            isAppDarkMode = isAppDarkMode,
                            viewModel = viewModel,
                            currentFolderId = currentFolderId,
                            onCloseDrawer = onCloseDrawer
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SidebarNoteItem(
    note: com.example.data.model.Note,
    isNoteSelected: Boolean,
    isAppDarkMode: Boolean,
    viewModel: NoteViewModel,
    currentFolderId: Long?,
    onCloseDrawer: () -> Unit
) {
    var showNoteMenu by remember { mutableStateOf(false) }
    var showRenameNoteDialog by remember { mutableStateOf(false) }
    var showMoveNoteDialog by remember { mutableStateOf(false) }

    val noteItemBg = if (isNoteSelected) {
        if (isAppDarkMode) Color(0xFF2C2C2E) else Color(0xFFF2F2F2)
    } else {
        Color.Transparent
    }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(noteItemBg)
                .combinedClickable(
                    onClick = {
                        viewModel.selectNote(note)
                        if (note.folderId != currentFolderId) {
                            viewModel.navigateToFolder(note.folderId)
                        }
                        viewModel.selectTab(SelectedTab.NOTES)
                        onCloseDrawer()
                    },
                    onLongClick = {
                        showNoteMenu = true
                    }
                )
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (note.isTodo) Icons.Default.TaskAlt else Icons.Default.Description,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (note.isTodo) MaterialTheme.colorScheme.secondary else (if (isAppDarkMode) Color(0xFF999999) else Color(0xFF666666))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = note.title.ifEmpty { "无标题" },
                fontSize = 13.sp,
                color = if (isNoteSelected) MaterialTheme.colorScheme.primary else (if (isAppDarkMode) Color(0xFFECECEC) else Color(0xFF333333)),
                fontWeight = if (isNoteSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        DropdownMenu(
            expanded = showNoteMenu,
            onDismissRequest = { showNoteMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("编辑页面", fontSize = 12.sp) },
                onClick = {
                    showNoteMenu = false
                    viewModel.selectNote(note)
                    if (note.folderId != currentFolderId) {
                        viewModel.navigateToFolder(note.folderId)
                    }
                    viewModel.selectTab(SelectedTab.NOTES)
                    onCloseDrawer()
                }
            )
            DropdownMenuItem(
                text = { Text("重命名", fontSize = 12.sp) },
                onClick = {
                    showNoteMenu = false
                    showRenameNoteDialog = true
                }
            )
            DropdownMenuItem(
                text = { Text("移动至...", fontSize = 12.sp) },
                onClick = {
                    showNoteMenu = false
                    showMoveNoteDialog = true
                }
            )
            DropdownMenuItem(
                text = { Text("删除页面", fontSize = 12.sp) },
                onClick = {
                    showNoteMenu = false
                    viewModel.deleteNote(note.id)
                }
            )
        }
    }

    if (showRenameNoteDialog) {
        var newTitle by remember { mutableStateOf(note.title) }
        AlertDialog(
            onDismissRequest = { showRenameNoteDialog = false },
            title = { Text("重命名页面") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newTitle.isNotBlank()) {
                        viewModel.updateNoteContent(
                            note = note,
                            newTitle = newTitle,
                            newContent = note.content,
                            newTags = note.tags,
                            isTodo = note.isTodo,
                            isTodoDone = note.isTodoDone
                        )
                    }
                    showRenameNoteDialog = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameNoteDialog = false }) { Text("取消") }
            }
        )
    }

    if (showMoveNoteDialog) {
        val folders by viewModel.allFolders.collectAsState()
        AlertDialog(
            onDismissRequest = { showMoveNoteDialog = false },
            title = { Text("移动目录") },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.moveNote(note.id, null)
                                    showMoveNoteDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("根目录 (未分类)", fontSize = 13.sp)
                        }
                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                    }
                    items(folders) { targetFolder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.moveNote(note.id, targetFolder.id)
                                    showMoveNoteDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(targetFolder.name, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoveNoteDialog = false }) { Text("取消") }
            }
        )
    }
}

// --- Component 1: SidebarDrawerContent (Matches Screenshot 3) ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SidebarDrawerContent(
    viewModel: NoteViewModel,
    onCreateFolder: (Long?) -> Unit,
    onCreateNote: () -> Unit,
    onCloseDrawer: () -> Unit
) {
    val folders by viewModel.allFolders.collectAsState()
    val diaries by viewModel.allDiaries.collectAsState()
    val allNotes by viewModel.allNotes.collectAsState()
    val currentFolderId by viewModel.currentFolderId.collectAsState()
    val selectedNote by viewModel.selectedNote.collectAsState()
    val isAppDarkMode by viewModel.isDarkMode.collectAsState()

    // Left and Right column background colors
    val leftBgColor = if (isAppDarkMode) Color(0xFF1E1E1E) else Color(0xFFF7F7F7)
    val rightBgColor = if (isAppDarkMode) Color(0xFF121212) else Color(0xFFFFFFFF)

    // Selection state initialized to match the current selected folder if active, or default to Recents
    var selectedSelection by remember {
        mutableStateOf<SidebarSelection>(
            currentFolderId?.let { SidebarSelection.FolderSelect(it) } ?: SidebarSelection.Recents
        )
    }

    val expandedFolderIds = remember {
        mutableStateOf(folders.filter { it.parentId == null }.map { it.id }.toSet())
    }

    // Keep Sidebar selection synced in case user navigates folders on main page
    LaunchedEffect(currentFolderId) {
        if (currentFolderId != null) {
            selectedSelection = SidebarSelection.FolderSelect(currentFolderId!!)
        } else if (selectedSelection is SidebarSelection.FolderSelect) {
            selectedSelection = SidebarSelection.Recents
        }
    }

    // Dynamic Counts
    val unclassifiedCount = remember(allNotes) { allNotes.count { it.folderId == null } }
    val starCount = remember(allNotes) { allNotes.count { it.isFavorite } }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(rightBgColor)
    ) {
        // --- Left Column (Folders & Navigation) (47% width) ---
        Column(
            modifier = Modifier
                .weight(0.47f)
                .fillMaxHeight()
                .background(leftBgColor)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Monthly Heatmap Widget placed at the Left Column top
                MonthlyHeatmapWidget(
                    diaries = diaries,
                    isDark = isAppDarkMode,
                    onDateClicked = { clickedDate ->
                        viewModel.selectDate(clickedDate)
                        viewModel.selectTab(SelectedTab.DIARY)
                        onCloseDrawer()
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Section 1: Standard Categories mimicking the top items of the screenshot
                SidebarLeftItem(
                    title = "最近使用",
                    count = null,
                    icon = Icons.Default.History,
                    isSelected = selectedSelection is SidebarSelection.Recents,
                    isDark = isAppDarkMode
                ) {
                    selectedSelection = SidebarSelection.Recents
                }

                SidebarLeftItem(
                    title = "星标",
                    count = starCount,
                    icon = Icons.Default.Star,
                    isSelected = selectedSelection is SidebarSelection.Favorites,
                    isDark = isAppDarkMode
                ) {
                    selectedSelection = SidebarSelection.Favorites
                }

                SidebarLeftItem(
                    title = "未分类",
                    count = unclassifiedCount,
                    icon = Icons.Default.Inbox,
                    isSelected = selectedSelection is SidebarSelection.Unclassified,
                    isDark = isAppDarkMode
                ) {
                    selectedSelection = SidebarSelection.Unclassified
                    viewModel.navigateToFolder(null)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Divider line mimicking screenshot
                HorizontalDivider(
                    thickness = 1.dp,
                    color = if (isAppDarkMode) Color(0xFF2E2E30) else Color(0xFFE8E8E8),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Section 2: Nested Document Tree
                val rootFolders = folders.filter { it.parentId == null }
                rootFolders.forEach { f ->
                    SidebarFolderTreeItem(
                        folder = f,
                        allFolders = folders,
                        allNotes = allNotes,
                        selectedSelection = selectedSelection,
                        level = 0,
                        isDark = isAppDarkMode,
                        onSelectFolder = { fId ->
                            selectedSelection = SidebarSelection.FolderSelect(fId)
                            viewModel.navigateToFolder(fId)
                        },
                        onCreateFolder = onCreateFolder,
                        onRenameFolder = { id, name -> viewModel.renameFolder(id, name) },
                        onDeleteFolder = { id -> viewModel.deleteFolder(id) },
                        onMoveFolder = { id, parentId -> viewModel.moveFolder(id, parentId) },
                        expandedFolderIds = expandedFolderIds.value,
                        onToggleExpand = { fId ->
                            val folderObj = folders.find { it.id == fId }
                            val parentId = folderObj?.parentId
                            val isCurrentlyExpanded = expandedFolderIds.value.contains(fId)
                            if (isCurrentlyExpanded) {
                                expandedFolderIds.value = expandedFolderIds.value - fId
                            } else {
                                val siblings = folders.filter { it.parentId == parentId }.map { it.id }.toSet()
                                expandedFolderIds.value = (expandedFolderIds.value - siblings) + fId
                            }
                        }
                    )
                }

                // Add Folder Action button at the bottom of the tree
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onCreateFolder(null) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "新建目录",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "新建目录",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Section 3: Bottom Gear Settings button aligned cleanly
            HorizontalDivider(
                thickness = 1.dp,
                color = if (isAppDarkMode) Color(0xFF2E2E30) else Color(0xFFE8E8E8),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )

            SidebarLeftItem(
                title = "设置",
                count = null,
                icon = Icons.Default.Settings,
                isSelected = selectedSelection is SidebarSelection.Settings,
                isDark = isAppDarkMode
            ) {
                selectedSelection = SidebarSelection.Settings
                viewModel.selectNote(null)
                viewModel.selectTab(SelectedTab.SETTINGS)
                onCloseDrawer()
            }
        }

        // --- Vertical Column Divider ---
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(if (isAppDarkMode) Color(0xFF2A2A2C) else Color(0xFFE5E5E5))
        )

        // --- Right Column (Filtered Notes under selected category) (53% width) ---
        val notesToShow = remember(selectedSelection, allNotes) {
            when (selectedSelection) {
                is SidebarSelection.Unclassified -> allNotes.filter { it.folderId == null }
                is SidebarSelection.Favorites -> allNotes.filter { it.isFavorite }
                is SidebarSelection.Recents -> allNotes.sortedByDescending { it.updatedAt }.take(25)
                is SidebarSelection.FolderSelect -> {
                    val fId = (selectedSelection as SidebarSelection.FolderSelect).folderId
                    allNotes.filter { it.folderId == fId }
                }
                else -> emptyList()
            }
        }

        Column(
            modifier = Modifier
                .weight(0.53f)
                .fillMaxHeight()
                .background(rightBgColor)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            // Header with active category/folder title & inline Add Note button
            val currentHeaderTitle = when (selectedSelection) {
                is SidebarSelection.Unclassified -> "未分类"
                is SidebarSelection.Favorites -> "星标"
                is SidebarSelection.Recents -> "最近使用"
                is SidebarSelection.FolderSelect -> {
                    val fId = (selectedSelection as SidebarSelection.FolderSelect).folderId
                    folders.find { it.id == fId }?.name ?: "已选目录"
                }
                is SidebarSelection.Settings -> "设置"
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = currentHeaderTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isAppDarkMode) Color(0xFFF0F0F0) else Color(0xFF333333),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Inline quick create folder and create page buttons
                if (selectedSelection !is SidebarSelection.Settings) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val activeFolderId = when (selectedSelection) {
                                    is SidebarSelection.FolderSelect -> (selectedSelection as SidebarSelection.FolderSelect).folderId
                                    else -> null
                                }
                                onCreateFolder(activeFolderId)
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CreateNewFolder,
                                contentDescription = "新建目录",
                                modifier = Modifier.size(18.dp),
                                tint = Color(0xFF10B981)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (selectedSelection is SidebarSelection.FolderSelect) {
                                    viewModel.navigateToFolder((selectedSelection as SidebarSelection.FolderSelect).folderId)
                                } else {
                                    viewModel.navigateToFolder(null)
                                }
                                onCreateNote()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "新建页面",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val subFolders = remember(selectedSelection, folders) {
                if (selectedSelection is SidebarSelection.FolderSelect) {
                    val fId = (selectedSelection as SidebarSelection.FolderSelect).folderId
                    folders.filter { it.parentId == fId }
                } else {
                    emptyList()
                }
            }

            var expandedSubfolderId by remember(selectedSelection) { mutableStateOf<Long?>(null) }

            if (selectedSelection is SidebarSelection.FolderSelect) {
                val fId = (selectedSelection as SidebarSelection.FolderSelect).folderId
                val directNotes = remember(allNotes, fId) { allNotes.filter { it.folderId == fId } }
                
                if (subFolders.isEmpty() && directNotes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "无页面",
                            fontSize = 12.sp,
                            color = if (isAppDarkMode) Color(0xFF555555) else Color(0xFFC0C0C0)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 1. Subfolders
                        subFolders.forEach { subFolder ->
                            SidebarRightFolderItem(
                                subFolder = subFolder,
                                allFolders = folders,
                                allNotes = allNotes,
                                isAppDarkMode = isAppDarkMode,
                                isSubFolderExpanded = expandedSubfolderId == subFolder.id,
                                onToggleExpand = {
                                    expandedSubfolderId = if (expandedSubfolderId == subFolder.id) null else subFolder.id
                                },
                                onEnterFolder = { fId ->
                                    selectedSelection = SidebarSelection.FolderSelect(fId)
                                    viewModel.navigateToFolder(fId)
                                    
                                    // Expand ancestor folder tree in the left column
                                    val ancestors = mutableListOf<Long>()
                                    var current = folders.find { it.id == fId }
                                    while (current != null) {
                                        ancestors.add(current.id)
                                        current = folders.find { it.id == current.parentId }
                                    }
                                    expandedFolderIds.value = expandedFolderIds.value + ancestors
                                },
                                onCreateSubFolder = onCreateFolder,
                                onRenameFolder = { id, name -> viewModel.renameFolder(id, name) },
                                onDeleteFolder = { id -> viewModel.deleteFolder(id) },
                                onMoveFolder = { id, parentId -> viewModel.moveFolder(id, parentId) },
                                viewModel = viewModel,
                                currentFolderId = currentFolderId,
                                onCloseDrawer = onCloseDrawer
                            )
                        }
                        
                        // 2. Direct Notes
                        directNotes.forEach { note ->
                            SidebarNoteItem(
                                note = note,
                                isNoteSelected = selectedNote?.id == note.id,
                                isAppDarkMode = isAppDarkMode,
                                viewModel = viewModel,
                                currentFolderId = currentFolderId,
                                onCloseDrawer = onCloseDrawer
                            )
                        }
                    }
                }
            } else {
                // Not a folder select (Unclassified, Favorites, Recents)
                if (notesToShow.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "无页面",
                            fontSize = 12.sp,
                            color = if (isAppDarkMode) Color(0xFF555555) else Color(0xFFC0C0C0)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        notesToShow.forEach { note ->
                            SidebarNoteItem(
                                note = note,
                                isNoteSelected = selectedNote?.id == note.id,
                                isAppDarkMode = isAppDarkMode,
                                viewModel = viewModel,
                                currentFolderId = currentFolderId,
                                onCloseDrawer = onCloseDrawer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GridModuleCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clickable { onClick() }
            .border(
                width = 1.dp,
                color = if (isActive) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// --- Component 2: Heatmap Matrix (Screenshot 3 Top) ---
@Composable
fun HeatmapWidget(
    diaries: List<Diary>,
    isDark: Boolean,
    onDateClicked: (String) -> Unit
) {
    val weeks = 26
    
    val dateGrid = remember(diaries) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()

        cal.add(Calendar.DAY_OF_YEAR, -(weeks * 7 - 1))
        val startDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        cal.add(Calendar.DAY_OF_YEAR, -(startDayOfWeek - 1)) // start on Sunday

        val list = mutableListOf<String>()
        repeat(weeks * 7) { 
            list.add(sdf.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        list
    }
    
    val diaryLengthMap = remember(diaries) {
        diaries.associate { it.date to it.content.length }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        val scrollState = androidx.compose.foundation.rememberScrollState()
        
        androidx.compose.runtime.LaunchedEffect(scrollState.maxValue) {
            if (scrollState.maxValue > 0) {
                scrollState.scrollTo(scrollState.maxValue)
            }
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
        ) {
            for (weekIndex in 0 until weeks) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(7) { dayIndex ->
                        val flatIndex = weekIndex * 7 + dayIndex
                        val dateString = dateGrid.getOrNull(flatIndex) ?: ""
                        
                        val contentLength = diaryLengthMap[dateString]
                        val activeLevel = when {
                            contentLength == null -> 0
                            contentLength > 200 -> 4
                            contentLength > 100 -> 3
                            contentLength > 30 -> 2
                            else -> 1
                        }

                        val tileColor = if (isDark) {
                            when (activeLevel) {
                                1 -> Color(0xFF064E3B) // Emerald 900
                                2 -> Color(0xFF065F46) // Emerald 800
                                3 -> Color(0xFF047857) // Emerald 700
                                4 -> Color(0xFF10B981) // Emerald 500
                                else -> Color(0xFF334155) // lighter gray for empty cells in dark mode
                            }
                        } else {
                            when (activeLevel) {
                                1 -> Color(0xFF6EE7B7) // Emerald 300
                                2 -> Color(0xFF34D399) // Emerald 400
                                3 -> Color(0xFF10B981) // Emerald 500
                                4 -> Color(0xFF059669) // Emerald 600
                                else -> Color(0xFFE0E0E0) // light gray for empty cells in light mode
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(tileColor)
                                .clickable {
                                    if (dateString.isNotEmpty()) {
                                        onDateClicked(dateString)
                                    }
                                }
                        )
                    }
                }
            }
        }
    }
}

// --- Component 3: FolderDirectoryView (Matches Screenshot 1) ---
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FolderDirectoryView(
    viewModel: NoteViewModel,
    onCreateFolder: (Long?) -> Unit,
    onCreateNote: () -> Unit
) {
    val currentFolderId by viewModel.currentFolderId.collectAsState()
    val allFolders by viewModel.allFolders.collectAsState()
    val allNotes by viewModel.allNotes.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isInSelectionMode by viewModel.isInSelectionMode.collectAsState()
    val selectedNoteIds by viewModel.selectedNoteIds.collectAsState()

    // Dynamic breadcrumb items
    val breadcrumbs = remember(currentFolderId, allFolders) {
        val path = mutableListOf<Folder>()
        var currentId = currentFolderId
        var count = 0
        while (currentId != null && count < 100) {
            val folder = allFolders.find { it.id == currentId }
            if (folder != null) {
                path.add(0, folder)
                currentId = folder.parentId
            } else {
                break
            }
            count++
        }
        path
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Breadcrumbs & top action bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Interactive breadcrumb links
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "根目录",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (currentFolderId == null) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.clickable { viewModel.navigateToFolder(null) }
                )
                
                breadcrumbs.forEach { step ->
                    Text(
                        text = " / ",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = step.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (currentFolderId == step.id) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.clickable { viewModel.navigateToFolder(step.id) }
                    )
                }
            }

            // Right utility actions
            IconButton(onClick = { onCreateFolder(currentFolderId) }) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = "新建目录", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onCreateNote) {
                Icon(Icons.Default.NoteAdd, contentDescription = "新建页面", tint = MaterialTheme.colorScheme.primary)
            }
        }

        // Subfolders and notes of active folder
        val filteredFolders = remember(currentFolderId, allFolders, searchQuery) {
            allFolders.filter { it.parentId == currentFolderId && (searchQuery.isEmpty() || it.name.contains(searchQuery, true)) }
        }

        // 可展开的标签顶栏 (Expandable Tag Top Bar)
        val tags by viewModel.allTags.collectAsState()
        val selectedTagFilter by viewModel.selectedTagFilter.collectAsState()
        
        if (tags.isNotEmpty()) {
            var isTagsExpanded by remember { mutableStateOf(false) }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tag,
                            contentDescription = "标签过滤",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (selectedTagFilter.isEmpty()) "页面标签过滤" else "已选标签: ${selectedTagFilter.joinToString(", ")}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    TextButton(
                        onClick = { isTagsExpanded = !isTagsExpanded },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(22.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = if (isTagsExpanded) "收起" else "展开",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                imageVector = if (isTagsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
                
                AnimatedVisibility(
                    visible = isTagsExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        InputChip(
                            selected = selectedTagFilter.isEmpty(),
                            onClick = {
                                viewModel.selectTagFilter(null)
                            },
                            label = { Text("全部", fontSize = 10.sp) },
                            modifier = Modifier.height(24.dp),
                            colors = InputChipDefaults.inputChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            )
                        )
                        
                        tags.forEach { (tag, count) ->
                            val isSelected = selectedTagFilter.contains(tag)
                            InputChip(
                                selected = isSelected,
                                onClick = {
                                    viewModel.toggleTagFilter(tag)
                                },
                                label = { Text("#$tag ($count)", fontSize = 10.sp) },
                                modifier = Modifier.height(24.dp),
                                colors = InputChipDefaults.inputChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                )
                            )
                        }
                    }
                }
                
                AnimatedVisibility(
                    visible = !isTagsExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        InputChip(
                            selected = selectedTagFilter.isEmpty(),
                            onClick = {
                                viewModel.selectTagFilter(null)
                            },
                            label = { Text("全部", fontSize = 10.sp) },
                            modifier = Modifier.height(24.dp),
                            colors = InputChipDefaults.inputChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            )
                        )
                        
                        tags.forEach { (tag, count) ->
                            val isSelected = selectedTagFilter.contains(tag)
                            InputChip(
                                selected = isSelected,
                                onClick = {
                                    viewModel.toggleTagFilter(tag)
                                },
                                label = { Text("#$tag ($count)", fontSize = 10.sp) },
                                modifier = Modifier.height(24.dp),
                                colors = InputChipDefaults.inputChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                )
                            )
                        }
                    }
                }
            }
        }

        val filteredNotes = remember(currentFolderId, allNotes, searchQuery, selectedTagFilter) {
            allNotes.filter { note ->
                val noteTags = note.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val matchesTags = selectedTagFilter.isEmpty() || selectedTagFilter.all { tag -> noteTags.contains(tag) }
                note.folderId == currentFolderId && 
                        (searchQuery.isEmpty() || note.title.contains(searchQuery, true) || note.content.contains(searchQuery, true)) &&
                        matchesTags
            }
        }

        if (filteredFolders.isEmpty() && filteredNotes.isEmpty()) {
            // Empty view
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("当前目录暂无内容", color = Color.Gray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onCreateFolder(currentFolderId) }) {
                            Text("+ 新建目录")
                        }
                        TextButton(onClick = onCreateNote) {
                            Text("+ 新建页面")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Directories Header
                if (filteredFolders.isNotEmpty()) {
                    item {
                        Text(
                            text = "文件夹目录",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    items(filteredFolders) { folder ->
                        FolderItemCard(
                            folder = folder,
                            onNavigateTo = { viewModel.navigateToFolder(it.id) },
                            onDelete = { viewModel.deleteFolder(it.id) }
                        )
                    }
                }

                // Pages/Notes Header
                if (filteredNotes.isNotEmpty()) {
                    item {
                        Text(
                            text = "我的页面 (笔记)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }

                    items(filteredNotes) { note ->
                        val folderName = allFolders.find { it.id == note.folderId }?.name ?: "根目录"
                        val isSelected = selectedNoteIds.contains(note.id)
                        NoteItemCard(
                            note = note,
                            folderName = folderName,
                            isSelected = isSelected,
                            isInSelectionMode = isInSelectionMode,
                            onEdit = { viewModel.selectNote(note) },
                            onDelete = { viewModel.deleteNote(note.id) },
                            onToggleFavorite = { viewModel.toggleNoteFavorite(note) },
                            onSelectToggle = { viewModel.toggleNoteSelection(note.id) },
                            onLongClick = { viewModel.startSelectionMode(note.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FolderItemCard(
    folder: Folder,
    onNavigateTo: (Folder) -> Unit,
    onDelete: (Folder) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val sdf = SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault())
    val dateString = sdf.format(Date(folder.createdAt))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateTo(folder) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.LightGray.copy(alpha = 0.3f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(text = dateString, fontSize = 10.sp, color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "包含子内容", fontSize = 11.sp, color = Color.Gray)
                }
            }

            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "操作", tint = Color.Gray)
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("删除目录", color = Color.Red) },
                        onClick = {
                            onDelete(folder)
                            showMenu = false
                        }
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.LightGray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteItemCard(
    note: Note,
    folderName: String? = null,
    isSelected: Boolean = false,
    isInSelectionMode: Boolean = false,
    onEdit: (Note) -> Unit,
    onDelete: (Note) -> Unit,
    onToggleFavorite: () -> Unit,
    onSelectToggle: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val sdf = SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault())
    val dateString = sdf.format(Date(note.createdAt))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isInSelectionMode) {
                        onSelectToggle()
                    } else {
                        onEdit(note)
                    }
                },
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(10.dp),
        border = if (isSelected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isInSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { _ -> onSelectToggle() },
                    modifier = Modifier.padding(end = 4.dp)
                )
            } else {
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (note.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "收藏",
                        tint = if (note.isFavorite) Color(0xFFFFB000) else Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (note.isTodo) {
                        Icon(
                            imageVector = if (note.isTodoDone) Icons.Default.CheckCircle else Icons.Outlined.CheckCircle,
                            contentDescription = "待办",
                            tint = if (note.isTodoDone) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = note.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (note.isTodo && note.isTodoDone) Color.Gray else MaterialTheme.colorScheme.onSurface,
                        style = TextStyle(
                            textDecoration = if (note.isTodo && note.isTodoDone) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                        )
                    )
                }
                
                if (note.tags.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 1.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        note.tags.split(",").take(2).forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .height(13.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f))
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "#$tag",
                                    fontSize = 7.5.sp,
                                    lineHeight = 8.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Folder name
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = folderName ?: "根目录",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }

                    // Date
                    Text(
                        text = dateString,
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )

                    // Todo status badge
                    if (note.isTodo) {
                        val statusText = if (note.isTodoDone) "已完成" else note.todoStatus
                        val textColor = when (statusText) {
                            "已完成" -> Color(0xFF2E7D32)
                            "进行中" -> Color(0xFFE65100)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        }
                        Text(
                            text = "• $statusText",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                }
            }

            if (!isInSelectionMode) {
                IconButton(onClick = { onDelete(note) }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = Color.Red.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}


@Composable
fun FormatBar(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    showFinishButton: Boolean = true,
    modifier: Modifier = Modifier,
    recentTags: List<String> = emptyList(),
    order: List<String> = listOf("outdent", "indent", "separator", "bullet", "todo", "h1", "h2", "bold", "italic", "strike", "link", "wiki", "image", "code", "tag"),
    onFinishClick: () -> Unit = {}
) {
    fun insertTextAtCursor(textToInsert: String) {
        val currentText = textFieldValue.text
        val selectionStart = textFieldValue.selection.start
        val selectionEnd = textFieldValue.selection.end

        val newText = StringBuilder(currentText).replace(selectionStart, selectionEnd, textToInsert).toString()
        val newSelectionPosition = selectionStart + textToInsert.length

        onValueChange(
            TextFieldValue(
                text = newText,
                selection = TextRange(newSelectionPosition)
            )
        )
    }

    fun insertFormatPair(startSymbol: String, endSymbol: String) {
        val currentText = textFieldValue.text
        val selectionStart = textFieldValue.selection.start
        val selectionEnd = textFieldValue.selection.end

        val selectedText = currentText.substring(selectionStart, selectionEnd)
        val textToInsert = if (selectedText.isNotEmpty()) {
            "$startSymbol$selectedText$endSymbol"
        } else {
            "$startSymbol$endSymbol"
        }

        val newText = StringBuilder(currentText).replace(selectionStart, selectionEnd, textToInsert).toString()
        val newSelectionPosition = if (selectedText.isNotEmpty()) {
            selectionStart + textToInsert.length
        } else {
            selectionStart + startSymbol.length
        }

        onValueChange(
            TextFieldValue(
                text = newText,
                selection = TextRange(newSelectionPosition)
            )
        )
    }

    fun handleIndent() {
        val currentText = textFieldValue.text
        val selectionStart = textFieldValue.selection.start
        
        val lastNewline = currentText.lastIndexOf('\n', (selectionStart - 1).coerceAtLeast(0))
        val lineStart = if (lastNewline == -1) 0 else lastNewline + 1
        
        val newText = StringBuilder(currentText).insert(lineStart, "  ").toString()
        onValueChange(
            textFieldValue.copy(
                text = newText,
                selection = TextRange(selectionStart + 2, textFieldValue.selection.end + 2)
            )
        )
    }

    fun handleOutdent() {
        val currentText = textFieldValue.text
        val selectionStart = textFieldValue.selection.start
        
        val lastNewline = currentText.lastIndexOf('\n', (selectionStart - 1).coerceAtLeast(0))
        val lineStart = if (lastNewline == -1) 0 else lastNewline + 1
        
        if (lineStart < currentText.length) {
            val remainingText = currentText.substring(lineStart)
            val charsToRemove = when {
                remainingText.startsWith("  ") -> 2
                remainingText.startsWith(" ") -> 1
                remainingText.startsWith("\t") -> 1
                else -> 0
            }
            
            if (charsToRemove > 0) {
                val newText = StringBuilder(currentText).delete(lineStart, lineStart + charsToRemove).toString()
                onValueChange(
                    textFieldValue.copy(
                        text = newText,
                        selection = TextRange(
                            (selectionStart - charsToRemove).coerceAtLeast(lineStart),
                            (textFieldValue.selection.end - charsToRemove).coerceAtLeast(lineStart)
                        )
                    )
                )
            }
        }
    }

    // Tag autocomplete logic
    fun getActiveTagQuery(text: String, selectionStart: Int): String? {
        if (selectionStart <= 0) return null
        var i = selectionStart - 1
        while (i >= 0) {
            val char = text[i]
            if (char == '#') {
                val tagSegment = text.substring(i + 1, selectionStart)
                if (!tagSegment.contains(" ") && !tagSegment.contains("\n")) {
                    return tagSegment
                }
                break
            } else if (char == ' ' || char == '\n') {
                break
            }
            i--
        }
        return null
    }

    val activeQuery = remember(textFieldValue) {
        getActiveTagQuery(textFieldValue.text, textFieldValue.selection.start)
    }

    val filteredTags = remember(activeQuery, recentTags) {
        if (activeQuery == null) {
            emptyList()
        } else if (activeQuery.isEmpty()) {
            recentTags
        } else {
            recentTags.filter { it.contains(activeQuery, ignoreCase = true) }
        }
    }

    fun handleTagSelect(selectedTag: String) {
        if (activeQuery == null) return
        val currentText = textFieldValue.text
        val selectionStart = textFieldValue.selection.start
        val hashIndex = selectionStart - 1 - activeQuery.length
        val before = currentText.substring(0, hashIndex)
        val after = currentText.substring(selectionStart)
        val replacement = "#$selectedTag "
        val newText = before + replacement + after
        val newCursorPos = hashIndex + replacement.length
        onValueChange(
            TextFieldValue(
                text = newText,
                selection = TextRange(newCursorPos)
            )
        )
    }

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        tonalElevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .border(
                width = 0.5.dp, 
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            // Display tag suggestions
            if (activeQuery != null && filteredTags.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        filteredTags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier.clickable { handleTagSelect(tag) }
                            ) {
                                Text(
                                    text = "#$tag",
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                @Composable
                fun SimpleFormatButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
                    IconButton(
                        onClick = onClick, 
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            icon, 
                            contentDescription = desc, 
                            modifier = Modifier.size(20.dp), 
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                @Composable
                fun TextFormatButton(text: String, onClick: () -> Unit, isSmall: Boolean = false) {
                    IconButton(
                        onClick = onClick, 
                        modifier = Modifier.size(42.dp)
                    ) {
                        Text(
                            text = text, 
                            fontWeight = FontWeight.Bold, 
                            fontSize = if (isSmall) 11.sp else 13.sp, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                order.forEach { action ->
                    when (action) {
                        "outdent" -> SimpleFormatButton(Icons.AutoMirrored.Filled.FormatIndentDecrease, "取消缩进", ::handleOutdent)
                        "indent" -> SimpleFormatButton(Icons.AutoMirrored.Filled.FormatIndentIncrease, "缩进", ::handleIndent)
                        "separator" -> {
                            VerticalDivider(
                                modifier = Modifier.height(20.dp).padding(horizontal = 6.dp),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                        "bullet" -> SimpleFormatButton(Icons.Default.FormatListBulleted, "列表") { insertTextAtCursor("\n- ") }
                        "todo" -> SimpleFormatButton(Icons.Default.Checklist, "待办") { insertTextAtCursor("\n- [ ] ") }
                        "h1" -> TextFormatButton("H1", { insertTextAtCursor("\n# ") })
                        "h2" -> TextFormatButton("H2", { insertTextAtCursor("\n## ") })
                        "bold" -> SimpleFormatButton(Icons.Default.FormatBold, "加粗") { insertFormatPair("**", "**") }
                        "italic" -> SimpleFormatButton(Icons.Default.FormatItalic, "斜体") { insertFormatPair("*", "*") }
                        "strike" -> SimpleFormatButton(Icons.Default.FormatStrikethrough, "删除线") { insertFormatPair("~~", "~~") }
                        "link" -> SimpleFormatButton(Icons.Default.Link, "链接") { insertFormatPair("[", "](url)") }
                        "wiki" -> TextFormatButton("[[]]", { insertFormatPair("[[", "]]") }, isSmall = true)
                        "image" -> SimpleFormatButton(Icons.Default.Image, "图片") { insertFormatPair("![", "](url)") }
                        "code" -> SimpleFormatButton(Icons.Default.Code, "代码") { insertFormatPair("`", "`") }
                        "tag" -> SimpleFormatButton(Icons.Default.Tag, "标签") { insertTextAtCursor("#") }
                    }
                }

                if (showFinishButton) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = onFinishClick, 
                        modifier = Modifier.height(34.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp)
                    ) {
                        Text("完成", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }
        }
    }
}

// --- Component 9: NotesTabView ---
@Composable
fun BrowserTabsRow(
    recentNotes: List<Note>,
    selectedNote: Note?,
    onSelect: (Note) -> Unit,
    onClose: (Note) -> Unit,
    primaryColor: Color,
    isDark: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (isDark) Color(0xFF1B1B20) else Color(0xFFE8EBF2))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            recentNotes.take(3).forEach { note ->
                val isActive = selectedNote?.id == note.id
                val tabBg = if (isActive) {
                    if (isDark) Color(0xFF2E2E35) else Color(0xFFFFFFFF)
                } else {
                    Color.Transparent
                }
                val textColor = if (isActive) {
                    primaryColor
                } else {
                    if (isDark) Color(0xFFECECEC).copy(alpha = 0.65f) else Color(0xFF333333).copy(alpha = 0.65f)
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(tabBg)
                        .clickable { onSelect(note) }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (note.isTodo) Icons.Default.TaskAlt else Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (isActive) primaryColor else (if (isDark) Color(0xFF888891) else Color(0xFF7A7A85))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = note.title.ifEmpty { "无标题" },
                            fontSize = 12.sp,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .clickable { onClose(note) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = if (isActive) {
                                if (isDark) Color(0xFFCCCCCC) else Color(0xFF555555)
                            } else {
                                if (isDark) Color(0xFF777777) else Color(0xFF999999)
                            },
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotesTabView(viewModel: NoteViewModel) {
    val allNotes by viewModel.allNotes.collectAsState()
    val recentOpenedNotes by viewModel.recentOpenedNotes.collectAsState()
    val selectedNote by viewModel.selectedNote.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val isAppDarkMode by viewModel.isDarkMode.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(recentOpenedNotes, selectedNote, allNotes, selectedTab) {
        if (selectedTab == SelectedTab.NOTES) {
            if (selectedNote == null && recentOpenedNotes.isNotEmpty()) {
                viewModel.selectNote(recentOpenedNotes.first())
            } else if (selectedNote == null && recentOpenedNotes.isEmpty() && allNotes.isNotEmpty()) {
                val mostRecent = allNotes.sortedByDescending { it.updatedAt }.firstOrNull()
                if (mostRecent != null) {
                    viewModel.selectNote(mostRecent)
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val activeNote = selectedNote ?: recentOpenedNotes.firstOrNull()
        if (activeNote == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("暂无笔记", color = Color.Gray, fontSize = 14.sp)
                }
            }
        } else {
            NoteEditorView(
                note = activeNote,
                viewModel = viewModel,
                onBack = {
                    viewModel.exitNote()
                },
                tabs = {
                    if (recentOpenedNotes.isNotEmpty()) {
                        BrowserTabsRow(
                            recentNotes = recentOpenedNotes,
                            selectedNote = selectedNote,
                            onSelect = { viewModel.selectNote(it) },
                            onClose = { viewModel.closeRecentNote(it.id) },
                            primaryColor = primaryColor,
                            isDark = isAppDarkMode
                        )
                    }
                }
            )
        }
    }
}

// --- Component 4: DiaryTabView (Matches Screenshot 2 Bullet journal) ---
@Composable
fun DiaryTabView(viewModel: NoteViewModel) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val diaries by viewModel.allDiaries.collectAsState()
    
    // Find diary of today if exists
    val diary = diaries.find { it.date == selectedDate }
    val isDiaryEditMode by viewModel.isDiaryEditMode.collectAsState()
    var currentTextVal by remember { mutableStateOf(TextFieldValue("")) }

    val allTags by viewModel.allTags.collectAsState()
    val recentTags = remember(allTags) { allTags.keys.toList() }

    // Display formatted date (Screenshot 2: 5月24日星期日)
    val parsedDate = remember(selectedDate) {
        try {
            val dateObj = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDate)
            if (dateObj != null) {
                SimpleDateFormat("M月d日EEEE", Locale.SIMPLIFIED_CHINESE).format(dateObj)
            } else {
                selectedDate
            }
        } catch (e: Exception) {
            selectedDate
        }
    }

    var activeLineIndex by remember { mutableStateOf<Int?>(null) }
    var activeLineTextValue by remember { mutableStateOf(TextFieldValue("")) }
    var lastSelectedDate by remember { mutableStateOf("") }

    var selectedLineIndices by remember { mutableStateOf(setOf<Int>()) }
    val isMultiSelectMode = selectedLineIndices.isNotEmpty()

    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    LaunchedEffect(activeLineIndex) {
        viewModel.setDiaryEditMode(activeLineIndex != null)
    }

    LaunchedEffect(selectedDate, diary) {
        if (selectedDate != lastSelectedDate) {
            lastSelectedDate = selectedDate
            currentTextVal = if (diary != null) {
                TextFieldValue(diary.content, TextRange(diary.content.length))
            } else {
                TextFieldValue("")
            }
            activeLineIndex = null
        } else {
            if (diary != null && currentTextVal.text != diary.content && activeLineIndex == null) {
                currentTextVal = TextFieldValue(diary.content, TextRange(diary.content.length))
            }
        }
    }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    androidx.activity.compose.BackHandler(enabled = activeLineIndex != null) {
        activeLineIndex = null
        focusManager.clearFocus()
    }

    val lastWeekDates = remember(selectedDate) {
        val dates = mutableListOf<String>()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        try {
            val baseDate = sdf.parse(selectedDate) ?: Date()
            val cal = Calendar.getInstance()
            for (i in 1..7) {
                cal.time = baseDate
                cal.add(Calendar.DAY_OF_YEAR, -i)
                dates.add(sdf.format(cal.time))
            }
        } catch (e: Exception) {
            // Ignore
        }
        dates
    }

    val lines = remember(currentTextVal.text) {
        val rawLines = currentTextVal.text.split("\n")
        if (rawLines.isEmpty() || (rawLines.size == 1 && rawLines[0].isEmpty())) {
            listOf("")
        } else {
            rawLines
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures {
                    if (activeLineIndex != null) {
                        activeLineIndex = null
                        focusManager.clearFocus()
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
        // Timeline navigations
        if (isMultiSelectMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Multi-select bulk actions
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedLineIndices = emptySet() }) {
                        Icon(Icons.Default.Close, contentDescription = "取消选择")
                    }
                    Text("${selectedLineIndices.size}项", fontWeight = FontWeight.Bold)
                }

                Row {
                    IconButton(onClick = {
                        val sortedIndices = selectedLineIndices.sorted().filter { it in lines.indices }
                        val selectedLines = sortedIndices.map { lines[it] }
                        val textToCopy = selectedLines.joinToString("\n")
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(textToCopy))
                        android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = {
                        val sortedIndices = selectedLineIndices.sorted().filter { it in lines.indices }
                        val selectedLines = sortedIndices.map { lines[it] }
                        val textToCopy = selectedLines.joinToString("\n")
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(textToCopy))

                        // Delete from lines
                        val updatedLines = lines.toMutableList()
                        sortedIndices.reversed().forEach { updatedLines.removeAt(it) }
                        val fullText = updatedLines.joinToString("\n")
                        currentTextVal = TextFieldValue(fullText)
                        viewModel.saveDiary(selectedDate, fullText)
                        selectedLineIndices = emptySet()
                        android.widget.Toast.makeText(context, "已剪切", android.widget.Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCut, contentDescription = "剪切", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = {
                        val updatedLines = lines.toMutableList()
                        selectedLineIndices.sorted().reversed().forEach { updatedLines.removeAt(it) }
                        val fullText = updatedLines.joinToString("\n")
                        currentTextVal = TextFieldValue(fullText)
                        viewModel.saveDiary(selectedDate, fullText)
                        selectedLineIndices = emptySet()
                        android.widget.Toast.makeText(context, "已删除", android.widget.Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Single scrolling layout that displays the current editor and last week's diaries below it
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Large Date Indicator
            item {
                Text(
                    text = parsedDate,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // 2. Direct Editor Card for Today
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                            .heightIn(min = 320.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (activeLineIndex == null) {
                                    activeLineIndex = if (lines.isEmpty()) 0 else lines.size - 1
                                    val text = if (lines.isEmpty()) "" else lines.last()
                                    activeLineTextValue = TextFieldValue(text, TextRange(text.length))
                                } else {
                                    activeLineIndex = null
                                    focusManager.clearFocus()
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📝 日记内容",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (activeLineIndex != null) {
                                Text(
                                    text = "自动保存中...",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            lines.forEachIndexed { index, line ->
                                if (activeLineIndex == index) {
                                    // Render editable TextField for the active line
                                    val lineFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Indie active dot indicator matching level
                                        val leadingTabs = line.takeWhile { it == '\t' || it == ' ' }.length
                                        val level = when {
                                            leadingTabs >= 4 -> 2
                                            leadingTabs >= 1 -> 1
                                            else -> 0
                                        }
                                        Spacer(modifier = Modifier.width((level * 16).dp))
                                        
                                        Box(
                                            modifier = Modifier
                                                .padding(end = 8.dp)
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                        )

                                        TextField(
                                            value = activeLineTextValue,
                                            onValueChange = { newTextVal ->
                                                activeLineTextValue = newTextVal
                                                val txt = newTextVal.text
                                                if (txt.contains("\n")) {
                                                    // Handle multi-line break on keyboard enter
                                                    val parts = txt.split("\n")
                                                    val updatedLines = lines.toMutableList()
                                                    updatedLines[index] = parts[0]
                                                    for (p in 1 until parts.size) {
                                                        updatedLines.add(index + p, parts[p])
                                                    }
                                                    val fullText = updatedLines.joinToString("\n")
                                                    currentTextVal = TextFieldValue(fullText)
                                                    viewModel.saveDiary(selectedDate, fullText)
                                                    
                                                    val nextIndex = index + 1
                                                    activeLineIndex = nextIndex
                                                    val nextLineText = updatedLines.getOrNull(nextIndex) ?: ""
                                                    activeLineTextValue = TextFieldValue(nextLineText, TextRange(nextLineText.length))
                                                } else {
                                                    val updatedLines = lines.toMutableList()
                                                    updatedLines[index] = txt
                                                    val fullText = updatedLines.joinToString("\n")
                                                    currentTextVal = TextFieldValue(fullText)
                                                    viewModel.saveDiary(selectedDate, fullText)
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .focusRequester(lineFocusRequester)
                                                .onPreviewKeyEvent { keyEvent ->
                                                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Backspace) {
                                                        if (activeLineTextValue.selection.start == 0) {
                                                            if (index > 0) {
                                                                val updatedLines = lines.toMutableList()
                                                                val currentLineText = lines[index]
                                                                val prevLineText = lines[index - 1]
                                                                
                                                                val (cleanedPrev, cleanedCurrent) = cleanupSplitLines(prevLineText, currentLineText)
                                                                val mergedText = cleanedPrev + cleanedCurrent
                                                                updatedLines[index - 1] = mergedText
                                                                updatedLines.removeAt(index)
                                                                
                                                                val fullText = updatedLines.joinToString("\n")
                                                                currentTextVal = TextFieldValue(fullText)
                                                                viewModel.saveDiary(selectedDate, fullText)
                                                                
                                                                val prevIndex = index - 1
                                                                activeLineIndex = prevIndex
                                                                activeLineTextValue = TextFieldValue(mergedText, TextRange(cleanedPrev.length))
                                                                return@onPreviewKeyEvent true
                                                            }
                                                        }
                                                    }
                                                    false
                                                },
                                            colors = TextFieldDefaults.colors(
                                                focusedContainerColor = Color.Transparent,
                                                unfocusedContainerColor = Color.Transparent,
                                                focusedIndicatorColor = Color.Transparent,
                                                unfocusedIndicatorColor = Color.Transparent,
                                                cursorColor = MaterialTheme.colorScheme.primary
                                            ),
                                            textStyle = TextStyle(
                                                fontSize = 15.sp,
                                                lineHeight = 22.sp,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                        )

                                        // Focus Request trigger strictly once per line selection
                                        var focusRequested by remember(activeLineIndex) { mutableStateOf(false) }
                                        LaunchedEffect(activeLineIndex) {
                                            if (activeLineIndex == index && !focusRequested) {
                                                try {
                                                    lineFocusRequester.requestFocus()
                                                    focusRequested = true
                                                } catch (e: Exception) {}
                                            }
                                        }

                                        // Close/Delete Action for active line
                                        IconButton(
                                            onClick = {
                                                val updatedLines = lines.toMutableList()
                                                updatedLines.removeAt(index)
                                                val fullText = updatedLines.joinToString("\n")
                                                currentTextVal = TextFieldValue(fullText)
                                                viewModel.saveDiary(selectedDate, fullText)
                                                if (updatedLines.isEmpty()) {
                                                    activeLineIndex = null
                                                } else {
                                                    val nextIndex = maxOf(0, index - 1)
                                                    activeLineIndex = nextIndex
                                                    val nextLineText = updatedLines.getOrNull(nextIndex) ?: ""
                                                    activeLineTextValue = TextFieldValue(nextLineText, TextRange(nextLineText.length))
                                                }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "删除行",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                } else {
                                    // Except for the active editing line, all other lines render as clean markdown/bullets
                                    BulletJournalLineItem(
                                        line = line, 
                                        viewModel = viewModel,
                                        isSelected = selectedLineIndices.contains(index),
                                        onToggle = {
                                            if (isMultiSelectMode) {
                                                selectedLineIndices = if (selectedLineIndices.contains(index)) {
                                                    selectedLineIndices - index
                                                } else {
                                                    selectedLineIndices + index
                                                }
                                                return@BulletJournalLineItem
                                            }
                                            val currentDiary = viewModel.allDiaries.value.find { it.date == selectedDate }
                                            if (currentDiary != null) {
                                                val clean = stripBulletPrefix(line)
                                                val status = when {
                                                    clean.startsWith("[x]", ignoreCase = true) -> "已完成"
                                                    clean.startsWith("[-]") || clean.startsWith("[/]") -> "进行中"
                                                    else -> "未完成"
                                                }
                                                viewModel.toggleDiaryLineTodo(currentDiary, index, status)
                                            }
                                        },
                                        onLongClick = {
                                            selectedLineIndices = selectedLineIndices + index
                                        }
                                    ) {
                                        if (isMultiSelectMode) {
                                            selectedLineIndices = if (selectedLineIndices.contains(index)) {
                                                selectedLineIndices - index
                                            } else {
                                                selectedLineIndices + index
                                            }
                                        } else {
                                            // Handle click-to-edit
                                            activeLineIndex = index
                                            activeLineTextValue = TextFieldValue(line, TextRange(line.length))
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    val updatedLines = lines.toMutableList()
                                    updatedLines.add("")
                                    val fullText = updatedLines.joinToString("\n")
                                    currentTextVal = TextFieldValue(fullText)
                                    viewModel.saveDiary(selectedDate, fullText)
                                    activeLineTextValue = TextFieldValue("", TextRange(0))
                                    activeLineIndex = updatedLines.size - 1
                                },
                                colors = ButtonDefaults.textButtonColors(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("添加行", fontSize = 13.sp)
                            }

                            if (activeLineIndex != null) {
                                TextButton(
                                    onClick = { activeLineIndex = null },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("退出编辑", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 3. Last Week Daily Contents Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "上周历史内容 (过去 7 天)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 4. Last Week Daily Cards
            items(lastWeekDates) { dayDate ->
                val dayDiary = diaries.find { it.date == dayDate }
                val dayFormattedStr = remember(dayDate) {
                    try {
                        val dateObj = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dayDate)
                        if (dateObj != null) {
                            SimpleDateFormat("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE).format(dateObj)
                        } else {
                            dayDate
                        }
                    } catch (e: Exception) {
                        dayDate
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.selectDate(dayDate)
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dayFormattedStr,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "点击编辑这一天",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (dayDiary == null || dayDiary.content.trim().isEmpty()) {
                            Text(
                                text = "（无记录哦，点击上方开始记录）",
                                fontSize = 13.sp,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        } else {
                            // Split content and render preview lines
                            val lines = dayDiary.content.split("\n").filter { it.isNotBlank() }
                            val displayLines = lines.take(5)
                            Column {
                                displayLines.forEach { line ->
                                    LastWeekBulletItem(line = line)
                                }
                                if (lines.size > 5) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "查看其它 ${lines.size - 5} 行内容...",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (activeLineIndex != null) {
                item {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }

    if (activeLineIndex != null) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            tonalElevation = 8.dp, // minor elevation to differentiate it over the background beautifully
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                FormatBar(
                    textFieldValue = activeLineTextValue,
                    onValueChange = { newValue ->
                        activeLineTextValue = newValue
                        val txt = newValue.text
                        val updatedLines = lines.toMutableList()
                        val targetIndex = activeLineIndex!!
                        if (targetIndex < updatedLines.size) {
                            if (txt.contains("\n")) {
                                val parts = txt.split("\n")
                                updatedLines[targetIndex] = parts[0]
                                for (p in 1 until parts.size) {
                                    updatedLines.add(targetIndex + p, parts[p])
                                }
                                val fullText = updatedLines.joinToString("\n")
                                currentTextVal = TextFieldValue(fullText)
                                viewModel.saveDiary(selectedDate, fullText)
                                
                                val nextIndex = targetIndex + 1
                                activeLineIndex = nextIndex
                                val nextLineText = updatedLines.getOrNull(nextIndex) ?: ""
                                activeLineTextValue = TextFieldValue(nextLineText, TextRange(nextLineText.length))
                            } else {
                                updatedLines[targetIndex] = txt
                                val fullText = updatedLines.joinToString("\n")
                                currentTextVal = TextFieldValue(fullText)
                                viewModel.saveDiary(selectedDate, fullText)
                            }
                        }
                    },
                    recentTags = recentTags,
                    showFinishButton = false
                )
            }
        }
    }
}
}

fun stripBulletPrefix(line: String): String {
    val leadingWhitespaces = line.takeWhile { it == '\t' || it == ' ' }.length
    val rawContent = line.substring(leadingWhitespaces)
    
    return when {
        rawContent.startsWith("- [ ] ") -> rawContent.removePrefix("- ")
        rawContent.startsWith("- [x] ", ignoreCase = true) -> rawContent.removePrefix("- ")
        rawContent.startsWith("- ") -> rawContent.removePrefix("- ")
        rawContent.startsWith("* ") -> rawContent.removePrefix("* ")
        rawContent.startsWith("· ") -> rawContent.removePrefix("· ")
        rawContent == "-" || rawContent == "*" || rawContent == "·" -> ""
        else -> rawContent
    }.trim()
}

@Composable
fun LastWeekBulletItem(line: String) {
    val cleanText = stripBulletPrefix(line)
    if (cleanText.isEmpty()) return
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "•",
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 6.dp),
            fontSize = 14.sp
        )
        Text(
            text = cleanText,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            lineHeight = 18.sp
        )
    }
}

@Composable
fun parseMarkdownToAnnotatedString(text: String, primaryColor: Color): AnnotatedString {
    val codeBackgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    
    return buildAnnotatedString {
        var i = 0
        val len = text.length
        while (i < len) {
            when {
                // Double brackets [[Link]]
                i + 1 < len && text[i] == '[' && text[i + 1] == '[' -> {
                    val endIdx = text.indexOf("]]", i + 2)
                    if (endIdx != -1) {
                        val linkText = text.substring(i + 2, endIdx)
                        pushStringAnnotation(tag = "WIKI_LINK", annotation = linkText)
                        pushStyle(
                            SpanStyle(
                                color = primaryColor,
                                fontWeight = FontWeight.Bold,
                                textDecoration = TextDecoration.Underline
                            )
                        )
                        append(linkText)
                        pop()
                        pop()
                        i = endIdx + 2
                    } else {
                        append("[[")
                        i += 2
                    }
                }
                // Hashtag like #tag (letters, digits, underscore, Chinese characters, dash)
                text[i] == '#' && (i == 0 || text[i - 1] == ' ' || text[i - 1] == '\t' || text[i - 1] == '\n' || text[i - 1] == '\r' || text[i - 1] == '(' || text[i - 1] == '[' || text[i - 1] == '{' || text[i - 1] == ',' || text[i - 1] == ';') -> {
                    var j = i + 1
                    while (j < len && (text[j].isLetterOrDigit() || text[j] == '_' || text[j] == '-' || text[j] in '\u4e00'..'\u9fa5')) {
                        j++
                    }
                    if (j > i + 1) {
                        val tagText = text.substring(i, j)
                        pushStyle(
                            SpanStyle(
                                color = primaryColor,
                                fontWeight = FontWeight.Bold,
                                background = primaryColor.copy(alpha = 0.12f)
                            )
                        )
                        append(tagText)
                        pop()
                        i = j
                    } else {
                        append('#')
                        i++
                    }
                }
                // Bold **
                i + 1 < len && text[i] == '*' && text[i + 1] == '*' -> {
                    val endIdx = text.indexOf("**", i + 2)
                    if (endIdx != -1) {
                        val innerText = text.substring(i + 2, endIdx)
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(parseMarkdownToAnnotatedString(innerText, primaryColor))
                        pop()
                        i = endIdx + 2
                    } else {
                        append("**")
                        i += 2
                    }
                }
                // Italic * (prevent matching double asterisks **)
                text[i] == '*' && (i + 1 >= len || text[i + 1] != '*') -> {
                    val endIdx = text.indexOf("*", i + 1)
                    if (endIdx != -1) {
                        val innerText = text.substring(i + 1, endIdx)
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(parseMarkdownToAnnotatedString(innerText, primaryColor))
                        pop()
                        i = endIdx + 1
                    } else {
                        append("*")
                        i += 1
                    }
                }
                // Strikethrough ~~
                i + 1 < len && text[i] == '~' && text[i + 1] == '~' -> {
                    val endIdx = text.indexOf("~~", i + 2)
                    if (endIdx != -1) {
                        val innerText = text.substring(i + 2, endIdx)
                        pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                        append(parseMarkdownToAnnotatedString(innerText, primaryColor))
                        pop()
                        i = endIdx + 2
                    } else {
                        append("~~")
                        i += 2
                    }
                }
                // Code `
                text[i] == '`' -> {
                    val endIdx = text.indexOf("`", i + 1)
                    if (endIdx != -1) {
                        val innerText = text.substring(i + 1, endIdx)
                        pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = codeBackgroundColor,
                                color = onSurfaceVariant
                            )
                        )
                        append(innerText)
                        pop()
                        i = endIdx + 1
                    } else {
                        append("`")
                        i += 1
                    }
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}

@Composable
fun ClickableMarkdownText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    onLinkClick: (String) -> Unit = {},
    onNormalClick: () -> Unit = {}
) {
    val defaultColor = MaterialTheme.colorScheme.onBackground
    val mergedStyle = if (style.color == androidx.compose.ui.graphics.Color.Unspecified) {
        style.copy(color = defaultColor)
    } else {
        style
    }
    
    var layoutResult by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    
    androidx.compose.material3.Text(
        text = text,
        modifier = modifier.pointerInput(text) {
            detectTapGestures(
                onTap = { pos ->
                    layoutResult?.let { layout ->
                        val offset = layout.getOffsetForPosition(pos)
                        val annotations = text.getStringAnnotations(tag = "WIKI_LINK", start = 0, end = text.length)
                        val exactMatch = annotations.firstOrNull { offset >= it.start && offset < it.end }
                        if (exactMatch != null) {
                            onLinkClick(exactMatch.item)
                        } else {
                            val fuzzyMatch = annotations.firstOrNull {
                                val distanceToStart = kotlin.math.abs(offset - it.start)
                                val distanceToEnd = kotlin.math.abs(offset - it.end)
                                distanceToStart <= 2 || distanceToEnd <= 2 || (offset >= it.start - 2 && offset <= it.end + 2)
                            }
                            if (fuzzyMatch != null) {
                                onLinkClick(fuzzyMatch.item)
                            } else {
                                onNormalClick()
                            }
                        }
                    } ?: onNormalClick()
                },
                onLongPress = { pos ->
                    layoutResult?.let { layout ->
                        val offset = layout.getOffsetForPosition(pos)
                        val annotations = text.getStringAnnotations(tag = "WIKI_LINK", start = 0, end = text.length)
                        val exactMatch = annotations.firstOrNull { offset >= it.start && offset < it.end }
                        if (exactMatch != null) {
                            onLinkClick(exactMatch.item)
                        } else {
                            onNormalClick()
                        }
                    } ?: onNormalClick()
                }
            )
        },
        style = mergedStyle,
        onTextLayout = { layoutResult = it }
    )
}

fun cleanupSplitLines(part1: String, part2: String): Pair<String, String> {
    var p1 = part1
    var p2 = part2
    
    // Clean up corresponding closing symbols from the start of the next line
    val p2Trimmed = p2.trimStart()
    if (p2Trimmed.startsWith("] ") || p2Trimmed.startsWith("]")) {
        p2 = p2Trimmed.substring(if (p2Trimmed.startsWith("] ")) 2 else 1)
    } else if (p2Trimmed.startsWith("- ")) {
        p2 = p2Trimmed.substring(2)
    } else if (p2Trimmed.startsWith("- [ ] ")) {
        p2 = p2Trimmed.substring(6)
    } else if (p2Trimmed.startsWith("- [x] ")) {
        p2 = p2Trimmed.substring(6)
    } else if (p2Trimmed.startsWith("- [X] ")) {
        p2 = p2Trimmed.substring(6)
    }
    
    // Clean inline styling splits
    if (p1.endsWith("[") && p2.startsWith("[")) {
        p1 = p1.dropLast(1)
        p2 = "[[" + p2.substring(1)
    }
    if (p1.endsWith("*") && p2.startsWith("*")) {
        p1 = p1.dropLast(1)
        p2 = "**" + p2.substring(1)
    }
    if (p1.endsWith("~") && p2.startsWith("~")) {
        p1 = p1.dropLast(1)
        p2 = "~~" + p2.substring(1)
    }
    
    return Pair(p1, p2)
}

fun splitFormattedLine(text: String, cursorLocation: Int): Pair<String, String> {
    if (cursorLocation < 0 || cursorLocation > text.length) {
        return Pair(text, "")
    }
    
    val part1 = text.substring(0, cursorLocation)
    val part2 = text.substring(cursorLocation)
    
    // 1. Double bracket Wiki links: [[...]]
    val wikiLinkRegex = "\\[\\[([^\\]]*)\\]\\]".toRegex()
    for (match in wikiLinkRegex.findAll(text)) {
        val range = match.range
        if (cursorLocation > range.first + 2 && cursorLocation < range.last - 1) {
            val insideText = match.groupValues[1]
            val splitOffset = cursorLocation - (range.first + 2)
            val p1 = insideText.substring(0, splitOffset)
            val p2 = insideText.substring(splitOffset)
            
            val newPart1 = text.substring(0, range.first) + "[[" + p1 + "]]"
            val newPart2 = "[[" + p2 + "]]" + text.substring(range.last + 1)
            return Pair(newPart1, newPart2)
        }
    }
    
    // 2. Bold: **...**
    val boldRegex = "\\*\\*([^\\*]*)\\*\\*".toRegex()
    for (match in boldRegex.findAll(text)) {
        val range = match.range
        if (cursorLocation > range.first + 2 && cursorLocation < range.last - 1) {
            val insideText = match.groupValues[1]
            val splitOffset = cursorLocation - (range.first + 2)
            val p1 = insideText.substring(0, splitOffset)
            val p2 = insideText.substring(splitOffset)
            
            val newPart1 = text.substring(0, range.first) + "**" + p1 + "**"
            val newPart2 = "**" + p2 + "**" + text.substring(range.last + 1)
            return Pair(newPart1, newPart2)
        }
    }
    
    // 3. Strikethrough: ~~...~~
    val strikeRegex = "~~([^~]*)~~".toRegex()
    for (match in strikeRegex.findAll(text)) {
        val range = match.range
        if (cursorLocation > range.first + 2 && cursorLocation < range.last - 1) {
            val insideText = match.groupValues[1]
            val splitOffset = cursorLocation - (range.first + 2)
            val p1 = insideText.substring(0, splitOffset)
            val p2 = insideText.substring(splitOffset)
            
            val newPart1 = text.substring(0, range.first) + "~~" + p1 + "~~"
            val newPart2 = "~~" + p2 + "~~" + text.substring(range.last + 1)
            return Pair(newPart1, newPart2)
        }
    }
    
    // 4. Italics or single bullet star: *...*
    val italicRegex = "\\*([^\\*]*)\\*".toRegex()
    for (match in italicRegex.findAll(text)) {
        val range = match.range
        if (cursorLocation > range.first + 1 && cursorLocation < range.last) {
            val insideText = match.groupValues[1]
            val splitOffset = cursorLocation - (range.first + 1)
            val p1 = insideText.substring(0, splitOffset)
            val p2 = insideText.substring(splitOffset)
            
            val newPart1 = text.substring(0, range.first) + "*" + p1 + "*"
            val newPart2 = "*" + p2 + "*" + text.substring(range.last + 1)
            return Pair(newPart1, newPart2)
        }
    }

    return Pair(part1, part2)
}

@Composable
fun MarkdownLineRenderer(
    line: String,
    onLinkClick: (String) -> Unit = {}
) {
    val trimmed = line.trimStart()
    val indentLevel = (line.length - trimmed.length) / 4
    val primaryColor = MaterialTheme.colorScheme.primary
    val defaultStyle = TextStyle(
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = MaterialTheme.colorScheme.onBackground
    )

    when {
        trimmed == "---" -> {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
        trimmed.startsWith("# ") -> {
            val content = trimmed.substring(2)
            ClickableMarkdownText(
                text = parseMarkdownToAnnotatedString(content, primaryColor),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(vertical = 8.dp),
                onLinkClick = onLinkClick
            )
        }
        trimmed.startsWith("## ") -> {
            val content = trimmed.substring(3)
            ClickableMarkdownText(
                text = parseMarkdownToAnnotatedString(content, primaryColor),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(vertical = 4.dp),
                onLinkClick = onLinkClick
            )
        }
        trimmed.startsWith("### ") -> {
            val content = trimmed.substring(4)
            ClickableMarkdownText(
                text = parseMarkdownToAnnotatedString(content, primaryColor),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(vertical = 4.dp),
                onLinkClick = onLinkClick
            )
        }
        trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ") -> {
            val content = trimmed.substring(6)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = (indentLevel * 16).dp, top = 4.dp, bottom = 4.dp)
            ) {
                Checkbox(checked = true, onCheckedChange = null, enabled = false, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                ClickableMarkdownText(
                    text = parseMarkdownToAnnotatedString(content, primaryColor),
                    style = defaultStyle.copy(
                        textDecoration = TextDecoration.LineThrough,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    ),
                    onLinkClick = onLinkClick
                )
            }
        }
        trimmed.startsWith("- [ ] ") -> {
            val content = trimmed.substring(6)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = (indentLevel * 16).dp, top = 4.dp, bottom = 4.dp)
            ) {
                Checkbox(checked = false, onCheckedChange = null, enabled = false, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                ClickableMarkdownText(
                    text = parseMarkdownToAnnotatedString(content, primaryColor),
                    style = defaultStyle,
                    onLinkClick = onLinkClick
                )
            }
        }
        trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("· ") -> {
            val content = trimmed.substring(2)
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.padding(start = (indentLevel * 16).dp, top = 4.dp, bottom = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp, end = 8.dp)
                        .size(6.dp)
                        .background(primaryColor, shape = CircleShape)
                )
                ClickableMarkdownText(
                    text = parseMarkdownToAnnotatedString(content, primaryColor),
                    style = defaultStyle,
                    onLinkClick = onLinkClick
                )
            }
        }
        else -> {
            if (line.isNotBlank()) {
                ClickableMarkdownText(
                    text = parseMarkdownToAnnotatedString(line, primaryColor),
                    style = defaultStyle,
                    modifier = Modifier.padding(vertical = 4.dp),
                    onLinkClick = onLinkClick
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

fun extractUrl(text: String): String? {
    val regex = "(https?://|www\\.)[a-zA-Z0-9\\-\\._~:/?#\\[\\]@!$&'()*+,;=]+".toRegex()
    val match = regex.find(text) ?: return null
    var url = match.value
    if (url.startsWith("www.")) {
        url = "https://$url"
    }
    return url
}

@Composable
fun BookmarkCard(url: String) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val cleanUrl = url.trim()
    val host = remember(cleanUrl) {
        try {
            java.net.URI(cleanUrl).host ?: ""
        } catch (e: Exception) {
            if (cleanUrl.contains("://")) {
                cleanUrl.substringAfter("://").substringBefore("/")
            } else {
                cleanUrl.substringBefore("/")
            }
        }
    }
    
    val displayTitle = remember(host) {
        when {
            host.contains("github.com") -> "GitHub · Build Software Better"
            host.contains("google.com") -> "Google Search"
            host.contains("wikipedia.org") -> "Wikipedia Encyclopedia"
            host.contains("bilibili.com") -> "Bilibili - 哔哩哔哩"
            host.contains("zhihu.com") -> "知乎 - 有问题上知乎"
            host.contains("baidu.com") -> "百度一下"
            host.isNotEmpty() -> host.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            else -> "网页链接"
        }
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable {
                try {
                    uriHandler.openUri(cleanUrl)
                } catch (e: Exception) {}
            },
        shape = androidx.compose.ui.graphics.RectangleShape, // Sharp/Non-rounded rectangular shape
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(34.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
            
            Icon(
                imageVector = Icons.Default.Bookmark,
                contentDescription = "Bookmark",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = displayTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = cleanUrl,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Icon(
                imageVector = Icons.Default.Launch,
                contentDescription = "Open Link",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BulletJournalLineItem(
    line: String,
    viewModel: NoteViewModel? = null,
    isSelected: Boolean = false,
    onToggle: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    // Determine level of indentation by checking tab counts or double spaces prefix
    val leadingTabs = line.takeWhile { it == '\t' || it == ' ' }.length
    val level = when {
        leadingTabs >= 4 -> 2
        leadingTabs >= 1 -> 1
        else -> 0
    }

    // Clean text: strip bullets, dashes, asterisks but preserve the header and checkmark symbols
    val cleanText = stripBulletPrefix(line)

    // Parse heading types
    val isH1 = cleanText.startsWith("# ") && !cleanText.startsWith("## ")
    val isH2 = cleanText.startsWith("## ") && !cleanText.startsWith("### ")
    val isH3 = cleanText.startsWith("### ")

    // Parse checkbox types
    val isChecked = cleanText.startsWith("[x] ", ignoreCase = true) || cleanText.startsWith("[X] ", ignoreCase = true) || cleanText.startsWith("[x]") || cleanText.startsWith("[X]")
    val isInProgress = cleanText.startsWith("[-] ") || cleanText.startsWith("[/] ") || cleanText.startsWith("[-]") || cleanText.startsWith("[/]")
    val isUnchecked = cleanText.startsWith("[ ] ") || cleanText.startsWith("[ ]")
    val isTodo = isChecked || isInProgress || isUnchecked

    // Extract display text based on headers or checkboxes
    val displayText = when {
        isH1 -> cleanText.removePrefix("# ").trim()
        isH2 -> cleanText.removePrefix("## ").trim()
        isH3 -> cleanText.removePrefix("### ").trim()
        isChecked -> {
            if (cleanText.startsWith("[X] ", ignoreCase = true) || cleanText.startsWith("[x] ", ignoreCase = true)) cleanText.substring(4).trim()
            else cleanText.substring(3).trim()
        }
        isInProgress -> {
            if (cleanText.startsWith("[-] ") || cleanText.startsWith("[/] ")) cleanText.substring(4).trim()
            else cleanText.substring(3).trim()
        }
        isUnchecked -> {
            if (cleanText.startsWith("[ ] ")) cleanText.substring(4).trim()
            else cleanText.substring(3).trim()
        }
        else -> cleanText
    }

    val primaryColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(
                start = (level * 20).dp,
                top = if (isH1) 12.dp else if (isH2) 8.dp else 4.dp,
                bottom = if (isH1) 12.dp else if (isH2) 8.dp else 4.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Render either checkbox, heading indicator, or classic bullet
        when {
            isH1 || isH2 || isH3 -> {
                // Large headings get a beautiful, colorful left vertical bar instead of bullets
                Box(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .width(4.dp)
                        .height(if (isH1) 22.dp else if (isH2) 18.dp else 14.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            color = when {
                                isH1 -> MaterialTheme.colorScheme.primary
                                isH2 -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.tertiary
                            }
                        )
                        .clickable { onClick() }
                )
            }
            isTodo -> {
                val status = when {
                    isChecked -> "已完成"
                    isInProgress -> "进行中"
                    else -> "未完成"
                }
                var isDark = false
                if (viewModel != null) {
                    val dm by viewModel.isDarkMode.collectAsState()
                    isDark = dm
                }
                ThreeStateCheckbox(
                    status = status,
                    onToggle = { if (onToggle != null) onToggle() else onClick() },
                    isAppDarkMode = isDark,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
            else -> {
                // Logseq-style bullet
                Box(
                    modifier = Modifier
                        .padding(start = 6.dp, end = 12.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (level == 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                        .border(1.dp, if (level == 0) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .clickable { onClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (level == 0) 5.dp else 3.dp)
                            .clip(CircleShape)
                            .background(if (level == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    )
                }
            }
        }

        // Apply corresponding typography style to text
        val textStyle = when {
            isH1 -> MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp
            )
            isH2 -> MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                fontSize = 17.sp
            )
            isH3 -> MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                fontSize = 15.sp
            )
            isChecked -> TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                textDecoration = TextDecoration.LineThrough,
                lineHeight = 22.sp
            )
            else -> TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                lineHeight = 22.sp
            )
        }

        val detectedUrl = remember(line) { extractUrl(line) }
        val displayTextWithoutUrlAndEmptyCheck = remember(displayText) {
            val urlRegex = "(https?://|www\\.)[a-zA-Z0-9\\-\\._~:/?#\\[\\]@!$&'()*+,;=]+".toRegex()
            val clean = displayText.replace(urlRegex, "").trim()
            if (clean.isEmpty()) null else clean
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (detectedUrl == null) {
                ClickableMarkdownText(
                    text = parseMarkdownToAnnotatedString(displayText, primaryColor),
                    style = textStyle,
                    modifier = Modifier.fillMaxWidth(),
                    onNormalClick = { 
                        if (isSelected || onLongClick != null) onClick() 
                    },
                    onLinkClick = { clickedLink ->
                        viewModel?.let { vm ->
                            val matchingNote = vm.allNotes.value.find { it.title.equals(clickedLink, ignoreCase = true) }
                            if (matchingNote != null) {
                                vm.selectNote(matchingNote)
                                vm.selectTab(SelectedTab.NOTES)
                            } else {
                                vm.createNote(title = clickedLink, content = "")
                                vm.selectTab(SelectedTab.NOTES)
                            }
                        }
                    }
                )
            } else {
                displayTextWithoutUrlAndEmptyCheck?.let { displayStr ->
                    ClickableMarkdownText(
                        text = parseMarkdownToAnnotatedString(displayStr, primaryColor),
                        style = textStyle,
                        modifier = Modifier.fillMaxWidth(),
                        onNormalClick = { 
                            if (isSelected || onLongClick != null) onClick() 
                        },
                        onLinkClick = { clickedLink ->
                            viewModel?.let { vm ->
                                val matchingNote = vm.allNotes.value.find { it.title.equals(clickedLink, ignoreCase = true) }
                                if (matchingNote != null) {
                                    vm.selectNote(matchingNote)
                                    vm.selectTab(SelectedTab.NOTES)
                                } else {
                                    vm.createNote(title = clickedLink, content = "")
                                    vm.selectTab(SelectedTab.NOTES)
                                }
                            }
                        }
                    )
                }
                BookmarkCard(url = detectedUrl)
            }
        }
    }
}

// --- Component 5: TodoTabView ---
@Composable
fun ThreeStateCheckbox(
    status: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    isAppDarkMode: Boolean = false
) {
    Box(
        modifier = modifier
            .size(40.dp) // Larger touch target
            .clip(CircleShape)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    when (status) {
                        "已完成" -> MaterialTheme.colorScheme.primary
                        "进行中" -> if (isAppDarkMode) Color(0xFF3A3A3C) else Color(0xFFF2F2F7)
                        else -> Color.Transparent
                    }
                )
                .border(
                    width = if (status == "未完成") 2.dp else 1.5.dp,
                    color = when (status) {
                        "已完成" -> MaterialTheme.colorScheme.primary
                        "进行中" -> if (isAppDarkMode) Color(0xFF636366) else Color(0xFF8E8E93)
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                    },
                    shape = RoundedCornerShape(6.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
        when (status) {
            "已完成" -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
            "进行中" -> {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(if (isAppDarkMode) Color(0xFF8E8E93) else Color(0xFF636366))
                )
            }
        }
    }
}
}

// Data class for representing unified Todo items extracted from note content, note tags or diary contents
data class TodoListItem(
    val id: String,
    val title: String,
    val noteTitle: String,
    val containerType: String, // "页面", "页中", "日记"
    val todoStatus: String, // "未完成", "进行中", "已完成"
    val isDone: Boolean,
    val folderName: String,
    val sourceNote: Note? = null,
    val sourceDiary: Diary? = null,
    val lineIndex: Int = -1,
    val tags: List<String> = emptyList()
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TodoTabView(viewModel: NoteViewModel) {
    val allNotes by viewModel.allNotes.collectAsState()
    val folders by viewModel.allFolders.collectAsState()
    val diaries by viewModel.allDiaries.collectAsState()
    val isAppDarkMode by viewModel.isDarkMode.collectAsState()
    
    var selectedStatusFilter by remember { mutableStateOf("进行中") }
    var showAddTodoDialog by remember { mutableStateOf(false) }
    var selectedTodoTagFilter by remember { mutableStateOf<String?>(null) }

    var selectedTodoIds by remember { mutableStateOf(setOf<String>()) }
    val isMultiSelectMode = selectedTodoIds.isNotEmpty()

    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    // Dynamically extract all items that have checklists/checkboxes
    val allTodos = remember(allNotes, folders, diaries) {
        val list = mutableListOf<TodoListItem>()
        
        // 1. Core todo notes (where isTodo == true)
        allNotes.filter { it.isTodo }.forEach { note ->
            val folderName = folders.find { it.id == note.folderId }?.name ?: "未分类"
            val tagList = note.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            list.add(
                TodoListItem(
                    id = "note_todo_${note.id}",
                    title = note.title.ifEmpty { "无标题" },
                    noteTitle = note.title.ifEmpty { "无标题" },
                    containerType = "页面",
                    todoStatus = note.todoStatus,
                    isDone = note.isTodoDone,
                    folderName = folderName,
                    sourceNote = note,
                    tags = tagList
                )
            )
        }
        
        // 2. Lines with checkmarks in ALL notes
        allNotes.forEach { note ->
            val lines = note.content.split("\n")
            lines.forEachIndexed { index, line ->
                val clean = stripBulletPrefix(line)
                val isBox = clean.startsWith("[ ]") || clean.startsWith("[-]") || clean.startsWith("[/]") || clean.startsWith("[x]", ignoreCase = true)
                if (isBox) {
                    val status = when {
                        clean.startsWith("[x]", ignoreCase = true) -> "已完成"
                        clean.startsWith("[-]") || clean.startsWith("[/]") -> "进行中"
                        else -> "未完成"
                    }
                    val folderName = folders.find { it.id == note.folderId }?.name ?: "未分类"
                    val displayText = if (clean.startsWith("[ ]") || clean.startsWith("[-]")) clean.substring(4).trim() else clean.substring(3).trim()
                    val tagList = note.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    list.add(
                        TodoListItem(
                            id = "inline_note_${note.id}_$index",
                            title = displayText.ifEmpty { "页面内空白待办" },
                            noteTitle = note.title.ifEmpty { "无标题" },
                            containerType = "页面内",
                            todoStatus = status,
                            isDone = (status == "已完成"),
                            folderName = folderName,
                            sourceNote = note,
                            lineIndex = index,
                            tags = tagList
                        )
                    )
                }
            }
        }
        
        // 3. Lines with checkmarks in Diaries
        diaries.forEach { diary ->
            val lines = diary.content.split("\n")
            lines.forEachIndexed { index, line ->
                val clean = stripBulletPrefix(line)
                val isBox = clean.startsWith("[ ]") || clean.startsWith("[-]") || clean.startsWith("[/]") || clean.startsWith("[x]", ignoreCase = true)
                if (isBox) {
                    val status = when {
                        clean.startsWith("[x]", ignoreCase = true) -> "已完成"
                        clean.startsWith("[-]") || clean.startsWith("[/]") -> "进行中"
                        else -> "未完成"
                    }
                    val displayText = if (clean.startsWith("[ ]") || clean.startsWith("[-]")) clean.substring(4).trim() else clean.substring(3).trim()
                    list.add(
                        TodoListItem(
                            id = "inline_diary_${diary.date}_$index",
                            title = displayText.ifEmpty { "日记内空白待办" },
                            noteTitle = "日记 ${diary.date}",
                            containerType = "日记",
                            todoStatus = status,
                            isDone = (status == "已完成"),
                            folderName = "日记目录",
                            sourceDiary = diary,
                            lineIndex = index,
                            tags = listOf("日记")
                        )
                    )
                }
            }
        }
        
        list
    }

    // Extract all distinct tags from these todo items
    val availableTags = remember(allTodos) {
        val tagsSet = mutableSetOf<String>()
        allTodos.forEach { todo ->
            tagsSet.addAll(todo.tags)
        }
        tagsSet.toList().sorted()
    }

    // Reset selected tag if it's no longer present in available tags
    LaunchedEffect(availableTags) {
        if (selectedTodoTagFilter != null && !availableTags.contains(selectedTodoTagFilter)) {
            selectedTodoTagFilter = null
        }
    }

    // Filter list based on segments and tag choices
    val filteredTodos = remember(allTodos, selectedStatusFilter, selectedTodoTagFilter) {
        allTodos.filter { item ->
            val matchesStatus = when (selectedStatusFilter) {
                "全部" -> true
                "未完成" -> item.todoStatus == "未完成"
                "进行中" -> item.todoStatus == "进行中"
                "已完成" -> item.todoStatus == "已完成"
                else -> true
            }
            val matchesTag = selectedTodoTagFilter == null || item.tags.contains(selectedTodoTagFilter)
            matchesStatus && matchesTag
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            if (isMultiSelectMode) {
                // Multi-select actions for Todos
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selectedTodoIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "取消选择")
                        }
                        Text("${selectedTodoIds.size}项", fontWeight = FontWeight.Bold)
                    }

                    Row {
                        IconButton(onClick = {
                            val selectedItems = allTodos.filter { selectedTodoIds.contains(it.id) }
                            val textToCopy = selectedItems.joinToString("\n") { it.title }
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(textToCopy))
                            android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "复制", modifier = Modifier.size(20.dp))
                        }

                        IconButton(onClick = {
                            val selectedItems = allTodos.filter { selectedTodoIds.contains(it.id) }
                            val textToCopy = selectedItems.joinToString("\n") { it.title }
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(textToCopy))

                            // Group by source to avoid conflicts
                            // 1. Note-level todos
                            selectedItems.filter { it.lineIndex == -1 && it.sourceNote != null }.forEach {
                                viewModel.deleteNote(it.sourceNote!!.id)
                            }
                            
                            // 2. Note inline lines
                            selectedItems.filter { it.lineIndex != -1 && it.sourceNote != null }
                                .groupBy { it.sourceNote!!.id }
                                .forEach { (noteId, items) ->
                                    val note = items.first().sourceNote!!
                                    val indicesToDelete = items.map { it.lineIndex }.toSet()
                                    val updatedLines = note.content.split("\n").filterIndexed { i, _ -> !indicesToDelete.contains(i) }
                                    val fullText = updatedLines.joinToString("\n")
                                    viewModel.updateNoteContent(note, note.title, fullText)
                                }
                                
                            // 3. Diary lines
                            selectedItems.filter { it.sourceDiary != null }
                                .groupBy { it.sourceDiary!!.date }
                                .forEach { (date, items) ->
                                    val diary = items.first().sourceDiary!!
                                    val indicesToDelete = items.map { it.lineIndex }.toSet()
                                    val updatedLines = diary.content.split("\n").filterIndexed { i, _ -> !indicesToDelete.contains(i) }
                                    val fullText = updatedLines.joinToString("\n")
                                    viewModel.saveDiary(date, fullText)
                                }
                            
                            selectedTodoIds = emptySet()
                            android.widget.Toast.makeText(context, "已剪切", android.widget.Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCut, contentDescription = "剪切", modifier = Modifier.size(20.dp))
                        }
                        
                        IconButton(onClick = {
                            // Bulk Delete logic
                            val selectedItems = allTodos.filter { selectedTodoIds.contains(it.id) }
                            
                            // Group by source to avoid conflicts
                            // 1. Note-level todos
                            selectedItems.filter { it.lineIndex == -1 && it.sourceNote != null }.forEach {
                                viewModel.deleteNote(it.sourceNote!!.id)
                            }
                            
                            // 2. Note inline lines
                            selectedItems.filter { it.lineIndex != -1 && it.sourceNote != null }
                                .groupBy { it.sourceNote!!.id }
                                .forEach { (noteId, items) ->
                                    val note = items.first().sourceNote!!
                                    val indicesToDelete = items.map { it.lineIndex }.toSet()
                                    val updatedLines = note.content.split("\n").filterIndexed { i, _ -> !indicesToDelete.contains(i) }
                                    val fullText = updatedLines.joinToString("\n")
                                    viewModel.updateNoteContent(note, note.title, fullText)
                                }
                                
                            // 3. Diary lines
                            selectedItems.filter { it.sourceDiary != null }
                                .groupBy { it.sourceDiary!!.date }
                                .forEach { (date, items) ->
                                    val diary = items.first().sourceDiary!!
                                    val indicesToDelete = items.map { it.lineIndex }.toSet()
                                    val updatedLines = diary.content.split("\n").filterIndexed { i, _ -> !indicesToDelete.contains(i) }
                                    val fullText = updatedLines.joinToString("\n")
                                    viewModel.saveDiary(date, fullText)
                                }
                            
                            selectedTodoIds = emptySet()
                            android.widget.Toast.makeText(context, "已删除", android.widget.Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            } else {
                // New redesigned Segmented Switch Filter with task counts (filtered by current tag)
                val statusOptions = listOf("进行中", "未完成", "已完成", "全部")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    statusOptions.forEach { option ->
                        val count = when(option) {
                            "全部" -> {
                                allTodos.count { selectedTodoTagFilter == null || it.tags.contains(selectedTodoTagFilter) }
                            }
                            "未完成" -> {
                                allTodos.count { 
                                    (selectedTodoTagFilter == null || it.tags.contains(selectedTodoTagFilter)) && it.todoStatus == "未完成"
                                }
                            }
                            "进行中" -> {
                                allTodos.count { 
                                    (selectedTodoTagFilter == null || it.tags.contains(selectedTodoTagFilter)) && it.todoStatus == "进行中"
                                }
                            }
                            "已完成" -> {
                                allTodos.count { 
                                    (selectedTodoTagFilter == null || it.tags.contains(selectedTodoTagFilter)) && it.todoStatus == "已完成"
                                }
                            }
                            else -> 0
                        }
                        val isSelected = selectedStatusFilter == option
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedStatusFilter = option }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val statusIcon = when(option) {
                                    "进行中" -> Icons.Default.PlayArrow
                                    "未完成" -> Icons.Default.Schedule
                                    "已完成" -> Icons.Default.CheckCircle
                                    else -> null
                                }
                                statusIcon?.let {
                                    Icon(
                                        imageVector = it,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Text(
                                    text = option,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Box(
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                                        .padding(horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = count.toString(),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // New tag bar (标签栏)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Tag,
                    contentDescription = "过滤标签",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                
                // "全部" Chip
                val isAllSelected = selectedTodoTagFilter == null
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isAllSelected) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .clickable { selectedTodoTagFilter = null }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "全部标签",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAllSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                availableTags.forEach { tag ->
                    val isSelected = selectedTodoTagFilter == tag
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .clickable { selectedTodoTagFilter = if (isSelected) null else tag }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "#$tag",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredTodos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.LightGray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("当前没有待办事项需要处理喔！", color = Color.Gray, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredTodos, key = { it.id }) { item ->
                        val isSelected = selectedTodoIds.contains(item.id)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (isMultiSelectMode) {
                                            selectedTodoIds = if (isSelected) selectedTodoIds - item.id else selectedTodoIds + item.id
                                        } else {
                                            if (item.sourceNote != null) {
                                                viewModel.selectNote(item.sourceNote)
                                                viewModel.selectTab(SelectedTab.NOTES)
                                            } else if (item.sourceDiary != null) {
                                                viewModel.selectDate(item.sourceDiary.date)
                                                viewModel.selectTab(SelectedTab.DIARY)
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        selectedTodoIds = selectedTodoIds + item.id
                                    }
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                } else if (!item.isDone) {
                                    if (isAppDarkMode) Color(0xFF1C1C1E) else Color(0xFFF9F9F9)
                                } else MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(10.dp),
                            border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ThreeStateCheckbox(
                                    status = item.todoStatus,
                                    isAppDarkMode = isAppDarkMode,
                                    onToggle = {
                                        if (item.sourceNote != null) {
                                            if (item.lineIndex == -1) {
                                                viewModel.toggleTodoDone(item.sourceNote)
                                            } else {
                                                viewModel.toggleContentLineTodo(
                                                    note = item.sourceNote,
                                                    lineIndex = item.lineIndex,
                                                    currentStatus = item.todoStatus
                                                )
                                            }
                                        } else if (item.sourceDiary != null) {
                                            viewModel.toggleDiaryLineTodo(
                                                diary = item.sourceDiary,
                                                lineIndex = item.lineIndex,
                                                currentStatus = item.todoStatus
                                            )
                                        }
                                    }
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (item.isDone) Color.Gray else MaterialTheme.colorScheme.onSurface,
                                        style = TextStyle(
                                            textDecoration = if (item.isDone) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // Context pill
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    if (isAppDarkMode) Color(0xFF2C2C2E) 
                                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "📍 [${item.containerType}] ${item.noteTitle}",
                                                fontSize = 9.sp,
                                                color = if (isAppDarkMode) Color(0xFF999999) else Color(0xFF666666)
                                            )
                                        }

                                        // Folder Name
                                        if (item.folderName.isNotBlank() && item.folderName != "未分类") {
                                            Text(
                                                text = "📁 ${item.folderName}",
                                                fontSize = 9.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                }

                                if (item.sourceNote != null) {
                                    IconButton(onClick = { viewModel.selectNote(item.sourceNote) }) {
                                        Icon(Icons.Default.Edit, contentDescription = "编辑详情", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Action Button at bottom right
        FloatingActionButton(
            onClick = { showAddTodoDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Default.Add, contentDescription = "新建待办事项")
        }

        // Add Todo Dialog
        if (showAddTodoDialog) {
            var newTodoTitle by remember { mutableStateOf("") }
            var newTodoContent by remember { mutableStateOf("") }
            var newTodoTags by remember { mutableStateOf("待办") }
            var selectedFolderId by remember { mutableStateOf<Long?>(null) }
            var dropdownExpanded by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showAddTodoDialog = false },
                title = { Text("新建待办事项", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = newTodoTitle,
                            onValueChange = { newTodoTitle = it },
                            label = { Text("待办标题 (必填)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = newTodoContent,
                            onValueChange = { newTodoContent = it },
                            label = { Text("详情备注 (可选)") },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = newTodoTags,
                            onValueChange = { newTodoTags = it },
                            label = { Text("标签 (逗号分隔)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Folder selector dropdown in Dialog
                        Box(modifier = Modifier.fillMaxWidth()) {
                            val activeFolderName = folders.find { it.id == selectedFolderId }?.name ?: "未分类"
                            OutlinedButton(
                                onClick = { dropdownExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("📁 分属目录: $activeFolderName", fontSize = 13.sp)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }
                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.85f)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("未分类 (根目录)") },
                                    onClick = {
                                        selectedFolderId = null
                                        dropdownExpanded = false
                                    }
                                )
                                folders.forEach { f ->
                                    DropdownMenuItem(
                                        text = { Text(f.name) },
                                        onClick = {
                                            selectedFolderId = f.id
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newTodoTitle.isNotBlank()) {
                                viewModel.createNote(
                                    title = newTodoTitle,
                                    content = newTodoContent.ifEmpty { "待办内容新建自快速面板" },
                                    folderId = selectedFolderId,
                                    tags = newTodoTags,
                                    isTodo = true
                                )
                                showAddTodoDialog = false
                            }
                        },
                        enabled = newTodoTitle.isNotBlank()
                    ) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddTodoDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }
    }
}

data class TaggedBlockItem(
    val id: String,
    val note: Note,
    val title: String,
    val contentBlock: String,
    val folderName: String,
    val tags: List<String>
)

// --- Component 6: TagCategorisationView ---
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TagCategorisationView(viewModel: NoteViewModel) {
    val tags by viewModel.allTags.collectAsState()
    val allNotes by viewModel.allNotes.collectAsState()
    val allFolders by viewModel.allFolders.collectAsState()
    val selectedTagFilter by viewModel.selectedTagFilter.collectAsState()
    val isInSelectionMode by viewModel.isInSelectionMode.collectAsState()
    val selectedNoteIds by viewModel.selectedNoteIds.collectAsState()
    val isAppDarkMode by viewModel.isDarkMode.collectAsState()

    var isMultiSelectMode by remember { mutableStateOf(false) }

    val allTaggedBlocks = remember(allNotes, allFolders) {
        val list = mutableListOf<TaggedBlockItem>()
        val inlineTagRegex = Regex("#([a-zA-Z0-9_\\u4e00-\\u9fa5]+)")

        allNotes.forEach { note ->
            val folderName = allFolders.find { it.id == note.folderId }?.name ?: "未分类"
            val lines = note.content.split("\n")
            val lineLevels = lines.map { line -> 
                line.takeWhile { it == ' ' || it == '\t' }.replace("\t", "    ").length
            }

            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                val tagsInLine = inlineTagRegex.findAll(line).map { it.groupValues[1] }.toList()
                val noteTagList = note.tags.split(",").map{ it.trim() }.filter{ it.isNotEmpty() }
                
                if (tagsInLine.isNotEmpty() || (noteTagList.isNotEmpty() && lineLevels[i] == 0 && line.isNotBlank())) {
                    val rootLevel = lineLevels[i]
                    val blockLines = mutableListOf<String>()
                    val allTagsInBlock = mutableSetOf<String>()
                    allTagsInBlock.addAll(tagsInLine)
                    allTagsInBlock.addAll(noteTagList)
                    
                    blockLines.add(line)
                    var j = i + 1
                    while (j < lines.size && (lineLevels[j] > rootLevel || lines[j].isBlank())) {
                        if (lines[j].isNotBlank()) {
                            blockLines.add(lines[j])
                            allTagsInBlock.addAll(inlineTagRegex.findAll(lines[j]).map { it.groupValues[1] })
                        }
                        j++
                    }
                    
                    list.add(
                        TaggedBlockItem(
                            id = "block_${note.id.hashCode()}_$i",
                            note = note,
                            title = note.title.ifEmpty { "无标题" },
                            contentBlock = blockLines.joinToString("\n").trimEnd(),
                            folderName = folderName,
                            tags = allTagsInBlock.toList()
                        )
                    )
                    
                    i = Math.max(i + 1, j)
                } else {
                    i++
                }
            }
        }
        list
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // New tag bar (标签栏)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Tag,
                    contentDescription = "过滤标签",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                
                // "全部" Chip
                val isAllSelected = selectedTagFilter.isEmpty()
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isAllSelected) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .clickable { viewModel.selectTagFilter(null) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "全部标签",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAllSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                tags.forEach { (tag, count) ->
                    val isSelected = selectedTagFilter.contains(tag)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .clickable { viewModel.toggleTagFilter(tag) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "#$tag ($count)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // List blocks filtered by active tags
        val filteredBlocks = remember(allTaggedBlocks, selectedTagFilter) {
            if (selectedTagFilter.isEmpty()) {
                allTaggedBlocks
            } else {
                allTaggedBlocks.filter { block ->
                    // Support matching implicit tags from parent note or explicit inline tags
                    selectedTagFilter.all { tag ->
                        if (tag == "★ 收藏") block.note.isFavorite else block.tags.contains(tag)
                    }
                }
            }
        }

        Text(
            text = if (selectedTagFilter.isEmpty()) "所有标签内容" else "包含：${selectedTagFilter.joinToString(", ") { "#$it" }} 的内容",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        if (filteredBlocks.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("此标签下没有任何内容", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(filteredBlocks, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                viewModel.selectNote(item.note)
                                viewModel.selectTab(SelectedTab.NOTES)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isAppDarkMode) Color(0xFF1C1C1E) else Color(0xFFF9F9F9)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = item.contentBlock,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Context pill
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            if (isAppDarkMode) Color(0xFF2C2C2E) 
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "📍 [页面] ${item.title}",
                                        fontSize = 9.sp,
                                        color = if (isAppDarkMode) Color(0xFF999999) else Color(0xFF666666)
                                    )
                                }

                                // Folder Name
                                if (item.folderName.isNotBlank() && item.folderName != "未分类") {
                                    Text(
                                        text = "📁 ${item.folderName}",
                                        fontSize = 9.sp,
                                        color = if (isAppDarkMode) Color(0xFF999999) else Color(0xFF666666)
                                    )
                                }

                                // Tags display
                                val visibleTags = item.tags.take(3)
                                visibleTags.forEach { t ->
                                    Text(
                                        text = "#$t",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (item.tags.size > 3) {
                                    Text(
                                        text = "+${item.tags.size - 3}",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

// --- Component 7: InteractiveGraphView ---
@Composable
fun InteractiveGraphView(viewModel: NoteViewModel) {
    val folders by viewModel.allFolders.collectAsState()
    val notes by viewModel.allNotes.collectAsState()

    // Nodes and Links Setup
    var nodesList by remember { mutableStateOf<List<GraphNode>>(emptyList()) }
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    
    // State to efficiently cache paint to prevent OOM/GC thrashing
    val textPaint = remember {
        android.graphics.Paint().apply {
            textSize = 28f
            this.color = android.graphics.Color.GRAY
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }
    }

    LaunchedEffect(folders, notes) {
        val list = mutableListOf<GraphNode>()
        // 1. Folders center elements
        folders.forEachIndexed { i, f ->
            val angle = (2 * Math.PI * i) / (folders.size.coerceAtLeast(1))
            val radius = 180f
            val x = (300f + radius * Math.cos(angle)).toFloat()
            val y = (320f + radius * Math.sin(angle)).toFloat()
            list.add(
                GraphNode(
                    id = "folder_${f.id}",
                    label = f.name,
                    type = "folder",
                    x = x,
                    y = y,
                    color = Color(0xFF059669) // Emerald
                )
            )
        }

        // 2. Notes nodes orbiting their matching folders
        notes.forEachIndexed { i, n ->
            val angle = (2 * Math.PI * i) / (notes.size.coerceAtLeast(1))
            val radius = 280f
            val x = (300f + radius * Math.cos(angle + 0.5)).toFloat()
            val y = (320f + radius * Math.sin(angle + 0.5)).toFloat()
            list.add(
                GraphNode(
                    id = "note_${n.id}",
                    label = n.title,
                    type = "note",
                    x = x,
                    y = y,
                    color = Color(0xFF0D9488) // Teal
                )
            )
        }
        nodesList = list
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "双链知识树图谱 (支持双指平移及单点拖拽元素)",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var draggedNodeId: String? = null
                            val startOffset = down.position
                            val adjX = (startOffset.x - pan.x) / scale
                            val adjY = (startOffset.y - pan.y) / scale
                            val clickedIndex = nodesList.indexOfFirst { node ->
                                val dx = node.x - adjX
                                val dy = node.y - adjY
                                (dx * dx + dy * dy) < (35f * 35f) // collision check
                            }
                            if (clickedIndex != -1) {
                                draggedNodeId = nodesList[clickedIndex].id
                            }

                            var pastTouch = startOffset
                            var isZooming = false
                            var hasMovedSignificantDistance = false

                            do {
                                val event = awaitPointerEvent()
                                val fingers = event.changes.filter { it.pressed }
                                
                                if (fingers.size >= 2) {
                                    isZooming = true
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    scale = (scale * zoomChange).coerceIn(0.5f, 5f)
                                    pan += panChange
                                    event.changes.forEach { it.consume() }
                                } else if (fingers.size == 1) {
                                    val change = fingers[0]
                                    val currentTouch = change.position
                                    val dragAmount = currentTouch - pastTouch
                                    
                                    if ((currentTouch - startOffset).getDistance() > 10f) {
                                        hasMovedSignificantDistance = true
                                    }
                                    
                                    pastTouch = currentTouch
                                    if (!isZooming) {
                                        if (draggedNodeId != null) {
                                            val nodeId = draggedNodeId
                                            nodesList = nodesList.map { node ->
                                                if (node.id == nodeId) {
                                                    node.copy(
                                                        x = node.x + dragAmount.x / scale,
                                                        y = node.y + dragAmount.y / scale
                                                    )
                                                } else {
                                                    node
                                                }
                                            }
                                        } else {
                                            pan += dragAmount
                                        }
                                        change.consume()
                                    }
                                }
                            } while (event.changes.any { it.pressed })

                            // Differentiate TAP from DRAG/ZOOM
                            if (!isZooming && !hasMovedSignificantDistance) {
                                if (clickedIndex != -1) {
                                    val node = nodesList[clickedIndex]
                                    if (node.type == "note") {
                                        val noteId = node.id.replace("note_", "").toLongOrNull()
                                        val note = notes.find { it.id == noteId }
                                        if (note != null) {
                                            viewModel.selectNote(note)
                                            viewModel.selectTab(SelectedTab.NOTES)
                                        }
                                    } else if (node.type == "folder") {
                                        val folderId = node.id.replace("folder_", "").toLongOrNull()
                                        viewModel.navigateToFolder(folderId)
                                        viewModel.selectTab(SelectedTab.NOTES)
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                withTransform({
                    translate(pan.x, pan.y)
                    scale(scale, scale)
                }) {
                    // 1. Draw Links inside canvas
                    nodesList.forEach { node ->
                        if (node.type == "note") {
                            val noteId = node.id.replace("note_", "").toLongOrNull()
                            val note = notes.find { it.id == noteId }
                            if (note?.folderId != null) {
                                val parentFolderNode = nodesList.find { it.id == "folder_${note.folderId}" }
                                if (parentFolderNode != null) {
                                    drawLine(
                                        color = Color.LightGray.copy(alpha = 0.45f),
                                        start = Offset(node.x, node.y),
                                        end = Offset(parentFolderNode.x, parentFolderNode.y),
                                        strokeWidth = 2.0f / scale
                                    )
                                }
                            }
                        }
                    }

                    // 2. Draw nodes circles with text label overlays
                    nodesList.forEach { node ->
                        // Drop Shadow circle
                        drawCircle(
                            color = Color.Gray.copy(alpha = 0.15f),
                            radius = if (node.type == "folder") 16f / scale else 12f / scale,
                            center = Offset(node.x + 2f, node.y + 2f)
                        )

                        drawCircle(
                            color = node.color,
                            radius = if (node.type == "folder") 14f / scale else 10f / scale,
                            center = Offset(node.x, node.y)
                        )

                        // Overlaid labels
                        drawContext.canvas.nativeCanvas.drawText(
                            node.label,
                            node.x,
                            node.y + (24f / scale) + (textPaint.textSize / 3),
                            textPaint
                        )
                    }
                }
            }
        }
    }
}


// --- Component 8: Rich Note Editor / Details Mode ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorView(
    note: Note,
    viewModel: NoteViewModel,
    onBack: (() -> Unit)? = null,
    tabs: @Composable () -> Unit = {}
) {
    val folders by viewModel.allFolders.collectAsState()
    
    var title by remember(note.id) { mutableStateOf(note.title) }
    var content by remember(note.id) { mutableStateOf(TextFieldValue(note.content, TextRange(note.content.length))) }
    var tags by remember(note.id) { mutableStateOf(note.tags) }
    var isTodo by remember(note.id) { mutableStateOf(note.isTodo) }
    var isTodoDone by remember(note.id) { mutableStateOf(note.isTodoDone) }
    var todoStatus by remember(note.id) { mutableStateOf(note.todoStatus) }
    var folderId by remember(note.id) { mutableStateOf(note.folderId) }

    var folderSelectorExpanded by remember { mutableStateOf(false) }
    val allTags by viewModel.allTags.collectAsState()
    val recentTags = remember(allTags) { allTags.keys.toList() }

    var activeLineIndex by remember(note.id) { mutableStateOf<Int?>(null) }
    var activeLineTextValue by remember(note.id) { mutableStateOf(TextFieldValue("")) }

    var selectedLineIndices by remember(note.id) { mutableStateOf(setOf<Int>()) }
    val isMultiSelectMode = selectedLineIndices.isNotEmpty()

    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    val lines = remember(content.text) {
        val rawLines = content.text.split("\n")
        if (rawLines.isEmpty() || (rawLines.size == 1 && rawLines[0].isEmpty())) {
            listOf("")
        } else {
            rawLines
        }
    }

    // Debounced Save Mechanism
    LaunchedEffect(title, content.text, tags, isTodo, isTodoDone, todoStatus, folderId) {
        // Skip initial frame if it matches input note exactly (to avoid immediate rewrite on open)
        if (title == note.title && content.text == note.content && tags == note.tags && 
            isTodo == note.isTodo && isTodoDone == note.isTodoDone && todoStatus == note.todoStatus &&
            folderId == note.folderId) {
            return@LaunchedEffect
        }
        
        // We add a delay to avoid hammering the DB on every keystroke.
        kotlinx.coroutines.delay(800)
        val updatedNote = if (folderId != note.folderId) note.copy(folderId = folderId) else note
        viewModel.updateNoteContent(updatedNote, title, content.text, tags, isTodo = isTodo, isTodoDone = isTodoDone, todoStatus = todoStatus)
    }

    val lazyListState = rememberLazyListState()

    LaunchedEffect(activeLineIndex) {
        activeLineIndex?.let { index ->
            if (index in lines.indices) {
                kotlinx.coroutines.delay(100)
                try {
                    lazyListState.animateScrollToItem(index)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {}
            }
        }
    }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    androidx.activity.compose.BackHandler(enabled = activeLineIndex != null) {
        activeLineIndex = null
        focusManager.clearFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp)
        ) {
            // Top Toolbar inside editor
            if (isMultiSelectMode || onBack != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (isMultiSelectMode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { selectedLineIndices = emptySet() }) {
                                Icon(Icons.Default.Close, contentDescription = "取消选择")
                            }
                            Text("${selectedLineIndices.size}项", fontWeight = FontWeight.Bold)
                        }

                        Row {
                            IconButton(onClick = {
                                val sortedIndices = selectedLineIndices.sorted().filter { it in lines.indices }
                                val selectedLines = sortedIndices.map { lines[it] }
                                val textToCopy = selectedLines.joinToString("\n")
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(textToCopy))
                                android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "复制", modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = {
                                val sortedIndices = selectedLineIndices.sorted().filter { it in lines.indices }
                                val selectedLines = sortedIndices.map { lines[it] }
                                val textToCopy = selectedLines.joinToString("\n")
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(textToCopy))

                                // Delete from lines
                                val updatedLines = lines.toMutableList()
                                sortedIndices.reversed().forEach { updatedLines.removeAt(it) }
                                val fullText = updatedLines.joinToString("\n")
                                content = TextFieldValue(fullText)
                                viewModel.updateNoteContent(note, title, fullText, tags, isTodo = isTodo, isTodoDone = isTodoDone)
                                selectedLineIndices = emptySet()
                                android.widget.Toast.makeText(context, "已剪切", android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCut, contentDescription = "剪切", modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = {
                                val updatedLines = lines.toMutableList()
                                selectedLineIndices.sorted().reversed().forEach { updatedLines.removeAt(it) }
                                val fullText = updatedLines.joinToString("\n")
                                content = TextFieldValue(fullText)
                                viewModel.updateNoteContent(note, title, fullText, tags, isTodo = isTodo, isTodoDone = isTodoDone)
                                selectedLineIndices = emptySet()
                                android.widget.Toast.makeText(context, "已删除", android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            }
                        }
                    } else {
                        // onBack is non-null because of the outer check
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                            val isTabletLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE && configuration.screenWidthDp >= 600
                            if (!isTabletLandscape) {
                                IconButton(onClick = { viewModel.toggleDrawer(true) }) {
                                    Icon(Icons.Default.Menu, contentDescription = "打开菜单")
                                }
                            }
                            IconButton(onClick = onBack!!) {
                                Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "保存并返回")
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Star Button
                            IconButton(onClick = { viewModel.toggleNoteFavorite(note) }) {
                                Icon(
                                    imageVector = if (note.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "收藏",
                                    tint = if (note.isFavorite) Color(0xFFFFB000) else Color.Gray
                                )
                            }
                        }
                    }
                }
            }

            // Tabs Row
            tabs()

            // Title text Input
            TextField(
                value = title,
                onValueChange = {
                    title = it
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                textStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp),
                placeholder = { Text("无标题页面", fontSize = 24.sp, fontWeight = FontWeight.Bold) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )

            var showRecentTags by remember { mutableStateOf(false) }

            // Metadata Row (Folder, Time, Tags)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Folder Selection Dropdown
                val activeFolder = folders.find { it.id == note.folderId }
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { folderSelectorExpanded = true },
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                        Text(
                            text = activeFolder?.name ?: "未分类",
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 12.sp
                        )
                    }

                    DropdownMenu(expanded = folderSelectorExpanded, onDismissRequest = { folderSelectorExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("未分类 (根目录)", fontSize = 13.sp) },
                            onClick = {
                                folderId = null
                                folderSelectorExpanded = false
                            }
                        )

                        folders.forEach { f ->
                            DropdownMenuItem(
                                text = { Text(f.name, fontSize = 13.sp) },
                                onClick = {
                                    folderId = f.id
                                    folderSelectorExpanded = false
                                }
                            )
                        }
                    }
                }

                // Date
                val sdf = remember { java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault()) }
                val dateString = remember(note.createdAt) { sdf.format(java.util.Date(note.createdAt)) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    Text(text = dateString, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }

                // Inline Tags Input
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Tag, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    BasicTextField(
                        value = tags,
                        onValueChange = {
                            tags = it
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { showRecentTags = it.isFocused },
                        textStyle = TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (tags.isEmpty()) {
                                Text("添加标签...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 12.sp)
                            }
                            innerTextField()
                        }
                    )
                }
            }

            // Display recently used tags selection list
            if (showRecentTags && recentTags.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "常用标签:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    recentTags.forEach { tag ->
                        val isSelected = tags.split(",").map { it.trim() }.contains(tag)
                        SuggestionChip(
                            onClick = {
                                val currentList = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                                if (currentList.contains(tag)) {
                                    currentList.remove(tag)
                                } else {
                                    currentList.add(tag)
                                }
                                val newTags = currentList.joinToString(", ")
                                tags = newTags
                                viewModel.updateNoteContent(note, title, content.text, newTags, isTodo = isTodo, isTodoDone = isTodoDone)
                            },
                            label = { Text("#$tag", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            modifier = Modifier.height(24.dp),
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                labelColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = null
                        )
                    }
                }
            }

            // Live Outliner Canvas (Line-by-line renderer & editor)
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(lines) { index, line ->
                    if (activeLineIndex == index) {
                        val lineFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val leadingTabs = line.takeWhile { it == '\t' || it == ' ' }.length
                            val level = when {
                                leadingTabs >= 4 -> 2
                                leadingTabs >= 1 -> 1
                                else -> 0
                            }
                            Spacer(modifier = Modifier.width((level * 16).dp))
                            
                            Box(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )

                            TextField(
                                value = activeLineTextValue,
                                onValueChange = { newTextVal ->
                                    activeLineTextValue = newTextVal
                                    val txt = newTextVal.text
                                    if (txt.contains("\n")) {
                                        val updatedLines = lines.toMutableList()
                                        val oldText = lines.getOrNull(index) ?: ""
                                        val cursorBeforeEnter = maxOf(0, newTextVal.selection.start - 1)
                                        val (p1, p2) = splitFormattedLine(oldText, cursorBeforeEnter)
                                        updatedLines[index] = p1
                                        updatedLines.add(index + 1, p2)
                                        val fullText = updatedLines.joinToString("\n")
                                        content = TextFieldValue(fullText)
                                        
                                        val nextIndex = index + 1
                                        activeLineIndex = nextIndex
                                        val nextLineText = updatedLines.getOrNull(nextIndex) ?: ""
                                        activeLineTextValue = TextFieldValue(nextLineText, TextRange(nextLineText.length))
                                    } else {
                                        val updatedLines = lines.toMutableList()
                                        updatedLines[index] = txt
                                        val fullText = updatedLines.joinToString("\n")
                                        content = TextFieldValue(fullText)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(lineFocusRequester)
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Backspace) {
                                            if (activeLineTextValue.selection.start == 0) {
                                                if (index > 0) {
                                                    val updatedLines = lines.toMutableList()
                                                    val currentLineText = lines[index]
                                                    val prevLineText = lines[index - 1]
                                                    
                                                    val (cleanedPrev, cleanedCurrent) = cleanupSplitLines(prevLineText, currentLineText)
                                                    val mergedText = cleanedPrev + cleanedCurrent
                                                    updatedLines[index - 1] = mergedText
                                                    updatedLines.removeAt(index)
                                                    
                                                    val fullText = updatedLines.joinToString("\n")
                                                    content = TextFieldValue(fullText)
                                                    viewModel.updateNoteContent(note, title, fullText, tags, isTodo = isTodo, isTodoDone = isTodoDone)
                                                    
                                                    val prevIndex = index - 1
                                                    activeLineIndex = prevIndex
                                                    activeLineTextValue = TextFieldValue(mergedText, TextRange(cleanedPrev.length))
                                                    return@onPreviewKeyEvent true
                                                }
                                            }
                                        }
                                        false
                                    },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    cursorColor = MaterialTheme.colorScheme.primary
                                ),
                                textStyle = TextStyle(
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            )

                            var focusRequested by remember(activeLineIndex) { mutableStateOf(false) }
                            LaunchedEffect(activeLineIndex) {
                                if (activeLineIndex == index && !focusRequested) {
                                    try {
                                        lineFocusRequester.requestFocus()
                                        focusRequested = true
                                    } catch (e: Exception) {}
                                }
                            }

                            IconButton(
                                onClick = {
                                    val updatedLines = lines.toMutableList()
                                    updatedLines.removeAt(index)
                                    val fullText = updatedLines.joinToString("\n")
                                    content = TextFieldValue(fullText)
                                    viewModel.updateNoteContent(note, title, fullText, tags, isTodo = isTodo, isTodoDone = isTodoDone)
                                    if (updatedLines.isEmpty()) {
                                        activeLineIndex = null
                                    } else {
                                        val nextIndex = maxOf(0, index - 1)
                                        activeLineIndex = nextIndex
                                        val nextLineText = updatedLines.getOrNull(nextIndex) ?: ""
                                        activeLineTextValue = TextFieldValue(nextLineText, TextRange(nextLineText.length))
                                    }
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "删除行",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    } else {
                        BulletJournalLineItem(
                            line = line, 
                            viewModel = viewModel,
                            isSelected = selectedLineIndices.contains(index),
                            onToggle = {
                                if (isMultiSelectMode) {
                                    selectedLineIndices = if (selectedLineIndices.contains(index)) {
                                        selectedLineIndices - index
                                    } else {
                                        selectedLineIndices + index
                                    }
                                    return@BulletJournalLineItem
                                }
                                val clean = stripBulletPrefix(line)
                                val status = when {
                                    clean.startsWith("[x]", ignoreCase = true) -> "已完成"
                                    clean.startsWith("[-]") || clean.startsWith("[/]") -> "进行中"
                                    else -> "未完成"
                                }
                                viewModel.toggleContentLineTodo(note, index, status)
                            },
                            onLongClick = {
                                selectedLineIndices = selectedLineIndices + index
                            }
                        ) {
                            if (isMultiSelectMode) {
                                selectedLineIndices = if (selectedLineIndices.contains(index)) {
                                    selectedLineIndices - index
                                } else {
                                    selectedLineIndices + index
                                }
                            } else {
                                activeLineTextValue = TextFieldValue(line, TextRange(line.length))
                                activeLineIndex = index
                            }
                        }
                    }
                }

                // Add Row / Plus button trigger inline
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            val updatedLines = lines.toMutableList()
                            updatedLines.add("")
                            val fullText = updatedLines.joinToString("\n")
                            content = TextFieldValue(fullText)
                            viewModel.updateNoteContent(note, title, fullText, tags, isTodo = isTodo, isTodoDone = isTodoDone)
                            
                            val nextIndex = updatedLines.size - 1
                            activeLineIndex = nextIndex
                            activeLineTextValue = TextFieldValue("")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "输入新行", modifier = Modifier.size(16.dp))
                            Text("添加新行...", fontSize = 13.sp)
                        }
                    }
                }

                if (activeLineIndex != null) {
                    item {
                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
            }
        }

        // Live Formatting Overlay panel at the bottom of standard editor window
        if (activeLineIndex != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    FormatBar(
                        textFieldValue = activeLineTextValue,
                        onValueChange = { newValue ->
                            activeLineTextValue = newValue
                            val txt = newValue.text
                            val updatedLines = lines.toMutableList()
                            val targetIndex = activeLineIndex!!
                            if (targetIndex < updatedLines.size) {
                                if (txt.contains("\n")) {
                                    val parts = txt.split("\n")
                                    updatedLines[targetIndex] = parts[0]
                                    for (p in 1 until parts.size) {
                                        updatedLines.add(targetIndex + p, parts[p])
                                    }
                                    val fullText = updatedLines.joinToString("\n")
                                    content = TextFieldValue(fullText)
                                    viewModel.updateNoteContent(note, title, fullText, tags, isTodo = isTodo, isTodoDone = isTodoDone)
                                    
                                    val nextIndex = targetIndex + 1
                                    activeLineIndex = nextIndex
                                    val nextLineText = updatedLines.getOrNull(nextIndex) ?: ""
                                    activeLineTextValue = TextFieldValue(nextLineText, TextRange(nextLineText.length))
                                } else {
                                    updatedLines[targetIndex] = txt
                                    val fullText = updatedLines.joinToString("\n")
                                    content = TextFieldValue(fullText)
                                }
                            }
                        },
                        recentTags = recentTags,
                        showFinishButton = false
                    )
                }
            }
        }
    }
}

// --- Component 9: Gemini Chat Assistant overlay dialog ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiFloatingAssistantSheet(
    viewModel: NoteViewModel,
    onDismiss: () -> Unit
) {
    val chatLog by viewModel.aiChatLog.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()
    
    var userPromptText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Header bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Gemini 知识大架构助理", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            }
            TextButton(onClick = { viewModel.clearAiChat() }) {
                Text("清除对话", style = TextStyle(color = Color.Red))
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "关闭")
            }
        }

        Divider()

        // Chat contents screen
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (chatLog.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.LocalActivity, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.LightGray)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("向 Gemini 询问您当前项目或者知识库大纲的关系吧！", color = Color.Gray, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedCard(
                                modifier = Modifier.clickable {
                                    viewModel.sendAiMessage("请根据我的‘项目笔记’知识库提出一个关于‘减肥计划’和‘app开发进程’的综合总结构想。")
                                }
                            ) {
                                Text("💡 综合总结减肥与app进度", modifier = Modifier.padding(10.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                items(chatLog) { chat ->
                    val isModel = chat.role == "model"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isModel) Arrangement.Start else Arrangement.End
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isModel) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary
                                )
                                .padding(12.dp)
                                .widthIn(max = 240.dp)
                        ) {
                            Text(
                                text = chat.content,
                                color = if (isModel) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            if (isAiLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(12.dp)
                        ) {
                            Text("Gemini 思考总结中...", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Quick entry field
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = userPromptText,
                onValueChange = { userPromptText = it },
                placeholder = { Text("发送消息协助构建架构...") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            )

            Button(
                onClick = {
                    if (userPromptText.isNotBlank()) {
                        viewModel.sendAiMessage(userPromptText)
                        userPromptText = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.align(Alignment.CenterVertically)
            ) {
                Icon(Icons.Default.Send, contentDescription = "发送", tint = Color.White)
            }
        }
    }
}

// --- Component 9: SettingsTabView ---
@Composable
fun SettingsTabView(viewModel: NoteViewModel) {
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }
    
    val syncStatus by viewModel.networkSyncManager.syncStatus.collectAsState()
    val discoveredDevices by viewModel.networkSyncManager.discoveredDevices.collectAsState()
    var isDiscoveryActive by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        viewModel.networkSyncManager.startServerAndRegister()
        onDispose {
            viewModel.networkSyncManager.stopDiscovery()
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清除数据") },
            text = { Text("确定要清除所有日记、文件和内容吗？此操作不可逆。") },
            confirmButton = {
                TextButton(onClick = { 
                    viewModel.clearAllData()
                    showClearDialog = false
                    android.widget.Toast.makeText(context, "数据已完全清除", android.widget.Toast.LENGTH_SHORT).show()
                }) {
                    Text("清除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("同步与备份", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
        
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                ListItem(
                    headlineContent = { Text("局域网同步") },
                    supportingContent = { Text("状态: $syncStatus\n本设备将自动开放同步，点击此处可以扫描并同步其他设备上的数据。") },
                    leadingContent = { Icon(Icons.Default.Wifi, null) },
                    modifier = Modifier.clickable {
                        isDiscoveryActive = true
                        viewModel.networkSyncManager.discoverServices()
                    }
                )
                
                if (isDiscoveryActive) {
                    if (discoveredDevices.isEmpty()) {
                        ListItem(
                            headlineContent = { Text("正在查找设备...") },
                            leadingContent = { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
                        )
                    } else {
                        discoveredDevices.forEach { device ->
                            ListItem(
                                headlineContent = { Text(device.serviceName) },
                                supportingContent = { Text("IP: ${device.host}:${device.port}") },
                                leadingContent = { Icon(Icons.Default.Phone, null) },
                                trailingContent = {
                                    Button(onClick = { viewModel.networkSyncManager.syncWithDevice(device) }) {
                                        Text("同步")
                                    }
                                }
                            )
                        }
                    }
                    TextButton(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        onClick = { 
                            isDiscoveryActive = false
                            viewModel.networkSyncManager.stopDiscovery()
                        }
                    ) {
                        Text("结束扫描")
                    }
                }
                
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("WebDAV 同步") },
                    supportingContent = { Text("未配置") },
                    leadingContent = { Icon(Icons.Default.CloudSync, null) },
                    modifier = Modifier.clickable { android.widget.Toast.makeText(context, "功能开发中...", android.widget.Toast.LENGTH_SHORT).show() }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("数据备份") },
                    supportingContent = { Text("将所有数据导出为 ZIP") },
                    leadingContent = { Icon(Icons.Default.Backup, null) },
                    modifier = Modifier.clickable { android.widget.Toast.makeText(context, "备份需要存储权限，功能开发中...", android.widget.Toast.LENGTH_SHORT).show() }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("数据恢复") },
                    supportingContent = { Text("从本地备份 ZIP 恢复") },
                    leadingContent = { Icon(Icons.Default.Restore, null) },
                    modifier = Modifier.clickable { android.widget.Toast.makeText(context, "功能开发中...", android.widget.Toast.LENGTH_SHORT).show() }
                )
            }
        }
        
        Text("危险操作", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.error)
        
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f))
        ) {
            ListItem(
                headlineContent = { Text("清除所有数据", color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text("清空全部日记、文件目录和笔记", color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)) },
                leadingContent = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable { showClearDialog = true }
            )
        }
    }
}
