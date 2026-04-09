package org.grakovne.lissen.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.cache.persistent.CacheState
import org.grakovne.lissen.content.cache.persistent.ContentCachingManager
import org.grakovne.lissen.content.cache.persistent.ContentCachingProgress
import org.grakovne.lissen.content.cache.persistent.ContentCachingService
import org.grakovne.lissen.content.cache.persistent.LocalCacheRepository
import org.grakovne.lissen.content.cache.temporary.CachedCoverProvider
import org.grakovne.lissen.lib.domain.AllItemsDownloadOption
import org.grakovne.lissen.lib.domain.CacheStatus
import org.grakovne.lissen.lib.domain.ContentCachingTask
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.DownloadOption
import org.grakovne.lissen.lib.domain.PlayingChapter
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.ui.screens.settings.advanced.cache.CachedItemsPageSource
import timber.log.Timber
import java.io.Serializable
import javax.inject.Inject

@HiltViewModel
class CachingModelView
  @Inject
  constructor(
    @ApplicationContext private val context: Context,
    private val localCacheRepository: LocalCacheRepository,
    private val mediaProvider: LissenMediaProvider,
    private val contentCachingProgress: ContentCachingProgress,
    private val contentCachingManager: ContentCachingManager,
    private val preferences: LissenSharedPreferences,
    private val cachedCoverProvider: CachedCoverProvider,
  ) : ViewModel() {
    private val _totalCount = MutableLiveData<Int>()
    val totalCount: LiveData<Int> = _totalCount

    val forceCache = preferences.forceCacheFlow

    private val _bookCachingProgress = mutableMapOf<String, MutableStateFlow<CacheState>>()

    data class DownloadAllState(
      val active: Boolean = false,
      val total: Int = 0,
      val alreadyCached: Int = 0,
      val scheduled: Int = 0,
      val downloading: Int = 0,
      val completed: Int = 0,
      val failed: Int = 0,
      val currentTitle: String? = null,
    ) {
      val progress: Float
        get() = if (total > 0) (alreadyCached + completed).toFloat() / total else 0f
    }

    private val _downloadAllState = MutableStateFlow(DownloadAllState())
    val downloadAllState = _downloadAllState

    private val pageConfig =
      PagingConfig(
        pageSize = PAGE_SIZE,
        initialLoadSize = PAGE_SIZE,
        prefetchDistance = PAGE_SIZE,
      )

    private var pageSource: PagingSource<Int, DetailedItem>? = null
    val libraryPager: Flow<PagingData<DetailedItem>> by lazy {
      Pager(
        config = pageConfig,
        pagingSourceFactory = {
          val source = CachedItemsPageSource(localCacheRepository) { _totalCount.postValue(it) }

          pageSource = source
          source
        },
      ).flow.cachedIn(viewModelScope)
    }

    init {
      viewModelScope.launch {
        contentCachingProgress.statusFlow.collect { (item, progress) ->
          val flow =
            _bookCachingProgress.getOrPut(item.id) {
              MutableStateFlow(progress)
            }
          flow.value = progress
        }
      }
    }

    suspend fun clearShortTermCache() {
      withContext(Dispatchers.IO) {
        cachedCoverProvider.clearCache()
      }
    }

    fun cache(
      mediaItem: DetailedItem,
      currentPosition: Double,
      option: DownloadOption,
    ) {
      val task =
        ContentCachingTask(
          item = mediaItem,
          options = option,
          currentPosition = currentPosition,
        )

      val intent =
        Intent(context, ContentCachingService::class.java).apply {
          action = ContentCachingService.CACHE_ITEM_ACTION
          putExtra(ContentCachingService.CACHING_TASK_EXTRA, task as Serializable)
        }

      context.startForegroundService(intent)
    }

    fun getProgress(bookId: String) =
      _bookCachingProgress
        .getOrPut(bookId) { MutableStateFlow(CacheState(CacheStatus.Idle)) }

    suspend fun dropCache(bookId: String) {
      contentCachingManager.dropCache(bookId)
    }

    fun stopCaching(item: DetailedItem) {
      val intent =
        Intent(context, ContentCachingService::class.java).apply {
          action = ContentCachingService.STOP_CACHING_ACTION
          putExtra(ContentCachingService.CACHING_PLAYING_ITEM, item as Serializable)
        }

      context.startForegroundService(intent)
    }

    suspend fun dropCache(
      item: DetailedItem,
      chapter: PlayingChapter,
    ) {
      contentCachingManager.dropCache(item, chapter)
    }

    fun downloadAll() {
      Timber.d("DownloadAll: triggered, preferredLibrary=${preferences.getPreferredLibrary()}")
      val libraryId =
        preferences.getPreferredLibrary()?.id ?: run {
          Timber.e("DownloadAll: no preferred library, aborting")
          return
        }
      _downloadAllState.value = DownloadAllState(active = true)

      viewModelScope.launch {
        withContext(Dispatchers.IO) {
          // Collect all book IDs first
          val allBooks = mutableListOf<org.grakovne.lissen.lib.domain.Book>()
          var page = 0
          val pageSize = 50
          while (true) {
            val books =
              mediaProvider.fetchBooks(libraryId, pageSize, page).fold(
                onSuccess = { it.items },
                onFailure = { emptyList() },
              )
            if (books.isEmpty()) break
            allBooks.addAll(books)
            page++
          }

          Timber.d("DownloadAll: fetched ${allBooks.size} books from server")

          // Check which books are already cached
          val cachedBookIds =
            localCacheRepository
              .fetchDetailedItems()
              .fold(
                onSuccess = { items ->
                  Timber.d("DownloadAll: ${items.items.size} items in local cache")
                  items.items.map { item -> item.id }.toSet()
                },
                onFailure = { error ->
                  Timber.e("DownloadAll: fetchDetailedItems failed: $error")
                  emptySet()
                },
              )

          val alreadyCached = allBooks.count { it.id in cachedBookIds }
          val toDownload = allBooks.filter { it.id !in cachedBookIds }
          Timber.d("DownloadAll: $alreadyCached cached, ${toDownload.size} to download")

          _downloadAllState.value =
            DownloadAllState(
              active = true,
              total = allBooks.size,
              alreadyCached = alreadyCached,
              scheduled = toDownload.size,
            )

          for ((index, book) in toDownload.withIndex()) {
            _downloadAllState.value =
              _downloadAllState.value.copy(
                scheduled = toDownload.size - index - 1,
                downloading = 1,
                currentTitle = book.title,
              )

            val detailed =
              mediaProvider.fetchBook(book.id).fold(
                onSuccess = { it },
                onFailure = { null },
              )

            if (detailed != null) {
              cache(detailed, 0.0, AllItemsDownloadOption)
              _downloadAllState.value =
                _downloadAllState.value.copy(
                  completed = _downloadAllState.value.completed + 1,
                  downloading = 0,
                )
            } else {
              _downloadAllState.value =
                _downloadAllState.value.copy(
                  failed = _downloadAllState.value.failed + 1,
                  downloading = 0,
                )
            }
          }

          _downloadAllState.value = _downloadAllState.value.copy(active = false, scheduled = 0, downloading = 0)
          Timber.d("DownloadAll: finished. State: ${_downloadAllState.value}")
        }
      }
    }

    fun toggleCacheForce() {
      when (localCacheUsing()) {
        true -> preferences.disableForceCache()
        false -> preferences.enableForceCache()
      }
    }

    fun localCacheUsing() = preferences.isForceCache()

    fun provideCacheState(bookId: String): LiveData<Boolean> = contentCachingManager.hasMetadataCached(bookId)

    fun provideCacheState(
      bookId: String,
      chapterId: String,
    ): LiveData<Boolean> = contentCachingManager.hasMetadataCached(bookId, chapterId)

    fun fetchCachedItems() {
      viewModelScope.launch {
        withContext(Dispatchers.IO) {
          pageSource?.invalidate()
        }
      }
    }

    suspend fun fetchLatestUpdate(libraryId: String) = localCacheRepository.fetchLatestUpdate(libraryId)

    companion object {
      private const val PAGE_SIZE = 20
    }
  }
