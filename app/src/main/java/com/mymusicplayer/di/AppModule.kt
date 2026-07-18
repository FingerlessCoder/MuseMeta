package com.mymusicplayer.di

import com.mymusicplayer.data.audio.AudioFocusManager
import com.mymusicplayer.data.audio.MusicPlayerController
import com.mymusicplayer.data.lyrics.LyricsFetcher
import com.mymusicplayer.data.preferences.SettingsDataStore
import com.mymusicplayer.data.scanner.FileSystemScanner
import com.mymusicplayer.data.scanner.MediaStoreScanner
import com.mymusicplayer.data.scanner.MetadataParser
import com.mymusicplayer.data.scanner.ScanRepository
import com.mymusicplayer.domain.repository.MusicRepository
import com.mymusicplayer.domain.repository.MusicRepositoryImpl
import com.mymusicplayer.ui.screens.directory_picker.DirectoryPickerViewModel
import com.mymusicplayer.data.db.dao.PlaylistDao
import com.mymusicplayer.ui.screens.albums.AlbumDetailViewModel
import com.mymusicplayer.ui.screens.artists.ArtistDetailViewModel
import com.mymusicplayer.ui.screens.home.HomeViewModel
import com.mymusicplayer.ui.screens.home.MultiSelectViewModel
import com.mymusicplayer.ui.screens.player.PlayerViewModel
import com.mymusicplayer.ui.screens.scan.ScanViewModel
import com.mymusicplayer.ui.screens.search.SearchViewModel
import com.mymusicplayer.ui.screens.settings.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

val appModule = module {
    single<MusicRepository> { MusicRepositoryImpl(androidContext(), get(), get(), get(), get(), get(), get()) }
    single { MusicPlayerController(androidContext(), get<SettingsDataStore>()) }
    single { AudioFocusManager(androidContext()) }
    single { MediaStoreScanner(get()) }
    single { FileSystemScanner() }
    single { MetadataParser() }
    single { ScanRepository(androidContext(), get(), get(), get(), get(), get(), get()) }
    single { SettingsDataStore(androidContext()) }
    single { LyricsFetcher(androidContext()) }
    factory { androidContext().contentResolver }

    viewModel { HomeViewModel(get(), get(), get<PlaylistDao>(), get()) }
    viewModel { MultiSelectViewModel(get(), get(), get()) }
    viewModel { PlayerViewModel(get(), get(), get()) }
    viewModel { SearchViewModel(get(), get()) }
    viewModel { SettingsViewModel(get(), get()) }
    viewModel { ScanViewModel(get(), get()) }
    viewModel { DirectoryPickerViewModel(get(), get()) }
    viewModel { params -> AlbumDetailViewModel(params.get(), get(), get()) }
    viewModel { params -> ArtistDetailViewModel(params.get(), get(), get()) }
}
