package com.metrolist.innertube.models

import com.anitail.innertube.models.Continuation
import com.anitail.innertube.models.MusicPlaylistShelfRenderer
import com.anitail.innertube.models.MusicShelfRenderer
import com.anitail.innertube.models.Tabs
import kotlinx.serialization.Serializable

@Serializable
data class TwoColumnBrowseResultsRenderer(
    val secondaryContents: SecondaryContents?,
    val tabs: List<Tabs.Tab>?
) {
    @Serializable
    data class SecondaryContents(
        val sectionListRenderer: SectionListRenderer?
    )

    @Serializable
    data class SectionListRenderer(
        val contents: List<Content>?,
        val continuations: List<Continuation>?,
    ) {
        @Serializable
        data class Content(
            val musicPlaylistShelfRenderer: MusicPlaylistShelfRenderer?,
            val musicShelfRenderer: MusicShelfRenderer?
        )
    }
}
