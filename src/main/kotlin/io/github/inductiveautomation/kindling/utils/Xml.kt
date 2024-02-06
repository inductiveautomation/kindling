package io.github.inductiveautomation.kindling.utils

import org.w3c.dom.Document
import java.io.InputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

val XML_FACTORY: DocumentBuilderFactory =
    DocumentBuilderFactory.newDefaultInstance().apply {
        isXIncludeAware = false
        isExpandEntityReferences = false
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
    }

fun DocumentBuilderFactory.parse(inputStream: InputStream): Document {
    return newDocumentBuilder().parse(inputStream).also(Document::normalizeDocument)
}
