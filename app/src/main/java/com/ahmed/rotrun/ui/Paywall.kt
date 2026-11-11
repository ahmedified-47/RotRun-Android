package com.ahmed.rotrun.ui

import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.ahmed.rotrun.data.SavedState
import androidx.compose.material3.CircularProgressIndicator
import com.ahmed.rotrun.logic.BillingQuotes
import com.ahmed.rotrun.logic.CatalogStatus
import com.ahmed.rotrun.logic.PlayCatalog
import com.ahmed.rotrun.logic.Products

@Composable
fun GetAccessPaywall(
    state: SavedState,
    showClose: Boolean,
    onClose: () -> Unit,
    onPurchase: (String) -> Unit,
    onRestore: () -> Unit,
    onRetry: () -> Unit,
    selected: String,
    onSelect: (String) -> Unit,
    onboarding: Boolean = false,
    onContinue: () -> Unit = {},
    notice: String? = null,
    signedInUserId: String? = null,
) {
    val chrome = rot()
    val catalog by PlayCatalog.status.collectAsState()
    val purchasing by PlayCatalog.purchasing.collectAsState()
    var legal by remember { mutableStateOf<String?>(null) }
    LegalDialog(legal) { legal = null }
    val shown = selected.takeIf { it == Products.YEARLY || it == Products.MONTHLY || it == Products.LIFETIME } ?: Products.YEARLY
    val pro = state.proFor(signedInUserId)
    Column(Modifier.fillMaxWidth().background(chrome.page).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(top = if (onboarding) 4.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onboarding) {
                Text("Restore Purchases", color = chrome.blue, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onRestore))
                Spacer(Modifier.weight(1f))
                if (showClose) CloseMark(onClose)
            } else {
                if (showClose) CloseMark(onClose)
                Spacer(Modifier.weight(1f))
            }
        }
        CartoonBrain(BrainStyle.Meadow, Modifier.size(if (onboarding) 96.dp else 120.dp), animated = false)
        val titleCopy = @Composable {
            Text("RotRun Premium", color = chrome.ink, fontSize = 32.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        }
        val subtitle = @Composable {
            Text(
                if (onboarding) "Free includes 3 chats a day. Voice is Premium. Prices come from Google Play."
                else "Free includes 3 chats with Rot a day. Voice is Premium. Prices come from Google Play.",
                color = chrome.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp),
            )
        }
        if (onboarding) {
            Appear(0f) { titleCopy() }
            Appear(0.08f) { subtitle() }
        } else {
            titleCopy()
            subtitle()
        }
        if (!notice.isNullOrBlank()) {
            Text(notice, color = chrome.danger, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp, start = 12.dp, end = 12.dp))
        }
        Spacer(Modifier.height(12.dp))
        if (catalog == CatalogStatus.Unavailable) {
            Text("Subscriptions unavailable", color = chrome.ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(BillingQuotes.UNAVAILABLE_MESSAGE, color = chrome.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            PrimaryButton("Try Again", onClick = onRetry)
        } else {
            if (pro) {
                Text("You’re Premium", color = chrome.ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Current plan: ${Products.planName(state.productId)}", color = chrome.muted)
                val renews = renewsLine(state)
                if (renews != null) Text(renews, color = chrome.muted, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(8.dp))
            }
            if (!state.lifetime) {
                listOf(Products.YEARLY, Products.MONTHLY, Products.LIFETIME).forEach { id ->
                    PlanCard(id, shown == id) { onSelect(id) }
                    Spacer(Modifier.height(10.dp))
                }
                val title = when {
                    onboarding && pro -> "Continue"
                    onboarding -> "Subscribe to ${Products.planName(shown)}"
                    pro && state.productId == shown -> "Current Plan"
                    pro -> "Change Plan"
                    else -> "Subscribe"
                }
                val locked = !onboarding && pro && state.productId == shown
                if (purchasing) {
                    CircularProgressIndicator(color = chrome.blue, modifier = Modifier.padding(top = 12.dp).size(28.dp))
                } else {
                    PrimaryButton(title, enabled = !locked) {
                        if (onboarding && pro) onContinue() else if (!locked) onPurchase(shown)
                    }
                }
            }
        }
        if (!onboarding) {
            Text("Restore Purchases", color = rot().blue, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp).clickable(onClick = onRestore))
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Terms of Use", color = chrome.blue, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.clickable { legal = "terms" })
            Text("Privacy Policy", color = chrome.blue, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.clickable { legal = "privacy" })
        }
        val footer = @Composable {
            Text(BillingQuotes.LEGAL_FOOTER, color = chrome.muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = if (onboarding) 8.dp else 16.dp))
        }
        if (onboarding) Appear(0.14f) { footer() } else footer()
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun PaywallPopup(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            window.setDimAmount(0f)
        }
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(onClick = onDismiss),
            )
            Box(
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp, vertical = 28.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.86f)
                    .clip(RoundedCornerShape(36.dp))
                    .background(rot().page)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
            ) {
                Column(Modifier.verticalScroll(rememberScrollState())) { content() }
            }
        }
    }
}

