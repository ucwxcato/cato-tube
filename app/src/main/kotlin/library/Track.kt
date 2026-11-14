package org.catosaur.app.library

import kotlinx.serialization.Serializable

/** Version written to library.json. Files with a newer version open read-only. */
internal const val LIBRARY_SCHEMA_VERSION = 1

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String = "",
    val album: String? = null,
    val durationMs: Long? = null,
    /** Path relative to the library root, with "/" separators, for example "songs/Track.mp3". */
    val relativePath: String,
    val dateAddedEpochMs: Long,
    val sourceUrl: String? = null,
    val youtubeId: String? = null,
    val artworkRelativePath: String? = null,
)

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    /** Ordered track IDs. A playlist refers to tracks by ID, never by file path. */
    val trackIds: List<String> = emptyList(),
    val createdEpochMs: Long,
)

@Serializable
data class LibraryData(
    val schemaVersion: Int = LIBRARY_SCHEMA_VERSION,
    val savedAtEpochMs: Long = 0,
    val tracks: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
)
