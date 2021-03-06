package com.github.davinkevin.podcastserver.service.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Created by kevin on 12/04/2016 for Podcast Server
 */
@ConfigurationProperties("podcastserver.externaltools")
data class ExternalTools(
        var ffmpeg: String = "/usr/local/bin/ffmpeg",
        var ffprobe: String = "/usr/local/bin/ffprobe",
        var rtmpdump: String = "/usr/local/bin/rtmpdump",
        var youtubedl: String = "/usr/local/bin/youtube-dl"
)
