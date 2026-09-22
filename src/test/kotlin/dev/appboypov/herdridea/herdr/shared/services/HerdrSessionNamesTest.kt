package dev.appboypov.herdridea.herdr.shared.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HerdrSessionNamesTest {
    private val herdrRule = Regex("[A-Za-z0-9._-]{1,64}")

    @Test
    fun `given one project path, when named twice, then the name is the same`() {
        assertEquals(
            HerdrSessionNames.projectSession("herdr-idea", "/Users/b/Repos/herdr-idea"),
            HerdrSessionNames.projectSession("herdr-idea", "/Users/b/Repos/herdr-idea"),
        )
    }

    @Test
    fun `given two projects with the same name at different paths, when named, then the names differ`() {
        assertNotEquals(
            HerdrSessionNames.projectSession("app", "/Users/b/work/app"),
            HerdrSessionNames.projectSession("app", "/Users/b/play/app"),
        )
    }

    @Test
    fun `given a plain project name, when named, then the name reads idea-slug-hash`() {
        assertTrue(Regex("idea-herdr-idea-[0-9a-f]{6}").matches(HerdrSessionNames.projectSession("Herdr Idea", "/p")))
    }

    @Test
    fun `given long, non-ASCII or dot-only project names, when named, then Herdr accepts the name`() {
        val names = listOf("x".repeat(300), "Café Ünïcødé 日本語", "..", ".", "---", "a b/c\\d")
        for (name in names) {
            val session = HerdrSessionNames.projectSession(name, "/p/$name")
            assertTrue(herdrRule.matches(session), session)
            assertTrue(session != "." && session != "..", session)
        }
    }
}
