package dev.appboypov.herdridea.ghostty.shared.enums

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NativePlatformTest {
    @Test
    fun `given each supported JVM os and arch, when resolved, then the matching bundled library is chosen`() {
        assertEquals("native/darwin-aarch64/libghostty-vt.dylib", NativePlatform.resolve("Mac OS X", "aarch64")?.resourcePath)
        assertEquals("native/darwin-x86_64/libghostty-vt.dylib", NativePlatform.resolve("Mac OS X", "x86_64")?.resourcePath)
        assertEquals("native/linux-x86_64/libghostty-vt.so", NativePlatform.resolve("Linux", "amd64")?.resourcePath)
        assertEquals("native/linux-aarch64/libghostty-vt.so", NativePlatform.resolve("Linux", "aarch64")?.resourcePath)
    }

    @Test
    fun `given Windows, when resolved, then the platform is unsupported`() {
        assertNull(NativePlatform.resolve("Windows 11", "amd64"))
    }

    @Test
    fun `given every platform, when its resource is looked up, then the plugin bundles it`() {
        for (platform in NativePlatform.entries) {
            assertEquals(true, javaClass.classLoader.getResource(platform.resourcePath) != null, platform.id)
        }
    }
}
