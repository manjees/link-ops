package com.manjee.linkops.domain.usecase.device

import com.manjee.linkops.domain.model.Device
import com.manjee.linkops.domain.repository.DeviceRepository

/**
 * UseCase for one-shot device refresh (manual button / keyboard shortcut).
 *
 * Distinct from [DetectDevicesUseCase], which exposes a polling Flow.
 */
class RefreshDevicesUseCase(
    private val deviceRepository: DeviceRepository
) {
    suspend operator fun invoke(): Result<List<Device>> {
        return deviceRepository.refreshDevices()
    }
}
