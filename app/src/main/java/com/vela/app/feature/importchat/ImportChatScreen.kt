package com.vela.app.feature.importchat

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.compose.foundation.background
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vela.app.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vela.app.data.ai.AiInputAttachment
import com.vela.app.data.model.ChatMessage
import com.vela.app.data.model.ChatMessageRole
import com.vela.app.data.mock.MockVelaRepository
import com.vela.app.data.model.EventCandidate
import com.vela.app.data.model.EventCandidateReviewStatus
import com.vela.app.data.model.ImportSession
import com.vela.app.data.model.Location
import com.vela.app.data.model.UserPreferences
import com.vela.app.data.model.remindersFromPreset
import com.vela.app.data.model.selectedReminderMinutes
import com.vela.app.data.model.validateEventInput
import com.vela.app.data.repository.ImportResult
import com.vela.app.notification.NotificationPermissionState
import com.vela.app.ui.ReminderSelector
import com.vela.app.ui.showDateTimePicker
import com.vela.app.widget.VelaWidgetUpdater
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.zip.ZipInputStream

data class ImportChatUiState(
    val session: ImportSession? = null,
    val candidates: List<EventCandidate> = emptyList(),
    val lastImportResult: ImportResult? = null,
    val userPreferences: UserPreferences = UserPreferences(),
    val isSubmittingAttachment: Boolean = false,
    val attachmentErrorText: String? = null,
) {
    private val activeCandidates = candidates.filterNot {
        it.reviewStatus == EventCandidateReviewStatus.Rejected ||
            it.reviewStatus == EventCandidateReviewStatus.Imported
    }

    val candidateDates: List<HighlightedImportDate> = activeCandidates
        .asSequence()
        .map { it.startAt.toDisplayDate() }
        .groupingBy { it }
        .eachCount()
        .map { (date, count) -> HighlightedImportDate(date = date, selectedCount = count) }
        .sortedBy { it.date }
        .toList()

    val visibleCandidates: List<EventCandidate> = activeCandidates

    val pendingCandidateCount: Int = activeCandidates.count {
        it.reviewStatus != EventCandidateReviewStatus.Imported
    }

    val selectedCandidateCount: Int = activeCandidates.count {
        it.isSelectedForImport && it.reviewStatus != EventCandidateReviewStatus.Imported
    }

    val warningText: String? = activeCandidates
        .firstOrNull { it.missingFields.isNotEmpty() }
        ?.let { candidate ->
            "${candidate.title} 缺少：${candidate.missingFields.joinToString("、")}"
        }
        ?: lastImportResult?.blockedReasons?.firstOrNull()

}

data class HighlightedImportDate(
    val date: String,
    val selectedCount: Int,
)

private data class AttachmentSubmissionState(
    val isSubmitting: Boolean = false,
    val errorText: String? = null,
)

class ImportChatViewModel : ViewModel() {
    private val repository = MockVelaRepository
    private val _lastImportResult = kotlinx.coroutines.flow.MutableStateFlow<ImportResult?>(null)
    private val _isSubmittingAttachment = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val _attachmentErrorText = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    private val attachmentSubmissionState = combine(
        _isSubmittingAttachment,
        _attachmentErrorText,
    ) { isSubmitting, errorText ->
        AttachmentSubmissionState(
            isSubmitting = isSubmitting,
            errorText = errorText,
        )
    }

    val uiState: StateFlow<ImportChatUiState> = combine(
        repository.importSession,
        repository.eventCandidates,
        _lastImportResult,
        repository.userPreferences,
        attachmentSubmissionState,
    ) { session, candidates, lastImportResult, userPreferences, attachmentState ->
        ImportChatUiState(
            session = session,
            candidates = candidates,
            lastImportResult = lastImportResult,
            userPreferences = userPreferences,
            isSubmittingAttachment = attachmentState.isSubmitting,
            attachmentErrorText = attachmentState.errorText,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ImportChatUiState(),
    )

    fun submitText(text: String) {
        repository.submitImportText(text)
        _lastImportResult.value = null
        _attachmentErrorText.value = null
    }

    fun submitImage(context: Context, uri: Uri) {
        submitAttachment(context = context, uri = uri, isImage = true)
    }

    fun submitFile(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isSubmittingAttachment.value = true
            _attachmentErrorText.value = null
            runCatching {
                context.toDocumentAiInputAttachments(uri)
            }.onSuccess { attachments ->
                repository.submitImportFile(attachments)
                _lastImportResult.value = null
            }.onFailure { throwable ->
                _attachmentErrorText.value = throwable.message?.takeIf { it.isNotBlank() }
                    ?: "文档读取失败，请重新选择。"
            }
            _isSubmittingAttachment.value = false
        }
    }

