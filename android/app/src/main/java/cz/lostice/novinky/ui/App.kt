package cz.lostice.novinky.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.lostice.novinky.MainViewModel
import cz.lostice.novinky.data.Category
import cz.lostice.novinky.data.Item
import cz.lostice.novinky.data.Prefs
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

        // Klepnutí na upozornění přepne záložku
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
                    if (item.displayDate.isNotEmpty()) {
                        Text(
                            item.displayDate,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
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
            Card(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
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
                        onClick = { if (reader.playing) reader.stop() else reader.play("${item.title}.\n${item.text}", rate) },
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
            HorizontalDivider()
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
                        lineHeight = (17 * fontScale * 1.45f).sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
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
