package eu.kanade.tachiyomi.data.track.kavita

import android.graphics.Color
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.EnhancedMangaTracker
import eu.kanade.tachiyomi.data.track.MangaTracker
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.sourcePreferences
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy
import java.security.MessageDigest
import tachiyomi.domain.track.manga.model.MangaTrack as DomainTrack

class Kavita(id: Long) : BaseTracker(id, "Kavita"), EnhancedMangaTracker, MangaTracker {

    companion object {
        const val STATUS_UNREAD = 1L
        const val STATUS_READING = 2L
        const val STATUS_COMPLETED = 3L
    }

    var authentications: OAuth? = null

    private val interceptor by lazy { KavitaInterceptor(this) }
    val api by lazy { KavitaApi(client, interceptor) }

    private val sourceManager: MangaSourceManager by injectLazy()

    override fun getLogo(): Int = R.drawable.ic_tracker_kavita

    override fun getLogoColor() = Color.rgb(74, 198, 148)

    override fun getStatusListManga(): List<Long> = listOf(STATUS_UNREAD, STATUS_READING, STATUS_COMPLETED)

    override fun getStatusForManga(status: Long): StringResource? = when (status) {
        STATUS_UNREAD -> MR.strings.unread
        STATUS_READING -> MR.strings.reading
        STATUS_COMPLETED -> MR.strings.completed
        else -> MR.strings.unknown_status // Optional: define an unknown status string in your resources
    }

    override fun getReadingStatus(): Long = STATUS_READING

    override fun getRereadingStatus(): Long = -1

    override fun getCompletionStatus(): Long = STATUS_COMPLETED

    override fun getScoreList(): ImmutableList<String> = persistentListOf()

    override fun displayScore(track: DomainTrack): String = ""

    override suspend fun update(track: MangaTrack, didReadChapter: Boolean): MangaTrack {
        if (track.status != STATUS_COMPLETED) {
            if (didReadChapter) {
                track.status = determineStatus(track)
            }
        }
        return api.updateProgress(track)
    }

    private fun determineStatus(track: MangaTrack): Long {
        return if (track.last_chapter_read.toLong() == track.total_chapters && track.total_chapters > 0) {
            STATUS_COMPLETED
        } else {
            STATUS_READING
        }
    }

    override suspend fun bind(track: MangaTrack, hasReadChapters: Boolean): MangaTrack {
        return track
    }

    override suspend fun searchManga(query: String): List<MangaTrackSearch> {
        TODO("Not yet implemented: search")
    }

    override suspend fun refresh(track: MangaTrack): MangaTrack {
        val remoteTrack = api.getTrackSearch(track.tracking_url)
        track.copyPersonalFrom(remoteTrack)
        track.total_chapters = remoteTrack.total_chapters
        return track
    }

    override suspend fun login(username: String, password: String) {
        saveCredentials("user", "pass")
    }

    // [Tracker].isLogged works by checking that credentials are saved.
    // By saving dummy, unused credentials, we can activate the tracker simply by login/logout
    override fun loginNoop() {
        saveCredentials("user", "pass")
    }

    override fun getAcceptedSources() = listOf("eu.kanade.tachiyomi.extension.all.kavita.Kavita")

    override suspend fun match(manga: Manga): MangaTrackSearch? {
        return try {
            api.getTrackSearch(manga.url)
        } catch (e: Exception) {
            // Log the error for debugging purposes
            Log.e("Kavita", "Error matching manga: ${manga.url}", e)
            null
        }
    }

    override fun isTrackFrom(track: DomainTrack, manga: Manga, source: MangaSource?): Boolean =
        track.remoteUrl == manga.url && source?.let { accept(it) } == true

    override fun migrateTrack(track: DomainTrack, manga: Manga, newSource: MangaSource): DomainTrack? =
        if (accept(newSource)) {
            track.copy(remoteUrl = manga.url)
        } else {
            null
        }

    // Refactored loadOAuth() to reduce duplication
    fun loadOAuth() {
        val oauth = OAuth()
        for (id in 1..3) {
            val authentication = oauth.authentications[id - 1]
            val sourceId = generateSourceId(id)
            val preferences = getSourcePreferences(sourceId)

            val (apiUrl, apiKey) = getApiCredentials(preferences)
            if (apiUrl.isNullOrEmpty() || apiKey.isNullOrEmpty()) continue

            val token = fetchToken(apiUrl, apiKey)
            if (token.isNullOrEmpty()) continue

            authentication.apiUrl = apiUrl
            authentication.jwtToken = token
        }
        authentications = oauth
    }

    private fun generateSourceId(id: Int): Long {
        val key = "kavita_$id/all/1" // Hardcoded versionID to 1
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }
            .reduce(Long::or) and Long.MAX_VALUE
    }

    private fun getSourcePreferences(sourceId: Long): SourcePreferences {
        return (sourceManager.get(sourceId) as ConfigurableSource).sourcePreferences()
    }

    private fun getApiCredentials(preferences: SourcePreferences): Pair<String, String> {
        val apiUrl = preferences.getString("APIURL", "")
        val apiKey = preferences.getString("APIKEY", "")
        return apiUrl to apiKey
    }

    private fun fetchToken(apiUrl: String, apiKey: String): String? {
        return api.getNewToken(apiUrl = apiUrl, apiKey = apiKey)
    }
}
