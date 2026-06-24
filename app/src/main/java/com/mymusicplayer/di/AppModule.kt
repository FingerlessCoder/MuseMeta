package com.mymusicplayer.di

import com.mymusicplayer.data.audio.AudioFocusManager
import com.mymusicplayer.data.audio.MusicPlayerController
import com.mymusicplayer.data.preferences.SettingsDataStore
import com.mymusicplayer.data.scanner.FileSystemScanner
import com.mymusicplayer.data.scanner.MediaStoreScanner
import com.mymusicplayer.data.scanner.MetadataParser
import com.mymusicplayer.data.scanner.ScanRepository
import com.mymusicplayer.domain.repository.MusicRepository
import com.mymusicplayer.domain.repository.MusicRepositoryImpl
import com.mymusicplayer.ui.screens.albums.AlbumViewModel
import com.mymusicplayer.ui.screens.artists.ArtistViewModel
import com.mymusicplayer.ui.screens.directory_picker.DirectoryPickerViewModel
import com.mymusicplayer.ui.screens.metadata.MetadataEditorViewModel
import com.mymusicplayer.ui.screens.player.PlayerViewModel
import com.mymusicplayer.ui.screens.playlists.PlaylistViewModel
import com.mymusicplayer.ui.screens.scan.ScanViewModel
import com.mymusicplayer.ui.screens.settings.SettingsViewModel
import com.mymusicplayer.ui.screens.tracks.TrackListViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<MusicRepository> { MusicRepositoryImpl(get(), get(), get(), get(), get()) }
    single { MusicPlayerController(androidContext(), get()) }
    single { AudioFocusManager(androidContext()) }
    single { MediaStoreScanner(get()) }
    single { FileSystemScanner() }
    single { MetadataParser() }
    single { ScanRepository(get(), get(), get(), get(), get(), get()) }
    single { SettingsDataStore(androidContext()) }
    factory { androidContext().contentResolver }

    viewModel { TrackListViewModel(get(), get()) }
    viewModel { AlbumViewModel(get()) }
    viewModel { ArtistViewModel(get()) }
    viewModel { PlayerViewModel(get(), get()) }
    viewModel { PlaylistViewModel(get(), get()) }
    viewModel { SettingsViewModel(get()) }
    viewModel { ScanViewModel(get(), get()) }
    viewModel { DirectoryPickerViewModel(get(), get()) }
    viewModel { MetadataEditorViewModel(get()) }
}
