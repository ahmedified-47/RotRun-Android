package com.ahmed.rotrun

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.ahmed.rotrun.data.RotRunGraph
import com.ahmed.rotrun.data.RotRunRepository
import com.ahmed.rotrun.ui.InterventionFlow
import com.ahmed.rotrun.ui.RotRunRoot
import com.ahmed.rotrun.ui.RotRunTheme

class RotRunApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val repository = RotRunRepository(this)
        RotRunGraph.repo = repository
        repository.onLaunch()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (RotRunGraph.ready()) RotRunGraph.repo.attach(this)
        handle(intent)
        setContent { RotRunTheme { RotRunRoot(RotRunGraph.repo) } }
    }

    override fun onResume() {
        super.onResume()
        if (RotRunGraph.ready()) RotRunGraph.repo.attach(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "rotrun") return
        val repo = RotRunGraph.repo
        when (uri.host) {
            "blocked" -> openIntervention(uri.getQueryParameter("app") ?: "this app", "style")
            "focus" -> when {
                uri.getQueryParameter("end") == "1" -> repo.endFocus()
                uri.getQueryParameter("minutes") != null -> {
                    val minutes = uri.getQueryParameter("minutes")?.toIntOrNull() ?: 30
                    repo.startFocus(minutes, false, "all")
                }
                else -> repo.requestFocusChooser()
            }
            "bailout" -> repo.noteBailout(uri.getQueryParameter("from") ?: "a friend")
            "audioslap" -> repo.noteAudioSlap(uri.getQueryParameter("from") ?: "a friend")
            "duel" -> repo.pinDuel(uri.getQueryParameter("vs") ?: "a friend")
        }
    }

    private fun openIntervention(appName: String, mode: String) {
        startActivity(
            Intent(this, InterventionActivity::class.java)
                .putExtra(InterventionActivity.EXTRA_APP, appName)
                .putExtra(InterventionActivity.EXTRA_MODE, mode),
        )
    }
}

class InterventionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appName = intent.getStringExtra(EXTRA_APP) ?: "this app"
        val packageName = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val mode = intent.getStringExtra(EXTRA_MODE) ?: "style"
        val token = intent.getLongExtra(EXTRA_TOKEN, 0L).takeIf { it != 0L } ?: System.currentTimeMillis().also {
            intent.putExtra(EXTRA_TOKEN, it)
        }
        if (mode != "shield") RotRunGraph.repo.chargeAskBrain(token)
        setContent {
            RotRunTheme {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                InterventionFlow(
                    appName = appName,
                    startMode = mode,
                    packageName = packageName,
                    repo = RotRunGraph.repo,
                    onDismiss = { goHome() },
                    onUnlock = { minutes ->
                        if (!RotRunGraph.repo.grantUnlock(minutes)) {
                            RotRunGraph.repo.toast("Unlock waits until Focus ends")
                        }
                        finish()
                    },
                )
            }
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        const val EXTRA_APP = "app"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_MODE = "mode"
        const val EXTRA_TOKEN = "token"
    }
}
