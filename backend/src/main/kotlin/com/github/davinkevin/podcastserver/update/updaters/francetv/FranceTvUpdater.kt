package com.github.davinkevin.podcastserver.update.updaters.francetv

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.davinkevin.podcastserver.extension.java.util.orNull
import com.github.davinkevin.podcastserver.manager.worker.*
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.util.DigestUtils
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import reactor.kotlin.core.publisher.toMono
import reactor.kotlin.core.util.function.component1
import reactor.kotlin.core.util.function.component2
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import com.github.davinkevin.podcastserver.service.image.ImageService

class FranceTvUpdater(
        private val franceTvClient: WebClient,
        private val franceTvApi: WebClient,
        private val image: ImageService,
        private val mapper: ObjectMapper
): Updater {

    private val log = LoggerFactory.getLogger(FranceTvUpdater::class.java)

    override fun blockingFindItems(podcast: PodcastToUpdate): Set<ItemFromUpdate> = TODO("not required anymore...")
    override fun blockingSignatureOf(url: URI): String = TODO("not required anymore...")

    override fun findItems(podcast: PodcastToUpdate): Flux<ItemFromUpdate> {

        val url = podcast.url.toASCIIString()

        log.debug("Fetch $url")

        val replayUrl = "${podcast.url}/replay-videos/ajax/?page=0"
                .substringAfter("https://www.france.tv/")

        return franceTvClient
                .get()
                .uri(replayUrl)
                .retrieve()
                .bodyToMono<String>()
                .map { Jsoup.parse(it, url) }
                .flatMapIterable { it.select("a[href]") }
                .map { it.attr("href") }
                .flatMap { urlToItem(it) }
    }

    private fun urlToItem(pathUrl: String): Mono<ItemFromUpdate> {
        return franceTvClient
                .get()
                .uri(pathUrl)
                .retrieve()
                .bodyToMono<String>()
                .map { Jsoup.parse(it, "https://www.france.tv/") }
                .flatMapIterable { it.select("script") }
                .map { it.html() }
                .filter { "FTVPlayerVideos" in it }
                .toMono()
                .map { it.substringAfter("=").trim(';') }
                .flatMapIterable { mapper.readValue<Set<FranceTvPageItem>>(it) }
                .filter { it.contentId in pathUrl }
                .toMono()
                .map { "/tools/getInfosOeuvre/v2/?idDiffusion=${it.videoId}" }
                .flatMap { franceTvApi.get()
                        .uri(it)
                        .retrieve()
                        .bodyToMono<FranceTvItem>()
                }
                .flatMap { it.toMono().zipWith(image.fetchCoverInformationOrOption(it.image)) }
                .map { (franceTvItem, cover) ->
                    ItemFromUpdate(
                            title = franceTvItem.title()!!,
                            description = franceTvItem.synopsis!!,
                            pubDate = franceTvItem.pubDate(),
                            url = URI("https://www.france.tv$pathUrl"),
                            cover = cover.orNull()
                    )
                }
                .onErrorResume {
                    log.error("Error during fetch of $pathUrl", it)
                    Mono.empty()
                }
    }

    override fun signatureOf(url: URI): Mono<String> {

        val replayUrl = "${url.toASCIIString()}/replay-videos/ajax/?page=0"
                .substringAfter("https://www.france.tv/")

        return franceTvClient
                .get()
                .uri(replayUrl)
                .retrieve()
                .bodyToMono<String>()
                .map { Jsoup.parse(it, url.toASCIIString()) }
                .flatMapIterable { it.select("a[href]") }
                .map { it.attr("href") }
                .sort()
                .reduce { t, u -> """$t-$u""" }
                .map { DigestUtils.md5DigestAsHex(it.toByteArray()) }
                .switchIfEmpty("".toMono())
    }

    override fun type() = Type("FranceTv", "France•tv")

    override fun compatibility(url: String?): Int {
        return if (!url.isNullOrEmpty() && "www.france.tv" in url) 1
        else Integer.MAX_VALUE
    }
}


@JsonIgnoreProperties(ignoreUnknown = true)
private data class FranceTvPageItem(val contentId: String, val videoId: String)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class FranceTvItem(
        private val titre: String? = null,
        val synopsis: String? = null,
        private val saison: String? = null,
        private val episode: String? = null,
        @field:JsonProperty("sous_titre") private val sousTitre: String? = null,
        val diffusion: Diffusion = Diffusion(),
        @field:JsonProperty("image_secure") val image: URI? = null
) {

    fun title(): String? {
        var title = titre

        if (!saison.isNullOrEmpty()) {
            title = "$title - S$saison"
        }

        if (!episode.isNullOrEmpty()) {
            title = "${title}E$episode"
        }

        if (!sousTitre.isNullOrEmpty()) {
            title = "$title - $sousTitre"
        }

        return title
    }

    fun pubDate(): ZonedDateTime {
        return if (diffusion.timestamp == null) ZonedDateTime.now()
        else ZonedDateTime.ofInstant(Instant.ofEpochSecond(diffusion.timestamp!!), ZONE_ID)
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class Diffusion(var timestamp: Long? = null)
}

private val ZONE_ID = ZoneId.of("Europe/Paris")


private fun ImageService.fetchCoverInformationOrOption(url: URI?): Mono<Optional<CoverFromUpdate>> {
    return Mono.justOrEmpty(url)
            .flatMap { fetchCoverInformation(url!!) }
            .map { CoverFromUpdate(it.width, it.height, it.url) }
            .map { Optional.of<CoverFromUpdate>(it) }
            .switchIfEmpty { Optional.empty<CoverFromUpdate>().toMono() }
}
