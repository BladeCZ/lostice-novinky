package cz.lostice.novinky.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.lostice.novinky.MainViewModel
import cz.lostice.novinky.data.Category
import cz.lostice.novinky.data.Item
import cz.lostice.novinky.data.Prefs
import cz.lostice.novinky.data.Resolution
import cz.lostice.novinky.data.ThemeMode
import cz.lostice.novinky.data.TocItem
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val LightColors = lightColorScheme(
    primary = Color(0xFF0D3B66),
    onPrimary = Color.White,
    secondary = Color(0xFF00AEEF),
    primaryContainer = Color(0xFFD6E6F5),
    onPrimaryContainer = Color(0xFF0D3B66),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E293B),
    background = Color(0xFFF5F8FB),
    onBackground = Color(0xFF1E293B)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF0F172A),
    secondary = Color(0xFF38BDF8),
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFBAE6FD),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF1F5F9),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFF1F5F9)
)

private val OledColors = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color.Black,
    secondary = Color(0xFF38BDF8),
    primaryContainer = Color(0xFF18181B),
    onPrimaryContainer = Color(0xFF38BDF8),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFFFFFFF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF)
)

@Composable
private fun buildColorScheme(prefs: Prefs): ColorScheme {
    val isSysDark = isSystemInDarkTheme()
    return when (prefs.themeMode) {
        ThemeMode.SYSTEM -> if (isSysDark) DarkColors else LightColors
        ThemeMode.LIGHT -> LightColors
        ThemeMode.DARK -> DarkColors
        ThemeMode.OLED -> OledColors
        ThemeMode.CUSTOM -> {
            val bg = Color(prefs.customBgColor)
            val header = Color(prefs.customHeaderColor)
            val isDarkBg = bg.luminance() < 0.45f
            val isDarkHeader = header.luminance() < 0.45f
            val textColor = if (isDarkBg) Color(0xFFF8FAFC) else Color(0xFF0F172A)
            val surfaceColor = if (isDarkBg) {
                bg.copy(alpha = 1f).compositeOver(Color.White.copy(alpha = 0.08f))
            } else {
                Color.White
            }
            if (isDarkBg) {
                darkColorScheme(
                    primary = header,
                    onPrimary = if (isDarkHeader) Color.White else Color.Black,
                    secondary = if (isDarkHeader) Color(0xFF38BDF8) else header,
                    background = bg,
                    onBackground = textColor,
                    surface = surfaceColor,
                    onSurface = textColor,
                    primaryContainer = header.copy(alpha = 0.25f),
                    onPrimaryContainer = Color.White
                )
            } else {
                lightColorScheme(
                    primary = header,
                    onPrimary = if (isDarkHeader) Color.White else Color.Black,
                    secondary = if (isDarkHeader) header else Color(0xFF0284C7),
                    background = bg,
                    onBackground = textColor,
                    surface = surfaceColor,
                    onSurface = textColor,
                    primaryContainer = header.copy(alpha = 0.15f),
                    onPrimaryContainer = header
                )
            }
        }
    }
}

private val Category.icon: ImageVector
    get() = when (this) {
        Category.HLASENI -> Icons.Filled.Campaign
        Category.RM -> Icons.Filled.AccountBalance
        Category.ZM -> Icons.Filled.Groups
        Category.HLAS -> Icons.Filled.MenuBook
    }

private const val SETTINGS_TAB = 4

data class PdfTarget(val url: String, val title: String, val page: Int = 1)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MainViewModel, startTab: Int) {
    // Dynamické sledování změn témat
    var themeStateTrigger by remember { mutableIntStateOf(0) }
    val colorScheme = buildColorScheme(vm.prefs)

