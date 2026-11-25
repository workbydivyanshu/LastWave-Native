package com.lastwave.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastwave.app.data.local.db.DownloadedTrackDao
import com.lastwave.app.data.lyrics.LrcFileSaver
import com.lastwave.app.data.lyrics.LrcTrack
import com.lastwave.app.playback.PlayableTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LyricsSaveViewModel @Inject constructor(
    private val dao: DownloadedTrackDao,
) : ViewModel() {
    fun save(track: PlayableTrack, lyrics: LyricsUiState) {
        val success = lyrics as? LyricsUiState.Success ?: return
        val path = track.playbackUrl ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                LrcFileSaver.save(
                    track = LrcTrack(title = track.title, artist = track.artist, filePath = path),
                    lines = success.lines,
                    plainLyrics = success.plainLyrics,
                    dao = dao,
                )
            }
        }
    }
}
