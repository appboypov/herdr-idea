package dev.appboypov.herdridea.herdr.shared.models

/**
 * What one pass over a Herdr `config.toml` produced.
 *
 * [skipped] holds every configured key string that did not become a binding, so the caller can log
 * it: the grammar rejected it, or Herdr disabled it (it conflicts with a binding that came first,
 * it repeats the prefix key, or it is a bare printable key that would swallow typing). Herdr keeps
 * running on the rest of the config, so a skipped entry never fails the parse.
 */
data class HerdrKeymapParseResult(val keymap: HerdrKeymap, val skipped: List<String>)
