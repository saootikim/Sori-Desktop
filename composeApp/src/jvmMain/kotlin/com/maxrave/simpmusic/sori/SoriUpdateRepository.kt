package com.maxrave.simpmusic.sori

import com.maxrave.domain.data.model.update.UpdateData
import com.maxrave.domain.repository.UpdateRepository
import com.maxrave.domain.utils.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Sori: update check against saootikim/Sori-Desktop releases.
 *
 * Upstream's UpdateRepositoryImpl lives in the core submodule and is hardwired to
 * maxrave-dev/SimpMusic; this replaces it through [soriUpdateModule] so core stays untouched.
 * The app shows the update dialog whenever the release tag differs from "v<versionName>".
 */
private const val SORI_RELEASES_API = "https://api.github.com/repos/saootikim/Sori-Desktop/releases/latest"

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val body: String? = null,
)

private val releaseJson = Json { ignoreUnknownKeys = true }

internal fun parseLatestRelease(body: String): UpdateData =
    releaseJson.decodeFromString<GithubRelease>(body).let {
        UpdateData(
            tagName = it.tagName.orEmpty(),
            releaseTime = it.publishedAt,
            body = it.body.orEmpty(),
        )
    }

class SoriUpdateRepository(
    private val client: HttpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build(),
) : UpdateRepository {
    override fun checkForGithubReleaseUpdate(): Flow<Resource<UpdateData>> =
        flow {
            val result =
                runCatching {
                    val request =
                        HttpRequest
                            .newBuilder(URI(SORI_RELEASES_API))
                            .timeout(Duration.ofSeconds(15))
                            .header("Accept", "application/vnd.github+json")
                            .GET()
                            .build()
                    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                    check(response.statusCode() == 200) { "GitHub returned HTTP ${response.statusCode()}" }
                    parseLatestRelease(response.body())
                }
            emit(
                result.fold(
                    onSuccess = { Resource.Success(it) },
                    onFailure = { Resource.Error(it.message ?: "Update check failed") },
                ),
            )
        }.flowOn(Dispatchers.IO)

    // Sori is not published on F-Droid; both update channels use GitHub releases.
    override fun checkForFdroidUpdate(): Flow<Resource<UpdateData>> = checkForGithubReleaseUpdate()
}

val soriUpdateModule =
    module {
        single<UpdateRepository> { SoriUpdateRepository() }
    }
