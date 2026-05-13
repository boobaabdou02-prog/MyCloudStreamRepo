package com.frenchstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import org.jsoup.nodes.Element

class FrenchStreamProvider : MainAPI() {
    override var mainUrl = "https://french-stream.one"
    override var name = "French Stream"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)
    override var lang = "fr"
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "$mainUrl/page/" to "Derniers Ajouts",
        "$mainUrl/films-streaming/" to "Films",
        "$mainUrl/series-streaming/" to "Séries",
        "$mainUrl/animes-vostfr/" to "Animes"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) request.data else "${request.data}page/$page/"
        val document = app.get(url).document
        val home = document.select("a.short-poster").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst(".short-title")?.text() ?: return null
        val href = this.attr("href")
        val posterUrl = this.selectFirst("img")?.attr("src")?.let { fixUrl(it) }
        
        // Try to detect if it's a series or movie from the URL or metadata
        val type = if (href.contains("series") || href.contains("animes")) TvType.TvSeries else TvType.Movie

        return if (type == TvType.TvSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.post(
            "$mainUrl/index.php?do=search",
            data = mapOf(
                "do" to "search",
                "subaction" to "search",
                "search_start" to "0",
                "full_search" to "0",
                "result_from" to "1",
                "story" to query
            )
        ).document

        return document.select("a.short-poster").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("h1")?.text() ?: ""
        val poster = document.selectFirst(".fposter img")?.attr("src")?.let { fixUrl(it) }
        val description = document.selectFirst(".f-desc")?.text()
        val year = document.selectFirst(".f-info li:contains(Année)")?.text()?.filter { it.isDigit() }?.toIntOrNull()
        
        val isSeries = url.contains("series") || url.contains("animes")

        if (isSeries) {
            // Series handling: Often episodes are listed in tabs or as direct links
            // This is a simplified version, as French-stream sometimes uses complex episode selection
            val episodes = mutableListOf<Episode>()
            
            // Logic for episodes would go here based on the site's layout
            // For now, adding a dummy episode to allow the player to load
            episodes.add(Episode(url, "Episode 1"))

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        
        // Find player iframe URLs
        // French-stream often hides these in script tags or behind clicks
        // We look for common video hosts: uqload, voe, doodstream, etc.
        
        document.select("iframe").forEach { iframe ->
            val src = iframe.attr("src")
            if (src.isNotBlank()) {
                loadExtractor(src, data, subtitleCallback, callback)
            }
        }

        // Also check for links in the body that look like video hosts
        document.select("a[href*='uqload'], a[href*='voe'], a[href*='dood']").forEach { link ->
            loadExtractor(link.attr("href"), data, subtitleCallback, callback)
        }

        return true
    }
}
