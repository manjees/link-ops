package com.manjee.linkops.domain.usecase.sniffer

import com.manjee.linkops.domain.model.IntentPayload
import com.manjee.linkops.domain.model.PayloadComparison

/**
 * Compares two intent payloads and produces a diff of their extras
 */
class ComparePayloadsUseCase {

    /**
     * Compares extras between two intent payloads
     *
     * @param oldPayload The baseline payload
     * @param newPayload The payload to compare against
     * @return PayloadComparison containing added, removed, changed, and unchanged extras
     */
    operator fun invoke(oldPayload: IntentPayload, newPayload: IntentPayload): PayloadComparison {
        val oldExtras = oldPayload.extras
        val newExtras = newPayload.extras

        val addedExtras = newExtras.filterKeys { it !in oldExtras }
        val removedExtras = oldExtras.filterKeys { it !in newExtras }
        val unchangedExtras = oldExtras.filterKeys { key ->
            key in newExtras && oldExtras[key] == newExtras[key]
        }
        val changedExtras = oldExtras.filterKeys { key ->
            key in newExtras && oldExtras[key] != newExtras[key]
        }.mapValues { (key, oldValue) -> Pair(oldValue, newExtras.getValue(key)) }

        return PayloadComparison(
            addedExtras = addedExtras,
            removedExtras = removedExtras,
            changedExtras = changedExtras,
            unchangedExtras = unchangedExtras
        )
    }
}
