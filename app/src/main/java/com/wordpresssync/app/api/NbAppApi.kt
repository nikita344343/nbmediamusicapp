package com.wordpresssync.app.api

import com.wordpresssync.app.api.model.CatalogResponse
import com.wordpresssync.app.api.model.StatsResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * API плагина NB App REST API: каталог и статистика (требуют авторизации).
 */
interface NbAppApi {

    @GET("nb-app/v1/catalog")
    suspend fun getCatalog(): Response<CatalogResponse>

    @GET("nb-app/v1/stats")
    suspend fun getStats(
        @Query("date_from") dateFrom: String? = null,
        @Query("date_to") dateTo: String? = null
    ): Response<StatsResponse>
}
