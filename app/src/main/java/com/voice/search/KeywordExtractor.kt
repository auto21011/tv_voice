package com.voice.search

object KeywordExtractor {
    private val PREFIX_PATTERN = Regex(
        "^(搜索|搜|看|播放|我想看|我要看|我想搜|我要搜|给我搜|帮我搜|帮我找|我想|我要)\\s*",
        RegexOption.IGNORE_CASE
    )
    private val SUFFIX_PATTERN = Regex(
        "\\s*(电视剧|电影|综艺|动漫|纪录片|这部|这个|一下|吧|呗|啊|啦|呢|吗)?$",
        RegexOption.IGNORE_CASE
    )

    fun extract(raw: String?): String? {
        if (raw.isNullOrBlank()) return null

        var keyword = raw.trim()
        keyword = PREFIX_PATTERN.replace(keyword, "")
        keyword = SUFFIX_PATTERN.replace(keyword, "")
        keyword = keyword.trim()

        return if (keyword.isEmpty()) null else keyword
    }
}