    MaterialTheme(colorScheme = colorScheme) {
        var tab by rememberSaveable { mutableIntStateOf(startTab) }
        var openCat by rememberSaveable { mutableIntStateOf(-1) }
        var openId by rememberSaveable { mutableStateOf("") }
        var pdfTarget by remember { mutableStateOf<PdfTarget?>(null) }

        val context = LocalContext.current

        LaunchedEffect(startTab) {
            tab = startTab
            openCat = -1
            pdfTarget = null
        }

        // Pokud je otevřen vestavěný prohlížeč PDF
        if (pdfTarget != null) {
            BackHandler { pdfTarget = null }
            PdfViewerScreen(
                url = pdfTarget!!.url,
                title = pdfTarget!!.title,
                initialPage = pdfTarget!!.page,
                onBack = { pdfTarget = null }
            )
            return@MaterialTheme
        }

        val opened: Item? = if (openCat >= 0) {
            Category.values()[openCat].let { c -> vm.feeds[c]?.items?.firstOrNull { it.id == openId } }
        } else null

        if (opened != null) {
            BackHandler { openCat = -1 }
            DetailScreen(
                item = opened,
                prefs = vm.prefs,
                onBack = { openCat = -1 },
                onOpenPdf = { url, title, page ->
                    if (vm.prefs.useInternalPdfViewer) {
                        pdfTarget = PdfTarget(url, title, page)
                    } else {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                }
            )
            return@MaterialTheme
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (tab == SETTINGS_TAB) "Nastavení" else Category.values()[tab].label,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    actions = {
                        if (tab != SETTINGS_TAB) {
                            IconButton(onClick = { vm.refresh() }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Obnovit")
                            }
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    Category.values().forEachIndexed { i, c ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(c.icon, contentDescription = c.label) },
                            label = { Text(c.shortLabel) }
                        )
                    }
                    NavigationBarItem(
                        selected = tab == SETTINGS_TAB,
                        onClick = { tab = SETTINGS_TAB },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = "Nastavení") },
                        label = { Text("Nastavení") }
                    )
                }
            }
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                if (tab == SETTINGS_TAB) {
                    SettingsScreen(vm.prefs) {
                        themeStateTrigger++
                    }
                } else {
                    val c = Category.values()[tab]
                    if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (vm.offline) {
                        Text(
                            "Nelze se připojit – zobrazena uložená data.",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    ItemList(vm.feeds[c]?.items, c, vm.loading) { item ->
                        openCat = c.ordinal
                        openId = item.id
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemList(items: List<Item>?, category: Category, loading: Boolean, onClick: (Item) -> Unit) {
    if (items == null || items.isEmpty()) {
        Text(
            if (loading || items == null) "Načítání…" else "Žádné záznamy.",
            modifier = Modifier.padding(24.dp)
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.id }) { item ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onClick(item) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        if (item.displayDate.isNotEmpty()) {
                            Text(
                                item.displayDate,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (item.resolutions.isNotEmpty()) {
                            Text(
                                "${item.resolutions.size} bodů",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else if (category == Category.HLAS && item.pages_count > 0) {
                            Text(
                                "${item.pages_count} stran A5",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val preview = item.text.replace(Regex("\\s+"), " ").take(160)
                    if (preview.isNotEmpty()) {
                        Text(
                            preview + if (item.text.length > 160) "…" else "",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScreen(
    item: Item,
    prefs: Prefs,
    onBack: () -> Unit,
    onOpenPdf: (url: String, title: String, page: Int) -> Unit
) {
    val context = LocalContext.current
    val reader = remember { Reader(context) }
    var fontScale by remember { mutableFloatStateOf(prefs.fontScale) }
    var rate by remember { mutableFloatStateOf(prefs.speechRate) }
    val hasResolutions = item.resolutions.isNotEmpty()
    val isHlas = !item.a5_url.isNullOrBlank() || item.toc.isNotEmpty()
    var detailTab by rememberSaveable { mutableIntStateOf(if (hasResolutions) 0 else 1) }

    var targetScrollIndex by remember { mutableIntStateOf(-1) }
    val summaryListState = rememberLazyListState()
    val fullListState = rememberLazyListState()

    DisposableEffect(Unit) { onDispose { reader.shutdown() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val viewUrl = item.fullA5Url ?: item.url
                        onOpenPdf(viewUrl, item.title, 1)
                    }) {
                        Icon(
                            if (prefs.useInternalPdfViewer) Icons.Filled.PictureAsPdf else Icons.Filled.OpenInBrowser,
                            contentDescription = "Otevřít PDF"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (isHlas) {
                // Přehled časopisu Hlas Loštic s obsahem a tlačítky
                HlasLosticDetail(
                    item = item,
                    prefs = prefs,
                    onOpenPdf = onOpenPdf
                )
            } else {
                // Ovládací lišta pro předčítání a velikost textu (Hlášení a Usnesení)
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Stepper(
                            label = "Velikost textu",
                            value = "${(fontScale * 100).roundToInt()} %",
                            onMinus = {
                                fontScale = round1(fontScale - Prefs.STEP_FONT).coerceAtLeast(Prefs.MIN_FONT)
                                prefs.fontScale = fontScale
                            },
                            onPlus = {
                                fontScale = round1(fontScale + Prefs.STEP_FONT).coerceAtMost(Prefs.MAX_FONT)
                                prefs.fontScale = fontScale
                            }
                        )
                        Stepper(
                            label = "Rychlost čtení",
                            value = "${(rate * 10).roundToInt() / 10.0}×",
                            onMinus = {
                                rate = round1(rate - Prefs.STEP_RATE).coerceAtLeast(Prefs.MIN_RATE)
                                prefs.speechRate = rate
                                reader.setRate(rate)
                            },
                            onPlus = {
                                rate = round1(rate + Prefs.STEP_RATE).coerceAtMost(Prefs.MAX_RATE)
                                prefs.speechRate = rate
                                reader.setRate(rate)
                            }
                        )
                        Button(
                            onClick = {
                                if (reader.playing) {
                                    reader.stop()
                                } else {
                                    val speakText = if (hasResolutions && detailTab == 0) {
                                        val sb = StringBuilder("${item.title}.\nStručný přehled schválených usnesení:\n")
                                        item.resolutions.filter { !it.is_procedural }.forEach {
                                            sb.append("Usnesení ${it.num}: ${it.summary}\n")
                                        }
                                        sb.toString()
                                    } else {
                                        "${item.title}.\n${item.text}"
                                    }
                                    reader.play(speakText, rate)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Icon(if (reader.playing) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
                            Text(if (reader.playing) "  Zastavit čtení" else "  Přečíst nahlas")
                        }
                        reader.error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Přepínač zobrazení: Přehled vs. Celé znění pro usnesení
                if (hasResolutions) {
                    SecondaryTabRow(
                        selectedTabIndex = detailTab,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Tab(
                            selected = detailTab == 0,
                            onClick = { detailTab = 0 },
                            text = { Text("📌 Přehled (${item.resolutions.count { !it.is_procedural }})") }
                        )
                        Tab(
                            selected = detailTab == 1,
                            onClick = { detailTab = 1 },
                            text = { Text("📄 Celé znění (${item.resolutions.size} bodů)") }
                        )
                    }

                    if (detailTab == 0) {
                        SummaryTab(
                            resolutions = item.resolutions,
                            fontScale = fontScale,
                            listState = summaryListState,
                            onItemClick = { origIndex ->
                                targetScrollIndex = origIndex
                                detailTab = 1
                            }
                        )
                    } else {
                        StructuredFullTextTab(
                            item = item,
                            fontScale = fontScale,
                            listState = fullListState,
                            targetScrollIndex = targetScrollIndex,
                            onTargetScrolled = { targetScrollIndex = -1 }
                        )
                    }
                } else {
                    // Běžný text pro hlášení rozhlasu
                    SelectionContainer {
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                            if (item.displayDate.isNotEmpty()) {
                                Text(
                                    item.displayDate, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary, fontSize = (15 * fontScale).sp
                                )
                            }
                            Text(
                                item.text.ifBlank { "Text se nepodařilo z dokumentu získat. Otevřete původní PDF." },
                                fontSize = (17 * fontScale).sp,
                                lineHeight = (17 * fontScale * 1.55f).sp,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Detailní zobrazení pro Hlas Loštic s přehledem obsahu / článků */
@Composable
private fun HlasLosticDetail(
    item: Item,
    prefs: Prefs,
    onOpenPdf: (url: String, title: String, page: Int) -> Unit
) {
    val a5Url = item.fullA5Url ?: item.url
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val filteredToc = remember(item.toc, searchQuery) {
        if (searchQuery.isBlank()) item.toc
        else item.toc.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Hlavička časopisu
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoStories, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            item.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (item.pages_count > 0) {
                        Text(
                            "Převedeno do formátu A5 na výšku (${item.pages_count} stran)",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onOpenPdf(a5Url, item.title, 1) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Číst A5 na výšku")
                        }
                        OutlinedButton(
                            onClick = { onOpenPdf(item.url, item.title, 1) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Původní PDF")
                        }
                    }
                }
            }
        }

        // Přehled obsahu (TOC)
        if (item.toc.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "📑 Obsah vydání (${item.toc.size} článků)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Hledat článek v obsahu…"
                )
            }

            if (filteredToc.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("Žádný článek neodpovídá hledání.", color = Color.Gray)
                    }
                }
            } else {
                items(filteredToc) { tocItem ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOpenPdf(a5Url, "${item.title} – ${tocItem.title}", tocItem.page)
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Row(
                            Modifier.padding(14.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    tocItem.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Klepnutím otevřete přímo na straně ${tocItem.page} →",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Text(
                                    "str. ${tocItem.page}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Text(
                    item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

/** Záložka se stručným přehledem podstatných usnesení */
@Composable
private fun SummaryTab(
    resolutions: List<Resolution>,
    fontScale: Float,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onItemClick: (Int) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var hideProcedural by rememberSaveable { mutableStateOf(true) }

    val filteredWithIndex = remember(resolutions, query, hideProcedural) {
        resolutions.mapIndexed { idx, res -> idx to res }.filter { (_, res) ->
            (!hideProcedural || !res.is_procedural) &&
                    (query.isBlank() || res.num.contains(query, ignoreCase = true) || res.summary.contains(query, ignoreCase = true) || res.text.contains(query, ignoreCase = true))
        }
    }

    Column(Modifier.fillMaxSize()) {
        SearchBar(query = query, onQueryChange = { query = it }, placeholder = "Hledat v přehledu…")

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = hideProcedural,
                onClick = { hideProcedural = !hideProcedural },
                label = { Text("Skrýt formální body") }
            )
        }

        if (filteredWithIndex.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Žádné usnesení neodpovídá hledání.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredWithIndex, key = { it.second.num }) { (origIdx, res) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onItemClick(origIdx) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Usnesení ${res.num}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (15 * fontScale).sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                DecisionBadge(res.decision)
                            }
                            Text(
                                res.summary,
                                fontSize = (16 * fontScale).sp,
                                lineHeight = (16 * fontScale * 1.45f).sp,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            Row(
                                Modifier.fillMaxWidth().padding(top = 6.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    "Zobrazit celý text →",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Záložka Celé znění s vyhledáváním a rychlým scrollováním mezi body */
@Composable
private fun StructuredFullTextTab(
    item: Item,
    fontScale: Float,
    listState: androidx.compose.foundation.lazy.LazyListState,
    targetScrollIndex: Int,
    onTargetScrolled: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(targetScrollIndex) {
        if (targetScrollIndex >= 0) {
            val offset = if (item.header.isNotBlank()) 1 else 0
            listState.animateScrollToItem(targetScrollIndex + offset)
            onTargetScrolled()
        }
    }

    val filteredIndices = remember(item.resolutions, query) {
        if (query.isBlank()) item.resolutions.indices.toList()
        else item.resolutions.indices.filter { idx ->
            val res = item.resolutions[idx]
            res.num.contains(query, ignoreCase = true) || res.text.contains(query, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        SearchBar(query = query, onQueryChange = { query = it }, placeholder = "Hledat v celém textu…")

        if (item.resolutions.size > 1 && query.isBlank()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(item.resolutions) { idx, res ->
                    val shortNum = res.num.split("/").getOrNull(1) ?: res.num
                    SuggestionChip(
                        onClick = {
                            scope.launch {
                                listState.animateScrollToItem(idx + if (item.header.isNotBlank()) 1 else 0)
                            }
                        },
                        label = { Text("Bod $shortNum", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }
            HorizontalDivider()
        }

        SelectionContainer {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (item.header.isNotBlank() && query.isBlank()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                item.header,
                                fontSize = (15 * fontScale).sp,
                                lineHeight = (15 * fontScale * 1.4f).sp,
                                color = Color(0xFF475569),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                items(filteredIndices) { idx ->
                    val res = item.resolutions[idx]
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                HighlightText(
                                    text = "Usnesení ${res.num}:",
                                    query = query,
                                    fontSize = (17 * fontScale).sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                DecisionBadge(res.decision)
                            }
                            Spacer(Modifier.height(6.dp))
                            HighlightText(
                                text = res.text,
                                query = query,
                                fontSize = (16 * fontScale).sp,
                                lineHeight = (16 * fontScale * 1.55f).sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Smazat")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun DecisionBadge(decision: String) {
    val (bg, fg) = when (decision) {
        "SCHVALUJE", "SOUHLASÍ", "VOLÍ", "JMENUJE" -> Color(0xFFDCFCE7) to Color(0xFF166534)
        "NESCHVALUJE" -> Color(0xFFFEE2E2) to Color(0xFF991B1B)
        "BERE NA VĚDOMÍ" -> Color(0xFFE0F2FE) to Color(0xFF075985)
        "UKLÁDÁ", "POVĚŘUJE" -> Color(0xFFFEF3C7) to Color(0xFF92400E)
        else -> Color(0xFFF1F5F9) to Color(0xFF475569)
    }
    Text(
        text = decision,
        color = fg,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = Modifier
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
private fun HighlightText(
    text: String,
    query: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight = FontWeight.Normal,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    color: Color = Color.Unspecified
) {
    if (query.isBlank() || !text.contains(query, ignoreCase = true)) {
        Text(text, fontSize = fontSize, fontWeight = fontWeight, lineHeight = lineHeight, color = color)
        return
    }

    val annotated = buildAnnotatedString {
        var start = 0
        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()
        while (start < text.length) {
            val idx = lowerText.indexOf(lowerQuery, start)
            if (idx < 0) {
                append(text.substring(start))
                break
            }
            append(text.substring(start, idx))
            withStyle(SpanStyle(background = Color(0xFFFEF08A), fontWeight = FontWeight.Bold, color = Color.Black)) {
                append(text.substring(idx, idx + query.length))
            }
            start = idx + query.length
        }
    }
    Text(annotated, fontSize = fontSize, fontWeight = fontWeight, lineHeight = lineHeight, color = color)
}

private fun round1(v: Float): Float = (v * 10).roundToInt() / 10f

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        IconButton(onClick = onMinus) { Icon(Icons.Filled.Remove, contentDescription = "$label méně") }
        Text(value, modifier = Modifier.padding(horizontal = 4.dp), fontWeight = FontWeight.Bold)
        IconButton(onClick = onPlus) { Icon(Icons.Filled.Add, contentDescription = "$label více") }
    }
}

/** Obrazovka nastavení: témata, prohlížeč PDF a notifikace */
@Composable
private fun SettingsScreen(prefs: Prefs, onThemeChanged: () -> Unit) {
    val states = remember { Category.values().associateWith { mutableStateOf(prefs.notifyEnabled(it)) } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    var selectedTheme by remember { mutableStateOf(prefs.themeMode) }
    var useInternalPdf by remember { mutableStateOf(prefs.useInternalPdfViewer) }
    var showCustomThemeDialog by remember { mutableStateOf(false) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        // Vzhled a téma
        Text("Vzhled aplikace", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Vyberte si barevný motiv podle vašich preferencí.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )

        ThemeOptionItem(
            title = "Podle systému",
            subtitle = "Přizpůsobí se světlému / tmavému režimu vašeho telefonu",
            icon = Icons.Filled.PhoneAndroid,
            selected = selectedTheme == ThemeMode.SYSTEM,
            onClick = {
                selectedTheme = ThemeMode.SYSTEM
                prefs.themeMode = ThemeMode.SYSTEM
                onThemeChanged()
            }
        )
        ThemeOptionItem(
            title = "Světlé téma",
            subtitle = "Klasické světlé barvy města Loštice",
            icon = Icons.Filled.LightMode,
            selected = selectedTheme == ThemeMode.LIGHT,
            onClick = {
                selectedTheme = ThemeMode.LIGHT
                prefs.themeMode = ThemeMode.LIGHT
                onThemeChanged()
            }
        )
        ThemeOptionItem(
            title = "Tmavé téma",
            subtitle = "Příjemné tmavé odstíny šetřící oči",
            icon = Icons.Filled.DarkMode,
            selected = selectedTheme == ThemeMode.DARK,
            onClick = {
                selectedTheme = ThemeMode.DARK
                prefs.themeMode = ThemeMode.DARK
                onThemeChanged()
            }
        )
        ThemeOptionItem(
            title = "OLED černé téma",
            subtitle = "Úspora baterie na OLED displejích (čistě černé pozadí)",
            icon = Icons.Filled.DarkMode,
            selected = selectedTheme == ThemeMode.OLED,
            onClick = {
                selectedTheme = ThemeMode.OLED
                prefs.themeMode = ThemeMode.OLED
                onThemeChanged()
            }
        )
        ThemeOptionItem(
            title = "Vlastní téma",
            subtitle = "Nastavte si vlastní barvu hlavičky a pozadí",
            icon = Icons.Filled.ColorLens,
            selected = selectedTheme == ThemeMode.CUSTOM,
            onClick = {
                selectedTheme = ThemeMode.CUSTOM
                prefs.themeMode = ThemeMode.CUSTOM
                showCustomThemeDialog = true
                onThemeChanged()
            },
            trailing = {
                if (selectedTheme == ThemeMode.CUSTOM) {
                    TextButton(onClick = { showCustomThemeDialog = true }) {
                        Text("Upravit barvy")
                    }
                }
            }
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        // Prohlížeč PDF
        Text("Prohlížeč PDF dokumentů", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Zvolte, jak chcete otevírat hlášení, usnesení a časopis Hlas Loštic.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )

        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Vestavěný prohlížeč PDF v aplikaci", fontWeight = FontWeight.SemiBold)
                Text(
                    if (useInternalPdf) "PDF se zobrazí přímo v aplikaci s podporou přiblížení a skoku na strany"
                    else "PDF se otevírá v externím internetovém prohlížeči nebo prohlížeči PDF",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Switch(
                checked = useInternalPdf,
                onCheckedChange = { v ->
                    useInternalPdf = v
                    prefs.useInternalPdfViewer = v
                }
            )
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        // Notifikace
        Text("Upozornění na nové záznamy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Aplikace kontroluje nové záznamy přibližně jednou za hodinu.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )
        Category.values().forEach { c ->
            var on by states.getValue(c)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(c.label, modifier = Modifier.weight(1f))
                Switch(checked = on, onCheckedChange = { v ->
                    on = v
                    prefs.setNotifyEnabled(c, v)
                    if (v && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                })
            }
            HorizontalDivider()
        }

        Text(
            "Zdroj dat: www.mu-lostice.cz (neoficiální aplikace). Text je automaticky převeden z PDF dokumentů.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 16.dp)
        )
    }

    if (showCustomThemeDialog) {
        CustomThemeDialog(
            prefs = prefs,
            onDismiss = { showCustomThemeDialog = false },
            onApply = { headerColor, bgColor ->
                prefs.customHeaderColor = headerColor.toArgb()
                prefs.customBgColor = bgColor.toArgb()
                prefs.themeMode = ThemeMode.CUSTOM
                selectedTheme = ThemeMode.CUSTOM
                showCustomThemeDialog = false
                onThemeChanged()
            }
        )
    }
}

@Composable
private fun ThemeOptionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Icon(icon, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.primary else Color.Gray)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        if (trailing != null) {
            trailing()
        }
    }
}

/** Dialog pro přizpůsobení vlastních barev pozadí a hlavičky */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomThemeDialog(
    prefs: Prefs,
    onDismiss: () -> Unit,
    onApply: (header: Color, bg: Color) -> Unit
) {
    var curHeader by remember { mutableStateOf(Color(prefs.customHeaderColor)) }
    var curBg by remember { mutableStateOf(Color(prefs.customBgColor)) }

    val headerPresets = listOf(
        Color(0xFF0D3B66), // Loštická modrá
        Color(0xFF1E3A8A), // Tmavý safír
        Color(0xFF065F46), // Smaragdová
        Color(0xFF14532D), // Lesní zeleň
        Color(0xFF831843), // Vínová / Bordó
        Color(0xFF581C87), // Fialová
        Color(0xFFC2410C), // Měděná / Oranžová
        Color(0xFF334155), // Břidlice
        Color(0xFF111827)  // Uhlově černá
    )

    val bgPresets = listOf(
        Color(0xFFF5F8FB), // Světle šedá (výchozí)
        Color(0xFFFAF7F2), // Teplá krémová
        Color(0xFFF0FDF4), // Jemná mátová
        Color(0xFFF0F9FF), // Chladný led
        Color(0xFF1E293B), // Břidlicově tmavá
        Color(0xFF262322), // Kávově tmavá
        Color(0xFF0F172A), // Noční modř
        Color(0xFF000000)  // Čistě černá
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Přizpůsobení vlastního tématu", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // Živý náhled
                Text("Náhled:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = curBg,
                    shadowElevation = 2.dp
                ) {
                    Column {
                        // Header náhled
                        Box(
                            Modifier.fillMaxWidth().height(36.dp).background(curHeader).padding(horizontal = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                "Hlavička aplikace",
                                color = if (curHeader.luminance() < 0.45f) Color.White else Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        // Obsah náhled
                        Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                            val textColor = if (curBg.luminance() < 0.45f) Color.White else Color(0xFF0F172A)
                            Text("Text na zvoleném pozadí", color = textColor, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Výběr barvy hlavičky
                Text("Barva hlavičky (Top Bar):", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    headerPresets.forEach { color ->
                        ColorCircle(
                            color = color,
                            isSelected = curHeader == color,
                            onClick = { curHeader = color }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Výběr barvy pozadí
                Text("Barva pozadí (Background):", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    bgPresets.forEach { color ->
                        ColorCircle(
                            color = color,
                            isSelected = curBg == color,
                            onClick = { curBg = color }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(curHeader, curBg) }) {
                Text("Uložit téma")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Zrušit")
            }
        }
    )
}

@Composable
private fun ColorCircle(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = if (color.luminance() < 0.45f) Color.White else Color.Black,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
