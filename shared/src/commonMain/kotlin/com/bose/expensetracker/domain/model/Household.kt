package com.bose.expensetracker.domain.model

data class Household(
    val id: String,
    val name: String,
    /**
     * Every member including the owner.
     *
     * Denormalised index, not the authority — [ownerUid] and [roles] decide what someone may
     * do. It stays because the "my households" query is `memberUids arrayContains uid`, and
     * Firestore can only prove that query safe against a rule written on this same field.
     */
    val memberUids: List<String>,
    /** Creator. Immutable once written; the only account that may delete the household. */
    val ownerUid: String = "",
    /** Non-owner members: uid -> "member" | "guest". The owner is deliberately absent. */
    val roles: Map<String, String> = emptyMap(),
    val inviteCode: String,
    val createdAt: Long
)
