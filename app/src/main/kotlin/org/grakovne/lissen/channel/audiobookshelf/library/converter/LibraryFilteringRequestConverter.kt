package org.grakovne.lissen.channel.audiobookshelf.library.converter

import android.util.Base64
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryFilteringRequestConverter
  @Inject
  constructor() {
    fun apply(preferences: LissenSharedPreferences): String? {
      val selectedTag = preferences.getSelectedTag()

      if (selectedTag != null) {
        val encoded = Base64.encodeToString(selectedTag.toByteArray(), Base64.NO_WRAP)
        return "tags.$encoded"
      }

      val hideCompleted = preferences.getHideCompleted()
      if (hideCompleted) {
        return "progress.bm90LWZpbmlzaGVk" // not-finished
      }

      return null
    }
  }