    fun submitVoice() {
        repository.submitImportVoice()
        _lastImportResult.value = null
        _attachmentErrorText.value = null
    }

    fun submitEditInstruction(instruction: String) {
        repository.submitNaturalLanguageEdit(instruction)
        _lastImportResult.value = null
        _attachmentErrorText.value = null
    }

    fun toggleCandidate(candidateId: String) {
        repository.toggleCandidateSelection(candidateId)
        _lastImportResult.value = null
    }

    fun updateCandidate(candidate: EventCandidate) {
        repository.updateCandidate(candidate)
        _lastImportResult.value = null
    }

    fun rejectCandidate(candidateId: String) {
        repository.rejectCandidate(candidateId)
        _lastImportResult.value = null
    }

    fun importSelected(): ImportResult {
        val result = repository.importSelectedCandidates()
        _lastImportResult.value = result
        return result
    }

    fun importCandidate(candidateId: String): ImportResult {
        val result = repository.importCandidate(candidateId)
        _lastImportResult.value = result
        return result
    }

    private fun submitAttachment(
        context: Context,
        uri: Uri,
        isImage: Boolean,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isSubmittingAttachment.value = true
            _attachmentErrorText.value = null
            runCatching {
                context.toAiInputAttachment(uri = uri, isImage = isImage)
            }.onSuccess { attachment ->
                if (isImage) {
                    repository.submitImportImage(attachment)
                }
                _lastImportResult.value = null
            }.onFailure {
                _attachmentErrorText.value = if (isImage) {
                    "图片读取失败，请重新选择。"
                } else {
                    "文档读取失败，请重新选择。"
                }
            }
            _isSubmittingAttachment.value = false
        }
    }
}

@Composable
fun ImportChatScreen(
    @Suppress("UNUSED_PARAMETER")
    onCalendarClick: () -> Unit,
    onScheduleClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: ImportChatViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var editingCandidate by remember { mutableStateOf<EventCandidate?>(null) }
    var areAllCandidatesExpanded by remember { mutableStateOf(false) }
    var selectedDateFilter by remember { mutableStateOf<String?>(null) }
    var notificationPermissionDenied by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationPermissionDenied = !granted
    }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            viewModel.submitImage(context, uri)
        }
    }
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            viewModel.submitFile(context, uri)
        }
    }

    fun requestNotificationPermissionIfNeeded() {
        if (NotificationPermissionState.needsRuntimePermission(context)) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun importSelectedCandidates() {
        val result = viewModel.importSelected()
        if (result.isSuccess) {
            requestNotificationPermissionIfNeeded()
            coroutineScope.launch {
                VelaWidgetUpdater.updateAll(context)
            }
        }
    }

    fun importSingleCandidate(candidateId: String) {
        val result = viewModel.importCandidate(candidateId)
        if (result.isSuccess) {
            requestNotificationPermissionIfNeeded()
            coroutineScope.launch {
                VelaWidgetUpdater.updateAll(context)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VelaPageBackground),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                AiScheduleHeader(onSettingsClick = onSettingsClick)
            }

            if (uiState.visibleCandidates.isEmpty() && uiState.session?.messages.orEmpty().size <= 1) {
                item {
                    AiPlanPreviewCard()
                }
                item {
                    AiSuggestionCard()
                }
            }

            uiState.session?.messages?.let { messages ->
                items(messages, key = { it.id }) { message ->
                    ChatBubble(message = message)
                }
            }

            uiState.warningText?.let { warningText ->
                item {
                    InlineStatusText(text = warningText, isError = true)
                }
            }
            uiState.attachmentErrorText?.let { errorText ->
                item {
                    InlineStatusText(text = errorText, isError = true)
                }
            }
            if (uiState.isSubmittingAttachment) {
                item {
                    InlineStatusText(text = "正在识别文件...", isError = false)
                }
            }
            if (notificationPermissionDenied) {
                item {
                    InlineStatusText(text = "通知权限未开启，日程会保存，但系统提醒可能无法弹出。", isError = true)
                }
            }
            uiState.lastImportResult?.takeIf { it.isSuccess }?.let { result ->
                item {
                    ImportSuccessBubble(
                        importedCount = result.importedCount,
                        onScheduleClick = onScheduleClick,
                    )
                }
            }
            if (uiState.visibleCandidates.isNotEmpty()) {
                item {
                    CandidateReviewBubble(
                        candidates = uiState.visibleCandidates,
                        dates = uiState.candidateDates,
                        selectedDateFilter = selectedDateFilter,
                        selectedCandidateCount = uiState.selectedCandidateCount,
                        areAllExpanded = areAllCandidatesExpanded,
                        onDateFilterChange = { selectedDateFilter = it },
                        onToggleExpanded = { areAllCandidatesExpanded = true },
                        onToggle = { candidateId -> viewModel.toggleCandidate(candidateId) },
                        onEdit = { candidate -> editingCandidate = candidate },
                        onDelete = { candidateId -> viewModel.rejectCandidate(candidateId) },
                        onImport = { candidateId -> importSingleCandidate(candidateId) },
                        onImportSelected = ::importSelectedCandidates,
                    )
                }
            }
        }

        ImportInputBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            text = inputText,
            onTextChange = { inputText = it },
            onImageClick = { imagePickerLauncher.launch("image/*") },
            onFileClick = { filePickerLauncher.launch("*/*") },
            onVoiceLongPress = { viewModel.submitVoice() },
            onSubmit = {
                if (inputText.looksLikeEditInstruction()) {
                    viewModel.submitEditInstruction(inputText)
                } else {
                    viewModel.submitText(inputText)
                }
                inputText = ""
            },
        )
    }

    editingCandidate?.let { candidate ->
        EditCandidateDialog(
            candidate = candidate,
            onDismiss = { editingCandidate = null },
            onSave = { updatedCandidate ->
                viewModel.updateCandidate(updatedCandidate)
                editingCandidate = null
            },
        )
    }

}

