package com.ahmed.rotrun.logic

import com.ahmed.rotrun.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow

enum class CatalogStatus { Loading, Ready, Unavailable }

data class PlanQuote(
    val id: String,
    val formattedPrice: String,
    val priceMicros: Long,
    val regularFormatted: String,
    val regularMicros: Long,
    val introPresent: Boolean,
    val renewalFormatted: String?,
    val renewalMicros: Long?,
    val offerToken: String,
)

object PlayCatalog {
    val status = MutableStateFlow(CatalogStatus.Loading)
    val plans = MutableStateFlow<Map<String, PlanQuote>>(emptyMap())
    val purchasing = MutableStateFlow(false)

    fun quote(id: String): PlanQuote? = plans.value[id]
}

object PaywallGate {
    val shows: Boolean get() = BuildConfig.SHOW_PAYWALL
}

object FreeTier {
    const val dailyMessageCap = 3
    const val limitMessage = "You’ve used today’s 3 chats with Rot. Back tomorrow, or get Premium."
    const val voiceMessage = "Voice is part of RotRun Premium."
    const val serverChatDone = "You’ve used today’s chats with Rot. Back tomorrow."
    const val hourlyMessage = "You’ve used your 15 chats with Rot this hour."
    const val tryAgain = "Rot couldn’t answer just now. Try again."
    const val voiceWrapUp = "Wrapping up in 15 seconds"

    fun periodMessage(day: String): String = "You’ve used your chats with Rot for this period. Back on $day."
}

object Products {
    const val MONTHLY = "com.ahmed.rotrun.subscription.monthly"
    const val YEARLY = "com.ahmed.rotrun.subscription.yearly"
    const val YEARLY70 = "com.ahmed.rotrun.subscription.yearly70"
    const val LIFETIME = "com.ahmed.rotrun.lifetime"
    val all = listOf(MONTHLY, YEARLY, YEARLY70, LIFETIME)
    val subscriptions = listOf(MONTHLY, YEARLY, YEARLY70)

    fun planName(id: String?): String = when (id) {
        MONTHLY -> "Monthly"
        YEARLY -> "Yearly"
        YEARLY70 -> "Yearly offer"
        LIFETIME -> "Lifetime"
        else -> "Not subscribed"
    }

    fun periodLabel(id: String): String = when (id) {
        YEARLY, YEARLY70 -> "/ year"
        MONTHLY -> "/ month"
        LIFETIME -> " once"
        else -> ""
    }
}
