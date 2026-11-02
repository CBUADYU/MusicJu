package com.musicapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.musicapp.ui.theme.HnrAnimation
import androidx.navigation.navArgument
import com.musicapp.data.local.LoginDataStore
import com.musicapp.data.local.dao.UserPlatformDao
import com.musicapp.data.repository.AuthRepository
import com.musicapp.data.repository.DownloadRepository
import com.musicapp.data.repository.LocalMusicRepository
import com.musicapp.data.repository.MusicRepository
import com.musicapp.data.repository.PlayHistoryRepository
import com.musicapp.data.repository.PlaylistRepository
import com.musicapp.domain.model.Song
import com.musicapp.ui.screens.favorites.FavoritesScreen
import com.musicapp.ui.screens.favorites.FavoritesViewModel
import com.musicapp.ui.screens.history.HistoryScreen
import com.musicapp.ui.screens.history.HistoryViewModel
import com.musicapp.ui.screens.home.HomeScreen
import com.musicapp.ui.screens.home.HomeViewModel
import com.musicapp.ui.screens.import_.ImportScreen
import com.musicapp.ui.screens.import_.ImportViewModel
import com.musicapp.ui.screens.local_music.LocalMusicScreen
import com.musicapp.ui.screens.local_music.LocalMusicViewModel
import com.musicapp.ui.screens.player.FoliaLyricsScreen
import com.musicapp.ui.screens.player.PlayerScreen
import com.musicapp.ui.screens.player.PlayerViewModel
import com.musicapp.ui.screens.player.mv.LyricMvFullscreen
import com.musicapp.ui.screens.player.mv.LyricMvScreen
import com.musicapp.ui.screens.player.mv.LyricMvStyle
import com.musicapp.ui.screens.playlist.PlaylistDetailScreen
import com.musicapp.ui.screens.playlist.PlaylistViewModel
import com.musicapp.ui.screens.profile.ProfileHubScreen
import com.musicapp.ui.screens.profile.ProfileViewModel
import com.musicapp.ui.screens.recommend.RecommendScreen
import com.musicapp.ui.screens.search.SearchScreen
import com.musicapp.ui.screens.search.SearchViewModel
import com.musicapp.ui.screens.settings.SettingsScreen
import com.musicapp.ui.screens.settings.SettingsViewModel
import android.app.Application

object NavRoutes {
    const val RECOMMEND = "recommend"
    const val LIBRARY = "library"
    const val FAVORITES = "favorites"
    const val PROFILE = "profile"

    const val SEARCH = "search"
    const val IMPORT = "import"
    const val PLAYLIST_DETAIL = "playlist_detail/{playlistId}"
    const val PLAYER = "player_full"
    const val SONG_DETAIL = "song_detail/{playlist_id}/{song_row_id}"
    const val LYRICS = "lyrics"
    const val LYRIC_MV = "lyric_mv"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val LOCAL_MUSIC = "local_music"

