package com.wordpresssync.app.api.model

import com.google.gson.annotations.SerializedName

data class StatsResponse(
    @SerializedName("total_plays") val totalPlays: Int = 0,
    val platforms: Map<String, Int>? = null,
    val tracks: List<StatsTrack>? = null
)

data class StatsTrack(
    val name: String?,
    val version: String?,
    val plays: Int = 0
)
