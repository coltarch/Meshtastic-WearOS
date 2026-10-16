/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.core.common.text

/**
 * Chooses which of the user's own quick replies to put in front of them.
 *
 * The rules are small and deterministic: the latest incoming message is classified into an intent, the phrases that
 * answer it move to the front, and everything else keeps the order the phone synced it in. No text is ever generated:
 * every string returned is one of the [replies] the user wrote.
 *
 * Kept pure on purpose — no state, no coroutines, no platform types — so it behaves the same on every target and is
 * testable without a device.
 */
object QuickReplySelector {

    /** What the message in front of the user is asking for. */
    private enum class Intent {
        Arrival,
        Thanks,
        Greeting,
        Question,
        None,
    }

    /** Words that mean "how far away are you". Checked before [QUESTION] so "where are you?" reads as an arrival. */
    private val ARRIVAL_ASKS = listOf("where", "eta", "how far", "how long", "when", "location", "coming", "close")

    private val ARRIVAL_ANSWERS =
        listOf("on my way", "omw", "arriving", "almost there", "be there", "heading out", "5 min", "10 min", "soon")

    private val THANKS_ASKS = listOf("thanks", "thank you", "thx", "cheers", "appreciate", "ty")

    private val THANKS_ANSWERS =
        listOf("welcome", "no problem", "np", "anytime", "sure thing", "happy to help", "you bet")

    private val GREETINGS = listOf("hey", "hi", "hello", "morning", "evening", "howdy")

    /** Interrogatives that are not an arrival question; a literal "?" is treated as one of these. */
    private val QUESTION_ASKS =
        listOf(
            "are you",
            "are we",
            "do you",
            "did you",
            "can you",
            "could you",
            "will you",
            "would you",
            "have you",
            "is it",
            "is there",
            "should i",
            "should we",
            "what",
            "who",
            "why",
            "how",
            "any news",
            "any update",
            "you there",
        )

    /** The three answers a question usually wants, in the order they are offered. */
    private val AFFIRMATIVE = listOf("yes", "yeah", "yep", "yup", "correct", "affirmative", "copy", "roger")

    private val NEGATIVE = listOf("no", "nope", "nah", "negative", "can't", "cannot", "won't")
    private val ASSENT = listOf("sure", "ok", "okay", "got it", "sounds good", "will do", "on it", "understood")

    private val SEPARATORS = Regex("[^\\p{L}\\p{N}]+")

    /**
     * Returns at most [limit] phrases out of [replies], best first.
     *
     * [recentTexts] is the conversation's incoming messages, most recent first — only the first entry is read, since
     * the chips answer the message in front of the user. Only strings from [replies] are ever returned, in the order
     * they appeared there when nothing matches.
     */
    fun select(replies: List<String>, recentTexts: List<String>, limit: Int = 3): List<String> {
        if (limit <= 0) return emptyList()

        val candidates = replies.filter(String::isNotBlank)
        val answers =
            when (recentTexts.firstOrNull()?.let(::intentOf)) {
                Intent.Arrival -> ARRIVAL_ANSWERS
                Intent.Thanks -> THANKS_ANSWERS
                Intent.Greeting -> GREETINGS
                Intent.Question -> AFFIRMATIVE + NEGATIVE + ASSENT
                Intent.None, null -> emptyList()
            }

        val matched = candidates.filter { reply -> answers.any { mention -> mentions(reply, mention) } }
        val rest = candidates.filterNot(matched::contains)

        return (matched + rest).take(limit)
    }

    private fun intentOf(text: String): Intent =
        when {
            ARRIVAL_ASKS.any { mentions(text, it) } -> Intent.Arrival
            THANKS_ASKS.any { mentions(text, it) } -> Intent.Thanks
            GREETINGS.any { mentions(text, it) } -> Intent.Greeting
            text.contains('?') || QUESTION_ASKS.any { mentions(text, it) } -> Intent.Question
            else -> Intent.None
        }

    /**
     * Whether [text] uses [keyword].
     *
     * Phrases and contractions are matched as substrings, an emoji is matched literally, and single words are matched
     * against whole words only — so "no" does not fire on "nobody" and "hi" does not fire on "this".
     */
    private fun mentions(text: String, keyword: String): Boolean =
        when {
            keyword.contains(' ') || keyword.contains('\'') -> text.lowercase().contains(keyword)
            keyword.none(Char::isLetter) -> text.contains(keyword)
            else -> wordsOf(text).contains(keyword)
        }

    private fun wordsOf(text: String): List<String> =
        text.lowercase().split(SEPARATORS).filter(String::isNotEmpty)
}
