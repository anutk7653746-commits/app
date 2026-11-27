package com.findora.app

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

object MediaRepository {
    private const val USER_AGENT = "FindoraAndroid/1.0 (https://github.com/anutk7653746-commits/app; Android media search)"
    private val translationTargets = listOf("en-GB", "ar-SA", "es-ES", "fr-FR", "de-DE", "ru-RU", "zh-CN", "pt-PT", "it-IT", "tr-TR", "ja-JP")

    suspend fun search(query: String, mode: SearchMode): List<MediaItem> = withContext(Dispatchers.IO) {
        val translated = translateQuery(query)
        val commonsResult: Result<List<MediaItem>> = runCatching { searchCommons(translated, mode) }
        val archiveResult: Result<List<MediaItem>> = if (mode != SearchMode.IMAGES) {
            runCatching { searchInternetArchive(translated) }
        } else {
            Result.success(emptyList())
        }
        if (commonsResult.isFailure && archiveResult.isFailure) {
            throw IllegalStateException("לא ניתן להגיע למאגרי המדיה כרגע. נסה שוב מאוחר יותר.", commonsResult.exceptionOrNull() ?: archiveResult.exceptionOrNull())
        }
        val commons = commonsResult.getOrDefault(emptyList())
        val archive = archiveResult.getOrDefault(emptyList())
        (commons + archive)
            .distinctBy { it.id }
            .sortedWith(compareByDescending<MediaItem> { it.isImage }.thenBy { it.title.lowercase(Locale.ROOT) })
            .take(40)
    }

    private suspend fun translateQuery(query: String): List<String> = coroutineScope {
        val source = detectLanguage(query)
        val translations = translationTargets
            .filterNot { it == source }
            .map { target ->
                async(Dispatchers.IO) {
                    runCatching { translate(query, source, target) }.getOrNull()
                }
            }
            .awaitAll()
            .filterNotNull()
        (listOf(query) + translations)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .take(12)
    }

    private fun detectLanguage(text: String): String = when {
        text.any { it in '\u0590'..'\u05FF' } -> "he-IL"
        text.any { it in '\u0600'..'\u06FF' } -> "ar-SA"
        text.any { it in '\u0400'..'\u04FF' } -> "ru-RU"
        text.any { it in '\u3040'..'\u30FF' } -> "ja-JP"
        text.any { it in '\u4E00'..'\u9FFF' } -> "zh-CN"
        else -> "en-GB"
    }

    private fun translate(text: String, from: String, to: String): String {
        if (text.toByteArray(Charsets.UTF_8).size > 500) return ""
        val q = encode(text)
        val url = "https://api.mymemory.translated.net/get?q=$q&langpair=$from%7C$to"
        val json = JSONObject(getText(url, 12_000))
        val response = json.optJSONObject("responseData") ?: return ""
        val translated = response.optString("translatedText", "")
        if (translated.equals("NO QUERY SPECIFIED", true) || translated.contains("MYMEMORY WARNING", true)) return ""
        return translated.replace(Regex("<[^>]*>"), "").trim()
    }

