package com.vela.app.feature.importchat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.background
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.vela.app.data.ai.AiVoiceRecording
import com.vela.app.data.ai.VoiceTranscriptionResult
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
import com.vela.app.data.repository.ImportSubmissionResult
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
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64

data class ImportChatUiState(
    val session: ImportSession? = null,
    val candidates: List<EventCandidate> = emptyList(),
    val lastImportResult: ImportResult? = null,
    val userPreferences: UserPreferences = UserPreferences(),
    val isSubmittingAttachment: Boolean = false,
    val attachmentErrorText: String? = null,
    val inputErrorText: String? = null,
    val isTranscribingVoice: Boolean = false,
    val voiceErrorText: String? = null,
    val voiceTranscriptText: String? = null,
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

private data class InputSubmissionState(
    val isSubmittingAttachment: Boolean = false,
    val attachmentErrorText: String? = null,
    val inputErrorText: String? = null,
    val isTranscribingVoice: Boolean = false,
    val voiceErrorText: String? = null,
    val voiceTranscriptText: String? = null,
)

private data class AttachmentSubmissionState(
    val isSubmittingAttachment: Boolean = false,
    val attachmentErrorText: String? = null,
    val inputErrorText: String? = null,
)

private data class VoiceSubmissionState(
    val isTranscribingVoice: Boolean = false,
    val voiceErrorText: String? = null,
    val voiceTranscriptText: String? = null,
)

class ImportChatViewModel : ViewModel() {
    private val repository = MockVelaRepository
    private val _lastImportResult = kotlinx.coroutines.flow.MutableStateFlow<ImportResult?>(null)
    private val _isSubmittingAttachment = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val _attachmentErrorText = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private val _inputErrorText = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private val _isTranscribingVoice = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val _voiceErrorText = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private val _voiceTranscriptText = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    private val attachmentSubmissionState = combine(
        _isSubmittingAttachment,
        _attachmentErrorText,
        _inputErrorText,
    ) { isSubmittingAttachment, attachmentErrorText, inputErrorText ->
        AttachmentSubmissionState(
            isSubmittingAttachment = isSubmittingAttachment,
            attachmentErrorText = attachmentErrorText,
            inputErrorText = inputErrorText,
        )
    }

    private val voiceSubmissionState = combine(
        _isTranscribingVoice,
        _voiceErrorText,
        _voiceTranscriptText,
    ) { isTranscribingVoice, voiceErrorText, voiceTranscriptText ->
        VoiceSubmissionState(
            isTranscribingVoice = isTranscribingVoice,
            voiceErrorText = voiceErrorText,
            voiceTranscriptText = voiceTranscriptText,
        )
    }

    private val inputSubmissionState = combine(
        attachmentSubmissionState,
        voiceSubmissionState,
    ) { attachmentState, voiceState ->
        InputSubmissionState(
            isSubmittingAttachment = attachmentState.isSubmittingAttachment,
            attachmentErrorText = attachmentState.attachmentErrorText,
            inputErrorText = attachmentState.inputErrorText,
            isTranscribingVoice = voiceState.isTranscribingVoice,
            voiceErrorText = voiceState.voiceErrorText,
            voiceTranscriptText = voiceState.voiceTranscriptText,
        )
    }

    val uiState: StateFlow<ImportChatUiState> = combine(
        repository.importSession,
        repository.eventCandidates,
        _lastImportResult,
        repository.userPreferences,
        inputSubmissionState,
    ) { session, candidates, lastImportResult, userPreferences, inputState ->
        ImportChatUiState(
            session = session,
            candidates = candidates,
            lastImportResult = lastImportResult,
            userPreferences = userPreferences,
            isSubmittingAttachment = inputState.isSubmittingAttachment,
            attachmentErrorText = inputState.attachmentErrorText,
            inputErrorText = inputState.inputErrorText,
            isTranscribingVoice = inputState.isTranscribingVoice,
            voiceErrorText = inputState.voiceErrorText,
            voiceTranscriptText = inputState.voiceTranscriptText,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ImportChatUiState(),
    )

    fun submitText(text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.submitImportText(text)
            _lastImportResult.value = null
            _attachmentErrorText.value = null
            _inputErrorText.value = result.compactFailureMessage()
            _voiceErrorText.value = null
        }
    }

    fun submitImage(context: Context, uri: Uri) {
        submitAttachment(context = context, uri = uri)
    }

    fun submitEditInstruction(instruction: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.submitNaturalLanguageEdit(instruction)
            _lastImportResult.value = null
            _attachmentErrorText.value = null
            _inputErrorText.value = result.compactFailureMessage()
            _voiceErrorText.value = null
        }
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

    private fun submitAttachment(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isSubmittingAttachment.value = true
            _attachmentErrorText.value = null
            _inputErrorText.value = null
            _voiceErrorText.value = null
            runCatching {
                context.toAiInputAttachment(uri = uri)
            }.onSuccess { attachment ->
                val result = repository.submitImportImage(attachment)
                _lastImportResult.value = null
                _inputErrorText.value = result.compactFailureMessage()
            }.onFailure {
                _attachmentErrorText.value = "图片读取失败，请重新选择。"
            }
            _isSubmittingAttachment.value = false
        }
    }

    fun transcribeVoiceRecording(recordingFile: File) {
        viewModelScope.launch(Dispatchers.IO) {
            _isTranscribingVoice.value = true
            _voiceErrorText.value = null
            _voiceTranscriptText.value = null
            runCatching {
                recordingFile.readBytes()
            }.onSuccess { bytes ->
                when (
                    val result = repository.transcribeVoice(
                        AiVoiceRecording(
                            fileName = recordingFile.name,
                            mimeType = "audio/mp4",
                            bytes = bytes,
                        ),
                    )
                ) {
                    is VoiceTranscriptionResult.Success -> {
                        _voiceTranscriptText.value = result.text
                    }

                    is VoiceTranscriptionResult.Failure -> {
                        _voiceErrorText.value = result.message
                    }
                }
            }.onFailure {
                _voiceErrorText.value = "录音读取失败，请重新录音。"
            }
            recordingFile.delete()
            _isTranscribingVoice.value = false
        }
    }

    fun clearVoiceTranscript() {
        _voiceTranscriptText.value = null
    }
}

