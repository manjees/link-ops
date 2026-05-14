package com.manjee.linkops.data.repository

import com.manjee.linkops.data.mapper.DeviceMapper
import com.manjee.linkops.domain.model.Device
import com.manjee.linkops.domain.repository.DeviceRepository
import com.manjee.linkops.infrastructure.adb.AdbShellExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.seconds

/**
 * Implementation of DeviceRepository using ADB commands
 */
class DeviceRepositoryImpl(
    private val adbExecutor: AdbShellExecutor,
    private val deviceMapper: DeviceMapper
) : DeviceRepository {

    companion object {
        private val POLLING_INTERVAL = 2.seconds
    }

    override fun observeDevices(): Flow<List<Device>> = flow {
        while (true) {
            val devices = fetchDevices()
            emit(devices)
            delay(POLLING_INTERVAL)
        }
    }.distinctUntilChanged()

    override suspend fun refreshDevices(): Result<List<Device>> {
        return adbExecutor.execute("devices -l")
            .mapCatching { output ->
                deviceMapper.parseDeviceList(output)
                    .map { device -> enrichDeviceInfo(device) }
            }
    }

    override suspend fun getDevice(serialNumber: String): Result<Device> {
        return adbExecutor.execute("devices -l")
            .mapCatching { output ->
                val devices = deviceMapper.parseDeviceList(output)
                val device = devices.firstOrNull { it.serialNumber == serialNumber }
                    ?: throw DeviceNotFoundException(serialNumber)

                // Fetch additional device info
                enrichDeviceInfo(device)
            }
    }

    override suspend fun isAdbAvailable(): Boolean {
        return adbExecutor.execute("version").isSuccess
    }

    private suspend fun fetchDevices(): List<Device> {
        return adbExecutor.execute("devices -l")
            .map { output -> deviceMapper.parseDeviceList(output) }
            .getOrElse { emptyList() }
            .map { device -> enrichDeviceInfo(device) }
    }

    private suspend fun enrichDeviceInfo(device: Device): Device {
        if (device.state != Device.DeviceState.ONLINE) {
            return device
        }

        val osVersion = adbExecutor
            .executeOnDevice(device.serialNumber, "getprop ro.build.version.release")
            .getOrElse { "" }
            .trim()

        val sdkLevel = adbExecutor
            .executeOnDevice(device.serialNumber, "getprop ro.build.version.sdk")
            .getOrElse { "" }
            .trim()
            .toIntOrNull() ?: 0

        return device.copy(
            osVersion = osVersion.ifEmpty { "Unknown" },
            sdkLevel = sdkLevel
        )
    }
}

/**
 * Exception thrown when a device is not found
 */
class DeviceNotFoundException(serialNumber: String) :
    Exception("Device not found: $serialNumber")
