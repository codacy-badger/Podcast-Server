package com.github.davinkevin.podcastserver.service

import arrow.core.Try
import arrow.core.getOrElse
import arrow.core.toOption
import com.mashape.unirest.http.Unirest
import com.mashape.unirest.request.GetRequest
import com.mashape.unirest.request.HttpRequestWithBody
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.server.ServerWebExchange
import java.io.BufferedReader
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.function.Consumer
import java.util.function.Function

/**
 * Created by kevin on 21/07/2016.
 */
@Service
open class UrlService {

    val log = LoggerFactory.getLogger(this.javaClass.name)!!

    init {
        System.setProperty("http.agent", USER_AGENT_DESKTOP)
    }

    /* Get, Post and Other standard request of UniREST */
    fun get(url: String): GetRequest = Unirest.get(url)

    fun post(url: String): HttpRequestWithBody = Unirest.post(url)

    @JvmOverloads fun getRealURL(url: String, connectionModifier: Consumer<HttpURLConnection> = NO_OP, numberOfRedirection: Int = 0): String {
        if (MAX_NUMBER_OF_REDIRECTION <= numberOfRedirection) {
            throw RuntimeException("Too many redirects")
        }
        
        try {
            val connection = URL(url).connectOverHttp().apply {
                connectionModifier.accept(this)
                this.instanceFollowRedirects = false
            }

            val isRedirect = connection.responseCode
                    .toOption()
                    .filter { isARedirection(it) }

            val location = isRedirect
                    .map { addDomainIfRelative(url, connection.getHeaderField("Location")) }
                    .getOrElse { "" }

            connection.disconnect()

            return isRedirect
                    .map { getRealURL(location, connectionModifier, numberOfRedirection + 1) }
                    .getOrElse { url }
        } catch (e: Exception) {
            log.error("Error during resolution of real url for $url", e)
            return url
        }
    }

    private fun isARedirection(status: Int): Boolean {
        return status != HttpURLConnection.HTTP_OK && (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                status == HttpURLConnection.HTTP_MOVED_PERM ||
                status == HttpURLConnection.HTTP_SEE_OTHER)
    }

    /* Transform to Stream or Reader */
    fun asStream(url: String): InputStream {
        return Try { get(url).asBinary().body }
                .getOrElse { e -> throw RuntimeException("Error during creation of stream", e) }
    }

    open fun asReader(url: String): BufferedReader = asStream(url).bufferedReader()

    /* Relative and absolute URL transformation */
    fun addDomainIfRelative(urlWithDomain: String, mayBeRelativeUrl: String): String {
        return mayBeRelativeUrl.addDomainIfRelative(urlWithDomain)
    }

    companion object {

        const val USER_AGENT_DESKTOP = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/45.0.2454.85 Safari/537.36"
        const val USER_AGENT_MOBILE = "AppleCoreMedia/1.0.0.10B400 (iPod; U; CPU OS 6_1_5 like Mac OS X; fr_fr)"
        const val USER_AGENT_KEY = "User-agent"

        const val PROTOCOL_SEPARATOR = "://"
        private const val MAX_NUMBER_OF_REDIRECTION = 10
        val NO_OP = Consumer<HttpURLConnection> { Function.identity<Any>().apply(it) }
        private val EMPTY_PORT = setOf(80, 443)

        @JvmStatic fun getDomainFromRequest(ex: ServerWebExchange): URI {
            val origin = ex.request.headers["Origin"]?.firstOrNull()
            if (origin != null) {
                return URI(origin)
            }

            val uri = ex.request.uri
            val port = if(uri.port in EMPTY_PORT) { "" } else { ":${uri.port}" }
            return URI("${uri.scheme}://${uri.host}$port/")
        }

        @JvmStatic fun addProtocolIfNecessary(protocol: String, url: String): String {
            return if (url.startsWith("http")) url else protocol + url

        }

        @JvmStatic fun removeQueryParameters(url: String): String {
            return StringUtils.substringBeforeLast(url, "?")
        }
    }
}

private fun URL.connectOverHttp(): HttpURLConnection {
    val connection = this.openConnection()
    return when(connection) {
        is HttpURLConnection -> connection
        else -> throw RuntimeException("Type of connection for ${this} not found")
    }
}

private operator fun URL.component1() = this.protocol
private operator fun URL.component2() = this.authority
private operator fun URL.component3() = this.path

fun String.addDomainIfRelative(basedOn: String): String {
    if (this.contains(UrlService.PROTOCOL_SEPARATOR)) {
        return this
    }

    val isFromRoot = this.startsWith("/")

    val (protocol, authority, path) = URL(basedOn)
    val lastSlash = if (isFromRoot) "" else StringUtils.substringBeforeLast(path, "/")
    val firstSlash = if (isFromRoot) "" else "/"

    return "$protocol${UrlService.PROTOCOL_SEPARATOR}$authority$lastSlash$firstSlash$this"
}
