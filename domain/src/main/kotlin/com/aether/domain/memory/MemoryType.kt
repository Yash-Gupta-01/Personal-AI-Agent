package com.aether.domain.memory

/**
 * What kind of thing a memory is about.
 *
 * The user is never asked to choose one. Categorising a thought at the moment
 * of capture is exactly the mental work Aether exists to remove, so everything
 * is captured as [UNCLASSIFIED] and classified later by the memory agent.
 *
 * Each type carries a [description] and an [example] because a 1.2B model
 * cannot infer what nine bare uppercase labels mean. Measured on a fixed set
 * of nine obvious notes (`ClassificationAccuracyTest`): bare labels 4/9,
 * plus descriptions 4/9, plus one worked example each 5/9. Both help; neither
 * makes a 1.2B model good at nine-way classification.
 *
 * They live on the enum rather than in the prompt so that adding a type cannot
 * leave the prompt teaching a set that no longer exists — the compiler
 * requires both strings for every new entry.
 *
 * **Declaration order is the model's decision order.** The prompt turns this
 * list into numbered tie-break rules, first match wins, so the most specific
 * types come first and REFLECTION comes last as the catch-all. Reordering
 * these entries changes classification behaviour; it is not cosmetic. (It is
 * safe for storage — a memory's type is persisted by name, never by ordinal.)
 */
enum class MemoryType(val description: String, val example: String) {
    PERSON(
        "another person in the user's life, and news about them",
        "Mum's birthday is on the third of March",
    ),
    FACT(
        "a general fact about the world, technology or how something works",
        "Postgres uses a B-tree index by default",
    ),
    HEALTH(
        "the user's body, sleep, fitness, or medical facts",
        "My resting heart rate has dropped to 58",
    ),
    CAREER(
        "the user's job, role, skills, or which field they work in",
        "My manager suggested I lead the migration",
    ),
    PROJECT(
        "a named app, tool or piece of work the user is building",
        "I am rewriting the invoice tool in Rust",
    ),
    PREFERENCE(
        "a like, a dislike, or a preferred way of doing things",
        "I like my coffee black, no sugar",
    ),
    GOAL(
        "something the user wants to achieve in future",
        "I want to save two lakh by December",
    ),
    HABIT(
        "something the user already does regularly",
        "I journal for ten minutes every night",
    ),
    REFLECTION(
        "how the user felt or is feeling about something",
        "I felt drained after today's standup",
    ),

    /** Captured but not yet classified. Every memory starts here. */
    UNCLASSIFIED("not yet classified", "");
}