private fun ImportSubmissionResult.compactFailureMessage(): String? {
    if (isSuccess) return null
    return when {
        message.contains("请输入") -> message
        message.contains("图片") -> "图片识别失败，请重试。"
        message.contains("未配置") -> "AI 服务未配置。"
        message.contains("自然语言修改") -> "暂不能修改，请稍后再试。"
        message.contains("连接失败") -> "连接失败，请重试。"
        message.contains("无法解析") || message.contains("没有返回") -> "识别失败，请重试。"
        else -> message.substringBefore("。").ifBlank { "识别失败，请重试" } + "。"
    }
}

@Composable
fun ImportChatScreen(
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
    var voiceStatusText by remember { mutableStateOf<String?>(null) }
    var isVoiceStatusError by remember { mutableStateOf(false) }
    var isVoiceRecording by remember { mutableStateOf(false) }
    var voiceRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var voiceRecordingFile by remember { mutableStateOf<File?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationPermissionDenied = !granted
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { voiceRecorder?.release() }
            voiceRecordingFile?.delete()
        }
    }
    LaunchedEffect(uiState.voiceTranscriptText) {
        val transcript = uiState.voiceTranscriptText?.trim().orEmpty()
        if (transcript.isNotBlank()) {
            inputText = if (inputText.isBlank()) {
                transcript
            } else {
                "${inputText.trim()} $transcript"
            }
            voiceStatusText = null
            isVoiceStatusError = false
            viewModel.clearVoiceTranscript()
        }
    }

    fun startVoiceRecording() {
        if (uiState.isTranscribingVoice) {
            voiceStatusText = "正在转写上一段语音，请稍等。"
            isVoiceStatusError = false
            return
        }
        val recordingFile = context.createVoiceRecordingFile()
        runCatching {
            val recorder = createVoiceRecorder(recordingFile)
            voiceRecorder = recorder
            voiceRecordingFile = recordingFile
            isVoiceRecording = true
            voiceStatusText = null
            isVoiceStatusError = false
        }.onFailure {
            runCatching { voiceRecorder?.release() }
            voiceRecorder = null
            voiceRecordingFile = null
            recordingFile.delete()
            isVoiceRecording = false
            voiceStatusText = "录音启动失败，请检查麦克风权限后再试。"
            isVoiceStatusError = true
        }
    }

    fun stopVoiceRecording() {
        val recorder = voiceRecorder
        val recordingFile = voiceRecordingFile
        voiceRecorder = null
        voiceRecordingFile = null
        isVoiceRecording = false
        val stopped = runCatching {
            recorder?.stop()
            true
        }.getOrDefault(false)
        runCatching { recorder?.release() }
        if (
            !stopped ||
            recordingFile == null ||
            !recordingFile.exists() ||
            recordingFile.length() < MinVoiceRecordingBytes
        ) {
            recordingFile?.delete()
            voiceStatusText = "录音太短或未保存成功，请重新录音。"
            isVoiceStatusError = true
            return
        }
        voiceStatusText = null
        isVoiceStatusError = false
        viewModel.transcribeVoiceRecording(recordingFile)
    }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            viewModel.submitImage(context, uri)
        }
    }
    val voicePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startVoiceRecording()
        } else {
            voiceStatusText = "未获得麦克风权限，无法使用语音输入。"
            isVoiceStatusError = true
        }
    }

    fun requestNotificationPermissionIfNeeded() {
        if (NotificationPermissionState.needsRuntimePermission(context)) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun requestVoiceInput() {
        if (isVoiceRecording) {
            stopVoiceRecording()
            return
        }
        if (context.hasAudioPermission()) {
            startVoiceRecording()
        } else {
            voicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
            contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ChatImportHeader(
                    onBackClick = onCalendarClick,
                    onSettingsClick = onSettingsClick,
                )
            }

            uiState.session?.messages?.let { messages ->
                items(
                    messages.filter { it.role == ChatMessageRole.User },
                    key = { it.id },
                ) { message ->
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
            uiState.inputErrorText?.let { errorText ->
                item {
                    InlineStatusText(text = errorText, isError = true)
                }
            }
            voiceStatusText?.let { statusText ->
                item {
                    InlineStatusText(text = statusText, isError = isVoiceStatusError)
                }
            }
            uiState.voiceErrorText?.let { errorText ->
                item {
                    InlineStatusText(text = errorText, isError = true)
                }
            }
            if (uiState.isSubmittingAttachment) {
                item {
                    InlineStatusText(text = "正在识别...", isError = false)
                }
            }
            if (uiState.isTranscribingVoice) {
                item {
                    InlineStatusText(text = "正在转写...", isError = false)
                }
            }
            if (notificationPermissionDenied) {
                item {
                    InlineStatusText(text = "通知权限未开启。", isError = true)
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
            isVoiceRecording = isVoiceRecording,
            onVoiceInput = ::requestVoiceInput,
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
private fun ChatImportHeader(
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                painter = painterResource(id = R.drawable.ic_arrow_back_24),
                contentDescription = "返回",
                tint = Color.Black,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onSettingsClick) {
            Icon(
                painter = painterResource(id = R.drawable.ic_settings_24),
                contentDescription = "设置",
                tint = Color.Black,
            )
        }
    }
}

@Composable
private fun ImportInputBar(
    modifier: Modifier = Modifier,
    text: String,
    onTextChange: (String) -> Unit,
    onImageClick: () -> Unit,
    isVoiceRecording: Boolean,
    onVoiceInput: () -> Unit,
    onSubmit: () -> Unit,
) {
    var isAttachmentMenuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        color = ImportInputBarColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                RoundIconButton(
                    icon = R.drawable.ic_add_24,
                    contentDescription = "添加",
                    onClick = { isAttachmentMenuExpanded = true },
                )
                DropdownMenu(
                    expanded = isAttachmentMenuExpanded,
                    onDismissRequest = { isAttachmentMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(text = "图片") },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_image_24),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            isAttachmentMenuExpanded = false
                            onImageClick()
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
                        text = if (isVoiceRecording) {
                            "正在录音..."
                        } else {
                            "输入..."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            RoundIconButton(
                icon = if (isVoiceRecording) R.drawable.ic_stop_24 else R.drawable.ic_mic_24,
                contentDescription = if (isVoiceRecording) "停止录音" else "语音输入",
                filled = isVoiceRecording,
                onClick = onVoiceInput,
            )
            RoundIconButton(
                icon = R.drawable.ic_send_24,
                contentDescription = "发送",
                enabled = text.isNotBlank(),
                filled = text.isNotBlank(),
                onClick = onSubmit,
            )
        }
    }
}

@Composable
private fun RoundIconButton(
    icon: Int,
    contentDescription: String,
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
            filled -> Color.Black
            else -> ImportInputButtonColor
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = contentDescription,
                tint = when {
                    filled -> Color.White
                    enabled -> Color.Black
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(22.dp),
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

private val AssistantBubbleColor = Color(0xFFE9E9EB)
private val UserBubbleColor = Color.Black
private val VelaPageBackground = Color.White
private val ImportInputBarColor = Color(0xFFF8ECE7)
private val ImportInputButtonColor = Color(0xFFF2DDD4)

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == ChatMessageRole.User
    val isSystem = message.role == ChatMessageRole.System
    if (isSystem) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF8A8A8F),
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(if (isUser) 0.86f else 0.88f),
                shape = RoundedCornerShape(24.dp),
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

private fun Context.hasAudioPermission(): Boolean =
    checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

@Suppress("DEPRECATION")
private fun createVoiceRecorder(file: File): MediaRecorder =
    MediaRecorder().apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setAudioEncodingBitRate(96_000)
        setAudioSamplingRate(16_000)
        setOutputFile(file.absolutePath)
        prepare()
        start()
    }

private fun Context.createVoiceRecordingFile(): File =
    File(cacheDir, "vela-voice-${System.currentTimeMillis()}.m4a")

private const val MinVoiceRecordingBytes = 1024L
private const val MaxAttachmentBytes = 12 * 1024 * 1024

private fun Context.toAiInputAttachment(uri: Uri): AiInputAttachment {
    val fileName = resolveDisplayName(uri) ?: "图片"
    val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
    val bytes = readUriBytes(uri)
    return AiInputAttachment(
        fileName = fileName,
        mimeType = mimeType,
        base64Data = Base64.getEncoder().encodeToString(bytes),
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
    } ?: error("图片读取失败，请重新选择。")

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