    fun playlistDetail(playlistId: Long) = "playlist_detail/$playlistId"
    fun songDetail(playlistId: Long, songRowId: Long) = "song_detail/$playlistId/$songRowId"
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun AppNavGraph(
    navController: NavHostController,
    playlistRepository: PlaylistRepository,
    musicRepository: MusicRepository,
    authRepository: AuthRepository,
    playHistoryRepository: PlayHistoryRepository,
    downloadRepository: DownloadRepository,
    localMusicRepository: LocalMusicRepository,
    playerViewModel: PlayerViewModel,
    loginDataStore: LoginDataStore? = null,
    userPlatformDao: UserPlatformDao? = null,
    app: Application? = null,
    modifier: Modifier = Modifier
) {
    val openPlayer: () -> Unit = {
        navController.navigate(NavRoutes.PLAYER) { launchSingleTop = true }
    }
    fun playAndOpen(songs: List<Song>, index: Int) {
        playerViewModel.playSong(songs, index)
        navController.navigate(NavRoutes.PLAYER) { launchSingleTop = true }
    }
    fun openSongDetailOrPlay(song: Song, playlistId: Long) {
        if (song.id > 0) navController.navigate(NavRoutes.songDetail(playlistId, song.id))
        else playAndOpen(listOf(song), 0)
    }

    NavHost(navController = navController, startDestination = NavRoutes.RECOMMEND, modifier = modifier) {
        // ---- RECOMMEND ----
        composable(
            route = NavRoutes.RECOMMEND,
            enterTransition = { HnrAnimation.fadeInPage },
            exitTransition = { HnrAnimation.fadeOutPage },
            popEnterTransition = { HnrAnimation.fadeInPage },
            popExitTransition = { HnrAnimation.fadeOutPage }
        ) {
            val vm: com.musicapp.ui.screens.recommend.RecommendViewModel = viewModel(
                factory = com.musicapp.ui.screens.recommend.RecommendViewModel.Factory(playlistRepository, musicRepository)
            )
            com.musicapp.ui.components.BottomTabScreen(
                currentRoute = NavRoutes.RECOMMEND,
                onNavigate = { target -> navController.navigate(target) { launchSingleTop = true } },
                playerViewModel = playerViewModel,
                onOpenPlayer = openPlayer
            ) {
                RecommendScreen(
                    viewModel = vm,
                    onPlaylistClick = { navController.navigate(NavRoutes.playlistDetail(it)) },
                    onSearchClick = { navController.navigate(NavRoutes.SEARCH) },
                    onImportClick = { navController.navigate(NavRoutes.IMPORT) },
                    onProfileClick = { navController.navigate(NavRoutes.PROFILE) },
                    onPlaySong = { songs, index -> playAndOpen(songs, index) }
                )
            }
        }

        // ---- LIBRARY ----
        composable(
            route = NavRoutes.LIBRARY,
            enterTransition = { HnrAnimation.fadeInPage },
            exitTransition = { HnrAnimation.fadeOutPage },
            popEnterTransition = { HnrAnimation.fadeInPage },
            popExitTransition = { HnrAnimation.fadeOutPage }
        ) {
            val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory(playlistRepository))
            com.musicapp.ui.components.BottomTabScreen(
                currentRoute = NavRoutes.LIBRARY,
                onNavigate = { target -> navController.navigate(target) { launchSingleTop = true } },
                playerViewModel = playerViewModel,
                onOpenPlayer = openPlayer
            ) {
                HomeScreen(
                    viewModel = vm,
                    onPlaylistClick = { navController.navigate(NavRoutes.playlistDetail(it)) },
                    onImportClick = { navController.navigate(NavRoutes.IMPORT) },
                    onSearchClick = { navController.navigate(NavRoutes.SEARCH) },
                    onProfileClick = { navController.navigate(NavRoutes.PROFILE) }
                )
            }
        }

        // ---- FAVORITES ----
        composable(
            route = NavRoutes.FAVORITES,
            enterTransition = { HnrAnimation.slideInRight },
            exitTransition = { HnrAnimation.slideOutLeft },
            popEnterTransition = { HnrAnimation.slideInLeft },
            popExitTransition = { HnrAnimation.slideOutRight }
        ) {
            val vm: FavoritesViewModel = viewModel(
                factory = FavoritesViewModel.Factory(
                    repository = playlistRepository,
                    musicPlayer = playerViewModel.getMusicPlayer()
                )
            )
            FavoritesScreen(
                viewModel = vm,
                onPlaySong = { songs, index -> playAndOpen(songs, index) },
                onNavigateBack = { navController.popBackStack() },
                onSearchClick = { navController.navigate(NavRoutes.SEARCH) }
            )
        }

        // ---- PROFILE ----
        composable(
            route = NavRoutes.PROFILE,
            enterTransition = { HnrAnimation.slideInRight },
            exitTransition = { HnrAnimation.slideOutLeft },
            popEnterTransition = { HnrAnimation.slideInLeft },
            popExitTransition = { HnrAnimation.slideOutRight }
        ) {
            val historyVm: HistoryViewModel = viewModel(
                factory = HistoryViewModel.Factory(playHistoryRepository)
            )
            val historyState = historyVm.uiState.collectAsState()

            com.musicapp.ui.components.BottomTabScreen(
                currentRoute = NavRoutes.PROFILE,
                onNavigate = { target -> navController.navigate(target) { launchSingleTop = true } },
                playerViewModel = playerViewModel,
                onOpenPlayer = openPlayer
            ) {
                ProfileHubScreen(
                    onLocalClick = { navController.navigate(NavRoutes.LOCAL_MUSIC) },
                    onImportClick = { navController.navigate(NavRoutes.IMPORT) },
                    onSettingsClick = { navController.navigate(NavRoutes.SETTINGS) },
                    recentSongs = historyState.value.history.take(10).map { historyVm.toSong(it) },
                    onPlaySong = { songs, idx -> playAndOpen(songs, idx) },
                    onViewAllHistory = { navController.navigate(NavRoutes.HISTORY) }
                )
            }
        }

        // ---- PLAYER ----
        composable(
            route = NavRoutes.PLAYER,
            enterTransition = { HnrAnimation.slideInBottom },
            exitTransition = { HnrAnimation.slideOutBottom },
            popEnterTransition = { HnrAnimation.slideInBottom },
            popExitTransition = { HnrAnimation.slideOutBottom }
        ) {
            PlayerScreen(
                viewModel = playerViewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenLyrics = { navController.navigate(NavRoutes.LYRICS) },
                onOpenLyricMv = { navController.navigate(NavRoutes.LYRIC_MV) }
            )
        }

        // ---- LYRIC_MV：效果选择页 → 横屏全屏原生 Canvas 播放 ----
        composable(
            route = NavRoutes.LYRIC_MV,
            enterTransition = { HnrAnimation.slideInBottom },
            exitTransition = { HnrAnimation.slideOutBottom },
            popEnterTransition = { HnrAnimation.slideInBottom },
            popExitTransition = { HnrAnimation.slideOutBottom }
        ) {
            var selectedStyle by remember { mutableStateOf(LyricMvStyle.NEON_STAGE) }
            var playing by remember { mutableStateOf(false) }
            val song by playerViewModel.currentSong.collectAsState()

            if (playing) {
                LyricMvFullscreen(
                    style = selectedStyle,
                    viewModel = playerViewModel,
                    onNavigateBack = { playing = false }
                )
            } else {
                LyricMvScreen(
                    currentSongTitle = song?.title ?: "",
                    currentSongArtist = song?.artist ?: "",
                    selectedStyle = selectedStyle,
                    onSelectStyle = { selectedStyle = it },
                    onConfirm = { playing = true },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        // ---- LYRICS ----
        composable(
            route = NavRoutes.LYRICS,
            enterTransition = { HnrAnimation.slideInBottom },
            exitTransition = { HnrAnimation.slideOutBottom },
            popEnterTransition = { HnrAnimation.slideInBottom },
            popExitTransition = { HnrAnimation.slideOutBottom }
        ) {
            FoliaLyricsScreen(playerViewModel, onNavigateBack = { navController.popBackStack() })
        }

        // ---- PLAYLIST_DETAIL ----
        composable(
            route = NavRoutes.PLAYLIST_DETAIL,
            arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
        ) { bse ->
            val playlistId = bse.arguments?.getLong("playlistId") ?: return@composable
            val vm: PlaylistViewModel = viewModel(
                factory = PlaylistViewModel.Factory(playlistRepository, playlistId))
            PlaylistDetailScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() },
                onSongClick = { idx ->
                    val songs = vm.songs.value
                    if (songs.isNotEmpty()) playAndOpen(songs, idx)
                },
                onOpenSongDetail = { song, plId -> openSongDetailOrPlay(song, plId) },
                playlistRepository = playlistRepository
            )
        }

        // ---- SONG_DETAIL ----
        composable(
            route = NavRoutes.SONG_DETAIL,
            arguments = listOf(
                navArgument("playlist_id") { type = NavType.LongType },
                navArgument("song_row_id") { type = NavType.LongType })
        ) { bse ->
            val playlistId = bse.arguments?.getLong("playlist_id") ?: 0L
            val songRowId = bse.arguments?.getLong("song_row_id") ?: 0L
            val actApp = app ?: throw IllegalStateException("Application context required")
            val vm: com.musicapp.ui.screens.song_detail.SongDetailViewModel = viewModel(
                factory = com.musicapp.ui.screens.song_detail.SongDetailViewModel.Factory(
                    application = actApp,
                    playlistRepository = playlistRepository,
                    songId = songRowId,
                    playlistId = playlistId
                ))
            com.musicapp.ui.screens.song_detail.SongDetailScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() },
                onOpenLyrics = { navController.navigate(NavRoutes.LYRICS) },
                onPlay = { song -> playAndOpen(listOf(song), 0) })
        }

