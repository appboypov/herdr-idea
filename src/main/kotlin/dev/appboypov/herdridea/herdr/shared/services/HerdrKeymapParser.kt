package dev.appboypov.herdridea.herdr.shared.services

import dev.appboypov.herdridea.herdr.shared.enums.HerdrNamedKey
import dev.appboypov.herdridea.herdr.shared.models.HerdrKey
import dev.appboypov.herdridea.herdr.shared.models.HerdrKeyStroke
import dev.appboypov.herdridea.herdr.shared.models.HerdrKeyStroke.Companion.ALT
import dev.appboypov.herdridea.herdr.shared.models.HerdrKeyStroke.Companion.CTRL
import dev.appboypov.herdridea.herdr.shared.models.HerdrKeyStroke.Companion.HYPER
import dev.appboypov.herdridea.herdr.shared.models.HerdrKeyStroke.Companion.SHIFT
import dev.appboypov.herdridea.herdr.shared.models.HerdrKeyStroke.Companion.SUPER
import dev.appboypov.herdridea.herdr.shared.models.HerdrKeymap
import dev.appboypov.herdridea.herdr.shared.models.HerdrKeymapParseResult
import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlTable

/**
 * Reads a Herdr `config.toml` the way Herdr v0.9.0 reads it and reports which keystrokes it binds.
 *
 * Ported from `src/config/keybinds.rs` (grammar, registration, rejection rules) and
 * `src/config/model.rs` (the default binding for every action) at commit `b99002ac`. Herdr resolves
 * its keybinds in two passes, user-configured fields first and built-in defaults second, so a
 * default that collides with something the user bound simply drops out.
 *
 * Navigate-mode bindings (`navigate_pane_left` and friends, `h`/`j`/`k`/`l`/`up`/`down` by default)
 * are reported as direct bindings: Herdr triggers them without its prefix. The keys navigate mode
 * reserves at runtime (esc, enter, tab, back-tab, left, right, `1`-`9`) are not bindings, so they
 * stay out of the keymap; they only block a navigate binding that would take them.
 */
object HerdrKeymapParser {

    /** Parses [configToml]; null, blank or `[keys]`-less input yields Herdr's defaults. */
    fun parse(configToml: String?): HerdrKeymapParseResult {
        val skipped = mutableListOf<String>()
        val input = readKeys(configToml, skipped)
        return build(input, skipped)
    }

    // region parsing the config file

    private fun readKeys(configToml: String?, skipped: MutableList<String>): KeysInput {
        if (configToml.isNullOrBlank()) return KeysInput()
        val document = Toml.parse(configToml)
        document.errors().forEach { skipped.add(it.toString()) }
        // Read every value as Any: a typed tomlj getter throws when the config holds another type.
        val keys = document.get("keys") as? TomlTable ?: return KeysInput()

        val prefix = when (val value = keys.get("prefix")) {
            null -> null
            is String -> value
            else -> {
                skipped.add("keys.prefix")
                null
            }
        }

        val values = LinkedHashMap<String, List<String>>()
        for (field in FIELDS) {
            val name = if (field.name == ZOOM && !keys.contains(ZOOM) && keys.contains(ZOOM_ALIAS)) ZOOM_ALIAS else field.name
            if (!keys.contains(name)) continue
            values[field.name] = bindingValues(keys.get(name), "keys.$name", skipped) ?: continue
        }

        val indexedTable = keys.get(INDEXED) as? TomlTable
        val indexed = LinkedHashMap<String, String>()
        for (name in LEGACY_INDEXED) {
            when (val value = indexedTable?.get(name)) {
                null -> Unit
                is String -> indexed[name] = value
                else -> skipped.add("keys.indexed.$name")
            }
        }

        val commands = mutableListOf<CommandInput>()
        val commandEntries = keys.get("command") as? TomlArray
        for (index in 0 until (commandEntries?.size() ?: 0)) {
            val entry = commandEntries?.get(index) as? TomlTable ?: continue
            val keyStrings = bindingValues(entry.get("key"), "keys.command[$index].key", skipped).orEmpty()
            val command = entry.get("command") as? String
            commands.add(CommandInput(keyStrings, !command.isNullOrBlank()))
        }

        return KeysInput(prefix, values, indexed, indexedTable != null, commands)
    }

