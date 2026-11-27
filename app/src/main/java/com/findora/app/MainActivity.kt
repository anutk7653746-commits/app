package com.findora.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(0xFF111A31.toInt()),
            navigationBarStyle = SystemBarStyle.dark(0xFF080D19.toInt())
        )
        setContent { FindoraApp() }
    }
}

private val AppBackground = Color(0xFF080D19)
private val CardBackground = Color(0xFF121C2F)
private val Accent = Color(0xFF8B7CFF)
private val Muted = Color(0xFF9AA8C0)
private val MainText = Color(0xFFF3F5FF)

@Composable
private fun FindoraApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var language by remember { mutableStateOf("he") }
    var query by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(SearchMode.ALL) }
    var results by remember { mutableStateOf(emptyList<MediaItem>()) }
    var isSearching by remember { mutableStateOf(false) }
    var savingId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var messageIsError by remember { mutableStateOf(false) }
    var preparedFile by remember { mutableStateOf<File?>(null) }
    val isHebrew = language == "he"

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { destination: Uri? ->
        val sourceFile = preparedFile
        if (destination == null || sourceFile == null) {
            sourceFile?.delete()
            preparedFile = null
            savingId = null
            message = if (isHebrew) "השמירה בוטלה" else "Save cancelled"
            messageIsError = false
        } else {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val output = context.contentResolver.openOutputStream(destination, "w")
                            ?: throw IllegalStateException("לא ניתן לפתוח את מיקום השמירה")
                        output.use { stream ->
                            sourceFile.inputStream().buffered().use { input -> input.copyTo(stream) }
                        }
                    }
                    message = if (isHebrew) "הקובץ נשמר בהצלחה במיקום שבחרת" else "File saved successfully to the chosen location"
                    messageIsError = false
                } catch (error: Exception) {
                    runCatching { context.contentResolver.delete(destination, null, null) }
                    message = error.message ?: if (isHebrew) "השמירה נכשלה" else "Saving failed"
                    messageIsError = true
                } finally {
                    sourceFile.delete()
                    preparedFile = null
                    savingId = null
                }
            }
        }
    }

    fun runSearch() {
        val searchText = query.trim()
        if (searchText.isEmpty() || isSearching) return
        scope.launch {
            isSearching = true
            message = if (isHebrew) "מתרגם את החיפוש ומחפש במאגרי מדיה פתוחים…" else "Translating your query and searching open media libraries…"
            messageIsError = false
            results = emptyList()
            try {
                results = MediaRepository.search(searchText, mode)
                message = if (results.isEmpty()) {
                    if (isHebrew) "לא נמצאו תוצאות. נסה ניסוח אחר או מילות חיפוש קצרות יותר." else "No results. Try different or shorter search terms."
                } else {
                    if (isHebrew) "נמצאו ${results.size} תוצאות. בדוק את פרטי הרישיון לפני שימוש." else "Found ${results.size} results. Check license details before reuse."
                }
            } catch (error: Exception) {
                message = error.message ?: if (isHebrew) "החיפוש נכשל. בדוק את החיבור לאינטרנט." else "Search failed. Check your internet connection."
                messageIsError = true
            } finally {
                isSearching = false
            }
        }
    }

    fun saveToDrive(item: MediaItem) {
        if (savingId != null) return
        scope.launch {
            savingId = item.id
            message = if (isHebrew) "מוריד את הקובץ המקורי…" else "Downloading the original file…"
            messageIsError = false
            try {
                val file = MediaRepository.downloadToCache(context, item)
                preparedFile = file
                message = if (isHebrew) "בחר Google Drive בחלון השמירה כדי להעלות את הקובץ לחשבון שלך" else "Choose Google Drive in the save dialog to upload the file to your account"
                documentLauncher.launch(MediaRepository.saveFileName(item))
            } catch (error: Exception) {
                preparedFile?.delete()
                preparedFile = null
                savingId = null
                message = error.message ?: if (isHebrew) "הורדת הקובץ נכשלה" else "File download failed"
                messageIsError = true
            }
        }
    }

    val colors = darkColorScheme(
        primary = Accent,
        onPrimary = Color.White,
        secondary = Color(0xFF50D6C6),
        background = AppBackground,
        onBackground = MainText,
        surface = CardBackground,
        onSurface = MainText
    )

    CompositionLocalProvider(LocalLayoutDirection provides if (isHebrew) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        MaterialTheme(colorScheme = colors) {
            Surface(modifier = Modifier.fillMaxSize(), color = AppBackground) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color(0xFF111A31), AppBackground, Color(0xFF080D19))))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                            .imePadding()
                            .padding(horizontal = 18.dp)
                    ) {
                        Header(
                            isHebrew = isHebrew,
                            onToggleLanguage = { language = if (isHebrew) "en" else "he" }
                        )
                        Spacer(Modifier.height(16.dp))
                        SearchPanel(
                            query = query,
                            onQueryChange = { query = it },
                            mode = mode,
                            onModeChange = { mode = it },
                            isHebrew = isHebrew,
                            isSearching = isSearching,
                            onSearch = { runSearch() }
                        )
                        Spacer(Modifier.height(12.dp))
                        if (message.isNotBlank()) {
                            Text(
                                text = message,
                                color = if (messageIsError) Color(0xFFFF9B9B) else Muted,
                                fontSize = 12.sp,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 4.dp)
                            )
                        }
                        if (isSearching) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(color = Accent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(if (isHebrew) "מחפש בכל השפות הנתמכות…" else "Searching supported languages…", color = Muted, fontSize = 13.sp)
                            }
                        }
                        if (results.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(if (isHebrew) "תוצאות החיפוש" else "Search results", color = MainText, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text("${results.size}", color = Accent, fontWeight = FontWeight.Bold)
                            }
                        }
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(11.dp)
                        ) {
                            items(results, key = { it.id }) { item ->
                                MediaResultCard(
                                    item = item,
                                    isHebrew = isHebrew,
                                    isSaving = savingId == item.id,
                                    anySaving = savingId != null,
                                    onSave = { saveToDrive(item) },
                                    onOpenSource = {
                                        val link = item.sourcePageUrl
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
                                    }
                                )
                            }
                            if (results.isEmpty() && !isSearching) {
                                item { WelcomeCard(isHebrew) }
                            }
                        }
                        Footer(isHebrew)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(isHebrew: Boolean, onToggleLanguage: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF8B7CFF), Color(0xFF4BCFCA)))),
                contentAlignment = Alignment.Center
            ) {
                Text("F", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Findora", color = MainText, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp)
                Text(if (isHebrew) "מוצאים מדיה. שומרים בקלות." else "Discover media. Save with ease.", color = Muted, fontSize = 11.sp)
            }
        }
        TextButton(onClick = onToggleLanguage) {
            Text(if (isHebrew) "EN" else "עברית", color = Color(0xFFCBC6FF), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    mode: SearchMode,
    onModeChange: (SearchMode) -> Unit,
    isHebrew: Boolean,
    isSearching: Boolean,
    onSearch: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Column(Modifier.padding(17.dp)) {
            Text(
                if (isHebrew) "מה תרצה למצוא?" else "What would you like to find?",
                color = MainText,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(5.dp))
            Text(
                if (isHebrew) "כתוב שם של תמונה, סרטון או סרט. החיפוש יתרחב לתרגומים." else "Enter an image, video or movie title. Search expands into translations.",
                color = Muted,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(13.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(15.dp),
                placeholder = { Text(if (isHebrew) "לדוגמה: מפלי הניאגרה / Niagara Falls" else "e.g. Niagara Falls / מפלי הניאגרה", color = Color(0xFF74829B), fontSize = 13.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Color(0xFF33415A),
                    focusedTextColor = MainText,
                    unfocusedTextColor = MainText,
                    cursorColor = Accent,
                    focusedContainerColor = Color(0xFF0D1526),
                    unfocusedContainerColor = Color(0xFF0D1526)
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() })
            )
            Spacer(Modifier.height(11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                FilterChip(
                    selected = mode == SearchMode.ALL,
                    onClick = { onModeChange(SearchMode.ALL) },
                    label = { Text(if (isHebrew) "הכול" else "All", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = mode == SearchMode.IMAGES,
                    onClick = { onModeChange(SearchMode.IMAGES) },
                    label = { Text(if (isHebrew) "תמונות" else "Images", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = mode == SearchMode.VIDEOS,
                    onClick = { onModeChange(SearchMode.VIDEOS) },
                    label = { Text(if (isHebrew) "סרטונים וסרטים" else "Videos & films", fontSize = 12.sp) }
                )
            }
            Spacer(Modifier.height(9.dp))
            Button(
                onClick = onSearch,
                enabled = query.isNotBlank() && !isSearching,
                modifier = Modifier.fillMaxWidth().height(49.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, disabledContainerColor = Color(0xFF3C3A60))
            ) {
                Text(if (isSearching) (if (isHebrew) "מחפש…" else "Searching…") else (if (isHebrew) "חיפוש מדיה" else "Search media"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (isHebrew) "החיפוש מתרגם למספר שפות ומחפש ב-Wikimedia Commons וב-Internet Archive. מוצגים קבצים ממקורות פתוחים." else "Search translates into several languages and checks Wikimedia Commons and Internet Archive for openly licensed files.",
                color = Color(0xFF7F8CA4),
                fontSize = 10.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun MediaResultCard(
    item: MediaItem,
    isHebrew: Boolean,
    isSaving: Boolean,
    anySaving: Boolean,
    onSave: () -> Unit,
    onOpenSource: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                if (!item.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = item.title,
                        modifier = Modifier.size(104.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF202B41)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.size(104.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF202B41)),
                        contentAlignment = Alignment.Center
                    ) { Text(if (item.isVideo) "▶" else "▧", color = Accent, fontSize = 30.sp) }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.title, color = MainText, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(5.dp))
                    Text(item.source, color = Color(0xFFB5AFFF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(item.description, color = Muted, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF51D7B5)))
                Spacer(Modifier.width(6.dp))
                Text(item.licenseName, color = Color(0xFFB9C7D8), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(sizeLabel(item.sizeBytes), color = Muted, fontSize = 10.sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onSave,
                    enabled = !anySaving,
                    modifier = Modifier.weight(1f).height(43.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6458CC), disabledContainerColor = Color(0xFF34334F)),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(7.dp))
                    }
                    Text(if (isHebrew) "שמירה ל-Drive" else "Save to Drive", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onOpenSource, contentPadding = PaddingValues(horizontal = 5.dp)) {
                    Text(if (isHebrew) "מקור ורישיון" else "Source & license", color = Color(0xFFB5AFFF), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun WelcomeCard(isHebrew: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10192A)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(if (isHebrew) "החיפוש שלך מתחיל כאן" else "Your search starts here", color = MainText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(7.dp))
            Text(
                if (isHebrew) "נסה שם של מקום, אירוע, אדם היסטורי או נושא. התוצאות מגיעות ממאגרי מדיה פתוחים, והקובץ עצמו יורד לפני בחירת מיקום השמירה." else "Try a place, event, historical figure or topic. Results come from open media libraries. The actual file is downloaded before you choose where to save it.",
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun Footer(isHebrew: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (isHebrew) "Findora  ·  מדיה פתוחה, שמירה פשוטה" else "Findora  ·  Open media, easy saving", color = Color(0xFF68768E), fontSize = 10.sp)
        Text(if (isHebrew) "יש לפעול לפי תנאי הרישיון והייחוס של כל קובץ." else "Follow each file's license and attribution requirements.", color = Color(0xFF58657A), fontSize = 9.sp)
    }
}

private fun sizeLabel(bytes: Long?): String {
    if (bytes == null || bytes <= 0) return ""
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return "${DecimalFormat("0.#").format(value)} ${units[index]}"
}