        // ---- SEARCH ----
        composable(
            route = NavRoutes.SEARCH,
            enterTransition = { HnrAnimation.slideInRight },
            exitTransition = { HnrAnimation.slideOutLeft },
            popEnterTransition = { HnrAnimation.slideInLeft },
            popExitTransition = { HnrAnimation.slideOutRight }
        ) {
            val vm: SearchViewModel = viewModel(
                factory = SearchViewModel.Factory(musicRepository, playlistRepository))
            SearchScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() },
                onPlaySong = { songs, index -> playAndOpen(songs, index) })
        }

        // ---- IMPORT ----
        composable(
            route = NavRoutes.IMPORT,
            enterTransition = { HnrAnimation.slideInRight },
            exitTransition = { HnrAnimation.slideOutLeft },
            popEnterTransition = { HnrAnimation.slideInLeft },
            popExitTransition = { HnrAnimation.slideOutRight }
        ) {
            val vm: ImportViewModel = viewModel(
                factory = ImportViewModel.Factory(musicRepository, playlistRepository))
            ImportScreen(viewModel = vm, onNavigateBack = { navController.popBackStack() })
        }

        // ---- HISTORY ----
        composable(
            route = NavRoutes.HISTORY,
            enterTransition = { HnrAnimation.slideInRight },
            exitTransition = { HnrAnimation.slideOutLeft },
            popEnterTransition = { HnrAnimation.slideInLeft },
            popExitTransition = { HnrAnimation.slideOutRight }
        ) {
            val vm: HistoryViewModel = viewModel(
                factory = HistoryViewModel.Factory(playHistoryRepository))
            HistoryScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() },
                onPlaySong = { songs, idx -> playAndOpen(songs, idx) })
        }

        // ---- SETTINGS ----
        composable(
            route = NavRoutes.SETTINGS,
            enterTransition = { HnrAnimation.slideInRight },
            exitTransition = { HnrAnimation.slideOutLeft },
            popEnterTransition = { HnrAnimation.slideInLeft },
            popExitTransition = { HnrAnimation.slideOutRight }
        ) {
            val dao = userPlatformDao ?: throw IllegalStateException("UserPlatformDao required")
            val ds = loginDataStore ?: throw IllegalStateException("LoginDataStore required")
            val vm: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(dao, ds))
            SettingsScreen(viewModel = vm, onNavigateBack = { navController.popBackStack() })
        }

        // ---- LOCAL_MUSIC ----
        composable(
            route = NavRoutes.LOCAL_MUSIC,
            enterTransition = { HnrAnimation.slideInRight },
            exitTransition = { HnrAnimation.slideOutLeft },
            popEnterTransition = { HnrAnimation.slideInLeft },
            popExitTransition = { HnrAnimation.slideOutRight }
        ) {
            val vm: LocalMusicViewModel = viewModel(
                factory = LocalMusicViewModel.Factory(localMusicRepository))
            LocalMusicScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() },
                onPlaySong = { localSongs, idx ->
                    val songs = localSongs.map { entity ->
                        Song(
                            songId = entity.songId,
                            title = entity.title,
                            artist = entity.artist,
                            album = entity.album,
                            duration = entity.duration,
                            sourceUrl = if (entity.decryptedPath.isNotEmpty())
                                "file://${entity.decryptedPath}"
                            else "file://${entity.filePath}",
                            platform = com.musicapp.domain.model.MusicPlatform.LOCAL)
                    }
                    playAndOpen(songs, idx)
                })
        }
    }
}
