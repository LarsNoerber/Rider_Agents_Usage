package io.github.larsnoerber.agentsusage.core.format

import java.util.Locale

private val planSeparatorPattern = Regex("[_-]+")

fun formatSubscriptionPlan(value: String?): String {
    val plan = value?.trim()?.takeIf { it.isNotEmpty() } ?: return "Unknown"
    return when (plan.lowercase(Locale.ROOT)) {
        "unknown" -> "Unknown"
        "free" -> "Free"
        "plus" -> "Plus"
        "pro" -> "Pro"
        "pro_plus", "pro+" -> "Pro+"
        "api_key", "apikey" -> "API key"
        "eap" -> "EAP"
        else -> plan.replace(planSeparatorPattern, " ").split(' ')
            .joinToString(" ") { it.replaceFirstChar { char -> char.titlecase(Locale.ENGLISH) } }
    }
}