    private fun searchCommons(terms: List<String>, mode: SearchMode): List<MediaItem> {
        val query = terms.take(12).joinToString(" OR ") { "\"${it.replace("\\", " ").replace("\"", " ")}\"" }
        val url = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch=${encode(query)}&gsrnamespace=6&gsrlimit=20&prop=imageinfo%7Cinfo&inprop=url&iiprop=url%7Cmime%7Csize%7Cextmetadata&iiurlwidth=640&format=json&origin=*"
        val root = JSONObject(getText(url))
        val pages = root.optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
        val keys = pages.keys()
        val results = mutableListOf<MediaItem>()
        while (keys.hasNext()) {
            val page = pages.optJSONObject(keys.next()) ?: continue
            val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
            val mediaUrl = info.optString("url").takeIf { it.startsWith("https://") } ?: continue
            val mime = info.optString("mime", "application/octet-stream")
            val isImage = mime.startsWith("image/")
            val isVideo = mime.startsWith("video/")
            if (mode == SearchMode.IMAGES && !isImage) continue
            if (mode == SearchMode.VIDEOS && !isVideo) continue
            if (!isImage && !isVideo) continue
            val ext = page.optJSONObject("imageinfo")?.optJSONObject("extmetadata")
            val licenseName = metadataValue(ext, "LicenseShortName").ifBlank { "Wikimedia Commons – check file page" }
            val licenseUrl = metadataValue(ext, "LicenseUrl").takeIf { it.startsWith("http") }
            val baseDescription = metadataValue(ext, "ImageDescription")
                .ifBlank { metadataValue(ext, "ObjectName") }
                .ifBlank { "קובץ מדיה מ-Wikimedia Commons" }
            val artist = metadataValue(ext, "Artist")
            val description = if (artist.isNotBlank()) "$baseDescription · יוצר: $artist" else baseDescription
            val title = page.optString("title").removePrefix("File:").ifBlank { "Media file" }
            val sourcePage = page.optString("fullurl").takeIf { it.startsWith("https://") }
                ?: "https://commons.wikimedia.org/wiki/File:${Uri.encode(title.replace(" ", "_"))}"
            val thumb = info.optString("thumburl").takeIf { it.startsWith("https://") } ?: mediaUrl.takeIf { isImage }
            val size = info.optLong("size", -1L).takeIf { it > 0L }
            val extension = extensionFromUrl(mediaUrl)
            results += MediaItem(
                id = "commons:${page.optString("pageid", title)}",
                title = title,
                description = cleanText(description),
                source = "Wikimedia Commons",
                sourcePageUrl = sourcePage,
                downloadUrl = mediaUrl,
                thumbnailUrl = thumb,
                licenseName = cleanText(licenseName),
                licenseUrl = licenseUrl,
                mimeType = mime,
                sizeBytes = size,
                fileName = safeFileName(title, extension)
            )
        }
        return results
    }

    private fun searchInternetArchive(terms: List<String>): List<MediaItem> {
        val clauses = terms.take(12).joinToString(" OR ") { term ->
            val escaped = term.replace("\\", " ").replace("\"", " ").trim()
            "title:\"$escaped\" OR description:\"$escaped\""
        }
        if (clauses.isBlank()) return emptyList()
        val query = "mediatype:movies AND ($clauses)"
        val url = "https://archive.org/advancedsearch.php?q=${encode(query)}&fl[]=identifier,title,description,year,creator,licenseurl&rows=6&page=1&output=json"
        val root = JSONObject(getText(url))
        val docs = root.optJSONObject("response")?.optJSONArray("docs") ?: JSONArray()
        val results = mutableListOf<MediaItem>()
        for (i in 0 until docs.length()) {
            val doc = docs.optJSONObject(i) ?: continue
            val identifier = doc.optString("identifier")
            if (identifier.isBlank()) continue
            runCatching {
                val metadata = JSONObject(getText("https://archive.org/metadata/${encodePathSegment(identifier)}", 10_000))
                val meta = metadata.optJSONObject("metadata") ?: JSONObject()
                val license = meta.optString("licenseurl").ifBlank { doc.optString("licenseurl") }
                if (!MediaLicensePolicy.isRecognizedOpenLicense(license)) return@runCatching
                val files = metadata.optJSONArray("files") ?: JSONArray()
                val selected = selectVideoFile(files) ?: return@runCatching
                val filename = selected.optString("name")
                if (filename.isBlank()) return@runCatching
                val mime = selected.optString("mime", mimeFromExtension(filename))
                val directUrl = "https://archive.org/download/${encodePathSegment(identifier)}/${encodePathSegment(filename)}"
                val thumb = "https://archive.org/services/img/${encodePathSegment(identifier)}"
                val title = doc.optString("title").ifBlank { identifier }
                val description = cleanText(doc.optString("description")).take(300)
                    .ifBlank { "סרטון או סרט מ-Internet Archive" }
                val creator = meta.optString("creator").ifBlank { doc.optString("creator") }
                val withCreator = if (creator.isNotBlank()) "$description · יוצר: ${cleanText(creator)}" else description
                val ext = extensionFromUrl(filename)
                results += MediaItem(
                    id = "archive:$identifier",
                    title = title,
                    description = withCreator,
                    source = "Internet Archive",
                    sourcePageUrl = "https://archive.org/details/${encodePathSegment(identifier)}",
                    downloadUrl = directUrl,
                    thumbnailUrl = thumb,
                    licenseName = formatLicense(license),
                    licenseUrl = license,
                    mimeType = mime,
                    sizeBytes = selected.optString("size").toLongOrNull(),
                    fileName = safeFileName(title, ext)
                )
            }
        }
        return results
    }

    private fun selectVideoFile(files: JSONArray): JSONObject? {
        val candidates = mutableListOf<JSONObject>()
        for (i in 0 until files.length()) {
            val f = files.optJSONObject(i) ?: continue
            val name = f.optString("name")
            if (name.isBlank() || name.endsWith("_files.xml", true) || name.endsWith(".srt", true)) continue
            val ext = extensionFromUrl(name).lowercase(Locale.ROOT)
            if (ext in setOf("mp4", "m4v", "webm", "ogv", "mov", "mkv")) candidates += f
        }
        return candidates.sortedBy { preferredVideoRank(extensionFromUrl(it.optString("name"))) }.firstOrNull()
    }

    private fun preferredVideoRank(extension: String): Int = when (extension.lowercase(Locale.ROOT)) {
        "mp4" -> 0
        "m4v" -> 1
        "webm" -> 2
        "ogv" -> 3
        "mov" -> 4
        else -> 5
    }

    private fun formatLicense(value: String): String {
        val lower = value.lowercase(Locale.ROOT)
        val version = value.trimEnd('/').substringAfterLast('/').takeIf { it.matches(Regex("\\d+(\\.\\d+)?")) }.orEmpty()
        return when {
            lower.contains("publicdomain/zero") || lower.contains("/cc0/") -> "CC0 / Public Domain Dedication"
            lower.contains("publicdomain/mark") -> "Public Domain Mark"
            lower.contains("/licenses/by-nc-sa/") -> "CC BY-NC-SA $version"
            lower.contains("/licenses/by-nc-nd/") -> "CC BY-NC-ND $version"
            lower.contains("/licenses/by-nc/") -> "CC BY-NC $version"
            lower.contains("/licenses/by-sa/") -> "CC BY-SA $version"
            lower.contains("/licenses/by-nd/") -> "CC BY-ND $version"
            lower.contains("/licenses/by/") -> "CC BY $version"
            else -> "Creative Commons / Open license"
        }
    }

    private fun metadataValue(metadata: JSONObject?, key: String): String {
        val value = metadata?.optJSONObject(key)?.optString("value") ?: return ""
        return cleanText(value)
    }

    private fun getText(address: String, timeoutMs: Int = 20_000): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json,text/plain,*/*")
            instanceFollowRedirects = true
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IllegalStateException("Server returned HTTP $status")
            return body
        } finally {
            connection.disconnect()
        }
    }

    suspend fun downloadToCache(context: Context, item: MediaItem): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "media-downloads").apply { mkdirs() }
        val output = File(directory, item.fileName)
        val connection = (URL(item.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 90_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "*/*")
            instanceFollowRedirects = true
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("ההורדה נכשלה (HTTP $code)")
            val contentLength = connection.contentLengthLong
            if (contentLength > 1_500L * 1024L * 1024L) throw IllegalStateException("הקובץ גדול מדי להורדה באפליקציה (מעל 1.5GB)")
            connection.inputStream.buffered().use { input ->
                output.outputStream().buffered().use { destination ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > 1_500L * 1024L * 1024L) throw IllegalStateException("הקובץ גדול מדי להורדה באפליקציה (מעל 1.5GB)")
                        destination.write(buffer, 0, count)
                    }
                }
            }
            if (!output.exists() || output.length() == 0L) throw IllegalStateException("הקובץ שהתקבל ריק")
            output
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    fun saveFileName(item: MediaItem): String = item.fileName

    private fun cleanText(value: String): String = value
        .replace(Regex("<[^>]+>"), " ")
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun safeFileName(title: String, extension: String): String {
        val clean = title.substringAfterLast('/').replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ").trim().take(90).ifBlank { "findora-file" }
        val ext = extension.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) }?.let { ".$it" }.orEmpty()
        return if (ext.isNotEmpty() && clean.endsWith(ext, true)) clean else "$clean$ext"
    }

    private fun extensionFromUrl(value: String): String {
        val path = value.substringBefore('?').substringBefore('#')
        return path.substringAfterLast('.', "bin").take(12)
    }

    private fun mimeFromExtension(filename: String): String = when (extensionFromUrl(filename).lowercase(Locale.ROOT)) {
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "ogv" -> "video/ogg"
        "mov" -> "video/quicktime"
        "mkv" -> "video/x-matroska"
        else -> "application/octet-stream"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    private fun encodePathSegment(value: String): String = Uri.encode(value)
}
