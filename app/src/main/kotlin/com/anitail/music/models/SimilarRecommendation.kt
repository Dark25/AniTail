package com.anitail.music.models

import com.anitail.innertube.models.YTItem
import com.anitail.music.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
