package dev.appboypov.herdridea.ghostty.shared.exceptions

/** libghostty-vt could not be loaded, or it does not offer what the plugin was built against. */
class GhosttyException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
