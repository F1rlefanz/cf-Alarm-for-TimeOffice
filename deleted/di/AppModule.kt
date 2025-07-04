package com.github.f1rlefanz.cf_alarmfortimeoffice.di

import android.content.Context
import com.github.f1rlefanz.cf_alarmfortimeoffice.auth.CredentialAuthManager
import com.github.f1rlefanz.cf_alarmfortimeoffice.data.AuthDataStoreRepository
import com.github.f1rlefanz.cf_alarmfortimeoffice.error.ErrorHandler
import com.github.f1rlefanz.cf_alarmfortimeoffice.repository.AlarmRepository
import com.github.f1rlefanz.cf_alarmfortimeoffice.calendar.CalendarRepository
import com.github.f1rlefanz.cf_alarmfortimeoffice.shift.ShiftConfigRepository
import com.github.f1rlefanz.cf_alarmfortimeoffice.service.AlarmManagerService
import com.github.f1rlefanz.cf_alarmfortimeoffice.shift.ShiftRecognitionEngine
import com.github.f1rlefanz.cf_alarmfortimeoffice.usecase.AlarmUseCase
import com.github.f1rlefanz.cf_alarmfortimeoffice.usecase.CalendarAuthUseCase
import com.github.f1rlefanz.cf_alarmfortimeoffice.usecase.ShiftUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAuthDataStoreRepository(
        @ApplicationContext context: Context
    ): AuthDataStoreRepository = AuthDataStoreRepository(context)

    @Provides
    @Singleton
    fun provideCalendarRepository(): CalendarRepository = CalendarRepository()

    @Provides
    @Singleton
    fun provideShiftConfigRepository(
        @ApplicationContext context: Context
    ): ShiftConfigRepository = ShiftConfigRepository(context)

    @Provides
    @Singleton
    fun provideAlarmRepository(
        @ApplicationContext context: Context
    ): AlarmRepository = AlarmRepository(context)

    @Provides
    @Singleton
    fun provideAlarmManagerService(
        @ApplicationContext context: Context
    ): AlarmManagerService = AlarmManagerService(context.applicationContext as android.app.Application)

    @Provides
    @Singleton
    fun provideShiftRecognitionEngine(
        shiftConfigRepository: ShiftConfigRepository
    ): ShiftRecognitionEngine = ShiftRecognitionEngine(shiftConfigRepository)

    @Provides
    @Singleton
    fun provideErrorHandler(): ErrorHandler = ErrorHandler()

    @Provides
    @Singleton
    fun provideCalendarAuthUseCase(
        authDataStoreRepository: AuthDataStoreRepository,
        calendarRepository: CalendarRepository
    ): CalendarAuthUseCase = CalendarAuthUseCase(
        authDataStoreRepository = authDataStoreRepository,
        calendarRepository = calendarRepository
    )

    @Provides
    @Singleton
    fun provideShiftUseCase(
        shiftConfigRepository: ShiftConfigRepository,
        shiftRecognitionEngine: ShiftRecognitionEngine
    ): ShiftUseCase = ShiftUseCase(
        shiftConfigRepository = shiftConfigRepository,
        shiftRecognitionEngine = shiftRecognitionEngine
    )

    @Provides
    @Singleton
    fun provideAlarmUseCase(
        alarmRepository: AlarmRepository,
        alarmManagerService: AlarmManagerService,
        shiftConfigRepository: ShiftConfigRepository
    ): AlarmUseCase = AlarmUseCase(
        alarmRepository = alarmRepository,
        alarmManagerService = alarmManagerService,
        shiftConfigRepository = shiftConfigRepository
    )

    @Provides
    @Singleton
    fun provideCredentialAuthManager(
        @ApplicationContext context: Context
    ): CredentialAuthManager = CredentialAuthManager(context)
}
