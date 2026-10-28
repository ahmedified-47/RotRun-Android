package com.ahmed.rotrun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmed.rotrun.data.SavedState
import com.ahmed.rotrun.logic.PlayCatalog
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
    onTerms: (() -> Unit)? = null,
    onPrivacy: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!settingsStyle) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(Color(0xFFE5E5EA))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(0.82f).background(Melt.Action))
                }
                if (showClose) {
                    Box(
                        Modifier.padding(start = 12.dp).size(36.dp).clip(CircleShape).background(Color(0xFFE5E5EA)).clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) { Text("✕", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                }
            }
        }
        Text(
            if (settingsStyle) "RotRun Premium" else "Get access to RotRun",
            fontSize = if (settingsStyle) 32.sp else 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 18.dp),
        )
        if (settingsStyle) {
            Text(
                "Free includes 3 chats with Rot a day. Voice is Premium. Prices come from Google Play.",
                color = Melt.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        if (state.premiumActive()) {
            Text("You’re Premium", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Current plan: ${Products.planName(state.productId ?: Products.YEARLY)}", color = Melt.Muted)
            Spacer(Modifier.height(8.dp))
        }
        val plans = listOf(Products.YEARLY, Products.MONTHLY, Products.LIFETIME)
        if (!state.lifetime) {
            plans.forEach { id ->
                PlanCard(id, selected == id) { onSelect(id) }
                Spacer(Modifier.height(10.dp))
            }
        }
        if (showDiscountLink) {
            Text(
                "70% off your first year",
                color = Melt.Action,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 4.dp).clickable(onClick = onDiscount),
            )
        }
        Spacer(Modifier.height(8.dp))
        CartoonBrain(BrainStyle.Meadow, Modifier.size(88.dp), animated = false)
        if (!settingsStyle) {
            FeatureRow("🔒", Melt.Orange, "Block unlimited apps", "Create custom schedules for different app categories")
            FeatureRow("📅", Melt.Action, "Track your progress", "Get detailed weekly reports showing your improvement")
            FeatureRow("", Melt.BrainPink, "Personalized brain score", "See how your digital habits affect cognitive health", mascot = true)
        }
        if (!state.lifetime) {
            val title = when {
                settingsStyle && state.premiumActive() && state.productId == selected -> "Current Plan"
                settingsStyle && state.premiumActive() -> "Change Plan"
                settingsStyle -> "Subscribe"
                selected == Products.MONTHLY -> "Unlock monthly"
                selected == Products.LIFETIME -> "Unlock lifetime"
                else -> "Unlock yearly"
            }
            val locked = settingsStyle && state.premiumActive() && state.productId == selected
            PrimaryButton(title, enabled = !locked) { if (!locked) onPurchase(selected) }
        }
        Text(
            "Restore Purchases",
            color = Melt.Action,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 14.dp).clickable(onClick = onRestore),
        )
        if (onTerms != null && onPrivacy != null) {
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Terms of Use", color = Melt.Action, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onTerms))
                Text("Privacy Policy", color = Melt.Action, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onPrivacy))
            }
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
    }
}