@Composable
private fun AiScheduleHeader(onSettingsClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(30.dp)),
        shape = RoundedCornerShape(30.dp),
        color = Color(0xFFF4F7FF),
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "◢", color = VelaPrimaryBlue, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Vela",
                            color = VelaPrimaryBlue,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = "AI 日程",
                        style = MaterialTheme.typography.headlineLarge,
                        color = VelaTextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "AI 助手为你智能规划，高效安排每一天",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VelaTextSecondary,
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = VelaPrimaryBlue,
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            text = "输入自然语言创建",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Image(
                    modifier = Modifier.size(112.dp),
                    painter = painterResource(id = R.mipmap.ic_launcher),
                    contentDescription = "Vela",
                )
            }
            TextButton(
                modifier = Modifier.align(Alignment.TopEnd),
                onClick = onSettingsClick,
            ) {
                Text(text = "设置")
            }
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun AiPlanPreviewCard() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI 日程安排",
                        style = MaterialTheme.typography.titleMedium,
                        color = VelaTextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "解析后会在这里生成可确认的候选日程",
                        style = MaterialTheme.typography.bodySmall,
                        color = VelaTextSecondary,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFEAF0FF),
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        text = "AI 生成",
                        color = VelaPrimaryBlue,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            listOf(
                DemoPlanItem("07:30", "起床", "开启活力满满的一天", "30分钟", Color(0xFF3BA7FF)),
                DemoPlanItem("09:00", "面试准备", "复习常见问题，准备材料", "60分钟", Color(0xFF8B5CF6)),
                DemoPlanItem("10:00", "面试", "产品经理岗位面试", "60分钟", Color(0xFF2D6BFF)),
                DemoPlanItem("13:00", "休息", "放松一下，调整状态", "30分钟", Color(0xFF8B5CF6)),
            ).forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = item.time,
                            color = VelaTextPrimary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = item.duration,
                            color = VelaPrimaryBlue,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(item.color.copy(alpha = 0.14f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "•", color = item.color, fontWeight = FontWeight.Bold)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            color = VelaTextPrimary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = item.subtitle,
                            color = VelaTextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Text(
                text = "AI 已为你优化时间安排，发送内容后可查看真实解析结果",
                color = VelaTextSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun AiSuggestionCard() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFFF7F3FF),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "AI 建议",
                color = VelaPrimaryBlue,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "告诉我你的目标，或上传图片/文档，我来帮你生成可确认的日程。",
                color = VelaTextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private data class DemoPlanItem(
    val time: String,
    val title: String,
    val subtitle: String,
    val duration: String,
    val color: Color,
)

@Composable
private fun ImportInputBar(
    modifier: Modifier = Modifier,
    text: String,
    onTextChange: (String) -> Unit,
    onImageClick: () -> Unit,
    onFileClick: () -> Unit,
    onVoiceLongPress: () -> Unit,
    onSubmit: () -> Unit,
) {
    var isAttachmentMenuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                RoundTextButton(
                    text = "+",
                    onClick = { isAttachmentMenuExpanded = true },
                )
                DropdownMenu(
                    expanded = isAttachmentMenuExpanded,
                    onDismissRequest = { isAttachmentMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(text = "图片") },
                        onClick = {
                            isAttachmentMenuExpanded = false
                            onImageClick()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = "文档") },
                        onClick = {
                            isAttachmentMenuExpanded = false
                            onFileClick()
                        },
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp),
            ) {
                BasicTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = text,
                    onValueChange = onTextChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                )
                if (text.isBlank()) {
                    Text(
                        text = "发消息或按住说话...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            RoundTextButton(
                text = "声",
                onClick = {},
                modifier = Modifier.pointerInput(onVoiceLongPress) {
                    detectTapGestures(
                        onLongPress = { onVoiceLongPress() },
                    )
                },
            )
            RoundTextButton(
                text = "发",
                enabled = text.isNotBlank(),
                filled = text.isNotBlank(),
                onClick = onSubmit,
            )
        }
    }
}

