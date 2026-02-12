package com.manjee.linkops.domain.usecase.diagnostics

import com.manjee.linkops.domain.model.CollisionGroup
import com.manjee.linkops.domain.model.CollisionReport
import com.manjee.linkops.domain.model.CollisionSeverity
import com.manjee.linkops.domain.model.SchemeRegistration
import com.manjee.linkops.domain.repository.CollisionRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fake CollisionRepository for testing
 */
class FakeCollisionRepository : CollisionRepository {
    var resultToReturn: Result<CollisionReport> = Result.success(
        CollisionReport(
            collisionGroups = emptyList(),
            totalRegistrations = 0,
            scannedPackages = 0
        )
    )

    var lastDeviceSerial: String? = null

    override suspend fun detectCollisions(deviceSerial: String): Result<CollisionReport> {
        lastDeviceSerial = deviceSerial
        return resultToReturn
    }
}

class DetectCollisionsUseCaseTest {

    private val fakeRepository = FakeCollisionRepository()
    private val useCase = DetectCollisionsUseCase(fakeRepository)

    @Test
    fun `invoke should delegate to repository with correct device serial`() = runTest {
        useCase("device-123")
        assertEquals("device-123", fakeRepository.lastDeviceSerial)
    }

    @Test
    fun `invoke should return success with collision report`() = runTest {
        val registrations = listOf(
            SchemeRegistration(
                packageName = "com.app.one",
                activityName = "com.app.one/.MainActivity",
                scheme = "https",
                host = "example.com",
                path = null,
                pathPrefix = null,
                pathPattern = null,
                autoVerify = true
            ),
            SchemeRegistration(
                packageName = "com.app.two",
                activityName = "com.app.two/.DeepLinkActivity",
                scheme = "https",
                host = "example.com",
                path = null,
                pathPrefix = null,
                pathPattern = null,
                autoVerify = false
            )
        )

        val expectedReport = CollisionReport(
            collisionGroups = listOf(
                CollisionGroup(
                    pattern = "https://example.com",
                    severity = CollisionSeverity.CRITICAL,
                    registrations = registrations
                )
            ),
            totalRegistrations = 5,
            scannedPackages = 10
        )

        fakeRepository.resultToReturn = Result.success(expectedReport)

        val result = useCase("device-123")
        assertTrue(result.isSuccess, "Should return success")

        val report = result.getOrNull()!!
        assertEquals(1, report.collisionGroups.size)
        assertEquals(1, report.criticalCount)
        assertEquals(0, report.warningCount)
        assertTrue(report.hasCollisions)
        assertEquals(10, report.scannedPackages)
        assertEquals(5, report.totalRegistrations)
    }

    @Test
    fun `invoke should return failure when repository fails`() = runTest {
        fakeRepository.resultToReturn = Result.failure(RuntimeException("ADB connection lost"))

        val result = useCase("device-123")
        assertTrue(result.isFailure, "Should return failure")
        assertEquals("ADB connection lost", result.exceptionOrNull()?.message)
    }

    @Test
    fun `invoke should return report with no collisions`() = runTest {
        val emptyReport = CollisionReport(
            collisionGroups = emptyList(),
            totalRegistrations = 20,
            scannedPackages = 15
        )
        fakeRepository.resultToReturn = Result.success(emptyReport)

        val result = useCase("device-123")
        assertTrue(result.isSuccess)

        val report = result.getOrNull()!!
        assertTrue(!report.hasCollisions, "Should have no collisions")
        assertEquals(0, report.criticalCount)
        assertEquals(0, report.warningCount)
        assertEquals(15, report.scannedPackages)
        assertEquals(20, report.totalRegistrations)
    }

