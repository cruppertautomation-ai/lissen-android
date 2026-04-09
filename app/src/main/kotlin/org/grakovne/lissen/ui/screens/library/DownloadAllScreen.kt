package org.grakovne.lissen.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.grakovne.lissen.viewmodel.CachingModelView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadAllScreen(
  onBack: () -> Unit,
  cachingModelView: CachingModelView = hiltViewModel(),
) {
  val state by cachingModelView.downloadAllState.collectAsState()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Download All") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { paddingValues ->
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(paddingValues)
          .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Top,
    ) {
      Spacer(modifier = Modifier.height(32.dp))

      Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
          progress = { state.progress },
          modifier = Modifier.size(160.dp),
          strokeWidth = 8.dp,
          trackColor = colorScheme.surfaceVariant,
          color = colorScheme.primary,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = "${state.alreadyCached + state.completed}/${state.total}",
            style = typography.headlineMedium,
          )
          if (state.active) {
            Text(
              text = "downloading",
              style = typography.bodySmall,
              color = colorScheme.onSurfaceVariant,
            )
          } else if (state.total > 0) {
            Text(
              text = "done",
              style = typography.bodySmall,
              color = colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(32.dp))

      if (state.currentTitle != null && state.active) {
        Text(
          text = state.currentTitle ?: "",
          style = typography.bodyLarge,
          textAlign = TextAlign.Center,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(24.dp))
      }

      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        DownloadStatRow("Total", state.total)
        DownloadStatRow("Already cached", state.alreadyCached)
        DownloadStatRow("Queued", state.scheduled)
        DownloadStatRow("Downloading", state.downloading)
        DownloadStatRow("Completed", state.completed)
        if (state.failed > 0) {
          DownloadStatRow("Failed", state.failed)
        }
      }
    }
  }
}

@Composable
private fun DownloadStatRow(
  label: String,
  count: Int,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(text = label, style = typography.bodyLarge)
    Text(
      text = count.toString(),
      style = typography.bodyLarge,
      color = colorScheme.onSurfaceVariant,
    )
  }
}
