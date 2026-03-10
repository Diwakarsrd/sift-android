package dev.sift.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sift.app.model.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

// ── Color palette (dark, on-device AI aesthetic) ──────────────────────────

private val BgPrimary    = Color(0xFF060B12)
private val BgSurface    = Color(0xFF07111E)
private val BorderColor  = Color(0xFF1C2E48)
private val AccentCyan   = Color(0xFF00D4FF)
private val AccentGreen  = Color(0xFF00F59B)
private val AccentOrange = Color(0xFFFF9F38)
private val AccentPurple = Color(0xFFA06FFF)
private val TextPrimary  = Color(0xFFC0D8F0)
private val TextDim      = Color(0xFF3A5070)
private val TextMid      = Color(0xFF6A8A9A)

@Composable
fun SearchScreen(
    state:   SearchUiState,
    onEvent: (SearchEvent) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    Box(modifier = Modifier.fillMaxSize().background(BgPrimary)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top Bar ───────────────────────────────────────────────────
            SiftTopBar(
                config   = state.llmConfig,
                onSettings = { onEvent(SearchEvent.ToggleSettings) },
            )

            // ── Main content ──────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {

                // Search bar
                item {
                    SearchBar(
                        query          = state.query,
                        step           = state.queryState.step,
                        llmConfig      = state.llmConfig,
                        focusRequester = focusRequester,
                        onQueryChange  = { onEvent(SearchEvent.QueryChanged(it)) },
                        onSubmit       = { onEvent(SearchEvent.Submit) },
                        onSettings     = { onEvent(SearchEvent.ToggleSettings) },
                    )
                }

                // Sample queries
                if (state.queryState.step == PipelineStep.IDLE || state.queryState.step == PipelineStep.ERROR) {
                    item {
                        SampleQueries { q -> onEvent(SearchEvent.SampleQuery(q)) }
                    }
                }

                // Pipeline steps
                if (state.queryState.step != PipelineStep.IDLE) {
                    item {
                        PipelineIndicator(step = state.queryState.step)
                    }
                }

                // Error
                state.queryState.error?.let { err ->
                    item {
                        ErrorCard(error = err, config = state.llmConfig,
                            onSettings = { onEvent(SearchEvent.ToggleSettings) })
                    }
                }

                // Intent chips
                state.queryState.intent?.let { intent ->
                    item {
                        IntentChips(intent = intent, config = state.llmConfig)
                    }
                }

                // Results
                if (state.queryState.results.isNotEmpty()) {
                    item {
                        Text(
                            "${state.queryState.results.size} RESULTS  ·  ${state.queryState.durationMs}ms",
                            color = AccentGreen, fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace, letterSpacing = 1.5.sp,
                        )
                    }
                    items(state.queryState.results, key = { it.event.id }) { result ->
                        ResultCard(result = result)
                    }
                }

                // Empty state
                if (state.queryState.step == PipelineStep.IDLE && state.queryState.results.isEmpty()) {
                    item { EmptyState() }
                }
            }
        }

        // Settings sheet
        if (state.showSettings) {
            SettingsSheet(
                config  = state.llmConfig,
                onSave  = { onEvent(SearchEvent.SaveConfig(it)) },
                onDismiss = { onEvent(SearchEvent.ToggleSettings) },
            )
        }
    }
}

// ── Top Bar ───────────────────────────────────────────────────────────────

