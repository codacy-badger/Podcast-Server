package com.github.davinkevin.podcastserver.download.downloaders.youtubedl

import arrow.core.Try
import arrow.core.getOrElse
import com.github.davinkevin.podcastserver.download.DownloadRepository
import com.github.davinkevin.podcastserver.download.downloaders.youtubedl.YoutubeDlService
import com.github.davinkevin.podcastserver.manager.downloader.AbstractDownloader
import com.github.davinkevin.podcastserver.manager.downloader.DownloadingInformation
import com.github.davinkevin.podcastserver.manager.downloader.DownloadingItem
import com.github.davinkevin.podcastserver.messaging.MessagingTemplate
import com.github.davinkevin.podcastserver.service.MimeTypeService
import com.github.davinkevin.podcastserver.service.properties.ExternalTools
import com.github.davinkevin.podcastserver.service.properties.PodcastServerParameters
import com.sapher.youtubedl.DownloadProgressCallback
import com.sapher.youtubedl.YoutubeDL
import com.sapher.youtubedl.YoutubeDLRequest
import com.sapher.youtubedl.YoutubeDLResponse
import org.apache.commons.io.FilenameUtils.getExtension
import org.apache.commons.io.FilenameUtils.removeExtension
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import kotlin.math.roundToInt
import kotlin.streams.asSequence

/**
 * Created by kevin on 2019-07-21
 */
class YoutubeDlDownloader(
        downloadRepository: DownloadRepository,
        podcastServerParameters: PodcastServerParameters,
        template: MessagingTemplate,
        mimeTypeService: MimeTypeService,
        clock: Clock,
        private val youtubeDl: YoutubeDlService
) : AbstractDownloader(downloadRepository, podcastServerParameters, template, mimeTypeService, clock) {

    private val log = LoggerFactory.getLogger(YoutubeDlDownloader::class.java)

    override fun download(): DownloadingItem {
        log.info("Starting download of ${downloadingInformation.item.url}")

        val url = downloadingInformation.url()
        downloadingInformation = downloadingInformation.fileName(youtubeDl.extractName(url))

        target = computeTargetFile(downloadingInformation)

        Try.invoke { youtubeDl.download(url, target!!, DownloadProgressCallback { p, _ ->
            val broadcast = downloadingInformation.item.progression < p.roundToInt()
            if (broadcast) {
                downloadingInformation = downloadingInformation.progression(p.roundToInt())
                broadcast(downloadingInformation.item)
            }
        }) }.getOrElse { RuntimeException(it) }

        finishDownload()

        return downloadingInformation.item
    }

    override fun finishDownload() {
        val t = target!!

        val savedPath = Files.walk(t.parent).asSequence()
                .first { it.toAbsolutePath().toString().startsWith(t.toAbsolutePath().toString()) }
                ?: throw RuntimeException("No file found after download with youtube-dl...")

        log.debug("File downloaded by youtube-dl is $savedPath")

        if(savedPath != t) {
            val realExtension = getExtension(savedPath.toString())
            val fileNameWithoutAnyExtension = removeExtension(removeExtension(t.fileName.toString()));

            target = t.resolveSibling("$fileNameWithoutAnyExtension.$realExtension$temporaryExtension")
            Try.invoke { Files.move(savedPath, target!!) }
        }

        super.finishDownload()
    }

    override fun compatibility(downloadingInformation: DownloadingInformation): Int {
        val url = downloadingInformation.urls.first().toLowerCase()

        return when {
            downloadingInformation.urls.size > 1 -> Int.MAX_VALUE
            isFromVideoPlatform(url) -> 5
            downloadingInformation.urls.size == 1 && downloadingInformation.urls.first().startsWith("http") -> Integer.MAX_VALUE - 1
            else -> Integer.MAX_VALUE
        }
    }
}
