package com.lagradost.shiro.utils

import com.lagradost.shiro.utils.extractors.*

data class ExtractorLink(
    val name: String,
    val url: String,
    val referer: String,
    val quality: Int,
    val isM3u8: Boolean = false
)

enum class Qualities(var value: Int) {
    Unknown(0),
    SD(-1), // 360p - 480p
    HD(1), // 720p
    FullHd(2), // 1080p
    UHD(3) // 4k
}

private val packedRegex = Regex("""eval\(function\(p,a,c,k,e,.*\)\)""")
fun getPacked(string: String): String? {
    return packedRegex.find(string)?.value
}

fun getAndUnpack(string: String): String? {
    val packedText = getPacked(string)
    return JsUnpacker(packedText).unpack()
}

val APIS: Array<ExtractorApi> = arrayOf(
    //AllProvider(),
    Shiro(),
    Mp4Upload(),
    StreamTape(),
    MixDrop(),
    XStreamCdn()
)


fun httpsify(url: String): String {
    return if (url.startsWith("//")) "https:$url" else url
}

abstract class ExtractorApi {
    open val name: String = "NONE"
    open val mainUrl: String = "NONE"

    open fun getUrl(url: String, referer: String? = null): List<ExtractorLink>? {
        return null
    }

    open fun getExtractorUrl(id: String): String{
        return id
    }
}