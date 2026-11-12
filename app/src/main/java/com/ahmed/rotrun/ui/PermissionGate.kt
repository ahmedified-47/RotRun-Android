package com.ahmed.rotrun.ui

import android.app.AlarmManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmed.rotrun.data.DeviceAccessReader
import com.ahmed.rotrun.logic.AccessibilityDisclosure
import com.ahmed.rotrun.logic.BlockArming

/** One missing grant at a time. Not now leaves the gate and leaves blocking disarmed. */
@Composable
fun PermissionGate(step: BlockArming.Step, onOpen: () -> Unit, onLeave: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(rot().page).rotInsets().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text(step.title, color = rot().ink, fontWeight = FontWeight.Bold, fontSize = 28.sp, textAlign = TextAlign.Center)
        Text(
            step.body,
            color = rot().muted,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            "Blocking stays off until this is on. RotRun checks again when you come back.",
            color = rot().muted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton("Open settings", onClick = onOpen)
        TextButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) { Text("Not now") }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun AccessibilityDisclosureDialog(onDecline: () -> Unit, onAccept: () -> Unit) {
    AlertDialog(
        onDismissRequest = {
            if (AccessibilityDisclosure.afterChoice(accepted = false) == AccessibilityDisclosure.DISARMED) onDecline()
        },
        title = { Text("Accessibility") },
        text = { Text(AccessibilityDisclosure.COPY) },
        confirmButton = {
            TextButton(onClick = {
                if (AccessibilityDisclosure.afterChoice(accepted = true) == AccessibilityDisclosure.SETTINGS) onAccept()
            }) { Text("Open settings") }
        },
        dismissButton = {
            TextButton(onClick = {
                if (AccessibilityDisclosure.afterChoice(accepted = false) == AccessibilityDisclosure.DISARMED) onDecline()
            }) { Text("Not now") }
        },
    )
}

fun openArmingStep(context: android.content.Context, id: String, onNotify: () -> Unit) {
    when (id) {
        "usage" -> DeviceAccessReader.open(context, DeviceAccessReader.usageIntent(context))
        "accessibility" -> Unit
        "overlay" -> DeviceAccessReader.open(context, DeviceAccessReader.overlayIntent(context))
        "notifications" -> onNotify()
        "battery" -> DeviceAccessReader.open(context, DeviceAccessReader.batteryIntent(context))
        "alarms" -> {
            if (Build.VERSION.SDK_INT >= 31) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            }
        }
    }
}

fun exactAlarmsAllowed(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < 31) return true
    val alarm = context.getSystemService(AlarmManager::class.java)
    return alarm.canScheduleExactAlarms()
}
