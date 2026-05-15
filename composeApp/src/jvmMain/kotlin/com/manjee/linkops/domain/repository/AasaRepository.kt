package com.manjee.linkops.domain.repository

import com.manjee.linkops.domain.model.AasaValidation

interface AasaRepository {
    /**
     * Fetches the AASA file for [domain] (trying `/.well-known/` first then the
     * legacy root path) and validates its content.
     */
    suspend fun validateAasa(domain: String): Result<AasaValidation>
}
