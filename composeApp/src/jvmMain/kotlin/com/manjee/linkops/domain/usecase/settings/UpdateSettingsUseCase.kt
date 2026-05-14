package com.manjee.linkops.domain.usecase.settings

import com.manjee.linkops.domain.model.AppSettings
import com.manjee.linkops.domain.repository.SettingsRepository

class UpdateSettingsUseCase(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(
        transform: (AppSettings) -> AppSettings
    ): Result<AppSettings> = settingsRepository.update(transform)
}
