package com.github.davinkevin.podcastserver.item

import com.github.davinkevin.podcastserver.manager.ItemDownloadManager
import com.github.davinkevin.podcastserver.podcast.PodcastRepositoryV2
import com.github.davinkevin.podcastserver.service.FileService
import com.github.davinkevin.podcastserver.service.properties.PodcastServerParameters
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.junit.jupiter.api.Test
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.context.annotation.UserConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import reactor.core.publisher.Mono
import java.util.concurrent.TimeUnit

class ItemConfigTest {

    private val contextRunner = ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(ItemDependencyMockConfig::class.java, ItemConfig::class.java))

    @Test
    fun `should trigger a reset of all item with a downloading status (started or paused) `() {
        /* Given */
        /* When */
        contextRunner
                /* Then */
                .withConfiguration(UserConfigurations.of(MockForResetAtStartupConfig::class.java))
                .run {
                    assertThat(it).hasSingleBean(CommandLineRunner::class.java)

                    val repo = it.getBean(ItemRepositoryV2::class.java)
                    val clr = it.getBean(CommandLineRunner::class.java)
                    clr.run()


                    verify(repo, times(1)).resetItemWithDownloadingState()
                }
    }
}

@Configuration
class MockForResetAtStartupConfig {
    @Bean @Primary fun mockItemRepository(): ItemRepositoryV2 = mock<ItemRepositoryV2>().apply {
        whenever(this.resetItemWithDownloadingState()).thenReturn(Mono.empty())
    }
}

@Configuration
class ItemDependencyMockConfig {
    @Bean @Primary fun mockJOOQ(): DSLContext = mock()
    @Bean @Primary fun mockItemService(): ItemService = mock()
    @Bean @Primary fun mockFileService(): FileService = mock()
    @Bean @Primary fun mockPodcastRepository(): PodcastRepositoryV2 = mock()
    @Bean @Primary fun mockIDM(): ItemDownloadManager = mock()
    @Bean @Primary fun mockPodcastProps(): PodcastServerParameters = mock()
}
