package dev.appboypov.herdridea.ghostty.shared.models

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.appboypov.herdridea.ghostty.shared.exceptions.GhosttyException

/**
 * Struct layouts and enum values exported by the loaded library through `ghostty_type_json`.
 *
 * Reading them from the library itself keeps the bindings correct across libghostty-vt versions
 * and turns a removed field or value into a clear load error instead of memory corruption.
 */
class GhosttyAbi(manifestJson: String) {
    private val types: JsonObject = JsonParser.parseString(manifestJson).asJsonObject.getAsJsonObject("types")

    fun size(type: String): Long = type(type).get("size").asLong

    fun offset(type: String, field: String): Long {
        val fields = type(type).getAsJsonObject("fields")
            ?: throw GhosttyException("libghostty-vt type $type has no fields")
        val entry = fields.getAsJsonObject(field)
            ?: throw GhosttyException("libghostty-vt type $type has no field $field")
        return entry.get("offset").asLong
    }

    fun enumValue(type: String, name: String): Int {
        val values = type(type).getAsJsonObject("values")
            ?: throw GhosttyException("libghostty-vt type $type is not an enum")
        return values.get(name)?.asInt ?: throw GhosttyException("libghostty-vt enum $type has no value $name")
    }

    fun enumValueOrNull(type: String, name: String): Int? =
        types.getAsJsonObject(type)?.getAsJsonObject("values")?.get(name)?.asInt

    private fun type(name: String): JsonObject =
        types.getAsJsonObject(name) ?: throw GhosttyException("libghostty-vt has no type $name")
}