    /** Herdr's `BindingConfig`: one key string, or a list of them. */
    private fun bindingValues(value: Any?, label: String, skipped: MutableList<String>): List<String>? = when (value) {
        null -> null
        is String -> listOf(value)
        is TomlArray -> (0 until value.size()).mapNotNull { index -> stringItem(value.get(index), label, skipped) }
        else -> {
            skipped.add(label)
            null
        }
    }

    private fun stringItem(value: Any?, label: String, skipped: MutableList<String>): String? {
        if (value is String) return value
        skipped.add(label)
        return null
    }

    // endregion

    // region resolving bindings

    private fun build(input: KeysInput, skipped: MutableList<String>): HerdrKeymapParseResult {
        val prefixSource = if (input.prefix == null) Source.DEFAULT else Source.USER
        val configuredPrefix = (input.prefix ?: DEFAULT_PREFIX).trim()
        val prefix = parseKeyCombo(configuredPrefix) ?: run {
            skipped.add(configuredPrefix)
            FALLBACK_PREFIX
        }

        val registry = Registry(prefix, prefixSource)
        val navigateRegistry = Registry(prefix, prefixSource)
        registry.reserveDirect(prefix, prefixSource)
        navigateRegistry.reserveDirect(prefix, prefixSource)
        NAVIGATE_RUNTIME_KEYS.forEach { navigateRegistry.reserveDirect(it, Source.DEFAULT) }

        val direct = mutableListOf(prefix)
        val prefixed = mutableListOf<HerdrKeyStroke>()
        val emit: (Binding) -> Unit = { binding ->
            if (binding.prefixed) prefixed.add(binding.stroke) else direct.add(binding.stroke)
        }

        for (source in listOf(Source.USER, Source.DEFAULT)) {
            for (field in FIELDS) {
                val configured = input.values[field.name]
                val fieldSource = if (configured == null) Source.DEFAULT else Source.USER
                if (fieldSource != source) continue
                val values = configured ?: listOf(field.default)
                when (field.kind) {
                    Kind.NAVIGATE -> applyBindings(values, navigateRegistry, source, skipped, ::rejectNavigateBinding, emit)
                    Kind.ACTION -> applyBindings(values, registry, source, skipped, ::rejectBinding, emit)
                    Kind.INDEXED -> {
                        val legacy = field.legacy?.let { input.indexed[it] }.orEmpty()
                        // A legacy [keys.indexed] entry is user configuration and displaces the default.
                        if (source == Source.DEFAULT && legacy.isNotBlank()) continue
                        applyIndexedBindings(values, registry, source, skipped, emit)
                    }
                }
            }

            if (source == (if (input.indexedConfigured) Source.USER else Source.DEFAULT)) {
                for (name in LEGACY_INDEXED) {
                    appendLegacyIndexed(input.indexed[name].orEmpty(), registry, source, skipped, emit)
                }
            }

            if (source == Source.USER) {
                for (command in input.commands) {
                    if (!command.hasCommand) {
                        command.keys.map { it.trim() }.filter { it.isNotEmpty() }.forEach { skipped.add(it) }
                        continue
                    }
                    applyBindings(command.keys, registry, Source.USER, skipped, ::rejectBinding, emit)
                }
            }
        }

        return HerdrKeymapParseResult(HerdrKeymap(prefix, direct, prefixed), skipped)
    }