@Composable
fun OneTimeOffer(onRedeem: () -> Unit, onClose: () -> Unit) {
    val chrome = rot()
    val plans by PlayCatalog.plans.collectAsState()
    val purchasing by PlayCatalog.purchasing.collectAsState()
    var legal by remember { mutableStateOf<String?>(null) }
    LegalDialog(legal) { legal = null }
    val offer = plans[Products.YEARLY70]
    val place = if (offer == null) BillingQuotes.placeholder(Products.YEARLY70) else null
    val off = if (offer != null) BillingQuotes.savingsPercent(Products.YEARLY70, offer, null) else BillingQuotes.placeholderSavings(Products.YEARLY70)
    Column(Modifier.fillMaxWidth().background(chrome.page).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { CloseMark(onClose) }
        CartoonBrain(BrainStyle.Meadow, Modifier.padding(top = 8.dp).size(120.dp), animated = false)
        Text("A one-time yearly offer", color = chrome.ink, fontWeight = FontWeight.Bold, fontSize = 28.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
        Text("This offer shows once. Prices come from Google Play.", color = chrome.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        if (off != null) {
            Text(
                "$off% OFF",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 12.dp).clip(CircleShape).background(Melt.Orange).padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
        Text(offer?.formattedPrice ?: place?.shown ?: "Price from Google Play", color = chrome.ink, fontWeight = FontWeight.Bold, fontSize = 34.sp, modifier = Modifier.padding(top = 8.dp))
        Text("/ year", color = chrome.muted, fontSize = 16.sp)
        if (offer?.introPresent == true && offer.renewalFormatted != null) {
            Text("then ${offer.renewalFormatted}/year", color = chrome.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        } else if (place?.renewal != null) {
            Text("then ${place.renewal}/year", color = chrome.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        }
        if (purchasing) {
            CircularProgressIndicator(color = chrome.blue, modifier = Modifier.padding(top = 16.dp).size(28.dp))
        } else {
            PrimaryButton("Subscribe to Yearly offer", onClick = onRedeem)
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Terms of Use", color = chrome.blue, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.clickable { legal = "terms" })
            Text("Privacy Policy", color = chrome.blue, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.clickable { legal = "privacy" })
        }
        Text(BillingQuotes.LEGAL_FOOTER, color = chrome.muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp, bottom = 16.dp))
    }
}

@Composable
private fun CloseMark(onClose: () -> Unit) {
    val chrome = rot()
    Box(Modifier.size(36.dp).clip(CircleShape).background(chrome.inset).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
        Text("✕", color = chrome.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

private fun renewsLine(state: SavedState): String? = when {
    state.lifetime || state.productId == Products.LIFETIME -> "never"
    state.premiumUntil > System.currentTimeMillis() -> "Renews ${java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(state.premiumUntil))}"
    else -> null
}

@Composable
private fun PlanCard(id: String, selected: Boolean, onClick: () -> Unit) {
    val plans by PlayCatalog.plans.collectAsState()
    val quote = plans[id]
    val monthly = plans[Products.MONTHLY]
    val chrome = rot()
    val place = if (quote == null) BillingQuotes.placeholder(id) else null
    val price = quote?.formattedPrice ?: place?.shown ?: "Price from Google Play"
    val period = if (quote == null && place == null) "" else Products.periodLabel(id)
    val subtitle = when {
        id == Products.LIFETIME -> "One-time purchase"
        quote?.introPresent == true && quote.renewalFormatted != null -> "then ${quote.renewalFormatted}/year"
        place?.renewal != null -> "then ${place.renewal}/year"
        id == Products.MONTHLY -> "Billed every month"
        else -> "Billed yearly"
    }
    val badge = if (id == Products.YEARLY) {
        if (quote != null) BillingQuotes.savingsPercent(id, quote, monthly?.regularMicros) else BillingQuotes.placeholderSavings(id)
    } else {
        null
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (chrome.dark) chrome.inset else chrome.card)
            .border(if (selected) 2.dp else 0.dp, if (selected) chrome.blue else Color.Transparent, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Products.planName(id), color = chrome.ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            if (badge != null) {
                Text(
                    "SAVE $badge%",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(start = 8.dp).clip(CircleShape).background(Melt.Orange).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(price, color = chrome.ink, fontWeight = FontWeight.Bold, fontSize = 28.sp)
            if (period.isNotBlank()) Text(period, color = chrome.muted, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        }
        Text(subtitle, color = chrome.muted, fontSize = 13.sp)
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
fun PrimaryButton(title: String, enabled: Boolean = true, light: Boolean = false, textColor: Color? = null, fadeDisabled: Boolean = false, loading: Boolean = false, onClick: () -> Unit) {
    val chrome = rot()
    val active = enabled && !loading
    val faded = !active && fadeDisabled && !loading
    val alpha by animateFloatAsState(if (faded) 0.45f else 1f, tween(220, easing = EaseInOut), label = "continue")
    val fill = when {
        !enabled && !fadeDisabled -> chrome.pill
        light -> Color.White
        else -> chrome.blue
    }
    val label = when {
        !enabled && !fadeDisabled -> chrome.muted
        textColor != null -> textColor
        light -> Melt.Ink
        else -> Color.White
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(fill)
            .clickable(enabled = active, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = loading || title.isEmpty(),
            transitionSpec = { fadeIn(tween(220, easing = EaseInOut)) togetherWith fadeOut(tween(220, easing = EaseInOut)) },
            label = "continue-spinner",
        ) { busy ->
            if (busy) {
                androidx.compose.material3.CircularProgressIndicator(color = label, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(title, color = label, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        }
    }
}

@Composable
fun ChoiceRow(label: String, selected: Boolean, emoji: String? = null, scaleSelected: Boolean = true, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected && scaleSelected) 1.015f else 1f, tween(200, easing = EaseInOut), label = "choice")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Melt.Action.copy(alpha = 0.16f) else Color(0xFFE9E9EB))
            .border(if (selected) 1.5.dp else 0.dp, if (selected) Melt.Action else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (emoji != null) {
            Text(emoji, fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
        }
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}
