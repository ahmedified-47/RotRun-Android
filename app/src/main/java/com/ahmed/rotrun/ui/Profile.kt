package com.ahmed.rotrun.ui

import android.app.Activity
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmed.rotrun.data.ProfilePhoto
import com.ahmed.rotrun.data.RotRunRepository
import com.ahmed.rotrun.logic.DailyBoard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private val photoClient = OkHttpClient()

@Composable
fun ProfilePrompt(repo: RotRunRepository, editing: Boolean, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp)).background(rot().page).clickable(enabled = false) {}.padding(20.dp),
        ) {
            Text(
                if (editing) "Edit your name" else "Your name on the board",
                color = rot().ink,
                fontFamily = Nunito,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
            )
            Text(
                "Shown as your first name and last initial, like Ahmed J. Change it by tapping your own leaderboard row. A photo is optional.",
                color = rot().muted,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
            ProfileEditor(repo, prompt = !editing, onFinished = onDismiss)
            Text("Not now", color = rot().muted, modifier = Modifier.padding(top = 8.dp).clickable(onClick = onDismiss))
        }
    }
}

@Composable
fun ProfileEditor(repo: RotRunRepository, prompt: Boolean, onFinished: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val draft = remember { repo.backend.profileDraft() }
    var given by remember { mutableStateOf(draft.givenName) }
    var family by remember { mutableStateOf(draft.familyName) }
    var remoteUrl by remember { mutableStateOf(draft.avatarUrl) }
    var jpeg by remember { mutableStateOf<ByteArray?>(null) }
    var preview by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val shown = DailyBoard.boardName(given, family)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { ProfilePhoto.jpeg(context.contentResolver, uri) }
            if (bytes == null) {
                error = "That photo couldn’t be read, or it is still over 2 MB."
            } else {
                jpeg = bytes
                preview = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                error = null
            }
        }
    }
    fun save(photo: ByteArray?) {
        if (saving || shown == null) return
        saving = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { repo.backend.saveProfile(given, family, photo) }
            saving = false
            if (!result.saved) error = result.message
            else {
                result.message?.let { repo.toast(it) }
                onFinished()
            }
        }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(rot().inset).clickable {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            contentAlignment = Alignment.Center,
        ) {
            when {
                preview != null -> Image(preview!!.asImageBitmap(), contentDescription = "Profile photo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                !remoteUrl.isNullOrBlank() -> PlayerAvatar(shown ?: DailyBoard.fallbackName, remoteUrl, 96.dp)
                else -> Text(letter(given), color = rot().ink, fontWeight = FontWeight.Bold, fontSize = 28.sp)
            }
        }
        Text("Photo is optional", color = rot().muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
        NameField(given, "First name") { if (it.length <= 40) given = it }
        NameField(family, "Last name") { if (it.length <= 40) family = it }
        shown?.let { Text("On the board: $it", color = rot().muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        error?.let { Text(it, color = rot().danger, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center) }
        PrimaryButton(if (saving) "Saving…" else "Save", enabled = !saving && shown != null) { save(jpeg) }
        if (prompt) {
            Text("Skip photo", color = rot().blue, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp).clickable { save(null) })
        }
        if (!prompt && !repo.backend.signedIn) {
            Text("Sign in with Google", color = rot().blue, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp).clickable {
                val activity = context as? Activity ?: return@clickable
                repo.backend.signIn(activity) { message -> message?.let { repo.toast(it) } }
            })
        }
    }
}

@Composable
private fun NameField(value: String, hint: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = TextStyle(color = rot().ink, fontFamily = Nunito, fontSize = 18.sp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(rot().card).padding(16.dp),
        decorationBox = { inner ->
            if (value.isBlank()) Text(hint, color = rot().muted)
            inner()
        },
    )
}

@Composable
fun PlayerAvatar(name: String, url: String?, size: Dp) {
    var bitmap by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = if (url.isNullOrBlank()) null else withContext(Dispatchers.IO) { loadBitmap(url) }
    }
    if (bitmap != null) {
        Image(bitmap!!.asImageBitmap(), contentDescription = name, modifier = Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
    } else {
        Box(Modifier.size(size).clip(CircleShape).background(rot().inset), contentAlignment = Alignment.Center) {
            Text(letter(name), color = rot().ink, fontWeight = FontWeight.Bold)
        }
    }
}

private fun letter(name: String): String = name.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "R"

private fun loadBitmap(url: String): android.graphics.Bitmap? = runCatching {
    photoClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
        if (!response.isSuccessful) return null
        val bytes = response.body?.bytes() ?: return null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}.getOrNull()