@Composable
private fun RoundTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
) {
    Surface(
        modifier = modifier
            .size(42.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = when {
            filled -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = when {
                    filled -> MaterialTheme.colorScheme.onPrimary
                    enabled -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun InlineStatusText(
    text: String,
    isError: Boolean,
) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
private fun ImportSuccessBubble(
    importedCount: Int,
    onScheduleClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.88f),
            shape = RoundedCornerShape(22.dp),
            color = AssistantBubbleColor,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = "已导入 $importedCount 条日程",
                    style = MaterialTheme.typography.bodyLarge,
                )
                TextButton(onClick = onScheduleClick) {
                    Text(text = "查看日程")
                }
            }
        }
    }
}

@Composable
private fun CandidateReviewBubble(
    candidates: List<EventCandidate>,
    dates: List<HighlightedImportDate>,
    selectedDateFilter: String?,
    selectedCandidateCount: Int,
    areAllExpanded: Boolean,
    onDateFilterChange: (String?) -> Unit,
    onToggleExpanded: () -> Unit,
    onToggle: (String) -> Unit,
    onEdit: (EventCandidate) -> Unit,
    onDelete: (String) -> Unit,
    onImport: (String) -> Unit,
    onImportSelected: () -> Unit,
) {
    val filteredCandidates = selectedDateFilter?.let { date ->
        candidates.filter { it.startAt.toDisplayDate() == date }
    } ?: candidates
    val displayedCandidates = if (areAllExpanded) {
        filteredCandidates
    } else {
        filteredCandidates.take(2)
    }
    val hiddenCount = filteredCandidates.size - displayedCandidates.size

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f),
            shape = RoundedCornerShape(24.dp),
            color = AssistantBubbleColor,
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "我整理出 ${candidates.size} 条候选日程",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (dates.isNotEmpty()) {
                    DateChipRow(
                        dates = dates,
                        selectedDate = selectedDateFilter,
                        onDateSelected = onDateFilterChange,
                    )
                }
                displayedCandidates.forEach { candidate ->
                    CandidateCompactCard(
                        candidate = candidate,
                        onToggle = { onToggle(candidate.id) },
                        onEdit = { onEdit(candidate) },
                        onDelete = { onDelete(candidate.id) },
                        onImport = { onImport(candidate.id) },
                    )
                }
                if (hiddenCount > 0) {
                    TextButton(onClick = onToggleExpanded) {
                        Text(text = "展开全部 $hiddenCount 条")
                    }
                }
                CandidateBatchBar(
                    selectedCount = selectedCandidateCount,
                    onImportSelected = onImportSelected,
                )
            }
        }
    }
}

