package io.github.inductiveautomation.kindling.cache

import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectStreamClass

class AliasingObjectInputStream private constructor(
    inputStream: InputStream,
    private val aliases: Map<String, Class<*>>,
) : ObjectInputStream(inputStream) {
    constructor(inputStream: InputStream, block: MutableMap<String, Class<*>>.() -> Unit) : this(inputStream, buildMap(block))

    override fun readClassDescriptor(): ObjectStreamClass {
        val baseDescriptor = super.readClassDescriptor()

        return if (aliases.containsKey(baseDescriptor.name)) {
            val aliasClassDescriptor = ObjectStreamClass.lookup(aliases[baseDescriptor.name])
            val aliasUid = aliasClassDescriptor.serialVersionUID
            val expectedUid = baseDescriptor.serialVersionUID

            require(aliasUid == expectedUid) { "serialVersionUID mismatch; expected $expectedUid but got $aliasUid" }

            aliasClassDescriptor
        } else {
            baseDescriptor
        }
    }
}
