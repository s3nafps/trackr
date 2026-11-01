package com.trackr.app.data.update

import com.trackr.app.BuildConfig
import com.trackr.app.data.local.UserPrefs
import com.trackr.app.domain.util.AppVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URI
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tag: String,
    @SerialName("html_url") val pageUrl: String,
    val body: String? = null,
    val assets: List<GithubAsset> = emptyList(),
    val draft: Boolean = false,
    val prerelease: Boolean = false,
)

@Serializable
data class GithubAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    /** GitHub's checksum for the file, as "sha256:<hex>". */
    val digest: String? = null,
)

/** One release's "What's new" bullets. */
data class ReleaseChanges(val version: String, val items: List<String>)

/**
 * A newer release than the installed app. [apkSha256] is the checksum the downloaded APK must match. [changes] holds
 * every release newer than the installed one, newest first, so someone who skipped versions sees all of it;
 * [highlights] is the newest release's part.
 */
data class UpdateInfo(
    val version: String,
    val pageUrl: String,
    val apkUrl: String?,
    val highlights: List<String>,
    val apkSha256: String? = null,
    val changes: List<ReleaseChanges> = listOf(ReleaseChanges(version, highlights)),
)

sealed interface UpdateCheck {
    data class Available(val info: UpdateInfo) : UpdateCheck
    data object UpToDate : UpdateCheck
    /** Nothing to show: checked recently, or the user already chose "Later" for this version. */
    data object Skipped : UpdateCheck
}

/** Reads the published releases of the app's GitHub repository. */
class GithubReleases @Inject constructor(@Named("github") private val client: OkHttpClient, private val json: Json) {
    /** The most recent releases, newest first (drafts and pre-releases included; [UpdateRepository] skips them). */
    suspend fun recent(): List<GithubRelease> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(RELEASES_URL).header("Accept", "application/vnd.github+json").build()
        client.newCall(request).execute().use { r ->
            when {
                r.code == 404 -> emptyList()
                !r.isSuccessful -> throw IOException("GitHub returned ${r.code}")
                else -> json.decodeFromString(ListSerializer(GithubRelease.serializer()), r.body?.string().orEmpty())
            }
        }
    }

    companion object {
        const val RELEASES_URL = "https://api.github.com/repos/s3nafps/trackr/releases?per_page=30"
    }
}

/**
 * Tells the user when a newer APK is on GitHub, since a sideloaded app gets no store updates. Automatic checks run
 * at most every [CHECK_INTERVAL_MS] and skip a version the user put off; checks from About always ask GitHub.
 */
@Singleton
class UpdateRepository @Inject constructor(private val releases: GithubReleases, private val prefs: UserPrefs) {
    /** Throws when GitHub can't be reached. */
    suspend fun check(manual: Boolean, now: Long = System.currentTimeMillis(), current: String = BuildConfig.VERSION_NAME): UpdateCheck {
        if (!manual && now - prefs.lastUpdateCheck.first() < CHECK_INTERVAL_MS) return UpdateCheck.Skipped
        val info = toUpdate(releases.recent(), current)
        prefs.setLastUpdateCheck(now)
        return when {
            info == null -> UpdateCheck.UpToDate
            !manual && info.version == prefs.dismissedUpdate.first() -> UpdateCheck.Skipped
            else -> UpdateCheck.Available(info)
        }
    }

    /**
     * The newer release, if any, for the background check: no throttling and nothing recorded, so the next launch still
     * offers it. Throws when GitHub can't be reached.
     */
    suspend fun available(current: String = BuildConfig.VERSION_NAME): UpdateInfo? = toUpdate(releases.recent(), current)

    /** "Later": automatic checks stay quiet about [version]. */
    suspend fun dismiss(version: String) = prefs.setDismissedUpdate(version)

    companion object {
        const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L

        /** An APK we'll offer to install: from GitHub over HTTPS, with the SHA-256 GitHub publishes for it. */
        private fun GithubAsset.isInstallable(): Boolean {
            val fromGithub = runCatching { URI(downloadUrl) }.getOrNull()?.let { it.scheme == "https" && it.host == "github.com" } == true
            return fromGithub && digest?.startsWith("sha256:") == true
        }

        /** The release as an update, or null when it isn't newer than [current] (or its tag isn't a version). */
        fun toUpdate(release: GithubRelease, current: String): UpdateInfo? = toUpdate(listOf(release), current)

        /**
         * The newest published release as an update, with the changes of every release newer than [current]; null when
         * none is newer. Drafts, pre-releases and tags that aren't versions are skipped.
         */
        fun toUpdate(releases: List<GithubRelease>, current: String): UpdateInfo? {
            val installed = AppVersion.parse(current) ?: return null
            val newer = releases.filter { !it.draft && !it.prerelease }
                .mapNotNull { r -> AppVersion.parse(r.tag)?.let { it to r } }
                .filter { (v, _) -> v > installed }
                .sortedByDescending { (v, _) -> v }
            val (latest, release) = newer.firstOrNull() ?: return null
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk") && it.isInstallable() }
            val newest = highlights(release.body.orEmpty())
            return UpdateInfo(
                version = latest.toString(),
                pageUrl = release.pageUrl,
                apkUrl = apk?.downloadUrl,
                highlights = newest,
                apkSha256 = apk?.digest?.removePrefix("sha256:"),
                changes = newer.map { (v, r) -> ReleaseChanges(v.toString(), highlights(r.body.orEmpty())) },
            )
        }

        /**
         * The bullet points of the release notes' "What's new" section (or of the whole text when there is none),
         * without Markdown emphasis, for the update dialog and notification.
         */
        fun highlights(notes: String, max: Int = Int.MAX_VALUE): List<String> {
            val lines = notes.lines()
            val start = lines.indexOfFirst { it.trimStart().startsWith("#") && it.contains("what's new", ignoreCase = true) }
            val section = if (start >= 0) lines.drop(start + 1).takeWhile { !it.trimStart().startsWith("#") } else lines
            return section.map { it.trim() }.filter { it.startsWith("- ") || it.startsWith("* ") }
                .map { it.drop(2).replace("**", "").replace("__", "").trim() }
                .filter { it.isNotEmpty() }.take(max)
        }
    }
}