@Composable
private fun DateChipRow(
    dates: List<HighlightedImportDate>,
    selectedDate: String?,
    onDateSelected: (String?) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            DateFilterChip(
                label = "全部",
                selected = selectedDate == null,
                onClick = { onDateSelected(null) },
            )
        }
        items(dates, key = { it.date }) { date ->
            DateFilterChip(
                label = "${date.date.takeLast(5)} · ${date.selectedCount}条",
                selected = selectedDate == date.date,
                onClick = { onDateSelected(date.date) },
            )
        }
    }
}

@Composable
private fun DateFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun CandidateCompactCard(
    candidate: EventCandidate,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onImport: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier
                        .size(22.dp)
                        .clickable(onClick = onToggle),
                    shape = CircleShape,
                    color = if (candidate.isSelectedForImport) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (candidate.isSelectedForImport) {
                            Text(
                                text = "✓",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
                Text(
                    modifier = Modifier.weight(1f),
                    text = candidate.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                candidate.confidence?.let { confidence ->
                    Text(
                        text = "${(confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = "${candidate.startAt.toDisplayDate()} ${candidate.startAt.toDisplayTime()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            candidate.location?.name?.let { locationName ->
                Text(
                    text = locationName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (candidate.missingFields.isNotEmpty()) {
                Text(
                    text = "待补全：${candidate.missingFields.joinToString("、")}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onEdit) {
                    Text(text = "编辑")
                }
                TextButton(onClick = onDelete) {
                    Text(text = "删除")
                }
                TextButton(onClick = onImport) {
                    Text(text = "导入")
                }
            }
        }
    }
}

@Composable
private fun CandidateBatchBar(
    selectedCount: Int,
    onImportSelected: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = "已选 $selectedCount 条",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                enabled = selectedCount > 0,
                onClick = onImportSelected,
            ) {
                Text(text = "导入")
            }
        }
    }
}

private val AssistantBubbleColor = Color(0xFFF4F5F7)
private val UserBubbleColor = Color(0xFF1478FF)
private val VelaPageBackground = Color(0xFFFAFBFF)
private val VelaPrimaryBlue = Color(0xFF2D6BFF)
private val VelaTextPrimary = Color(0xFF12162A)
private val VelaTextSecondary = Color(0xFF72788A)

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == ChatMessageRole.User
    val isSystem = message.role == ChatMessageRole.System
    if (isSystem) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = message.content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(if (isUser) 0.86f else 0.88f),
                shape = RoundedCornerShape(22.dp),
                color = if (isUser) UserBubbleColor else AssistantBubbleColor,
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun EditCandidateDialog(
    candidate: EventCandidate,
    onDismiss: () -> Unit,
    onSave: (EventCandidate) -> Unit,
) {
    val context = LocalContext.current
    var title by remember(candidate.id) { mutableStateOf(candidate.title) }
    var startAt by remember(candidate.id) { mutableStateOf(candidate.startAt) }
    var endAt by remember(candidate.id) { mutableStateOf(candidate.endAt.orEmpty()) }
    var locationName by remember(candidate.id) { mutableStateOf(candidate.location?.name.orEmpty()) }
    var reminderMinutes by remember(candidate.id) {
        mutableStateOf(selectedReminderMinutes(candidate.reminders))
    }
    val validation = validateEventInput(title, startAt, endAt)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "编辑候选日程") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                validation.message?.let { errorText ->
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showDateTimePicker(context, startAt) { selectedTime ->
                                startAt = selectedTime
                                if (endAt.isBlank()) {
                                    endAt = selectedTime.plusDefaultDuration()
                                }
                            }
                        },
                    ) {
                        Text(text = "选开始")
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showDateTimePicker(context, endAt) { selectedTime ->
                                endAt = selectedTime
                            }
                        },
                    ) {
                        Text(text = "选结束")
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(text = "标题") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = startAt,
                    onValueChange = { startAt = it },
                    label = { Text(text = "开始时间") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = endAt,
                    onValueChange = { endAt = it },
                    label = { Text(text = "结束时间") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = locationName,
                    onValueChange = { locationName = it },
                    label = { Text(text = "地点") },
                    singleLine = true,
                )
                ReminderSelector(
                    selectedMinutes = reminderMinutes,
                    onSelected = { reminderMinutes = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = validation.isValid,
                onClick = {
                    onSave(
                        candidate.copy(
                            title = title,
                            startAt = startAt,
                            endAt = endAt.ifBlank { null },
                            location = if (locationName.isBlank()) {
                                null
                            } else {
                                candidate.location?.copy(name = locationName)
                                    ?: Location(name = locationName)
                            },
                            reminders = remindersFromPreset(reminderMinutes),
                        ),
                    )
                },
            ) {
                Text(text = "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消")
            }
        },
    )
}

private fun String.toDisplayDate(): String = substringBefore("T")

private fun String.toDisplayTime(): String = substringAfter("T", this)
    .take(5)

private fun String.plusDefaultDuration(): String =
    runCatching {
        OffsetDateTime
            .parse(this)
            .plusHours(1)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }.getOrDefault("")

private fun String.looksLikeEditInstruction(): Boolean {
    val text = trim()
    return listOf("修改", "改成", "改到", "提前", "推迟", "删除", "取消", "往后", "往前")
        .any { keyword -> text.contains(keyword) }
}

private const val MaxAttachmentBytes = 12 * 1024 * 1024
private const val MaxDocumentTextChars = 24_000
private const val MaxPdfRenderedPages = 3
private const val PdfRenderWidth = 1280

private fun Context.toAiInputAttachment(
    uri: Uri,
    isImage: Boolean,
): AiInputAttachment {
    val fileName = resolveDisplayName(uri) ?: if (isImage) {
        "图片"
    } else {
        "文档"
    }
    val mimeType = contentResolver.getType(uri) ?: if (isImage) {
        "image/jpeg"
    } else {
        "application/octet-stream"
    }
    val bytes = contentResolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            totalBytes += read
            if (totalBytes > MaxAttachmentBytes) {
                error("附件过大")
            }
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    } ?: error("无法读取附件")

    val textContent = if (!isImage && mimeType.isTextLike(fileName)) {
        bytes.toString(Charsets.UTF_8)
            .replace("\u0000", "")
            .take(MaxDocumentTextChars)
    } else {
        null
    }
    return AiInputAttachment(
        fileName = fileName,
        mimeType = mimeType,
        base64Data = if (isImage || textContent == null) {
            Base64.getEncoder().encodeToString(bytes)
        } else {
            null
        },
        textContent = textContent,
    )
}

