package com.bose.expensetracker.di

import com.bose.expensetracker.data.repository.AuthRepositoryImpl
import com.bose.expensetracker.data.repository.CategoryRepositoryImpl
import com.bose.expensetracker.data.repository.ExpenseRepositoryImpl
import com.bose.expensetracker.data.repository.HouseholdRepositoryImpl
import com.bose.expensetracker.data.repository.NetWorthRepositoryImpl
import com.bose.expensetracker.domain.repository.AuthRepository
import com.bose.expensetracker.domain.repository.CategoryRepository
import com.bose.expensetracker.domain.repository.ExpenseRepository
import com.bose.expensetracker.domain.repository.HouseholdRepository
import com.bose.expensetracker.domain.repository.NetWorthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindHouseholdRepository(impl: HouseholdRepositoryImpl): HouseholdRepository

    @Binds
    @Singleton
    abstract fun bindExpenseRepository(impl: ExpenseRepositoryImpl): ExpenseRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindNetWorthRepository(impl: NetWorthRepositoryImpl): NetWorthRepository
}
