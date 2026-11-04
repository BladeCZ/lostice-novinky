package cz.lostice.novinky

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.lostice.novinky.data.Category
import cz.lostice.novinky.data.Feed
import cz.lostice.novinky.data.Prefs
import cz.lostice.novinky.data.Repository
import cz.lostice.novinky.ui.App
import cz.lostice.novinky.work.UpdateWorker
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    val prefs = Prefs(app)
    val feeds = mutableStateMapOf<Category, Feed>()
    var loading by mutableStateOf(false)
        private set
    var offline by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            Category.values().forEach { c -> repo.cached(c)?.let { feeds[c] = it } }
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            var failed = false
            for (c in Category.values()) {
                val feed = repo.refresh(c)
                if (feed == null) {
                    failed = true
                } else {
                    feeds[c] = feed
                    // Otevření aplikace = položky jsou "viděné", upozornění se pošle jen na novější.
                    prefs.setSeen(c, (prefs.seen(c) ?: emptySet()) + feed.items.map { it.id })
                }
            }
            offline = failed
            loading = false
        }
    }
}

class MainActivity : ComponentActivity() {
    private var startTab by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startTab = intent?.getIntExtra(UpdateWorker.EXTRA_TAB, 0) ?: 0
        UpdateWorker.schedule(this)
        setContent {
            val vm: MainViewModel = viewModel()
            App(vm, startTab)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        startTab = intent.getIntExtra(UpdateWorker.EXTRA_TAB, 0)
    }
}
