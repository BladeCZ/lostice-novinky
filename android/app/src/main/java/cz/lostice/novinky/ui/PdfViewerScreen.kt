package cz.lostice.novinky.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    url: String,
    title: String,
    initialPage: Int = 1,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var downloading by remember { mutableStateOf(true) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var localPdfFile by remember { mutableStateOf<File?>(null) }
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }

    // Ovládání zoomu
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var zoomOffset by remember { mutableStateOf(Offset.Zero) }

    val listState = rememberLazyListState()

    // Stažení PDF do lokální mezipaměti
    fun startDownload() {
        downloading = true
        errorMsg = null
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val safeHash = (url.hashCode().toLong() and 0xFFFFFFFFL).toString(16)
                    val cacheFile = File(context.cacheDir, "pdf_$safeHash.pdf")

                    // Pokud soubor existuje a má nenulovou velikost, použijeme ho
                    if (cacheFile.exists() && cacheFile.length() > 1024) {
                        return@withContext cacheFile
                    }

                    val u = URL(url)
                    val conn = u.openConnection() as HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.readTimeout = 30000
                    conn.instanceFollowRedirects = true
                    conn.connect()

                    if (conn.responseCode !in 200..299) {
                        throw Exception("Chyba serveru: HTTP ${conn.responseCode}")
                    }

                    val totalBytes = conn.contentLength
                    val tempFile = File(context.cacheDir, "pdf_${safeHash}_tmp.pdf")
                    val input = conn.inputStream
                    val output = FileOutputStream(tempFile)

                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalBytes > 0) {
                            downloadProgress = totalRead.toFloat() / totalBytes
                        }
                    }

                    output.flush()
                    output.close()
                    input.close()
                    conn.disconnect()

                    if (tempFile.renameTo(cacheFile)) cacheFile else tempFile
                }

                localPdfFile = file
                val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                pfd = descriptor
                val r = PdfRenderer(descriptor)
                renderer = r
                pageCount = r.pageCount
                downloading = false

                // Přechod na požadovanou počáteční stranu (1-based index)
                if (initialPage > 1 && initialPage <= r.pageCount) {
                    listState.scrollToItem(initialPage - 1)
                }
            } catch (e: Exception) {
                downloading = false
                errorMsg = "Nepodařilo se stáhnout PDF: ${e.localizedMessage ?: e.message}"
            }
        }
    }

    LaunchedEffect(url) {
        startDownload()
    }

    DisposableEffect(url) {
        onDispose {
            try {
                renderer?.close()
                pfd?.close()
            } catch (_: Exception) {}
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        if (pageCount > 0) {
                            val cur = (listState.firstVisibleItemIndex + 1).coerceAtMost(pageCount)
                            Text(
                                "Strana $cur z $pageCount",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }) {
                        Icon(Icons.Filled.OpenInBrowser, contentDescription = "Otevřít v prohlížeči")
                    }
                    if (localPdfFile != null) {
                        IconButton(onClick = {
                            sharePdf(context, localPdfFile!!, title)
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "Sdílet PDF")
                        }
                    }
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
        Box(
            Modifier
                .padding(pad)
                .fillMaxSize()
        ) {
            when {
                downloading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (downloadProgress > 0f) "Stahování PDF: ${(downloadProgress * 100).toInt()} %" else "Stahování PDF dokumentu…",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                errorMsg != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { startDownload() }) {
                                Icon(Icons.Filled.Refresh, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Zkusit znovu")
                            }
                            Button(onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }) {
                                Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Otevřít v prohlížeči")
                            }
                        }
                    }
                }
                renderer != null -> {
                    val r = renderer!!
                    val density = context.resources.displayMetrics.density
                    val screenWidthPx = context.resources.displayMetrics.widthPixels

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    zoomScale = (zoomScale * zoom).coerceIn(1f, 4f)
                                    if (zoomScale > 1f) {
                                        val maxPanX = (screenWidthPx * (zoomScale - 1f)) / 2f
                                        val maxPanY = 1000f * zoomScale
                                        zoomOffset = Offset(
                                            x = (zoomOffset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                                            y = (zoomOffset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                                        )
                                    } else {
                                        zoomOffset = Offset.Zero
                                    }
                                }
                            }
                            .graphicsLayer {
                                scaleX = zoomScale
                                scaleY = zoomScale
                                translationX = zoomOffset.x
                                translationY = zoomOffset.y
                            }
                    ) {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(pageCount, key = { it }) { pageIdx ->
                                PdfPageItem(
                                    renderer = r,
                                    pageIndex = pageIdx,
                                    targetWidthPx = screenWidthPx
                                )
                            }
                        }
                    }

                    // Plovoucí ovládání přiblížení (+ / - / reset)
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnimatedVisibility(visible = zoomScale > 1.05f) {
                            FloatingActionButton(
                                onClick = {
                                    zoomScale = 1f
                                    zoomOffset = Offset.Zero
                                },
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                                modifier = Modifier.size(44.dp),
                                elevation = FloatingActionButtonDefaults.elevation(3.dp)
                            ) {
                                Icon(Icons.Filled.RestartAlt, contentDescription = "Původní velikost", modifier = Modifier.size(20.dp))
                            }
                        }
                        FloatingActionButton(
                            onClick = {
                                zoomScale = (zoomScale + 0.35f).coerceAtMost(4f)
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(44.dp),
                            elevation = FloatingActionButtonDefaults.elevation(3.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Zvětšit", modifier = Modifier.size(20.dp))
                        }
                        FloatingActionButton(
                            onClick = {
                                zoomScale = (zoomScale - 0.35f).coerceAtLeast(1f)
                                if (zoomScale <= 1f) zoomOffset = Offset.Zero
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(44.dp),
                            elevation = FloatingActionButtonDefaults.elevation(3.dp)
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = "Zmenšit", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPageItem(
    renderer: PdfRenderer,
    pageIndex: Int,
    targetWidthPx: Int
) {
    var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }
    var aspectRatio by remember(pageIndex) { mutableFloatStateOf(1.414f) } // default A4/A5 ratio

    LaunchedEffect(pageIndex) {
        withContext(Dispatchers.IO) {
            val (bm, ratio) = synchronized(renderer) {
                val page = renderer.openPage(pageIndex)
                val w = page.width
                val h = page.height
                val rat = h.toFloat() / w.toFloat()

                // Renderujeme v rozumném rozlišení pro displej (max 1440px šířka pro úsporu paměti)
                val renderW = targetWidthPx.coerceIn(720, 1440)
                val renderH = (renderW * rat).toInt().coerceAtLeast(1)

                val b = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(b)
                canvas.drawColor(AndroidColor.WHITE)
                page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                b to rat
            }
            bitmap = bm
            aspectRatio = ratio
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp)),
        color = Color.White
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Strana ${pageIndex + 1}",
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)
                    .background(Color(0xFFEEEEEE)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.height(8.dp))
                    Text("Načítání strany ${pageIndex + 1}…", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
    }
}

private fun sharePdf(context: Context, file: File, title: String) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Sdílet $title"))
    } catch (_: Exception) {
        // Fallback pro přímé otevření
        val uri = Uri.fromFile(file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Sdílet $title"))
    }
}
