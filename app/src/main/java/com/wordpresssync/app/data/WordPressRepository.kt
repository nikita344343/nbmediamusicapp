package com.wordpresssync.app.data

import android.util.Base64
import com.wordpresssync.app.api.NbAppApi
import com.wordpresssync.app.api.WordPressApi
import com.wordpresssync.app.api.model.CatalogResponse
import com.wordpresssync.app.api.model.StatsResponse
import com.wordpresssync.app.api.model.WpPost
import com.wordpresssync.app.api.model.WpUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class WordPressRepository(private val settings: SettingsRepository) {

    private var api: WordPressApi? = null
    private var nbAppApi: NbAppApi? = null

    private fun getOrCreateApi(): WordPressApi? {
        val baseUrl = settings.getApiBaseUrl() ?: return null
        if (api == null) {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
                .build()
            api = Retrofit.Builder()
                .baseUrl("$baseUrl/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(WordPressApi::class.java)
        }
        return api
    }

    private fun getOrCreateNbAppApi(): NbAppApi? {
        val baseUrl = settings.getApiBaseUrl() ?: return null
        val user = settings.username ?: return null
        val pass = settings.appPassword ?: return null
        if (nbAppApi == null) {
            val credentials = Base64.encodeToString("$user:$pass".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val authInterceptor = Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Basic $credentials")
                    .build()
                chain.proceed(request)
            }
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(authInterceptor)
                .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
                .build()
            nbAppApi = Retrofit.Builder()
                .baseUrl("$baseUrl/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(NbAppApi::class.java)
        }
        return nbAppApi
    }

    suspend fun getPosts(perPage: Int = 20, page: Int = 1): Result<List<WpPost>> = withContext(Dispatchers.IO) {
        val api = getOrCreateApi()
            ?: return@withContext Result.failure(Exception("URL сайта не задан. Укажи его в настройках."))
        runCatching {
            val response = api.getPosts(perPage = perPage, page = page)
            if (response.isSuccessful) {
                response.body() ?: emptyList()
            } else {
                throw Exception("Ошибка ${response.code()}: ${response.message()}")
            }
        }
    }

    suspend fun getUsers(perPage: Int = 50, page: Int = 1): Result<List<WpUser>> = withContext(Dispatchers.IO) {
        val api = getOrCreateApi()
            ?: return@withContext Result.failure(Exception("URL сайта не задан."))
        runCatching {
            val response = api.getUsers(perPage = perPage, page = page)
            if (response.isSuccessful) {
                response.body() ?: emptyList()
            } else {
                throw Exception("Ошибка ${response.code()}")
            }
        }
    }

    suspend fun getCatalog(): Result<CatalogResponse> = withContext(Dispatchers.IO) {
        val api = getOrCreateNbAppApi()
            ?: return@withContext Result.failure(Exception("Укажи логин и пароль приложений в настройках."))
        runCatching {
            val response = api.getCatalog()
            if (response.isSuccessful) {
                response.body() ?: throw Exception("Пустой ответ")
            } else {
                throw Exception("Ошибка ${response.code()}: ${response.message()}")
            }
        }
    }

    suspend fun getStats(dateFrom: String? = null, dateTo: String? = null): Result<StatsResponse> = withContext(Dispatchers.IO) {
        val api = getOrCreateNbAppApi()
            ?: return@withContext Result.failure(Exception("Укажи логин и пароль приложений в настройках."))
        runCatching {
            val response = api.getStats(dateFrom = dateFrom, dateTo = dateTo)
            if (response.isSuccessful) {
                response.body() ?: throw Exception("Пустой ответ")
            } else {
                throw Exception("Ошибка ${response.code()}: ${response.message()}")
            }
        }
    }

    fun clearApiCache() {
        api = null
        nbAppApi = null
    }
}
