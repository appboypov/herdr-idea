package dev.appboypov.herdridea.core.services

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.HerdrIdeaBundle"

/** The plugin's localized copy. */
object HerdrIdeaBundle : DynamicBundle(BUNDLE) {
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String = getMessage(key, *params)
}
