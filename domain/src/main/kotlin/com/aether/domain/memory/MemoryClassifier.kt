package com.aether.domain.memory

/**
 * Builds the classification prompt and parses the model's answer.
 *
 * Pure functions with no dependency on any runtime, so the whole
 * prompt-and-parse contract is testable on the JVM in milliseconds without a
 * model present. This is the part most likely to need iteration, and it would
 * be miserable to tune through a device test.
 *
 * **The model is never asked for JSON.** A 1.5B model emits malformed JSON
 * often enough to be a real source of failure. Instead it is asked for one word
 * from a closed set, and [parse] validates against [MemoryType], falling back to
 * [MemoryType.UNCLASSIFIED] on anything unexpected. Robustness comes from the
 * output space being tiny, not from asking the model nicely.
 */
object MemoryClassifier {

    /** Small on purpose: one label is a handful of tokens, and this bounds latency. */
    const val MAX_TOKENS: Int = 8

    /**
     * Labels the model may choose from.
     *
     * Derived from [MemoryType] rather than hardcoded, so adding a type cannot
     * silently leave the prompt out of date.
     */
    private val selectableTypes: List<MemoryType> =
        MemoryType.entries.filterNot { it == MemoryType.UNCLASSIFIED }

    fun buildPrompt(content: String): String {
        val labels = selectableTypes.joinToString("\n") { "- ${it.name}" }
        return """
            Classify the note into exactly one category.

            Categories:
            $labels

            Rules:
            - Reply with the category name only.
            - No explanation, no punctuation, no extra words.

            Note: ${content.trim()}

            Category:
        """.trimIndent()
    }

    /**
     * Reads a [MemoryType] out of the model's raw output.
     *
     * Tolerant of the ways a small model deviates while still being right —
     * lowercase, surrounding whitespace, a trailing full stop, a leading
     * "Category:", or a short preamble before the label. Genuinely unrecognised
     * output becomes [MemoryType.UNCLASSIFIED], which leaves the memory in the
     * queue rather than mislabelling it.
     */
    fun parse(rawOutput: String): MemoryType {
        val normalised = rawOutput
            .substringAfter("Category:", rawOutput)
            .uppercase()
            .replace(NON_LETTER, " ")
            .trim()

        if (normalised.isEmpty()) return MemoryType.UNCLASSIFIED

        val words = normalised.split(WHITESPACE).filter { it.isNotBlank() }

        // Exact match on the first word is the overwhelmingly common case.
        selectableTypes.firstOrNull { it.name == words.firstOrNull() }?.let { return it }

        // Otherwise accept a single label appearing anywhere. Requiring exactly
        // one match matters: output naming two categories is genuinely
        // ambiguous, and guessing between them would be worse than deferring.
        val mentioned = selectableTypes.filter { type -> words.any { it == type.name } }
        return mentioned.singleOrNull() ?: MemoryType.UNCLASSIFIED
    }

    private val NON_LETTER = Regex("[^A-Z]")
    private val WHITESPACE = Regex("\\s+")
}
