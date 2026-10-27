package com.ahmed.rotrun.logic

data class ShieldCopy(val id: String, val headline: String, val body: String, val footer: String?)

object InterventionCopy {
    private val templates = listOf(
        ShieldCopy("stop-scrolling", "Stop scrolling {app}, start living", "The dopamine hit isn't worth what {app} is taking from you.", "Close it."),
        ShieldCopy("read-anything", "Read anything but {app}", "A page, an article, a cereal box.", null),
        ShieldCopy("hydrate", "Hydrate, don't doomscroll {app}", "Drink some water. Stretch.", "{app} can wait an hour."),
        ShieldCopy("cheap-dopamine", "{app} = cheap dopamine", "Real life pays better.", "You know this."),
        ShieldCopy("how-many-hours", "How many hours on {app}?", "{app} has eaten more of your year than you think.", "Go reclaim some."),
        ShieldCopy("future-self", "Your future self begs you to close {app}", "Five years from now, will you wish you'd spent more time on {app}?", "Exactly."),
        ShieldCopy("touch-grass", "Go touch grass, not {app}", "{app} will be there forever.", "The afternoon won't."),
        ShieldCopy("brain-rest", "Your brain needs rest, not {app}", "Screens now means worse sleep later.", "Put it down and wind down."),
        ShieldCopy("how-much-today", "How much {app} do you need today?", "You've had your fill.", "The rest is just scrolling for scrolling's sake."),
        ShieldCopy("zoned-out", "Zoned out on {app} again?", "Twenty minutes will vanish and you won't remember any of them.", "Snap out of it."),
        ShieldCopy("not-again", "Not {app} again", "Even your brain is mad about this one.", "Prove it wrong. Close the app."),
        ShieldCopy("kick-habit", "Kick the {app} habit", "The chains of habit are too weak to be felt until they are too strong to be broken.", "— Samuel Johnson"),
        ShieldCopy("brain-first", "Brain first. {app} last.", "You opened {app} on autopilot.", "Choose something better."),
        ShieldCopy("lock-in", "Lock in, not on {app}", "Discipline beats dopamine.", "We are what we repeatedly do. — Aristotle"),
        ShieldCopy("melting", "{app} is melting your brain", "Every minute you spend on {app} is a minute your future self loses.", "Close it. Do literally anything else."),
    )

    fun displayName(raw: String?): String = raw?.trim()?.takeIf { it.isNotEmpty() } ?: "this app"

    fun forApp(appName: String): ShieldCopy {
        val name = displayName(appName)
        val index = (fnv(name).toUInt() % templates.size.toUInt()).toInt()
        val template = templates[index]
        fun fill(value: String?) = value?.replace("{app}", name)
        return template.copy(headline = fill(template.headline).orEmpty(), body = fill(template.body).orEmpty(), footer = fill(template.footer))
    }

    fun roast(kind: String, name: String): String {
        val who = name.ifBlank { "a friend" }.take(40)
        return when (kind) {
            "bailout" -> "$who already rescued you once today. Opening this anyway is a bold choice."
            "audioSlap" -> "$who queued a nature documentary about your scrolling habits."
            "duel" -> "$who challenged you. More Daily XP before midnight — or own the sloth era."
            else -> "$who left a note. This does not look important."
        }
    }

    fun natureScript(name: String): String {
        val who = name.ifBlank { "a friend" }.take(40)
        return "In tonight's episode: the rare Rotter, friend of $who, approaches a glowing rectangle in search of crumbs of dopamine. Nature is patient. Nature is judging you. Close the app."
    }

    private fun fnv(text: String): Int {
        var hash = 2166136261u
        for (char in text) {
            hash = hash xor char.code.toUInt()
            hash *= 16777619u
        }
        return hash.toInt()
    }
}

data class Faq(val question: String, val answer: String)

object SupportCopy {
    const val EMAIL = "rotrunsupport@gmail.com"
    val faqs = listOf(
        Faq(
            "I want to report a bug",
            "Use Feedback / Bug Report in Support & Feedback, or email $EMAIL with screenshots and steps to reproduce.",
        ),
        Faq(
            "I'm having issues blocking/unblocking apps",
            "Tap Refresh Blocking System. Then re-select apps in Excluded Apps or Block.",
        ),
        Faq(
            "Why do I see 'Too many individual apps selected'?",
            "Apple limits how many individual apps can be shielded. Group them into categories when possible.",
        ),
        Faq(
            "How does brain health work?",
            "Screen time drains health. Focus Mode restores it. Easy / Medium / Hard change how fast that happens.",
        ),
        Faq(
            "Why are there data loading issues in the app?",
            "Screen Time reports can take a moment to sync. Pull to refresh or reopen Stats.",
        ),
        Faq(
            "I have a feature request",
            "Send it through Feedback / Bug Report (choose Feedback), or email $EMAIL. We read every one.",
        ),
    )
}

data class BlockerPreset(val title: String, val subtitle: String, val tags: List<String>, val start: Int, val end: Int, val days: List<Int>)

object Presets {
    val catalog = listOf(
        BlockerPreset("Bedtime", "Power down for sleep", listOf("10pm-7am", "Every Day"), 22, 7, (0..6).toList()),
        BlockerPreset("Touch Grass", "Weekend brain reset", listOf("10am-2pm", "Weekends"), 10, 14, listOf(0, 6)),
        BlockerPreset("Evening Wind Down", "Wind down for bed", listOf("8pm-11pm", "Every Day"), 20, 23, (0..6).toList()),
        BlockerPreset("Mindful Morning", "Start the day clear", listOf("6am-9am", "Every Day"), 6, 9, (0..6).toList()),
        BlockerPreset("Dinner Time", "Eat without distractions", listOf("6pm-8pm", "Weekdays"), 18, 20, listOf(1, 2, 3, 4, 5)),
    )
}