    private fun applyBindings(
        values: List<String>,
        registry: Registry,
        source: Source,
        skipped: MutableList<String>,
        reject: (Binding, Registry, Source, String, MutableList<String>) -> Boolean,
        emit: (Binding) -> Unit,
    ) {
        for (raw in values) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) continue
            val parsed = parseBindingString(trimmed)
            // A range binding is only valid for the indexed actions.
            if (parsed == null || parsed.isRange) {
                skipped.add(trimmed)
                continue
            }
            val binding = parsed.bindings.first()
            if (reject(binding, registry, source, trimmed, skipped)) continue
            registry.register(binding, source)
            emit(binding)
        }
    }

    private fun applyIndexedBindings(
        values: List<String>,
        registry: Registry,
        source: Source,
        skipped: MutableList<String>,
        emit: (Binding) -> Unit,
    ) {
        for (raw in values) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) continue
            val parsed = parseBindingString(trimmed)
            if (parsed == null) {
                skipped.add(trimmed)
                continue
            }
            for (binding in parsed.bindings) {
                val key = binding.stroke.key
                if (key !is HerdrKey.Char || key.char !in '1'..'9') {
                    skipped.add(trimmed)
                    continue
                }
                if (rejectBinding(binding, registry, source, trimmed, skipped)) continue
                registry.register(binding, source)
                emit(binding)
            }
        }
    }

    /** Herdr's `[keys.indexed]` shortcuts: one modifier combo expanded over the number keys. */
    private fun appendLegacyIndexed(
        label: String,
        registry: Registry,
        source: Source,
        skipped: MutableList<String>,
        emit: (Binding) -> Unit,
    ) {
        if (label.isBlank()) return
        val modifiers = parseModifierCombo(label) ?: run {
            skipped.add(label.trim())
            return
        }
        for (digit in '1'..'9') {
            val binding = Binding(HerdrKeyStroke(HerdrKey.Char(digit), modifiers), prefixed = false)
            val raw = "${label.trim()}+$digit"
            if (rejectBinding(binding, registry, source, raw, skipped)) continue
            registry.register(binding, source)
            emit(binding)
        }
    }

    private fun rejectBinding(
        binding: Binding,
        registry: Registry,
        source: Source,
        raw: String,
        skipped: MutableList<String>,
    ): Boolean {
        if (binding.prefixed && registry.prefixIsReserved(binding.stroke)) {
            if (!silentDefault(source, registry.prefixSource)) skipped.add(raw)
            return true
        }
        val conflict = registry.conflict(binding)
        if (conflict != null) {
            if (!silentDefault(source, conflict)) skipped.add(raw)
            return true
        }
        if (!binding.prefixed && isUnmodifiedPrintable(binding.stroke)) {
            skipped.add(raw)
            return true
        }
        return false
    }

    private fun rejectNavigateBinding(
        binding: Binding,
        registry: Registry,
        source: Source,
        raw: String,
        skipped: MutableList<String>,
    ): Boolean {
        if (binding.prefixed || binding.stroke.key == ESC) {
            skipped.add(raw)
            return true
        }
        val conflict = registry.conflict(binding)
        if (conflict != null) {
            if (!silentDefault(source, conflict)) skipped.add(raw)
            return true
        }
        return false
    }

    /** Herdr drops a default that a user binding already claimed without warning about it. */
    private fun silentDefault(source: Source, owner: Source): Boolean =
        source == Source.DEFAULT && owner == Source.USER

    /** Herdr's `is_unmodified_printable`: a bare printable key would swallow typing. */
    private fun isUnmodifiedPrintable(stroke: HerdrKeyStroke): Boolean {
        val key = stroke.key
        return key is HerdrKey.Char && !Character.isISOControl(key.char) && stroke.modifiers and SHIFT.inv() == 0
    }

    // endregion

    // region the key grammar

    /** Herdr's `parse_binding_string`: an optional `prefix+`, then a key combo or a `1..9` range. */
    private fun parseBindingString(raw: String): ParsedBinding? {
        val trimmed = raw.trim()
        val prefixed = trimmed.startsWith(PREFIX_TOKEN)
        val body = if (prefixed) trimmed.substring(PREFIX_TOKEN.length) else trimmed

        val rangeModifiers = parseRangeModifiers(body)
        if (rangeModifiers != null) {
            val bindings = ('1'..'9').map { Binding(HerdrKeyStroke(HerdrKey.Char(it), rangeModifiers), prefixed) }
            return ParsedBinding(bindings, isRange = true)
        }

        val stroke = parseKeyCombo(body) ?: return null
        return ParsedBinding(listOf(Binding(stroke, prefixed)), isRange = false)
    }

    /** Herdr's `parse_key_combo`. */
    private fun parseKeyCombo(text: String): HerdrKeyStroke? {
        var modifiers = 0
        var keyToken: String? = null
        for (part in text.split('+')) {
            val trimmed = part.trim()
            if (trimmed.isEmpty()) return null
            val modifier = parseModifierToken(trimmed)
            when {
                modifier != null -> modifiers = modifiers or modifier
                keyToken != null -> return null
                else -> keyToken = trimmed
            }
        }

        val token = keyToken ?: return null
        val single = token.singleOrNull()
        val key = when (val lower = token.lowercase()) {
            "space" -> HerdrKey.Char(' ')
            "enter", "return" -> named(HerdrNamedKey.ENTER)
            "esc", "escape" -> named(HerdrNamedKey.ESC)
            "tab" -> if (modifiers and SHIFT != 0) {
                modifiers = modifiers and SHIFT.inv()
                named(HerdrNamedKey.BACK_TAB)
            } else {
                named(HerdrNamedKey.TAB)
            }

            "backspace", "bs" -> named(HerdrNamedKey.BACKSPACE)
            "left" -> named(HerdrNamedKey.LEFT)
            "right" -> named(HerdrNamedKey.RIGHT)
            "up" -> named(HerdrNamedKey.UP)
            "down" -> named(HerdrNamedKey.DOWN)
            "minus" -> HerdrKey.Char('-')
            "comma" -> HerdrKey.Char(',')
            "period" -> HerdrKey.Char('.')
            "slash" -> HerdrKey.Char('/')
            "backslash" -> HerdrKey.Char('\\')
            "quote" -> HerdrKey.Char('\'')
            "double_quote", "double-quote" -> HerdrKey.Char('"')
            "semicolon" -> HerdrKey.Char(';')
            "colon" -> HerdrKey.Char(':')
            "percent" -> HerdrKey.Char('%')
            "ampersand" -> HerdrKey.Char('&')
            "backtick" -> HerdrKey.Char('`')
            "plus" -> HerdrKey.Char('+')
            else -> when {
                single != null -> if (single in 'A'..'Z') {
                    modifiers = modifiers or SHIFT
                    HerdrKey.Char(single.lowercaseChar())
                } else {
                    HerdrKey.Char(single)
                }

                lower.startsWith('f') -> functionKey(lower.substring(1)) ?: return null
                else -> return null
            }
        }

        return HerdrKeyStroke(key, modifiers).normalized()
    }

    private fun functionKey(number: String): HerdrKey? {
        if (number.isEmpty() || !number.all { it.isDigit() }) return null
        val parsed = number.toIntOrNull()?.takeIf { it in 0..MAX_FUNCTION_TOKEN } ?: return null
        return HerdrNamedKey.function(parsed)?.let { HerdrKey.Named(it) }
    }

    private fun parseModifierToken(token: String): Int? = when (token.lowercase()) {
        "ctrl", "control" -> CTRL
        "shift" -> SHIFT
        "alt", "option", "meta" -> ALT
        "cmd", "command", "super" -> SUPER
        "hyper" -> HYPER
        else -> null
    }

    /** Herdr's `parse_range_modifiers`: the modifiers around exactly one `1..9` token. */
    private fun parseRangeModifiers(text: String): Int? {
        var modifiers = 0
        var sawRange = false
        for (part in text.split('+')) {
            val trimmed = part.trim()
            if (trimmed == RANGE_TOKEN) {
                if (sawRange) return null
                sawRange = true
            } else {
                modifiers = modifiers or (parseModifierToken(trimmed) ?: return null)
            }
        }
        return if (sawRange) modifiers else null
    }

    /** Herdr's `parse_modifier_combo`: modifiers only, at least one. */
    private fun parseModifierCombo(text: String): Int? {
        var modifiers = 0
        for (part in text.split('+')) {
            val trimmed = part.trim()
            if (trimmed.isEmpty()) return null
            modifiers = modifiers or (parseModifierToken(trimmed) ?: return null)
        }
        return modifiers.takeIf { it != 0 }
    }

    private fun named(key: HerdrNamedKey): HerdrKey = HerdrKey.Named(key)

    // endregion

    private class KeysInput(
        val prefix: String? = null,
        val values: Map<String, List<String>> = emptyMap(),
        val indexed: Map<String, String> = emptyMap(),
        val indexedConfigured: Boolean = false,
        val commands: List<CommandInput> = emptyList(),
    )

    private class CommandInput(val keys: List<String>, val hasCommand: Boolean)

    private class Binding(val stroke: HerdrKeyStroke, val prefixed: Boolean)

    private class ParsedBinding(val bindings: List<Binding>, val isRange: Boolean)

    private enum class Source { USER, DEFAULT }

    private enum class Kind { ACTION, NAVIGATE, INDEXED }

    private class KeyField(val name: String, val kind: Kind, val default: String, val legacy: String? = null)

    /**
     * Herdr's `BindingRegistry`. Every keystroke handed to it is already normalised, so back-tab and
     * shift+tab land on the same entry. It keeps which source claimed a keystroke first: the user
     * pass runs before the default pass, so a default never displaces a user binding.
     */
    private class Registry(private val prefix: HerdrKeyStroke, val prefixSource: Source) {
        private val direct = HashMap<HerdrKeyStroke, Source>()
        private val prefixed = HashMap<HerdrKeyStroke, Source>()

        fun reserveDirect(stroke: HerdrKeyStroke, source: Source) {
            direct.putIfAbsent(stroke, source)
        }

        fun conflict(binding: Binding): Source? = map(binding.prefixed)[binding.stroke]

        fun register(binding: Binding, source: Source) {
            map(binding.prefixed)[binding.stroke] = source
        }

        /** Pressing the prefix twice sends a literal prefix key, so Herdr disables such a binding. */
        fun prefixIsReserved(stroke: HerdrKeyStroke): Boolean = stroke == prefix

        private fun map(prefixed: Boolean) = if (prefixed) this.prefixed else direct
    }

    private const val PREFIX_TOKEN = "prefix+"
    private const val RANGE_TOKEN = "1..9"
    private const val DEFAULT_PREFIX = "ctrl+b"
    private const val ZOOM = "zoom"
    private const val ZOOM_ALIAS = "fullscreen"
    private const val INDEXED = "indexed"

    /** Herdr parses an `f<number>` token as a `u8`, so anything above 255 is not a key at all. */
    private const val MAX_FUNCTION_TOKEN = 255

    private val ESC = HerdrKey.Named(HerdrNamedKey.ESC)
    private val FALLBACK_PREFIX = HerdrKeyStroke(HerdrKey.Char('b'), CTRL)
    private val LEGACY_INDEXED = listOf("tabs", "workspaces", "agents")

    /** Keys navigate mode answers itself; a navigate binding cannot take them. */
    private val NAVIGATE_RUNTIME_KEYS: List<HerdrKeyStroke> = buildList {
        add(HerdrKeyStroke(HerdrKey.Named(HerdrNamedKey.ESC), 0))
        add(HerdrKeyStroke(HerdrKey.Named(HerdrNamedKey.ENTER), 0))
        add(HerdrKeyStroke(HerdrKey.Named(HerdrNamedKey.TAB), 0))
        add(HerdrKeyStroke(HerdrKey.Named(HerdrNamedKey.BACK_TAB), 0))
        add(HerdrKeyStroke(HerdrKey.Named(HerdrNamedKey.LEFT), 0))
        add(HerdrKeyStroke(HerdrKey.Named(HerdrNamedKey.RIGHT), 0))
        ('1'..'9').forEach { add(HerdrKeyStroke(HerdrKey.Char(it), 0)) }
    }

    /**
     * Every action Herdr binds, in the order it resolves them, with the default from
     * `impl Default for KeysConfig`. An empty default means the action is unbound until configured.
     */
    private val FIELDS = listOf(
        KeyField("navigate_workspace_up", Kind.NAVIGATE, "up"),
        KeyField("navigate_workspace_down", Kind.NAVIGATE, "down"),
        KeyField("navigate_pane_left", Kind.NAVIGATE, "h"),
        KeyField("navigate_pane_down", Kind.NAVIGATE, "j"),
        KeyField("navigate_pane_up", Kind.NAVIGATE, "k"),
        KeyField("navigate_pane_right", Kind.NAVIGATE, "l"),
        KeyField("help", Kind.ACTION, "prefix+?"),
        KeyField("settings", Kind.ACTION, "prefix+s"),
        KeyField("new_workspace", Kind.ACTION, "prefix+shift+n"),
        KeyField("new_worktree", Kind.ACTION, "prefix+shift+g"),
        KeyField("open_worktree", Kind.ACTION, ""),
        KeyField("remove_worktree", Kind.ACTION, ""),
        KeyField("rename_workspace", Kind.ACTION, "prefix+shift+w"),
        KeyField("close_workspace", Kind.ACTION, "prefix+shift+d"),
        KeyField("workspace_picker", Kind.ACTION, "prefix+w"),
        KeyField("goto", Kind.ACTION, "prefix+g"),
        KeyField("detach", Kind.ACTION, "prefix+q"),
        KeyField("reload_config", Kind.ACTION, "prefix+shift+r"),
        KeyField("open_notification_target", Kind.ACTION, "prefix+o"),
        KeyField("previous_workspace", Kind.ACTION, ""),
        KeyField("next_workspace", Kind.ACTION, ""),
        KeyField("previous_agent", Kind.ACTION, ""),
        KeyField("next_agent", Kind.ACTION, ""),
        KeyField("focus_agent", Kind.INDEXED, "", legacy = "agents"),
        KeyField("new_tab", Kind.ACTION, "prefix+c"),
        KeyField("rename_tab", Kind.ACTION, "prefix+shift+t"),
        KeyField("previous_tab", Kind.ACTION, "prefix+p"),
        KeyField("next_tab", Kind.ACTION, "prefix+n"),
        KeyField("move_tab_previous", Kind.ACTION, ""),
        KeyField("move_tab_next", Kind.ACTION, ""),
        KeyField("switch_tab", Kind.INDEXED, "prefix+1..9", legacy = "tabs"),
        KeyField("switch_workspace", Kind.INDEXED, "", legacy = "workspaces"),
        KeyField("close_tab", Kind.ACTION, "prefix+shift+x"),
        KeyField("rename_pane", Kind.ACTION, "prefix+shift+p"),
        KeyField("edit_scrollback", Kind.ACTION, "prefix+e"),
        KeyField("copy_mode", Kind.ACTION, "prefix+["),
        KeyField("focus_pane_left", Kind.ACTION, "prefix+h"),
        KeyField("focus_pane_down", Kind.ACTION, "prefix+j"),
        KeyField("focus_pane_up", Kind.ACTION, "prefix+k"),
        KeyField("focus_pane_right", Kind.ACTION, "prefix+l"),
        KeyField("swap_pane_left", Kind.ACTION, "prefix+shift+h"),
        KeyField("swap_pane_down", Kind.ACTION, "prefix+shift+j"),
        KeyField("swap_pane_up", Kind.ACTION, "prefix+shift+k"),
        KeyField("swap_pane_right", Kind.ACTION, "prefix+shift+l"),
        KeyField("last_pane", Kind.ACTION, ""),
        KeyField("cycle_pane_next", Kind.ACTION, "prefix+tab"),
        KeyField("cycle_pane_previous", Kind.ACTION, "prefix+shift+tab"),
        KeyField("split_vertical", Kind.ACTION, "prefix+v"),
        KeyField("split_horizontal", Kind.ACTION, "prefix+minus"),
        KeyField("close_pane", Kind.ACTION, "prefix+x"),
        KeyField(ZOOM, Kind.ACTION, "prefix+z"),
        KeyField("resize_mode", Kind.ACTION, "prefix+r"),
        KeyField("resize_pane_left", Kind.ACTION, ""),
        KeyField("resize_pane_down", Kind.ACTION, ""),
        KeyField("resize_pane_up", Kind.ACTION, ""),
        KeyField("resize_pane_right", Kind.ACTION, ""),
        KeyField("toggle_sidebar", Kind.ACTION, "prefix+b"),
    )
}