@Composable
fun SiftTopBar(config: LlmConfig, onSettings: () -> Unit) {
    val pulseAnim = rememberInfiniteTransition()
    val pulseAlpha by pulseAnim.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF06101A))
            .border(BorderWidth, BorderColor, shape = RectangleShape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("◈", color = AccentCyan.copy(alpha = pulseAlpha), fontSize = 20.sp)
            Column {
                Text("SIFT", color = Color(0xFFE0F0FF), fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace, letterSpacing = 3.sp)
                Text("MEMORY INTELLIGENCE · RECALL ARCHITECTURE", color = TextDim,
                    fontSize = 7.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
            }
        }
        IconButton(onClick = onSettings) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(AccentGreen))
                Text(config.model, color = TextMid, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextDim, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ── Search Bar ────────────────────────────────────────────────────────────

@Composable
fun SearchBar(
    query:          String,
    step:           PipelineStep,
    llmConfig:      LlmConfig,
    focusRequester: FocusRequester,
    onQueryChange:  (String) -> Unit,
    onSubmit:       () -> Unit,
    onSettings:     () -> Unit,
) {
    val busy = step != PipelineStep.IDLE && step != PipelineStep.DONE && step != PipelineStep.ERROR
    val borderColor by animateColorAsState(
        if (busy) AccentCyan else BorderColor,
        animationSpec = tween(300),
    )

    Surface(
        color  = BgSurface,
        shape  = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, BorderColor),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("MEMORY QUERY  ·  ${llmConfig.backend.name}  ·  ${llmConfig.model}",
                color = TextDim, fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                letterSpacing = 1.5.sp, modifier = Modifier.padding(bottom = 10.dp))

            Surface(
                color  = BgPrimary,
                shape  = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, borderColor),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("⟩", color = AccentCyan, fontSize = 15.sp)
                    BasicTextField(
                        value         = query,
                        onValueChange = onQueryChange,
                        modifier      = Modifier.weight(1f).focusRequester(focusRequester),
                        textStyle     = LocalTextStyle.current.copy(
                            color = TextPrimary, fontSize = 14.sp, fontFamily = FontFamily.Monospace
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                        singleLine      = true,
                        decorationBox   = { inner ->
                            if (query.isEmpty()) {
                                Text("Ask anything about your past activity...",
                                    color = TextDim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            }
                            inner()
                        }
                    )
                    Button(
                        onClick = onSubmit,
                        enabled = !busy && query.isNotBlank(),
                        colors  = ButtonDefaults.buttonColors(
                            containerColor = AccentCyan,
                            disabledContainerColor = Color(0xFF0A1E30),
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(if (busy) "···" else "RUN ▶", color = BgPrimary,
                            fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

// ── Pipeline indicator ────────────────────────────────────────────────────

@Composable
fun PipelineIndicator(step: PipelineStep) {
    val steps = listOf("Intent", "Constraints", "Graph", "Vectors", "Results")
    val stepIdx = when (step) {
        PipelineStep.PARSING_INTENT        -> 0
        PipelineStep.EXTRACTING_CONSTRAINTS -> 1
        PipelineStep.GRAPH_FILTERING        -> 2
        PipelineStep.VECTOR_RANKING         -> 3
        PipelineStep.ASSEMBLING_RESULTS, PipelineStep.DONE -> 4
        else                                -> -1
    }

    Surface(color = BgSurface, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, BorderColor)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            steps.forEachIndexed { i, label ->
                val done   = stepIdx > i
                val active = stepIdx == i
                val color  = if (done || active) AccentCyan else TextDim

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        color  = if (done || active) AccentCyan.copy(alpha = 0.12f) else BgPrimary,
                        shape  = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, color),
                        modifier = Modifier.defaultMinSize(minWidth = 60.dp),
                    ) {
                        Text(
                            if (done) "✓ $label" else if (active) "⟳ $label" else label,
                            color = color, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(6.dp),
                        )
                    }
                }

                if (i < steps.size - 1) {
                    Text("→", color = if (stepIdx > i) AccentCyan.copy(0.5f) else TextDim,
                        modifier = Modifier.align(Alignment.CenterVertically))
                }
            }
        }
    }
}

// ── Intent chips ──────────────────────────────────────────────────────────

@Composable
fun IntentChips(intent: ParsedIntent, config: LlmConfig) {
    AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically()) {
        Surface(color = BgSurface, shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.15f))) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("PARSED INTENT  ·  ${config.model}", color = AccentCyan,
                    fontSize = 8.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(bottom = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    IntentChip("TIME",  intent.timeConstraint?.description ?: "any",  AccentCyan)
                    IntentChip("WHO",   intent.personConstraint?.name ?: "any",        AccentOrange)
                    IntentChip("TYPE",  intent.fileTypeConstraint,                      AccentGreen)
                    IntentChip("ACT",   intent.action,                                  AccentPurple)
                    IntentChip("CONF",  "${(intent.confidence * 100).toInt()}%",        TextMid)
                }
                if (intent.summary.isNotBlank()) {
                    Text("\"${intent.summary}\"", color = TextMid, fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
fun IntentChip(label: String, value: String, color: Color) {
    Surface(color = BgPrimary, shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
            Text(label, color = color, fontSize = 8.sp,
                fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
            Text(value.take(16), color = TextPrimary, fontSize = 10.sp,
                fontFamily = FontFamily.Monospace, maxLines = 1)
        }
    }
}

// ── Result card ───────────────────────────────────────────────────────────

@Composable
fun ResultCard(result: SearchResult) {
    val event     = result.event
    val typeColor = when (event.type) {
        EventType.CALL_START, EventType.CALL_END -> AccentCyan
        EventType.FILE_OPEN                       -> AccentOrange
        EventType.APP_OPEN, EventType.APP_CLOSE  -> AccentGreen
        EventType.SCREENSHOT                      -> Color(0xFFF06AFF)
        else                                      -> TextMid
    }
    val typeIcon = when (event.type) {
        EventType.CALL_START, EventType.CALL_END -> "◉"
        EventType.FILE_OPEN                       -> "▪"
        EventType.APP_OPEN                        -> "◈"
        EventType.SCREENSHOT                      -> "▣"
        else                                      -> "·"
    }

    Surface(
        color  = BgSurface,
        shape  = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, BorderColor),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Type indicator bar
            Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(typeColor))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(typeIcon, color = typeColor, fontSize = 13.sp)
                        Text(
                            (event.contactName.ifBlank { event.title.ifBlank { event.appLabel } }).take(40),
                            color = Color(0xFFD0E8FF), fontSize = 13.sp,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(timeAgo(event.timestamp), color = TextMid, fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace)
                        if (event.appLabel.isNotBlank() && event.type != EventType.APP_OPEN) {
                            Text("· via ${event.appLabel}", color = TextDim, fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace)
                        }
                        if (event.contactName.isNotBlank() && event.type == EventType.FILE_OPEN) {
                            Text("· after ${event.contactName}'s call", color = AccentCyan,
                                fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                Surface(color = typeColor.copy(alpha = 0.08f), shape = RoundedCornerShape(3.dp),
                    border = BorderStroke(1.dp, typeColor.copy(alpha = 0.3f))) {
                    Text(event.type.name.replace("_", " "), color = typeColor,
                        fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
        }
    }
}

// ── Sample queries ────────────────────────────────────────────────────────

@Composable
fun SampleQueries(onSelect: (String) -> Unit) {
    val samples = listOf(
        "PDF I opened after Rahul's call",
        "What apps did I use yesterday?",
        "Files from last week",
        "Screenshots from today",
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        samples.forEach { q ->
            Surface(
                onClick = { onSelect(q) },
                color   = BgPrimary,
                shape   = RoundedCornerShape(4.dp),
                border  = BorderStroke(1.dp, BorderColor),
            ) {
                Text(q, color = TextDim, fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
    }
}

// ── Error card ────────────────────────────────────────────────────────────

@Composable
fun ErrorCard(error: String, config: LlmConfig, onSettings: () -> Unit) {
    Surface(color = Color(0xFF180A0A), shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, Color(0xFF380000))) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("⚠  $error", color = Color(0xFFFF4060), fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Make sure ${config.backend.name} is running at ${config.baseUrl}",
                color = Color(0xFF6A3040), fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onSettings,
                border  = BorderStroke(1.dp, Color(0xFF3A1020)),
                shape   = RoundedCornerShape(4.dp),
            ) {
                Text("⚙  Configure Backend", color = AccentOrange,
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────

@Composable
fun EmptyState() {
    val pulseAnim = rememberInfiniteTransition()
    val alpha by pulseAnim.animateFloat(0.2f, 0.6f,
        infiniteRepeatable(tween(2000), RepeatMode.Reverse))

    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("◈", color = AccentCyan.copy(alpha = alpha), fontSize = 48.sp)
            Text("AWAITING MEMORY QUERY", color = Color(0xFF1C2E40), fontSize = 11.sp,
                fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
            Text("100% ON-DEVICE · ZERO CLOUD DEPENDENCY", color = Color(0xFF111D2C),
                fontSize = 9.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
        }
    }
}

// ── Settings bottom sheet ─────────────────────────────────────────────────

@Composable
fun SettingsSheet(config: LlmConfig, onSave: (LlmConfig) -> Unit, onDismiss: () -> Unit) {
    var local by remember { mutableStateOf(config) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f))
        .clickable { onDismiss() }) {
        Surface(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .clickable {}, // stop propagation
            color = BgSurface,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp).navigationBarsPadding()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("LLM BACKEND", color = AccentCyan, fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMid)
                    }
                }
                Spacer(Modifier.height(16.dp))

                // Backend picker
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LlmBackend.values().forEach { backend ->
                        val selected = local.backend == backend
                        Surface(
                            onClick = { local = local.copy(backend = backend,
                                model  = when (backend) {
                                    LlmBackend.OLLAMA       -> "gemma2:2b"
                                    LlmBackend.HUGGING_FACE -> "mistralai/Mistral-7B-Instruct-v0.3"
                                    LlmBackend.LM_STUDIO    -> "gemma-2-2b-it-Q4_K_M"
                                },
                                baseUrl = when (backend) {
                                    LlmBackend.OLLAMA       -> "http://10.0.2.2:11434"
                                    LlmBackend.HUGGING_FACE -> "https://api-inference.huggingface.co"
                                    LlmBackend.LM_STUDIO    -> "http://10.0.2.2:1234"
                                }
                            )},
                            modifier = Modifier.weight(1f),
                            color    = if (selected) AccentGreen.copy(0.1f) else BgPrimary,
                            shape    = RoundedCornerShape(8.dp),
                            border   = BorderStroke(1.dp, if (selected) AccentGreen else BorderColor),
                        ) {
                            Text(backend.name.replace("_", "\n"), color = if (selected) AccentGreen else TextDim,
                                fontSize = 10.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center,
                                modifier = Modifier.padding(10.dp))
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                ConfigTextField("Endpoint URL", local.baseUrl) { local = local.copy(baseUrl = it) }
                ConfigTextField("Model",        local.model)   { local = local.copy(model = it) }
                if (local.backend == LlmBackend.HUGGING_FACE) {
                    ConfigTextField("API Token (hf_xxx)", local.apiKey, password = true) { local = local.copy(apiKey = it) }
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onSave(local) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    shape  = RoundedCornerShape(8.dp),
                ) {
                    Text("SAVE & CONNECT", color = BgPrimary, fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable
fun ConfigTextField(label: String, value: String, password: Boolean = false, onChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(label, color = TextDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 4.dp))
        OutlinedTextField(
            value         = value,
            onValueChange = onChange,
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true,
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = AccentCyan,
                unfocusedBorderColor = BorderColor,
                focusedTextColor     = TextPrimary,
                unfocusedTextColor   = TextPrimary,
                cursorColor          = AccentCyan,
            ),
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            shape     = RoundedCornerShape(6.dp),
        )
    }
}

// ── Utils ─────────────────────────────────────────────────────────────────

private fun timeAgo(ts: Long): String {
    val d = System.currentTimeMillis() - ts
    val DAY = 86_400_000L
    return when {
        d < 3_600_000L -> "${d / 60_000}m ago"
        d < DAY        -> "${d / 3_600_000}h ago"
        else           -> "${d / DAY}d ago"
    }
}

private val BorderWidth = 1.dp
private val CircleShape = RoundedCornerShape(50)
private val RectangleShape = RoundedCornerShape(0)

