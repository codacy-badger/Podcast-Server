package com.github.davinkevin.podcastserver.service.factory

import com.github.axet.wget.WGet
import com.github.axet.wget.info.DownloadInfo
import com.nhaarman.mockitokotlin2.mock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URL
import java.nio.file.Paths

/**
 * Created by kevin on 22/01/2016 for Podcast Server
 */
class WGetFactoryTest {

    private val wGetFactory = WGetFactory()

    @Test
    fun `should parse with wget`() {
        /* Given */
        val downloadInfo = mock<DownloadInfo>()
        val path = Paths.get("/tmp/afile.tmp")

        /* When */
        val wGet = wGetFactory.newWGet(downloadInfo, path.toFile())

        /* Then */
        assertThat(wGet).isNotNull().isInstanceOf(WGet::class.java)
    }

    @Test
    fun `should get wget download info`() {
        /* Given */
        val url = "http://www.youtube.com/user/cauetofficiel"

        /* When */
        val downloadInfo = wGetFactory.newDownloadInfo(url)

        /* Then */
        assertThat(downloadInfo).isNotNull().isInstanceOf(DownloadInfo::class.java)
    }

}
