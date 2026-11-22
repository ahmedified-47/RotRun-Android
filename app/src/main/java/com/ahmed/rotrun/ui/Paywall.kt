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
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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
import com.ahmed.rotrun.R
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
    if (onboarding) {
        OnboardingCatalog(state, showClose, onClose, onPurchase, onRestore, onRetry, selected, onSelect, onContinue, notice, signedInUserId)
    } else {
        PremiumPaywall(state, showClose, onClose, onPurchase, onRestore, onRetry, selected, onSelect, notice, signedInUserId)
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
        Column(
            Modifier.fillMaxSize().background(rot().page).rotInsets().verticalScroll(rememberScrollState()),
        ) { content() }
    }
}

@Composable
fun OneTimeOffer(onRedeem: () -> Unit, onClose: () -> Unit) {
    val ink = paywallInk()
    val plans by PlayCatalog.plans.collectAsState()
    val purchasing by PlayCatalog.purchasing.collectAsState()
    val offer = plans[Products.YEARLY70]
    val place = if (offer == null) BillingQuotes.placeholder(Products.YEARLY70) else null
    val monthly = plans[Products.MONTHLY]?.regularMicros
    val off = if (offer != null) BillingQuotes.savingsPercent(Products.YEARLY70, offer, monthly) else BillingQuotes.placeholderSavings(Products.YEARLY70)
    val price = offer?.formattedPrice ?: place?.shown
    val thenLine = renewalLine(Products.YEARLY70, offer, place)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
            CloseMark(onClose, paywallFill())
        }
        Spacer(Modifier.weight(1f))
        RotPose(R.drawable.rot_brain_character, Modifier.size(140.dp), animated = false)
        Text("A one-time yearly offer", color = ink, fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 28.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
        Text(
            "This offer shows once. Prices come from Google Play.",
            color = paywallSecondary(),
            fontFamily = FontFamily.Default,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 28.dp),
        )
        if (off != null) {
            Text(
                "$off% OFF",
                color = Color.White,
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.clip(CircleShape).background(Melt.Orange).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        if (price != null) {
            Text(price, color = ink, fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 36.sp)
            Text(Products.periodLabel(Products.YEARLY70), color = paywallSecondary(), fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 15.sp)
        }
        if (thenLine != null) {
            Text(thenLine, color = paywallSecondary(), fontFamily = FontFamily.Default, fontSize = 14.sp)
        }
        Spacer(Modifier.weight(1f))
        val offerReady = offer != null
        Box(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
            PaywallCta(
                if (offerReady) "Subscribe to Yearly offer" else "Continue",
                enabled = offerReady && !purchasing,
                loading = purchasing,
                onClick = onRedeem,
            )
        }
        Text(
            BillingQuotes.LEGAL_FOOTER,
            color = paywallTertiary(),
            fontFamily = FontFamily.Default,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun CloseMark(onClose: () -> Unit, fill: Color) {
    val ink = paywallInk()
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(fill).clickable(onClick = onClose).semantics { contentDescription = "Close" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(14.dp)) {
            val stroke = 2.2.dp.toPx()
            drawLine(ink, Offset(0f, 0f), Offset(size.width, size.height), stroke, cap = StrokeCap.Round)
            drawLine(ink, Offset(size.width, 0f), Offset(0f, size.height), stroke, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun PremiumPaywall(
    state: SavedState,
    showClose: Boolean,
    onClose: () -> Unit,
    onPurchase: (String) -> Unit,
    onRestore: () -> Unit,
    onRetry: () -> Unit,
    selected: String,
    onSelect: (String) -> Unit,
    notice: String?,
    signedInUserId: String?,
) {
    val catalog by PlayCatalog.status.collectAsState()
    val purchasing by PlayCatalog.purchasing.collectAsState()
    var legal by remember { mutableStateOf<String?>(null) }
    LegalDialog(legal) { legal = null }
    val shown = mainSelection(selected)
    LaunchedEffect(state.productId) {
        val current = state.productId ?: return@LaunchedEffect
        if (current == Products.YEARLY || current == Products.MONTHLY || current == Products.LIFETIME) onSelect(current)
    }
    val pro = state.proFor(signedInUserId)
    val currentOnCard = pro && state.productId == shown
    val ink = paywallInk()
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showClose) CloseMark(onClose, if (rot().dark) Color(0xFF1C1C1E) else Color.White)
            Spacer(Modifier.weight(1f))
        }
        RotPose(R.drawable.rot_brain_character, Modifier.size(120.dp), animated = false)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("RotRun Premium", color = ink, fontFamily = FontFamily.Default, fontSize = 32.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                "Free includes 3 chats with Rot a day. Voice is Premium. Prices come from Google Play.",
                color = paywallSecondary(),
                fontFamily = FontFamily.Default,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        when (catalog) {
            CatalogStatus.Loading -> CircularProgressIndicator(color = Melt.Action, modifier = Modifier.padding(top = 24.dp).size(28.dp))
            CatalogStatus.Unavailable -> EmptyCatalog(onRetry)
            CatalogStatus.Ready -> {
                if (currentOnCard) CurrentPlan(state)
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(Products.YEARLY, Products.MONTHLY, Products.LIFETIME).forEach { id ->
                        SettingsPlanCard(id, shown == id) { onSelect(id) }
                    }
                }
            }
        }
        if (!notice.isNullOrBlank()) {
            Text(notice, color = Color.Red, fontFamily = FontFamily.Default, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
        }
        if (catalog == CatalogStatus.Ready && !state.lifetime) {
            val title = when {
                currentOnCard -> "Current Plan"
                pro -> "Change Plan"
                else -> "Subscribe"
            }
            Box(Modifier.padding(horizontal = 20.dp)) {
                PaywallCta(title, enabled = !currentOnCard && !purchasing, loading = purchasing) { onPurchase(shown) }
            }
        }
        Text(
            "Restore Purchases",
            color = Melt.Action,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            modifier = Modifier.clickable(enabled = !purchasing, onClick = onRestore),
        )
        LegalFooter { legal = it }
    }
}

@Composable
private fun OnboardingCatalog(
    state: SavedState,
    showClose: Boolean,
    onClose: () -> Unit,
    onPurchase: (String) -> Unit,
    onRestore: () -> Unit,
    onRetry: () -> Unit,
    selected: String,
    onSelect: (String) -> Unit,
    onContinue: () -> Unit,
    notice: String?,
    signedInUserId: String?,
) {
    val catalog by PlayCatalog.status.collectAsState()
    val purchasing by PlayCatalog.purchasing.collectAsState()
    var legal by remember { mutableStateOf<String?>(null) }
    LegalDialog(legal) { legal = null }
    val shown = mainSelection(selected)
    val pro = state.proFor(signedInUserId)
    val ink = paywallInk()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Restore Purchases",
                color = Melt.Action,
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.clickable(enabled = !purchasing, onClick = onRestore),
            )
            Spacer(Modifier.weight(1f))
            if (showClose) CloseMark(onClose, paywallFill())
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("RotRun Premium", color = ink, fontFamily = FontFamily.Default, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                "Free includes 3 chats a day. Voice is Premium. Prices come from Google Play.",
                color = paywallSecondary(),
                fontFamily = FontFamily.Default,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            when (catalog) {
                CatalogStatus.Loading -> CircularProgressIndicator(color = Melt.Action, modifier = Modifier.align(Alignment.CenterHorizontally).size(28.dp))
                CatalogStatus.Unavailable -> {
                    Text(notice ?: BillingQuotes.UNAVAILABLE_MESSAGE, color = paywallSecondary(), fontFamily = FontFamily.Default, fontSize = 14.sp, textAlign = TextAlign.Center)
                    Text("Try Again", color = ink, fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.clickable(onClick = onRetry))
                }
                CatalogStatus.Ready -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(Products.YEARLY, Products.MONTHLY, Products.LIFETIME).forEach { id ->
                        OutlinePlanCard(id, shown == id) { onSelect(id) }
                    }
                }
            }
            if (catalog == CatalogStatus.Ready && !notice.isNullOrBlank()) {
                Text(notice, color = Color.Red, fontFamily = FontFamily.Default, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            LegalFooter(bottom = 0.dp, horizontal = 0.dp, gap = 16.dp) { legal = it }
        }
        val title = when {
            pro -> "Continue"
            catalog != CatalogStatus.Ready -> "Subscribe"
            else -> "Subscribe to ${Products.planName(shown)}"
        }
        val canBuy = pro || catalog == CatalogStatus.Ready
        Box(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            PaywallCta(title, enabled = canBuy && !purchasing, loading = purchasing) {
                if (pro) onContinue() else onPurchase(shown)
            }
        }
    }
}

@Composable
private fun EmptyCatalog(onRetry: () -> Unit) {
    Column(Modifier.padding(horizontal = 24.dp).padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Subscriptions unavailable", color = paywallInk(), fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(BillingQuotes.UNAVAILABLE_MESSAGE, color = paywallSecondary(), fontFamily = FontFamily.Default, fontSize = 14.sp, textAlign = TextAlign.Center)
        Text(
            "Try Again",
            color = Color.White,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.clip(CircleShape).background(Melt.Action).clickable(onClick = onRetry).padding(horizontal = 28.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun CurrentPlan(state: SavedState) {
    Column(Modifier.padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("You’re Premium", color = paywallInk(), fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Current plan: ${Products.planName(state.productId)}", color = paywallSecondary(), fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 15.sp)
        val renews = renewsLine(state)
        if (renews != null) Text(renews, color = paywallTertiary(), fontFamily = FontFamily.Default, fontSize = 13.sp)
    }
}

@Composable
private fun LegalFooter(
    bottom: androidx.compose.ui.unit.Dp = 28.dp,
    horizontal: androidx.compose.ui.unit.Dp = 24.dp,
    gap: androidx.compose.ui.unit.Dp = 10.dp,
    onOpen: (String) -> Unit,
) {
    Column(Modifier.padding(horizontal = horizontal).padding(bottom = bottom), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(gap)) {
        Text(BillingQuotes.LEGAL_FOOTER, color = paywallTertiary(), fontFamily = FontFamily.Default, fontSize = 12.sp, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Privacy Policy", color = Melt.Action, fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.clickable { onOpen("privacy") })
            Text("Terms of Use", color = Melt.Action, fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.clickable { onOpen("terms") })
        }
    }
}

@Composable
internal fun PaywallCta(title: String, enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    val alpha by animateFloatAsState(if (enabled && !loading) 1f else 0.45f, tween(220, easing = EaseInOut), label = "paywall")
    Row(
        Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha }.clip(CircleShape).background(Melt.Action).clickable(enabled = enabled && !loading, onClick = onClick).padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(title, color = Color.White, fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

private fun renewsLine(state: SavedState): String? = when {
    state.lifetime || state.productId == Products.LIFETIME -> null
    state.premiumUntil > System.currentTimeMillis() -> "Renews ${java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(state.premiumUntil))}"
    else -> null
}

@Composable
private fun SettingsPlanCard(id: String, selected: Boolean, onClick: () -> Unit) {
    val quote = planQuote(id)
    val place = if (quote == null) BillingQuotes.placeholder(id) else null
    val shape = ContinuousCornerShape(22.dp)
    val fill = if (rot().dark) Color(0xFF1C1C1E) else Color.White
    Column(
        Modifier.fillMaxWidth().clip(shape).background(fill).border(if (selected) 2.dp else 0.dp, if (selected) Melt.Action else Color.Transparent, shape).clickable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(Products.planName(id), color = paywallInk(), fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            SaveBadge(id, quote)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(shownPrice(quote, place), color = paywallInk(), fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 28.sp, modifier = Modifier.alignByBaseline())
            Text(Products.periodLabel(id), color = paywallSecondary(), fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 15.sp, modifier = Modifier.alignByBaseline())
        }
        val thenLine = renewalLine(id, quote, place)
        if (thenLine != null) {
            Text(thenLine, color = paywallSecondary(), fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        }
    }
}

@Composable
private fun OutlinePlanCard(id: String, selected: Boolean, onClick: () -> Unit) {
    val quote = planQuote(id)
    val place = if (quote == null) BillingQuotes.placeholder(id) else null
    val shape = ContinuousCornerShape(18.dp)
    val stroke = if (selected) Melt.Action else if (rot().dark) Color(0x99545458) else Color(0x4A3C3C43)
    Column(
        Modifier.fillMaxWidth().border(if (selected) 2.dp else 1.dp, stroke, shape).clickable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(Products.planName(id), color = paywallInk(), fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            SaveBadge(id, quote)
            Spacer(Modifier.weight(1f))
            Text(shownPrice(quote, place), color = paywallInk(), fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(Products.periodLabel(id), color = paywallSecondary(), fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        }
        val thenLine = renewalLine(id, quote, place)
        if (thenLine != null) {
            Text(thenLine, color = paywallSecondary(), fontFamily = FontFamily.Default, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SaveBadge(id: String, quote: com.ahmed.rotrun.logic.PlanQuote?) {
    if (id != Products.YEARLY) return
    val plans by PlayCatalog.plans.collectAsState()
    val badge = if (quote != null) BillingQuotes.savingsPercent(id, quote, plans[Products.MONTHLY]?.regularMicros) else BillingQuotes.placeholderSavings(id)
    if (badge == null) return
    Text(
        "SAVE $badge%",
        color = Color.White,
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        modifier = Modifier.clip(CircleShape).background(Melt.Orange).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun planQuote(id: String): com.ahmed.rotrun.logic.PlanQuote? {
    val plans by PlayCatalog.plans.collectAsState()
    return plans[id]
}

private fun mainSelection(selected: String): String =
    selected.takeIf { it == Products.YEARLY || it == Products.MONTHLY || it == Products.LIFETIME } ?: Products.YEARLY

private fun shownPrice(quote: com.ahmed.rotrun.logic.PlanQuote?, place: BillingQuotes.PricePlaceholder?): String =
    quote?.formattedPrice ?: place?.shown ?: "Price from Google Play"

private fun renewalLine(id: String, quote: com.ahmed.rotrun.logic.PlanQuote?, place: BillingQuotes.PricePlaceholder?): String? {
    if (id == Products.LIFETIME) return null
    val renewal = when {
        quote?.introPresent == true -> quote.renewalFormatted
        quote == null -> place?.renewal
        else -> null
    } ?: return null
    return "then $renewal${Products.periodLabel(id)}"
}

@Composable
private fun paywallInk(): Color = if (rot().dark) Color.White else Color.Black

@Composable
private fun paywallSecondary(): Color = if (rot().dark) Color(0xFF8D8D93) else Color(0xFF8A8A8E)

@Composable
private fun paywallTertiary(): Color = if (rot().dark) Color(0xFF47474A) else Color(0xFFC4C4C6)

@Composable
private fun paywallFill(): Color = if (rot().dark) Color(0x52787880) else Color(0x29787880)

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
