package mihon.core.common.extensions

import kotlinx.serialization.json.JsonObject

// Local copy of Mihon's extension helper (Apache-2.0). Extensions link against it by name.
val JsonObjectEmpty = JsonObject(emptyMap())

val JsonObject.Companion.EMPTY: JsonObject
    inline get() = JsonObjectEmpty
