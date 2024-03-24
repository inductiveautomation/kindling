package io.github.inductiveautomation.kindling.core

interface KindlingSerializable {
    /**
     * A globally unique identifier used to disambiguate the _object_ this interface is implemented on.
     * Because this value will be stored in Kindling's preferences.json, these values cannot be renamed lightly.
     */
    val serialKey: String
}
