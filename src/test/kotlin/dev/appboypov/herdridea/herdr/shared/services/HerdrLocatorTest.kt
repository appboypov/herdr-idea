package dev.appboypov.herdridea.herdr.shared.services

import dev.appboypov.herdridea.herdr.shared.models.HerdrLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

class HerdrLocatorTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `given herdr on the login shell PATH, when located, then its path and the shell environment are returned`() {
        val bin = Files.createDirectories(dir.resolve("bin"))
        val herdr = executable(bin.resolve("herdr"), "#!/bin/sh\n")
        val shell = stubShell(bin)

        val location = HerdrLocator(shell.toString()).locate(null)

        location as HerdrLocation.Found
        assertEquals(herdr.toString(), location.path)
        assertEquals("from-login-shell", location.environment["HERDR_IDEA_TEST"])
    }

    @Test
    fun `given no herdr on the login shell PATH, when located, then the searched locations are returned`() {
        val bin = Files.createDirectories(dir.resolve("empty"))
        val shell = stubShell(bin)

        val location = HerdrLocator(shell.toString()).locate(null)

        location as HerdrLocation.Missing
        assertTrue("$bin/herdr" in location.searched)
    }

    @Test
    fun `given a configured path that does not exist, when located, then herdr is missing at that path`() {
        val bin = Files.createDirectories(dir.resolve("bin"))
        executable(bin.resolve("herdr"), "#!/bin/sh\n")
        val configured = dir.resolve("nope/herdr").toString()

        val location = HerdrLocator(stubShell(bin).toString()).locate(configured)

        assertEquals(HerdrLocation.Missing(listOf(configured)), location)
    }

    @Test
    fun `given a configured path that exists, when located, then it wins over the login shell`() {
        val bin = Files.createDirectories(dir.resolve("bin"))
        executable(bin.resolve("herdr"), "#!/bin/sh\n")
        val configured = executable(dir.resolve("custom-herdr"), "#!/bin/sh\n").toString()

        val location = HerdrLocator(stubShell(bin).toString()).locate(configured)

        assertEquals(configured, (location as HerdrLocation.Found).path)
    }

    /** A login shell whose PATH holds only [bin] plus the system directories. */
    private fun stubShell(bin: Path): Path = executable(
        dir.resolve("shell-${bin.fileName}"),
        "#!/bin/sh\nPATH='$bin:/usr/bin:/bin'; HERDR_IDEA_TEST=from-login-shell; export PATH HERDR_IDEA_TEST\nexec /bin/sh -c \"\$2\"\n",
    )

    private fun executable(path: Path, content: String): Path {
        Files.writeString(path, content)
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"))
        return path
    }
}
