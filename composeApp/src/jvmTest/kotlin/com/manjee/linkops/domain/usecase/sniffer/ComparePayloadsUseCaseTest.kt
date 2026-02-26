package com.manjee.linkops.domain.usecase.sniffer

import com.manjee.linkops.domain.model.BundleExtra
import com.manjee.linkops.domain.model.BundleExtraType
import com.manjee.linkops.domain.model.IntentPayload
import com.manjee.linkops.domain.model.ParsingMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComparePayloadsUseCaseTest {

    private val useCase = ComparePayloadsUseCase()

    private fun createPayload(extras: Map<String, BundleExtra>): IntentPayload {
        return IntentPayload(
            action = "android.intent.action.VIEW",
            dataUri = "https://example.com",
            categories = emptyList(),
            flags = 0,
            component = "com.example/.Main",
            extras = extras,
            rawOutput = "test",
            timestamp = System.currentTimeMillis(),
            parsingMethod = ParsingMethod.REGEX
        )
    }

    private fun stringExtra(key: String, value: String): BundleExtra {
        return BundleExtra(key = key, value = value, type = BundleExtraType.STRING)
    }

    private fun intExtra(key: String, value: String): BundleExtra {
        return BundleExtra(key = key, value = value, type = BundleExtraType.INT)
    }

    @Test
    fun `invoke should detect added extras`() {
        val oldPayload = createPayload(
            mapOf("key1" to stringExtra("key1", "value1"))
        )
        val newPayload = createPayload(
            mapOf(
                "key1" to stringExtra("key1", "value1"),
                "key2" to stringExtra("key2", "value2")
            )
        )

        val result = useCase(oldPayload, newPayload)

        assertEquals(1, result.addedExtras.size)
        assertTrue(result.addedExtras.containsKey("key2"))
        assertEquals("value2", result.addedExtras["key2"]?.value)
    }

    @Test
    fun `invoke should detect removed extras`() {
        val oldPayload = createPayload(
            mapOf(
                "key1" to stringExtra("key1", "value1"),
                "key2" to stringExtra("key2", "value2")
            )
        )
        val newPayload = createPayload(
            mapOf("key1" to stringExtra("key1", "value1"))
        )

        val result = useCase(oldPayload, newPayload)

        assertEquals(1, result.removedExtras.size)
        assertTrue(result.removedExtras.containsKey("key2"))
    }

    @Test
    fun `invoke should detect changed extras`() {
        val oldPayload = createPayload(
            mapOf("key1" to stringExtra("key1", "old_value"))
        )
        val newPayload = createPayload(
            mapOf("key1" to stringExtra("key1", "new_value"))
        )

        val result = useCase(oldPayload, newPayload)

        assertEquals(1, result.changedExtras.size)
        assertTrue(result.changedExtras.containsKey("key1"))
        assertEquals("old_value", result.changedExtras["key1"]?.first?.value)
        assertEquals("new_value", result.changedExtras["key1"]?.second?.value)
    }

    @Test
    fun `invoke should detect unchanged extras`() {
        val oldPayload = createPayload(
            mapOf("key1" to stringExtra("key1", "same_value"))
        )
        val newPayload = createPayload(
            mapOf("key1" to stringExtra("key1", "same_value"))
        )

        val result = useCase(oldPayload, newPayload)

        assertEquals(1, result.unchangedExtras.size)
        assertTrue(result.unchangedExtras.containsKey("key1"))
        assertTrue(result.addedExtras.isEmpty())
        assertTrue(result.removedExtras.isEmpty())
        assertTrue(result.changedExtras.isEmpty())
    }

    @Test
    fun `invoke should handle empty extras on both sides`() {
        val oldPayload = createPayload(emptyMap())
        val newPayload = createPayload(emptyMap())

        val result = useCase(oldPayload, newPayload)

        assertTrue(result.addedExtras.isEmpty())
        assertTrue(result.removedExtras.isEmpty())
        assertTrue(result.changedExtras.isEmpty())
        assertTrue(result.unchangedExtras.isEmpty())
    }

    @Test
    fun `invoke should handle all additions`() {
        val oldPayload = createPayload(emptyMap())
        val newPayload = createPayload(
            mapOf(
                "key1" to stringExtra("key1", "value1"),
                "key2" to intExtra("key2", "42")
            )
        )

        val result = useCase(oldPayload, newPayload)

        assertEquals(2, result.addedExtras.size)
        assertTrue(result.removedExtras.isEmpty())
        assertTrue(result.changedExtras.isEmpty())
        assertTrue(result.unchangedExtras.isEmpty())
    }

    @Test
    fun `invoke should handle all removals`() {
        val oldPayload = createPayload(
            mapOf(
                "key1" to stringExtra("key1", "value1"),
                "key2" to intExtra("key2", "42")
            )
        )
        val newPayload = createPayload(emptyMap())

        val result = useCase(oldPayload, newPayload)

        assertTrue(result.addedExtras.isEmpty())
        assertEquals(2, result.removedExtras.size)
        assertTrue(result.changedExtras.isEmpty())
        assertTrue(result.unchangedExtras.isEmpty())
    }

    @Test
    fun `invoke should handle complex mixed scenario`() {
        val oldPayload = createPayload(
            mapOf(
                "unchanged" to stringExtra("unchanged", "same"),
                "changed" to stringExtra("changed", "old"),
                "removed" to stringExtra("removed", "gone")
            )
        )
        val newPayload = createPayload(
            mapOf(
                "unchanged" to stringExtra("unchanged", "same"),
                "changed" to stringExtra("changed", "new"),
                "added" to stringExtra("added", "fresh")
            )
        )

        val result = useCase(oldPayload, newPayload)

        assertEquals(1, result.addedExtras.size)
        assertTrue(result.addedExtras.containsKey("added"))
        assertEquals(1, result.removedExtras.size)
        assertTrue(result.removedExtras.containsKey("removed"))
        assertEquals(1, result.changedExtras.size)
        assertTrue(result.changedExtras.containsKey("changed"))
        assertEquals(1, result.unchangedExtras.size)
        assertTrue(result.unchangedExtras.containsKey("unchanged"))
    }

    @Test
    fun `invoke should detect type change as changed extra`() {
        val oldPayload = createPayload(
            mapOf("key1" to stringExtra("key1", "42"))
        )
        val newPayload = createPayload(
            mapOf("key1" to intExtra("key1", "42"))
        )

        val result = useCase(oldPayload, newPayload)

        // BundleExtra with different type is considered changed
        assertEquals(1, result.changedExtras.size)
        assertEquals(BundleExtraType.STRING, result.changedExtras["key1"]?.first?.type)
        assertEquals(BundleExtraType.INT, result.changedExtras["key1"]?.second?.type)
    }
}