    @Test
    fun `collision report should correctly count critical and warning groups`() = runTest {
        val report = CollisionReport(
            collisionGroups = listOf(
                CollisionGroup(
                    pattern = "https://example.com",
                    severity = CollisionSeverity.CRITICAL,
                    registrations = listOf(
                        SchemeRegistration("com.a", "com.a/.A", "https", "example.com", null, null, null),
                        SchemeRegistration("com.b", "com.b/.B", "https", "example.com", null, null, null)
                    )
                ),
                CollisionGroup(
                    pattern = "myapp://*",
                    severity = CollisionSeverity.WARNING,
                    registrations = listOf(
                        SchemeRegistration("com.c", "com.c/.C", "myapp", null, null, null, null),
                        SchemeRegistration("com.d", "com.d/.D", "myapp", null, "/x", null, null)
                    )
                ),
                CollisionGroup(
                    pattern = "https://test.com",
                    severity = CollisionSeverity.CRITICAL,
                    registrations = listOf(
                        SchemeRegistration("com.e", "com.e/.E", "https", "test.com", null, null, null),
                        SchemeRegistration("com.f", "com.f/.F", "https", "test.com", null, null, null)
                    )
                )
            ),
            totalRegistrations = 50,
            scannedPackages = 25
        )

        fakeRepository.resultToReturn = Result.success(report)

        val result = useCase("device-123")
        val actual = result.getOrNull()!!
        assertEquals(2, actual.criticalCount, "Should have 2 critical groups")
        assertEquals(1, actual.warningCount, "Should have 1 warning group")
        assertTrue(actual.hasCollisions)
    }

    @Test
    fun `collision group should report correct app count and package names`() {
        val group = CollisionGroup(
            pattern = "https://example.com",
            severity = CollisionSeverity.CRITICAL,
            registrations = listOf(
                SchemeRegistration("com.a", "com.a/.A1", "https", "example.com", null, null, null),
                SchemeRegistration("com.a", "com.a/.A2", "https", "example.com", "/path", null, null),
                SchemeRegistration("com.b", "com.b/.B1", "https", "example.com", null, null, null)
            )
        )

        assertEquals(2, group.appCount, "Should count distinct packages")
        assertEquals(listOf("com.a", "com.b"), group.packageNames)
    }

    @Test
    fun `collision group should detect verified apps`() {
        val groupWithVerified = CollisionGroup(
            pattern = "https://example.com",
            severity = CollisionSeverity.CRITICAL,
            registrations = listOf(
                SchemeRegistration("com.a", "com.a/.A", "https", "example.com", null, null, null, autoVerify = true),
                SchemeRegistration("com.b", "com.b/.B", "https", "example.com", null, null, null, autoVerify = false)
            )
        )

        assertTrue(groupWithVerified.hasVerifiedApp, "Should detect verified app")

        val groupWithoutVerified = CollisionGroup(
            pattern = "myapp://*",
            severity = CollisionSeverity.WARNING,
            registrations = listOf(
                SchemeRegistration("com.a", "com.a/.A", "myapp", null, null, null, null, autoVerify = false),
                SchemeRegistration("com.b", "com.b/.B", "myapp", null, null, null, null, autoVerify = false)
            )
        )

        assertTrue(!groupWithoutVerified.hasVerifiedApp, "Should not detect verified app")
    }

    @Test
    fun `scheme registration should generate correct uri pattern`() {
        val reg1 = SchemeRegistration("com.a", "com.a/.A", "https", "example.com", "/products", null, null)
        assertEquals("https://example.com/products", reg1.uriPattern)

        val reg2 = SchemeRegistration("com.a", "com.a/.A", "https", "example.com", null, "/api/", null)
        assertEquals("https://example.com/api/*", reg2.uriPattern)

        val reg3 = SchemeRegistration("com.a", "com.a/.A", "myapp", null, null, null, null)
        assertEquals("myapp://*/*", reg3.uriPattern)

        val reg4 = SchemeRegistration("com.a", "com.a/.A", "https", "example.com", null, null, "/.*")
        assertEquals("https://example.com/.*", reg4.uriPattern)
    }

    @Test
    fun `scheme registration should generate correct collision key`() {
        val reg1 = SchemeRegistration("com.a", "com.a/.A", "https", "example.com", "/products", null, null)
        assertEquals("https://example.com", reg1.collisionKey)

        val reg2 = SchemeRegistration("com.a", "com.a/.A", "myapp", null, null, null, null)
        assertEquals("myapp://*", reg2.collisionKey)
    }
}