private fun Context.toDocumentAiInputAttachments(uri: Uri): List<AiInputAttachment> {
    val fileName = resolveDisplayName(uri) ?: "文档"
    val mimeType = contentResolver.getType(uri) ?: fileName.guessMimeType()
    return when {
        mimeType == "application/pdf" || fileName.endsWith(".pdf", ignoreCase = true) ->
            renderPdfToImageAttachments(uri, fileName)
        mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
            fileName.endsWith(".docx", ignoreCase = true) ->
            listOf(extractDocxTextAttachment(uri, fileName, mimeType))
        mimeType.isTextLike(fileName) ->
            listOf(readTextDocumentAttachment(uri, fileName, mimeType))
        fileName.endsWith(".doc", ignoreCase = true) ->
            listOf(readLegacyDocAsTextAttachment(uri, fileName, mimeType))
        else ->
            listOf(toAiInputAttachment(uri = uri, isImage = false))
    }
}

private fun Context.renderPdfToImageAttachments(
    uri: Uri,
    fileName: String,
): List<AiInputAttachment> {
    val descriptor = contentResolver.openFileDescriptor(uri, "r")
        ?: error("PDF 读取失败，请重新选择。")
    ParcelFileDescriptor.AutoCloseInputStream(descriptor).close()
    val rendererDescriptor = contentResolver.openFileDescriptor(uri, "r")
        ?: error("PDF 读取失败，请重新选择。")
    PdfRenderer(rendererDescriptor).use { renderer ->
        if (renderer.pageCount <= 0) {
            error("PDF 没有可识别页面。")
        }
        return (0 until renderer.pageCount.coerceAtMost(MaxPdfRenderedPages)).map { pageIndex ->
            renderer.openPage(pageIndex).use { page ->
                val scale = PdfRenderWidth.toFloat() / page.width.toFloat()
                val width = PdfRenderWidth
                val height = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val output = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)
                bitmap.recycle()
                AiInputAttachment(
                    fileName = "${fileName.substringBeforeLast(".")}-第${pageIndex + 1}页.jpg",
                    mimeType = "image/jpeg",
                    base64Data = Base64.getEncoder().encodeToString(output.toByteArray()),
                    textContent = null,
                )
            }
        }
    }
}

