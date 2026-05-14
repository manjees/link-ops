package com.manjee.linkops.domain.usecase.settings

import com.manjee.linkops.domain.model.AppSettings
import com.manjee.linkops.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

class ObserveSettingsUseCase(
    private val settingsRepository: SettingsRepository
) {
    operator fun invoke(): Flow<AppSettings> = settingsRepository.observeSettings()
}
