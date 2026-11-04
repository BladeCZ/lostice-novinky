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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val Colors = lightColorScheme(
    primary = Color(0xFF0D3B66),
    secondary = Color(0xFF00AEEF),
    primaryContainer = Color(0xFFD6E6F5),
    surface = Color(0xFFFFFFFF),
    background = Color(0xFFF5F8FB)
)

private val Category.icon: ImageVector
    get() = when (this) {
        Category.HLASENI -> Icons.Filled.Campaign
        Category.RM -> Icons.Filled.AccountBalance
        Category.ZM -> Icons.Filled.Groups
    }

private const val SETTINGS_TAB = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MainViewModel, startTab: Int) {
    MaterialTheme(colorScheme = Colors) {
        var tab by rememberSaveable { mutableIntStateOf(startTab) }
        var openCat by rememberSaveable { mutableIntStateOf(-1) }
        var openId by rememberSaveable { mutableStateOf("") }

        LaunchedEffect(startTab) {
            tab = startTab
            openCat = -1
        }

        val opened: Item? = if (openCat >= 0) {
            Category.values()[openCat].let { c -> vm.feeds[c]?.items?.firstOrNull { it.id == openId } }
        } else null

        if (opened != null) {
            BackHandler { openCat = -1 }
            DetailScreen(opened, vm.prefs, onBack = { openCat = -1 })
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
                        titleContentColor = Color.White,
                        actionIconContentColor = Color.White
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
                    SettingsScreen(vm.prefs)
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
                    ItemList(vm.feeds[c]?.items, vm.loading) { item ->
                        openCat = c.ordinal
                        openId = item.id
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemList(items: List<Item>?, loading: Boolean, onClick: (Item) -> Unit) {
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
private fun DetailScreen(item: Item, prefs: Prefs, onBack: () -> Unit) {
    val context = LocalContext.current
    val reader = remember { Reader(context) }
    var fontScale by remember { mutableFloatStateOf(prefs.fontScale) }
    var rate by remember { mutableFloatStateOf(prefs.speechRate) }
    val hasResolutions = item.resolutions.isNotEmpty()
    var detailTab by rememberSaveable { mutableIntStateOf(if (hasResolutions) 0 else 1) }

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
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
                    }) { Icon(Icons.Filled.OpenInBrowser, contentDescription = "Otevřít původní PDF") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Ovládací lišta pro předčítání a velikost textu
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

            // Přepínač zobrazení: Přehled vs. Celé znění
            if (hasResolutions) {
                SecondaryTabRow(
                    selectedTabIndex = detailTab,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(
                        selected = detailTab == 0,
                        onClick = { detailTab = 0 },
                        text = { Text("📌 Přehled usnesení (${item.resolutions.count { !it.is_procedural }})") }
                    )
                    Tab(
                        selected = detailTab == 1,
                        onClick = { detailTab = 1 },
                        text = { Text("📄 Celé znění (${item.resolutions.size} bodů)") }
                    )
                }
            }

            // Obsah podle záložky
            if (hasResolutions && detailTab == 0) {
                SummaryTab(item.resolutions, fontScale)
            } else if (hasResolutions) {
                StructuredFullTextTab(item, fontScale)
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

/** Záložka se stručným přehledem podstatných usnesení */
@Composable
private fun SummaryTab(resolutions: List<Resolution>, fontScale: Float) {
    var query by rememberSaveable { mutableStateOf("") }
    var hideProcedural by rememberSaveable { mutableStateOf(true) }

    val filtered = remember(resolutions, query, hideProcedural) {
        resolutions.filter { res ->
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
                label = { Text("Skrýt formální body (komise, program)") }
            )
        }

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Žádné usnesení neodpovídá hledání.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.num }) { res ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
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
                        }
                    }
                }
            }
        }
    }
}

/** Záložka Celé znění s vyhledáváním a rychlým scrollováním mezi body */
@Composable
private fun StructuredFullTextTab(item: Item, fontScale: Float) {
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val filteredIndices = remember(item.resolutions, query) {
        if (query.isBlank()) item.resolutions.indices.toList()
        else item.resolutions.indices.filter { idx ->
            val res = item.resolutions[idx]
            res.num.contains(query, ignoreCase = true) || res.text.contains(query, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        SearchBar(query = query, onQueryChange = { query = it }, placeholder = "Hledat v celém textu…")

        // Rychlý horizontální výběr bodů (scrollování)
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
                                // +1 kvůli záhlaví na indexu 0
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

@Composable
private fun SettingsScreen(prefs: Prefs) {
    val states = remember { Category.values().associateWith { mutableStateOf(prefs.notifyEnabled(it)) } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
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
}
