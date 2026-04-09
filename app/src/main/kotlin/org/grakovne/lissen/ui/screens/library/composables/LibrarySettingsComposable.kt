package org.grakovne.lissen.ui.screens.library.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.grakovne.lissen.R
import org.grakovne.lissen.lib.domain.LibraryType
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.viewmodel.CachingModelView
import org.grakovne.lissen.viewmodel.LibraryViewModel
import org.grakovne.lissen.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibrarySettingsComposable(
  cachingModelView: CachingModelView = hiltViewModel(),
  onDismissRequest: () -> Unit,
  onForceLocalToggled: () -> Unit,
  onHideCompletedToggled: () -> Unit,
  onTagSelected: (String?) -> Unit,
  onDownloadAll: () -> Unit,
  navController: AppNavigationService,
  settingsModelView: SettingsViewModel = hiltViewModel(),
  libraryViewModel: LibraryViewModel = hiltViewModel(),
) {
  val forceCache by cachingModelView.forceCache.collectAsState(false)
  val hideCompleted by settingsModelView.hideCompleted.collectAsState(false)
  val selectedTag by settingsModelView.selectedTag.collectAsState(null)
  val availableTags by libraryViewModel.availableTags.observeAsState(emptyList())

  val context = LocalContext.current

  ModalBottomSheet(
    containerColor = colorScheme.background,
    onDismissRequest = onDismissRequest,
    content = {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .padding(horizontal = 16.dp),
      ) {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
          item {
            LibrarySettingsComposableItem(
              title = context.getString(R.string.show_downloaded_content_only),
              state = forceCache,
              onStateChange = { onForceLocalToggled() },
            )

            if (libraryViewModel.fetchPreferredLibraryType() == LibraryType.LIBRARY) {
              LibrarySettingsComposableItem(
                title = stringResource(R.string.hide_completed_items),
                state = hideCompleted,
                onStateChange = { onHideCompletedToggled() },
              )
            }

            if (availableTags.isNotEmpty()) {
              HorizontalDivider()

              Text(
                text = "Filter by tag",
                style = typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
              )

              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp)
                    .verticalScroll(rememberScrollState()),
              ) {
                availableTags.forEach { tag ->
                  FilterChip(
                    selected = tag == selectedTag,
                    onClick = {
                      onTagSelected(if (tag == selectedTag) null else tag)
                    },
                    label = { Text(text = tag, style = typography.bodySmall) },
                    colors =
                      FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colorScheme.primaryContainer,
                        selectedLabelColor = colorScheme.onPrimaryContainer,
                      ),
                  )
                }
              }
            }

            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            ListItem(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .clickable { onDownloadAll() },
              headlineContent = {
                Text(
                  text = "Download all books",
                  style = typography.bodyLarge,
                )
              },
            )

            HorizontalDivider()

            ApplicationSettingsItemComposable(
              onClicked = {
                onDismissRequest()
                navController.showSettings()
              },
            )
          }
        }
      }
    },
  )
}

@Composable
fun LibrarySettingsComposableItem(
  title: String,
  state: Boolean,
  onStateChange: (Boolean) -> Unit,
) {
  ListItem(
    modifier = Modifier,
    headlineContent = { Text(text = title) },
    trailingContent = {
      Switch(
        checked = state,
        onCheckedChange = onStateChange,
        enabled = true,
        colors =
          SwitchDefaults.colors(
            uncheckedTrackColor = colorScheme.background,
            checkedBorderColor = colorScheme.onSurface,
            checkedThumbColor = colorScheme.onSurface,
            checkedTrackColor = colorScheme.background,
          ),
      )
    },
  )
}

@Composable
fun ApplicationSettingsItemComposable(onClicked: () -> Unit) {
  ListItem(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable { onClicked() },
    headlineContent = {
      Text(
        text = stringResource(R.string.application_settings),
        style = typography.bodyLarge,
      )
    },
    trailingContent = {
      Icon(
        imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
        contentDescription = null,
      )
    },
  )
}
