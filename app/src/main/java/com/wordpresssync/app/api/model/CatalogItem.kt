package com.wordpresssync.app.api.model

import com.google.gson.annotations.SerializedName

data class CatalogResponse(
    val items: List<CatalogItem>
)

data class CatalogItem(
    val id: Long,
    val title: String?,
    val artist: String?,
    val date: String?,
    val upc: String?,
    val genre: String?,
    val status: String?,
    @SerializedName("status_class") val statusClass: String?,
    @SerializedName("cover_url") val coverUrl: String?,
    @SerializedName("total_plays") val totalPlays: Int = 0,
    val platforms: Map<String, Int>? = null,
    val tracks: List<CatalogTrack>? = null
)

data class CatalogTrack(
    val track: String?,
    val artist: String?,
    val version: String?,
    val isrc: String?
)
