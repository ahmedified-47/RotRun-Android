package com.ahmed.rotrun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmed.rotrun.data.SavedState
import com.ahmed.rotrun.logic.Products
import com.ahmed.rotrun.logic.Scoring

@Composable
fun GetAccessPaywall(
    state: SavedState,
    showClose: Boolean,
    showDiscountLink: Boolean,
    onClose: () -> Unit,
    onDiscount: () -> Unit,
    onPurchase: (String) -> Unit,
    onRestore: () -> Unit,
    selected: String,
    onSelect: (String) -> Unit,
    settingsStyle: Boolean = false,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            if (showClose) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Color.White).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Text("✕", fontWeight = FontWeight.Bold) }
            }
        }
        Text(
            if (settingsStyle) "RotRun Premium" else "Get access to RotRun",
            fontSize = if (settingsStyle) 32.sp else 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        if (settingsStyle) {
            Text(
                "Plans renew automatically until you cancel. Prices match RotRun’s App Store plans.",
                color = Melt.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp),
            )
            Text(
                "This Android build saves Premium on your device until Google Play products are connected.",
                color = Melt.Muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        if (state.premiumActive()) {
            Text("You’re Premium", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Current plan: ${Products.planName(state.productId ?: Products.YEARLY)}", color = Melt.Muted)
            Spacer(Modifier.height(8.dp))
        }
        val plans = if (settingsStyle) listOf(Products.YEARLY, Products.YEARLY70, Products.MONTHLY) else listOf(Products.YEARLY, Products.MONTHLY)
        plans.forEach { id ->
            PlanCard(id, selected == id, state.introUsed) { onSelect(id) }
            Spacer(Modifier.height(10.dp))
        }
        if (!settingsStyle) {
            FeatureRow("Block unlimited apps", "Create custom schedules for different app categories")
            FeatureRow("Track your progress", "Get detailed weekly reports showing your improvement")
            FeatureRow("Personalized brain score", "See how your digital habits affect cognitive health")
        }
        val title = when {
            settingsStyle && state.premiumActive() && state.productId == selected -> "Current Plan"
            settingsStyle && state.premiumActive() -> "Change Plan"
            settingsStyle -> "Subscribe"
            selected == Products.MONTHLY -> "Unlock monthly"
            else -> "Unlock yearly"
        }
        val locked = settingsStyle && state.premiumActive() && state.productId == selected
        PrimaryButton(title, enabled = !locked) { if (!locked) onPurchase(selected) }
        Text(
            "Restore Purchases",
            color = Melt.Action,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 14.dp).clickable(onClick = onRestore),
        )
        if (showDiscountLink) {
            Text(
                "70% off your first year",
                color = Melt.Action,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp).clickable(onClick = onDiscount),
            )
        }
        if (settingsStyle) {
            Text(
                "Subscriptions auto-renew unless canceled at least 24 hours before the end of the current period. You can cancel or change plans in Manage Subscription.",
                color = Melt.Muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        CartoonBrain(BrainStyle.Meadow, Modifier.size(88.dp), animated = false)
    }
}

@Composable
fun OneTimeOffer(onRedeem: () -> Unit, onOther: () -> Unit, showClose: Boolean, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (showClose) {
            Row(Modifier.fillMaxWidth()) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Color.White).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Text("✕", fontWeight = FontWeight.Bold) }
            }
        }
        Text("70% OFF", color = Melt.Orange, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text("70% off your first year", fontWeight = FontWeight.Bold, fontSize = 28.sp, textAlign = TextAlign.Center)
        Text(
            "Pay up front for the first year. Then it renews automatically.",
            color = Melt.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).padding(16.dp),
        ) {
            Text("YEARLY ONLY", color = Melt.Action, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Yearly", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(Scoring.usd(Products.YEARLY70_INTRO_CENTS), fontWeight = FontWeight.Black, fontSize = 32.sp)
            Text("then ${Scoring.usd(Products.YEARLY_CENTS)}/year", color = Melt.Muted)
        }
        Text("Cancel anytime • Money back guarantee", color = Melt.Muted, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Redeem 70% Off", onClick = onRedeem)
        Text(
            "See other plans",
            color = Melt.Action,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 14.dp).clickable(onClick = onOther),
        )
    }
}

@Composable
private fun PlanCard(id: String, selected: Boolean, introUsed: Boolean, onClick: () -> Unit) {
    val intro = !introUsed && (id == Products.YEARLY || id == Products.YEARLY70)
    val cents = when {
        id == Products.MONTHLY -> Products.MONTHLY_CENTS
        id == Products.YEARLY70 && intro -> Products.YEARLY70_INTRO_CENTS
        id == Products.YEARLY && intro -> Products.YEARLY_INTRO_CENTS
        else -> Products.YEARLY_CENTS
    }
    val perMonth = if (id == Products.MONTHLY) cents else cents / 12
    val badge = Scoring.savePercent(if (id == Products.MONTHLY) Products.MONTHLY_CENTS * 12 else cents)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .border(if (selected) 2.dp else 0.dp, if (selected) Melt.Action else Color.Transparent, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Products.planName(id), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
            if (id != Products.MONTHLY && badge > 0) {
                Text(
                    "SAVE $badge%",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(CircleShape).background(Melt.Action).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        Text(
            if (id == Products.MONTHLY) Scoring.usd(cents) else "${Scoring.usd(perMonth)}/mo",
            fontWeight = FontWeight.Black,
            fontSize = 28.sp,
        )
        Text(
            when {
                id == Products.MONTHLY -> "Billed every month"
                intro -> "then ${Scoring.usd(Products.YEARLY_CENTS)}/year"
                else -> "12 months • ${Scoring.usd(Products.YEARLY_CENTS)}"
            },
            color = Melt.Muted,
        )
    }
}

@Composable
private fun FeatureRow(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        Text(body, color = Melt.Muted, fontSize = 14.sp)
    }
}

@Composable
fun PrimaryButton(title: String, enabled: Boolean = true, light: Boolean = false, onClick: () -> Unit) {
    val brush = if (light) Brush.linearGradient(listOf(Color.White, Color.White)) else Brush.linearGradient(listOf(Melt.Action, Melt.Action))
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(CircleShape)
            .background(brush)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, color = if (light) Melt.Ink else Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

@Composable
fun ChoiceRow(label: String, selected: Boolean, emoji: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color(0xFFE8F1FF) else Color.White)
            .border(if (selected) 2.dp else 1.dp, if (selected) Melt.Action else Color(0xFFE5E5EA), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (emoji != null) {
            Text(emoji, fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
        }
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}
