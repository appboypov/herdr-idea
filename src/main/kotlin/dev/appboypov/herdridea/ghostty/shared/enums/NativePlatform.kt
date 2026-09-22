package dev.appboypov.herdridea.ghostty.shared.enums

/** A platform the plugin ships a libghostty-vt build for. */
enum class NativePlatform(val id: String, val libraryFileName: String) {
    DARWIN_AARCH64("darwin-aarch64", "libghostty-vt.dylib"),
    DARWIN_X86_64("darwin-x86_64", "libghostty-vt.dylib"),
    LINUX_X86_64("linux-x86_64", "libghostty-vt.so"),
    LINUX_AARCH64("linux-aarch64", "libghostty-vt.so");

    /** Classpath location of this platform's library inside the plugin jar. */
    val resourcePath: String get() = "native/$id/$libraryFileName"

    companion object {
        /** The platform for JVM `os.name` and `os.arch` values, or null when unsupported. */
        fun resolve(osName: String, osArch: String): NativePlatform? {
            val os = osName.lowercase()
            val arm = osArch.lowercase() in setOf("aarch64", "arm64")
            val x64 = osArch.lowercase() in setOf("x86_64", "amd64", "x64")
            return when {
                os.startsWith("mac") && arm -> DARWIN_AARCH64
                os.startsWith("mac") && x64 -> DARWIN_X86_64
                os.startsWith("linux") && arm -> LINUX_AARCH64
                os.startsWith("linux") && x64 -> LINUX_X86_64
                else -> null
            }
        }

        fun current(): NativePlatform? = resolve(System.getProperty("os.name"), System.getProperty("os.arch"))
    }
}
