package com.manjee.linkops.domain.usecase.diagnostics

import com.manjee.linkops.domain.model.AasaValidation
import com.manjee.linkops.domain.repository.AasaRepository

class ValidateAasaUseCase(
    private val aasaRepository: AasaRepository
) {
    suspend operator fun invoke(domain: String): Result<AasaValidation> {
        return aasaRepository.validateAasa(domain)
    }
}
