package com.bose.expensetracker.domain.model

data class Household(
    val id: String,
    val name: String,
    val memberUids: List<String>,
    val inviteCode: String,
    val createdAt: Long
)
