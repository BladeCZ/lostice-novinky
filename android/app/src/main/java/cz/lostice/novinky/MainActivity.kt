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

    var readIds by mutableStateOf<Set<String>>(prefs.getReadIds())
        private set

    init {
        viewModelScope.launch {
            Category.values().forEach { c -> repo.cached(c)?.let { feeds[c] = it } }
            checkInitialReadState()
            refresh()
        }
    }

    private fun checkInitialReadState() {
        if (!prefs.isFirstRun() && feeds.isNotEmpty()) {
            val toMark = mutableSetOf<String>()
            feeds.values.forEach { feed ->
                if (feed.items.size > 2) {
                    toMark.addAll(feed.items.drop(2).map { it.id })
                }
            }
            prefs.markAllRead(toMark)
            prefs.setFirstRunCompleted()
            readIds = prefs.getReadIds()
        }
    }

    fun isItemRead(id: String): Boolean = id in readIds

    fun markAsRead(id: String) {
        prefs.markRead(id)
        readIds = prefs.getReadIds()
    }

    fun markAllAsRead() {
        val allIds = feeds.values.flatMap { it.items.map { it.id } }.toSet()
        prefs.markAllRead(allIds)
        readIds = prefs.getReadIds()
    }

    fun unreadCount(c: Category): Int {
        val items = feeds[c]?.items ?: return 0
        return items.count { it.id !in readIds }
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
                    prefs.setSeen(c, (prefs.seen(c) ?: emptySet()) + feed.items.map { it.id })
                }
            }
            checkInitialReadState()
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
