package dev.appboypov.herdridea.core.services

import com.intellij.openapi.diagnostic.Logger

/**
 * The plugin's one logging API over the IDE logger.
 *
 * [error] reaches the IDE's error reporter (IDE Internal Errors), which names this plugin;
 * that is the plugin's crash reporting. Fields are appended as `key=value` for searching idea.log.
 */
class HerdrLog private constructor(private val logger: Logger) {
    fun debug(message: String, vararg fields: Pair<String, Any?>) {
        if (logger.isDebugEnabled) logger.debug(format(message, fields))
    }

    fun info(message: String, vararg fields: Pair<String, Any?>) = logger.info(format(message, fields))

    fun warn(message: String, error: Throwable? = null, vararg fields: Pair<String, Any?>) =
        logger.warn(format(message, fields), error)

    fun error(message: String, error: Throwable? = null, vararg fields: Pair<String, Any?>) =
        logger.error(format(message, fields), error)

    private fun format(message: String, fields: Array<out Pair<String, Any?>>): String =
        if (fields.isEmpty()) message else fields.joinToString(" ", "$message ") { (key, value) -> "$key=$value" }

    companion object {
        fun of(owner: Class<*>) = HerdrLog(Logger.getInstance(owner))
    }
}
