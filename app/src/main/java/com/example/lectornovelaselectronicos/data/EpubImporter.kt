package com.example.lectornovelaselectronicos.data

import android.content.Context
import android.net.Uri
import android.text.Html
import androidx.core.text.HtmlCompat
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

// ==================== Helpers de normalización ====================

// Normaliza el nombre de entrada del ZIP para usarlo como clave
private fun String.normalizeEntryKey(): String =
    replace('\\', '/').lowercase()

// Normaliza rutas tipo "OEBPS/../content.opf"
private fun String.normalizePath(): String {
    val segments = mutableListOf<String>()
    for (segment in split('/')) {
        when (segment) {
            "", "." -> Unit
            ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
            else -> segments.add(segment)
        }
    }
    return segments.joinToString("/")
}


// ============================= Clase ==============================

/**
 * Utilidad ligera para leer archivos EPUB sin dependencias externas. Soporta
 * EPUB 2 y 3 extrayendo metadatos básicos, capítulos y portada.
 */
class EpubImporter(private val context: Context) {

    data class Chapter(
        val title: String,
        val content: String,
    )

    data class Result(
        val title: String,
        val author: String,
        val language: String,
        val description: String,
        val coverImage: ByteArray?,
        val chapters: List<Chapter>,
    )

    fun parse(uri: Uri): Result {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val entries = readZipEntries(input)

            // 1) container.xml
            val containerKey = "META-INF/container.xml".normalizeEntryKey()
            val container = entries[containerKey]
                ?: error("container.xml no encontrado")

            // 2) ruta del paquete principal (.opf)
            val rootPath = parseRootFilePath(container)
            val opfKey = rootPath.normalizeEntryKey()
            val opfBytes = entries[opfKey]
                ?: error("Paquete principal no encontrado")

            val packageDoc = parseXml(opfBytes)
            val manifest = parseManifest(packageDoc, rootPath)
            val spine = parseSpine(packageDoc)
            val metadata = parseMetadata(packageDoc)
            val coverBytes = findCover(manifest, entries, metadata.coverId)

            val chapters = spine.mapIndexedNotNull { index, idRef ->
                val manifestItem = manifest[idRef] ?: return@mapIndexedNotNull null
                if (!manifestItem.isHtml()) return@mapIndexedNotNull null

                val entryKey = manifestItem.fullPathKey
                val htmlBytes = entries[entryKey] ?: return@mapIndexedNotNull null
                val html = htmlBytes.toString(Charsets.UTF_8)
                val title = extractTitle(html, index + 1)
                val textContent = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
                    .toString()
                    .replace("\u00a0", " ")
                    .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")  // respeta \n
                    .replace(Regex("\n{3,}"), "\n\n")
                    .trim()

                if (textContent.isBlank()) return@mapIndexedNotNull null

                Chapter(title = title, content = textContent)
            }

            val title = metadata.title.ifBlank { uri.lastPathSegment.orEmpty() }
            return Result(
                title = title,
                author = metadata.author,
                language = metadata.language,
                description = metadata.description,
                coverImage = coverBytes,
                chapters = chapters,
            )
        }
        error("No se pudo abrir el archivo EPUB")
    }


    // Lee todas las entradas del ZIP y las guarda con clave normalizada
    private fun readZipEntries(input: InputStream): Map<String, ByteArray> {
        val map = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val bytes = zip.readBytes()
                    val key = entry.name.normalizeEntryKey()
                    map[key] = bytes
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return map
    }

    private fun parseRootFilePath(containerXml: ByteArray): String {
        val xml = containerXml.toString(Charsets.UTF_8)
        val regex = Regex("""full-path\s*=\s*["']([^"']+)["']""")
        val match = regex.find(xml) ?: error("rootfile no encontrado en container.xml")
        val fullPath = match.groupValues[1].trim()
        if (fullPath.isBlank()) error("Ruta de paquete vacía en container.xml")
        return fullPath
    }


    private data class ManifestItem(
        val id: String,
        val href: String,
        val mediaType: String,
        val properties: String?,
        val baseDir: String,
    ) {
        // clave tal como se guarda en el mapa de entries
        val fullPathKey: String
            get() {
                val combined = if (baseDir.isBlank()) href else "$baseDir/$href"
                return combined.normalizePath().normalizeEntryKey()
            }

        fun isHtml(): Boolean = mediaType.contains("html", ignoreCase = true)
    }

    private data class PackageMetadata(
        val title: String,
        val author: String,
        val language: String,
        val description: String,
        val coverId: String?,
    )

    private fun parseManifest(packageDoc: Document, rootPath: String): Map<String, ManifestItem> {
        val manifestNode = packageDoc.getElementsByTagName("manifest").item(0) as? Element
            ?: return emptyMap()

        val baseDir = rootPath.substringBeforeLast('/', "").trimEnd('/')
        val items = manifestNode.getElementsByTagName("item")
        val list = mutableMapOf<String, ManifestItem>()
        for (i in 0 until items.length) {
            val item = items.item(i) as? Element ?: continue
            val id = item.getAttribute("id")
            val href = item.getAttribute("href")
            val mediaType = item.getAttribute("media-type")
            val properties = item.getAttribute("properties")
            if (id.isNotBlank() && href.isNotBlank()) {
                list[id] = ManifestItem(id, href, mediaType, properties, baseDir)
            }
        }
        return list
    }

    private fun parseSpine(packageDoc: Document): List<String> {
        val spineNode = packageDoc.getElementsByTagName("spine").item(0) as? Element
            ?: return emptyList()

        val itemRefs = spineNode.getElementsByTagName("itemref")
        val ids = mutableListOf<String>()
        for (i in 0 until itemRefs.length) {
            val item = itemRefs.item(i) as? Element ?: continue
            val idRef = item.getAttribute("idref")
            if (idRef.isNotBlank()) ids.add(idRef)
        }
        return ids
    }

    private fun parseMetadata(packageDoc: Document): PackageMetadata {
        val metadata = packageDoc.getElementsByTagName("metadata").item(0) as? Element
        val title = metadata?.getFirstByName("title")?.textContent?.trim().orEmpty()
        val creator = metadata?.getFirstByName("creator")?.textContent?.trim().orEmpty()
        val language = metadata?.getFirstByName("language")?.textContent?.trim().orEmpty()
        val description = metadata?.getFirstByName("description")?.textContent?.trim().orEmpty()
        val coverId = metadata?.getMetaContent("cover")
        return PackageMetadata(title, creator, language, description, coverId)
    }

    private fun findCover(
        manifest: Map<String, ManifestItem>,
        entries: Map<String, ByteArray>,
        coverId: String?,
    ): ByteArray? {
        val manifestItem = coverId?.let { manifest[it] }
            ?: manifest.values.firstOrNull { it.properties?.contains("cover-image") == true }
            ?: manifest.values.firstOrNull { it.id.equals("cover", ignoreCase = true) }
            ?: manifest.values.firstOrNull { it.id.contains("cover", ignoreCase = true) }
        manifestItem ?: return null
        return entries[manifestItem.fullPathKey]
    }

    private fun parseXml(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false

            fun safeFeature(name: String, value: Boolean) {
                try {
                    setFeature(name, value)
                } catch (_: Exception) {
                    // En algunas implementaciones de Android no están soportadas, las ignoramos
                }
            }

            safeFeature("http://xml.org/sax/features/external-general-entities", false)
            safeFeature("http://xml.org/sax/features/external-parameter-entities", false)
            safeFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }

        val builder = factory.newDocumentBuilder()
        return builder.parse(ByteArrayInputStream(bytes))
    }


    private fun Element.getFirstByName(tag: String): Element? {
        val direct = getElementsByTagName(tag)
        if (direct.length > 0) return direct.item(0) as? Element
        val namespaced = getElementsByTagName("dc:$tag")
        return namespaced.item(0) as? Element
    }

    private fun Element.getMetaContent(name: String): String? {
        val metas = getElementsByTagName("meta")
        for (i in 0 until metas.length) {
            val meta = metas.item(i) as? Element ?: continue
            val metaName = meta.getAttribute("name")
            val property = meta.getAttribute("property")
            if (metaName.equals(name, ignoreCase = true) || property.equals(name, ignoreCase = true)) {
                val content = meta.getAttribute("content")
                if (content.isNotBlank()) return content
            }
        }
        return null
    }

    private fun extractTitle(html: String, index: Int): String {
        val patterns = listOf("title", "h1", "h2", "h3")
        for (tag in patterns) {
            val regex = "<$tag[^>]*>(.*?)</$tag>".toRegex(RegexOption.IGNORE_CASE)
            val match = regex.find(html)
            if (match != null) {
                val raw = match.groupValues[1]
                val text = Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString().trim()
                if (text.isNotBlank()) return text
            }
        }
        return "Capítulo $index"
    }
}
