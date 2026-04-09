package org.grakovne.lissen.content

import android.net.Uri
import org.grakovne.lissen.channel.audiobookshelf.AudiobookshelfChannelProvider
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfRepository
import org.grakovne.lissen.channel.common.ChannelAuthService
import org.grakovne.lissen.channel.common.MediaChannel
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.cache.persistent.LocalCacheRepository
import org.grakovne.lissen.content.cache.temporary.CachedBookmarkProvider
import org.grakovne.lissen.content.cache.temporary.CachedCoverProvider
import org.grakovne.lissen.lib.domain.Book
import org.grakovne.lissen.lib.domain.Bookmark
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.Library
import org.grakovne.lissen.lib.domain.LibraryType
import org.grakovne.lissen.lib.domain.PagedItems
import org.grakovne.lissen.lib.domain.PlaybackProgress
import org.grakovne.lissen.lib.domain.PlaybackSession
import org.grakovne.lissen.lib.domain.RecentBook
import org.grakovne.lissen.lib.domain.UserAccount
import org.grakovne.lissen.lib.domain.isSame
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.playback.service.calculateChapterIndex
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LissenMediaProvider
  @Inject
  constructor(
    private val preferences: LissenSharedPreferences,
    private val channelProvider: AudiobookshelfChannelProvider,
    private val dataRepository: AudioBookshelfRepository,
    private val localCacheRepository: LocalCacheRepository,
    private val cachedCoverProvider: CachedCoverProvider,
    private val cachedBookmarkProvider: CachedBookmarkProvider,
  ) {
    suspend fun dropBookmark(bookmark: Bookmark) = cachedBookmarkProvider.dropBookmark(bookmark = bookmark)

    suspend fun createBookmark(
      libraryItemId: String,
      chapterPosition: Double,
      totalPosition: Double,
    ): Bookmark? {
      val playingItem = preferences.getPlayingBook() ?: return null

      return cachedBookmarkProvider
        .createBookmark(
          chapterTime = chapterPosition,
          libraryItemId = libraryItemId,
          totalTime = totalPosition,
          currentChapter = playingItem.chapters[calculateChapterIndex(playingItem, totalPosition)].title,
        )
    }

    suspend fun provideBookmarks(playingItemId: String): List<Bookmark> =
      cachedBookmarkProvider
        .provideBookmarks(playingItemId)
        .sortedByDescending { it.createdAt }
        .fold(emptyList()) { acc, item -> if (acc.any { it.isSame(item) }) acc else acc + item }

    suspend fun updateAndProvideBookmarks(playingItemId: String): List<Bookmark> =
      cachedBookmarkProvider
        .fetchBookmarks(playingItemId)
        .sortedByDescending { it.createdAt }
        .fold(emptyList()) { acc, b -> if (acc.any { it.isSame(b) }) acc else acc + b }

    fun provideFileUri(
      libraryItemId: String,
      chapterId: String,
    ): OperationResult<Uri> {
      Timber.d("Fetching File $libraryItemId and $chapterId URI")

      return when (preferences.isForceCache()) {
        true -> {
          localCacheRepository
            .provideFileUri(libraryItemId, chapterId)
            ?.let { OperationResult.Success(it) }
            ?: OperationResult.Error(OperationError.InternalError)
        }

        false -> {
          localCacheRepository
            .provideFileUri(libraryItemId, chapterId)
            ?.let { OperationResult.Success(it) }
            ?: providePreferredChannel()
              .provideFileUri(libraryItemId, chapterId)
              .let { OperationResult.Success(it) }
        }
      }
    }

    suspend fun syncProgress(
      sessionId: String,
      detailedItem: DetailedItem,
      progress: PlaybackProgress,
    ): OperationResult<Unit> {
      Timber.d("Syncing Progress for ${detailedItem.id}. $progress")

      localCacheRepository.syncProgress(detailedItem, progress)

      val channelSyncResult =
        providePreferredChannel()
          .syncProgress(sessionId, progress)

      return when (preferences.isForceCache()) {
        true -> OperationResult.Success(Unit)
        false -> channelSyncResult
      }
    }

    suspend fun fetchBookCover(bookId: String): OperationResult<File> {
      Timber.d("Fetching Cover stream for $bookId")
      return when (preferences.isForceCache()) {
        true -> {
          localCacheRepository.fetchBookCover(bookId)
        }

        false -> {
          val result =
            cachedCoverProvider.provideCover(
              channel = providePreferredChannel(),
              itemId = bookId,
            )
          when (result) {
            is OperationResult.Success -> {
              result
            }

            is OperationResult.Error -> {
              Timber.d("API fetchBookCover failed, falling back to local cache")
              localCacheRepository.fetchBookCover(bookId)
            }
          }
        }
      }
    }

    suspend fun searchBooks(
      libraryId: String,
      query: String,
      limit: Int,
    ): OperationResult<List<Book>> {
      Timber.d("Searching books with query $query of library: $libraryId")

      return when (preferences.isForceCache()) {
        true -> {
          localCacheRepository.searchBooks(libraryId = libraryId, query = query)
        }

        false -> {
          val result =
            providePreferredChannel()
              .searchBooks(
                libraryId = libraryId,
                query = query,
                limit = limit,
              )
          when (result) {
            is OperationResult.Success -> {
              result
            }

            is OperationResult.Error -> {
              Timber.d("API searchBooks failed, falling back to local cache")
              localCacheRepository.searchBooks(libraryId = libraryId, query = query)
            }
          }
        }
      }
    }

    suspend fun fetchBooks(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
    ): OperationResult<PagedItems<Book>> {
      Timber.d("Fetching page $pageNumber of library: $libraryId")

      return when (preferences.isForceCache()) {
        true -> {
          localCacheRepository.fetchBooks(libraryId = libraryId, pageSize = pageSize, pageNumber = pageNumber)
        }

        false -> {
          val result = providePreferredChannel().fetchBooks(libraryId = libraryId, pageSize = pageSize, pageNumber = pageNumber)
          when (result) {
            is OperationResult.Success -> {
              result
            }

            is OperationResult.Error -> {
              Timber.d("API fetchBooks failed, falling back to local cache")
              localCacheRepository.fetchBooks(libraryId = libraryId, pageSize = pageSize, pageNumber = pageNumber)
            }
          }
        }
      }
    }

    suspend fun fetchLibraries(): OperationResult<List<Library>> {
      Timber.d("Fetching List of libraries")

      return when (preferences.isForceCache()) {
        true -> {
          localCacheRepository.fetchLibraries()
        }

        false -> {
          val result = providePreferredChannel().fetchLibraries()
          result.foldAsync(
            onSuccess = { libraries ->
              localCacheRepository.updateLibraries(libraries)
              OperationResult.Success(libraries)
            },
            onFailure = {
              Timber.d("API fetchLibraries failed, falling back to local cache")
              localCacheRepository.fetchLibraries()
            },
          )
        }
      }
    }

    suspend fun startPlayback(
      itemId: String,
      chapterId: String,
      supportedMimeTypes: List<String>,
      deviceId: String,
    ): OperationResult<PlaybackSession> {
      Timber.d("Starting Playback for $itemId. $supportedMimeTypes are supported")

      return providePreferredChannel()
        .startPlayback(
          bookId = itemId,
          episodeId = chapterId,
          supportedMimeTypes = supportedMimeTypes,
          deviceId = deviceId,
        ).foldAsync(
          onSuccess = {
            OperationResult.Success(it)
          },
          onFailure = {
            OperationResult.Success(PlaybackSession.local(itemId))
          },
        )
    }

    suspend fun fetchRecentListenedBooks(libraryId: String): OperationResult<List<RecentBook>> {
      Timber.d("Fetching Recent books of library $libraryId")

      return when (preferences.isForceCache()) {
        true -> {
          localCacheRepository.fetchRecentListenedBooks(libraryId)
        }

        false -> {
          val result = providePreferredChannel().fetchRecentListenedBooks(libraryId)
          when (result) {
            is OperationResult.Success -> {
              result.map { items -> syncFromLocalProgress(libraryId = libraryId, detailedItems = items) }
            }

            is OperationResult.Error -> {
              Timber.d("API fetchRecentListenedBooks failed, falling back to local cache")
              localCacheRepository.fetchRecentListenedBooks(libraryId)
            }
          }
        }
      }
    }

    suspend fun fetchBook(bookId: String): OperationResult<DetailedItem> {
      Timber.d("Fetching Detailed book info for $bookId")

      return when (preferences.isForceCache()) {
        true -> {
          localCacheRepository
            .fetchBook(bookId)
            ?.let { OperationResult.Success(it) }
            ?: OperationResult.Error(OperationError.InternalError)
        }

        false -> {
          val result = providePreferredChannel().fetchBook(bookId)
          when (result) {
            is OperationResult.Success -> {
              result.map { syncFromLocalProgress(it) }
            }

            is OperationResult.Error -> {
              Timber.d("API fetchBook failed, falling back to local cache")
              localCacheRepository
                .fetchBook(bookId)
                ?.let { OperationResult.Success(it) }
                ?: OperationResult.Error(OperationError.InternalError)
            }
          }
        }
      }
    }

    suspend fun authorize(
      host: String,
      username: String,
      password: String,
    ): OperationResult<UserAccount> {
      Timber.d("Authorizing for $username@$host")
      return provideAuthService().authorize(host, username, password) { onPostLogin(host, it) }
    }

    suspend fun startOAuth(
      host: String,
      onSuccess: () -> Unit,
      onFailure: (OperationError) -> Unit,
    ) {
      Timber.d("Starting OAuth for $host")

      return provideAuthService()
        .startOAuth(
          host = host,
          onSuccess = onSuccess,
          onFailure = { onFailure(it) },
        )
    }

    suspend fun onPostLogin(
      host: String,
      account: UserAccount,
    ) {
      provideAuthService()
        .persistCredentials(
          host = host,
          username = account.username,
          token = account.token,
          accessToken = account.accessToken,
          refreshToken = account.refreshToken,
        )

      fetchLibraries()
        .fold(
          onSuccess = {
            val preferredLibrary =
              it
                .find { item -> item.id == account.preferredLibraryId }
                ?: it.firstOrNull()

            preferredLibrary
              ?.let { library ->
                preferences.savePreferredLibrary(
                  Library(
                    id = library.id,
                    title = library.title,
                    type = library.type,
                  ),
                )
              }
          },
          onFailure = {
            account
              .preferredLibraryId
              ?.let { library ->
                Library(
                  id = library,
                  title = "Default Library",
                  type = LibraryType.LIBRARY,
                )
              }?.let { preferences.savePreferredLibrary(it) }
          },
        )
    }

    private suspend fun syncFromLocalProgress(
      libraryId: String,
      detailedItems: List<RecentBook>,
    ): List<RecentBook> {
      val localRecentlyBooks =
        localCacheRepository
          .fetchRecentListenedBooks(libraryId)
          .fold(
            onSuccess = { it },
            onFailure = { return@fold detailedItems },
          )

      val syncedRecentlyBooks =
        detailedItems
          .mapNotNull { item -> localRecentlyBooks.find { it.id == item.id }?.let { item to it } }
          .map { (remote, local) ->
            val localTimestamp = local.listenedLastUpdate ?: return@map remote
            val remoteTimestamp = remote.listenedLastUpdate ?: return@map remote

            when (remoteTimestamp > localTimestamp) {
              true -> remote
              false -> local
            }
          }

      return detailedItems
        .map { item ->
          syncedRecentlyBooks
            .find { item.id == it.id }
            ?.let { local -> item.copy(listenedPercentage = local.listenedPercentage) }
            ?: item
        }
    }

    private suspend fun syncFromLocalProgress(detailedItem: DetailedItem): DetailedItem {
      val cachedProgress = localCacheRepository.fetchPlayingItemProgress(detailedItem.id)
      val channelProgress = detailedItem.progress

      val updatedProgress =
        listOfNotNull(cachedProgress, channelProgress)
          .maxByOrNull { it.lastUpdate }
          ?: return detailedItem

      Timber.d(
        """
        Merging local playback progress into channel-fetched:
            Channel Progress: $channelProgress
            Cached Progress: $cachedProgress
            Final Progress: $updatedProgress
        """.trimIndent(),
      )

      return detailedItem.copy(progress = updatedProgress)
    }

    fun fetchConnectionHost() = providePreferredChannel().fetchConnectionHost()

    suspend fun fetchConnectionInfo() = providePreferredChannel().fetchConnectionInfo()

    suspend fun fetchAvailableTags(libraryId: String): List<String> =
      dataRepository
        .fetchFilterData(libraryId)
        .fold(
          onSuccess = { it.tags ?: emptyList() },
          onFailure = { emptyList() },
        )

    fun provideAuthService(): ChannelAuthService = channelProvider.provideChannelAuth()

    fun providePreferredChannel(): MediaChannel = channelProvider.provideMediaChannel()
  }