private fun Context.extractDocxTextAttachment(
    uri: Uri,
    fileName: String,
    mimeType: String,
): AiInputAttachment {
    val textBuilder = StringBuilder()
    contentResolver.openInputStream(uri)?.use { input ->
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") {
                    val xml = zip.bufferedReader(Charsets.UTF_8).readText()
                    textBuilder.append(xml.toPlainDocxText())
                    break
                }
            }
        }
    } ?: error("Word 文档读取失败，请重新选择。")
    val text = textBuilder.toString().trim()
    if (text.isBlank()) {
        error("Word 文档没有读到可识别文本。")
    }
    return AiInputAttachment(
        fileName = fileName,
        mimeType = mimeType,
        textContent = text.take(MaxDocumentTextChars),
    )
}

private fun Context.readTextDocumentAttachment(
    uri: Uri,
    fileName: String,
    mimeType: String,
): AiInputAttachment {
    val bytes = readUriBytes(uri)
    return AiInputAttachment(
        fileName = fileName,
        mimeType = mimeType,
        textContent = bytes.toString(Charsets.UTF_8)
            .replace("\u0000", "")
            .take(MaxDocumentTextChars),
    )
}

private fun Context.readLegacyDocAsTextAttachment(
    uri: Uri,
    fileName: String,
    mimeType: String,
): AiInputAttachment {
    val bytes = readUriBytes(uri)
    val text = bytes
        .map { byte -> byte.toInt().toChar() }
        .joinToString("")
        .replace(Regex("[^\\u4e00-\\u9fa5A-Za-z0-9：:，,。！？?\\-_/\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    if (text.length < 12) {
        error("旧版 .doc 暂时无法稳定读取，请转成 docx 或 PDF 后再试。")
    }
    return AiInputAttachment(
        fileName = fileName,
        mimeType = mimeType,
        textContent = text.take(MaxDocumentTextChars),
    )
}

private fun Context.readUriBytes(uri: Uri): ByteArray =
    contentResolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            totalBytes += read
            if (totalBytes > MaxAttachmentBytes) {
                error("附件超过 12MB，Demo 版先选择更小的文件。")
            }
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    } ?: error("文档读取失败，请重新选择。")

private fun Context.resolveDisplayName(uri: Uri): String? =
    runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else {
                    null
                }
            }
    }.getOrNull()
        ?: uri.lastPathSegment?.substringAfterLast('/')

private fun String.isTextLike(fileName: String): Boolean {
    val normalizedMime = lowercase()
    val normalizedName = fileName.lowercase()
    return normalizedMime.startsWith("text/") ||
        normalizedMime in setOf(
            "application/json",
            "application/xml",
            "application/csv",
            "application/javascript",
            "application/x-ndjson",
        ) ||
        normalizedName.endsWith(".txt") ||
        normalizedName.endsWith(".md") ||
        normalizedName.endsWith(".markdown") ||
        normalizedName.endsWith(".csv") ||
        normalizedName.endsWith(".json") ||
        normalizedName.endsWith(".xml") ||
        normalizedName.endsWith(".html") ||
        normalizedName.endsWith(".htm")
}

private fun String.guessMimeType(): String = when {
    endsWith(".pdf", ignoreCase = true) -> "application/pdf"
    endsWith(".docx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    endsWith(".doc", ignoreCase = true) -> "application/msword"
    endsWith(".txt", ignoreCase = true) -> "text/plain"
    endsWith(".md", ignoreCase = true) -> "text/markdown"
    endsWith(".csv", ignoreCase = true) -> "text/csv"
    else -> "application/octet-stream"
}

private fun String.toPlainDocxText(): String =
    replace(Regex("<w:tab\\s*/>"), "\t")
        .replace(Regex("</w:p>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace(Regex("\\n{3,}"), "\n\n")
