package com.lastwave.app.ui.shell

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MainTabTest {

    @Test
    fun mainTabOrder_isLibraryStatsPlaylists() {
        assertThat(MainTab.entries.map { it.name })
            .isEqualTo(listOf("LIBRARY", "STATS", "PLAYLISTS"))
    }

    @Test
    fun firstTabIsLibrary() {
        assertThat(MainTab.entries.first()).isEqualTo(MainTab.LIBRARY)
    }

    @Test
    fun tabLabelResourcesAreDistinct() {
        assertThat(MainTab.entries.map { it.labelRes }.toSet()).hasSize(3)
    }

    @Test
    fun libraryTabUsesNavLibraryString() {
        assertThat(MainTab.LIBRARY.labelRes).isEqualTo(com.lastwave.app.R.string.nav_library)
    }
}
