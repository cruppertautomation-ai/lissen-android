package org.grakovne.lissen.channel.audiobookshelf.common.model.metadata

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class FilterDataResponse(
  val tags: List<String>?,
  val genres: List<String>?,
  val authors: List<FilterDataAuthor>?,
)

@Keep
@JsonClass(generateAdapter = true)
data class FilterDataAuthor(
  val id: String,
  val name: String,
)