@Composable
fun OneTimeOffer(
    onRedeem: () -> Unit,
    onOther: () -> Unit,
    showClose: Boolean,
    onClose: () -> Unit,
    onTerms: (() -> Unit)? = null,
    onPrivacy: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (showClose) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFE5E5EA)).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Text("✕", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            }
        }
        val offer = PlayCatalog.quote(Products.YEARLY70)
        val yearly = PlayCatalog.quote(Products.YEARLY)
        val regularMicros = yearly?.renewalMicros ?: yearly?.priceMicros
        val off = if (offer != null && regularMicros != null) Scoring.discountPercent(regularMicros, offer.priceMicros) else 0
        Text(offer?.formattedPrice ?: "Price from Google Play", fontWeight = FontWeight.Bold, fontSize = 34.sp)
        Text("A one-time yearly offer", color = Melt.Muted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text("This offer shows once. Prices come from Google Play.", color = Melt.Muted, fontSize = 14.sp)
        if (offer?.renewalFormatted != null) {
            Text("then ${offer.renewalFormatted}${Products.periodLabel(Products.YEARLY70)}", color = Melt.Muted, fontSize = 14.sp)
        }
        if (off > 0) Text(
            "$off% OFF",
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 12.dp).rotate(-6f).clip(CircleShape).background(Melt.Orange).padding(horizontal = 14.dp, vertical = 6.dp),
        )
        CartoonBrain(BrainStyle.Meadow, Modifier.padding(top = 8.dp).size(88.dp), animated = false)
        Text(if (off > 0) "$off% off your first year" else "A one-time yearly offer", fontWeight = FontWeight.Bold, fontSize = 22.sp, textAlign = TextAlign.Center)
        Text(
            "Pay up front for the first year. Then it renews automatically.",
            color = Melt.Muted,
            textAlign = TextAlign.Center,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp),
        )
        Column(
            Modifier.padding(top = 16.dp).fillMaxWidth().border(2.dp, Melt.Orange, RoundedCornerShape(18.dp)).padding(16.dp),
        ) {
            Text(
                "YEARLY ONLY",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                modifier = Modifier.clip(CircleShape).background(Melt.Action).padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Yearly", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        offer?.renewalFormatted?.let { "then $it${Products.periodLabel(Products.YEARLY70)}" } ?: "Prices come from Google Play",
                        color = Melt.Muted,
                        fontSize = 13.sp,
                    )
                }
                Text(offer?.formattedPrice ?: "—", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
        Text("Cancel anytime • Money back guarantee", color = Melt.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(8.dp))
        PrimaryButton("Redeem 70% Off", onClick = onRedeem)
        if (onTerms != null && onPrivacy != null) {
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Terms of Use", color = Melt.Action, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onTerms))
                Text("Privacy Policy", color = Melt.Action, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onPrivacy))
            }
        }
        Text(
            "See other plans",
            color = Melt.Muted,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp).clickable(onClick = onOther),
        )
    }
}

@Composable
private fun PlanCard(id: String, selected: Boolean, onClick: () -> Unit) {
    val plans by PlayCatalog.plans.collectAsState()
    val quote = plans[id]
    val monthly = plans[Products.MONTHLY]
    val price = quote?.let { it.formattedPrice + Products.periodLabel(id) } ?: "Price from Google Play"
    val subtitle = when {
        id == Products.MONTHLY -> "Billed every month"
        id == Products.LIFETIME -> "One-time purchase. Does not renew."
        quote?.renewalFormatted != null -> "then ${quote.renewalFormatted}${Products.periodLabel(id)}"
        else -> "Billed yearly"
    }
    val badge = if (id == Products.YEARLY && quote != null && monthly != null) {
        Scoring.savePercent(monthly.priceMicros, quote.priceMicros)
    } else {
        0
    }
    Column(
        Modifier
            .fillMaxWidth()
            .border(if (selected) 2.dp else 1.dp, if (selected) Melt.Action else Color(0xFFC6C6C8), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        if (id != Products.MONTHLY && badge > 0) {
            Text(
                "SAVE $badge%",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.clip(CircleShape).background(Melt.Action).padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Products.planName(id), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(subtitle, color = Melt.Muted, fontSize = 13.sp)
            }
            Text(price, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
private fun FeatureRow(symbol: String, tint: Color, title: String, body: String, mascot: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (mascot) {
            CartoonBrain(BrainStyle.Meadow, Modifier.padding(end = 8.dp).size(32.dp), animated = false)
        } else {
            Text(symbol, color = tint, fontSize = 22.sp, modifier = Modifier.width(32.dp))
        }
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(body, color = Melt.Muted, fontSize = 14.sp)
        }
    }
}

@Composable
fun PrimaryButton(title: String, enabled: Boolean = true, light: Boolean = false, textColor: Color? = null, onClick: () -> Unit) {
    val fill = if (light) Color.White else Melt.Action
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(CircleShape)
            .background(fill.copy(alpha = if (enabled) 1f else 0.45f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, color = textColor ?: if (light) Melt.Ink else Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

@Composable
fun ChoiceRow(label: String, selected: Boolean, emoji: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Melt.Action.copy(alpha = 0.16f) else Color(0xFFE9E9EB))
            .border(if (selected) 1.5.dp else 0.dp, if (selected) Melt.Action else Color.Transparent, RoundedCornerShape(16.dp))
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
