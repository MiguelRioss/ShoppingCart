package db.postgres

import config.Environment
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.*

/** Read-only smoke test against the configured database; never creates or changes users. */
class AdminUsersReadIntegrationTest {
    @Test fun readsUserSummariesFromConfiguredDatabase() {
        val database = Database.fromEnvironment()
        assumeTrue(database != null, "DATABASE_URL is not configured")
        val rows = PostgresUserRepository(requireNotNull(database)).listUserSummaries()
        val configuredAdmins = Environment.get("ADMIN_USER_IDS").orEmpty().split(',').filter { it.isNotBlank() }
        assertTrue(configuredAdmins.isNotEmpty(), "Configure ADMIN_USER_IDS for this integration test")
        assertTrue(rows.any { it.id.toString() in configuredAdmins })
        assertTrue(rows.zipWithNext().all { (a, b) -> a.createdAt >= b.createdAt })
    }
}
