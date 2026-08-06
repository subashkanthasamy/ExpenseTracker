package com.bose.expensetracker.di

import com.bose.expensetracker.domain.usecase.smsimport.SmsCategoryMatcher
import com.bose.expensetracker.domain.usecase.smsimport.SmsTransactionParser
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val sharedModule = module {
    // SMS parsing (pure Kotlin, shared across platforms)
    singleOf(::SmsCategoryMatcher)
    singleOf(::SmsTransactionParser)
}
