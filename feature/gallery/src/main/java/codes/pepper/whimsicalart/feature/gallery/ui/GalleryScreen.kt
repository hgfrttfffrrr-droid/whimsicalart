
package codes.pepper.whimsicalart.feature.gallery.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil.compose.AsyncImage
import codes.pepper.whimsicalart.core.designsystem.LocalUiScale
import codes.pepper.whimsicalart.core.designsystem.ScaledTopBar
import codes.pepper.whimsicalart.core.designsystem.ScaledTopBarIconButton
import codes.pepper.whimsicalart.feature.gallery.domain.GroupDimension
import codes.pepper.whimsicalart.feature.gallery.domain.GroupDirection
import codes.pepper.whimsicalart.feature.gallery.domain.Photo
import codes.pepper.whimsicalart.feature.gallery.domain.PhotoGroup
import codes.pepper.whimsicalart.feature.gallery.domain.SortDirection
import codes.pepper.whimsicalart.feature.gallery.domain.SortField
import codes.pepper.whimsicalart.feature.gallery.domain.tag.SceneTag
import codes.pepper.whimsicalart.feature.gallery.ui.viewmodel.GalleryViewModel
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    onPhotoSelected: (Uri) -> Unit,
    onPhotosSelected: (List<Uri>) -> Unit,
    onOpenCamera: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val s = LocalUiScale.current

    var showSortGroupDialog by remember { mutableStateOf(false) }

    // Admin panel state
    var showAdminLogin by rememberSaveable { mutableStateOf(false) }
    var showAdminPanel by rememberSaveable { mutableStateOf(false) }
    var adminPassword by rememberSaveable { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }

    val stats = remember {
        context.getSharedPreferences(
            "whimsicalart_panel_stats",
            android.content.Context.MODE_PRIVATE
        )
    }

    val launchCount = remember(showAdminPanel) {
        stats.getLong("launch_count", 0L)
    }

    val lastLaunch = remember(showAdminPanel) {
        stats.getLong("last_launch", 0L)
    }

    val lastLaunchText = remember(lastLaunch) {
        if (lastLaunch == 0L) {
            "نامشخص"
        } else {
            SimpleDateFormat(
                "yyyy/MM/dd - HH:mm",
                Locale.getDefault()
            ).format(Date(lastLaunch))
        }
    }

    val topBarTextButtonColors = ButtonDefaults.textButtonColors(
        contentColor = MaterialTheme.colorScheme.onPrimary,
        disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
    )

    val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                mediaPermission
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            viewModel.loadPhotos()
        }
    }

    LaunchedEffect(hasPermission, uiState.photos.isEmpty()) {
        if (hasPermission) {
            if (uiState.photos.isEmpty()) {
                viewModel.loadPhotos()
            }
        } else {
            permissionLauncher.launch(mediaPermission)
        }
    }

    var hasResumedOnce by rememberSaveable {
        mutableStateOf(false)
    }

    val currentHasPermission by rememberUpdatedState(hasPermission)

    LifecycleResumeEffect(Unit) {
        val isFirstResume = !hasResumedOnce
        hasResumedOnce = true

        if (currentHasPermission && !isFirstResume) {
            viewModel.onGalleryResumed()
        }

        onPauseOrDispose { }
    }

    BackHandler(
        enabled = showAdminPanel || showAdminLogin ||
            uiState.openedGroup != null || uiState.isMultiSelectMode
    ) {
        when {
            showAdminPanel -> showAdminPanel = false
            showAdminLogin -> showAdminLogin = false
            uiState.isMultiSelectMode -> viewModel.clearSelection()
            else -> viewModel.closeGroup()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        ScaledTopBar(
            title = when {
                uiState.isMultiSelectMode -> {
                    "Select Photos (${uiState.selectedPhotos.size})"
                }
                uiState.openedGroup != null -> {
                    uiState.openedGroup.orEmpty()
                }
                else -> {
                    "Gallery"
                }
            },
            navigationIcon = {
                if (uiState.openedGroup != null && !uiState.isMultiSelectMode) {
                    ScaledTopBarIconButton(
                        onClick = { viewModel.closeGroup() },
                        contentDescription = "Back to gallery",
                        icon = Icons.AutoMirrored.Filled.ArrowBack
                    )
                }
            },
            actions = {
                if (uiState.isMultiSelectMode) {
                    TextButton(
                        colors = topBarTextButtonColors,
                        onClick = { viewModel.selectAllPhotos() }
                    ) {
                        Text(
                            text = "Select All",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                            )
                        )
                    }

                    TextButton(
                        colors = topBarTextButtonColors,
                        onClick = { viewModel.clearSelection() }
                    ) {
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                            )
                        )
                    }
                } else if (uiState.openedGroup == null) {
                    ScaledTopBarIconButton(
                        onClick = { showSortGroupDialog = true },
                        contentDescription = "Sort / Group",
                        icon = Icons.Filled.FolderOpen
                    )

                    onOpenCamera?.let {
                        ScaledTopBarIconButton(
                            onClick = it,
                            contentDescription = "Camera",
                            icon = Icons.Filled.PhotoCamera
                        )
                    }

                    ScaledTopBarIconButton(
                        enabled = !uiState.isTagging && uiState.photos.isNotEmpty(),
                        onClick = {
                            val targets = uiState.filteredPhotos ?: uiState.photos
                            targets.forEach { viewModel.tagPhoto(it) }
                        },
                        contentDescription = if (uiState.isTagging) {
                            "Tagging"
                        } else {
                            "Tag all photos"
                        },
                        icon = Icons.Filled.Tag
                    )

                    onOpenSettings?.let {
                        ScaledTopBarIconButton(
                            onClick = it,
                            contentDescription = "Settings",
                            icon = Icons.Filled.Settings
                        )
                    }
                } else {
                    ScaledTopBarIconButton(
                        enabled = !uiState.isTagging && uiState.photos.isNotEmpty(),
                        onClick = {
                            val targets = uiState.filteredPhotos ?: uiState.photos
                            targets.forEach { viewModel.tagPhoto(it) }
                        },
                        contentDescription = if (uiState.isTagging) {
                            "Tagging"
                        } else {
                            "Tag all photos"
                        },
                        icon = Icons.Filled.Tag
                    )
                }
            }
        )

        // Admin button in the center of the main gallery
        if (uiState.openedGroup == null && !uiState.isMultiSelectMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 16.dp * s,
                        vertical = 6.dp * s
                    ),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = {
                        adminPassword = ""
                        passwordError = false
                        showAdminLogin = true
                    }
                ) {
                    Text("@panel")
                }
            }
        }

        TagFilterRow(
            availableTags = uiState.availableTags,
            selectedTag = uiState.selectedTag,
            onTagSelected = { viewModel.setSelectedTag(it) }
        )

        if (showSortGroupDialog) {
            ViewingOptionsDialog(
                sortField = uiState.sortField,
                sortDirection = uiState.sortDirection,
                groupDimension = uiState.groupDimension,
                groupDirection = uiState.groupDirection,
                onSortField = viewModel::setSortField,
                onSortDirection = viewModel::setSortDirection,
                onGroupDimension = viewModel::setGroupDimension,
                onGroupDirection = viewModel::setGroupDirection,
                onDismiss = { showSortGroupDialog = false }
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                !hasPermission -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp * s),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Photo access is required to show your gallery.",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = MaterialTheme.typography.bodyLarge.fontSize * s
                            ),
                            textAlign = TextAlign.Center
                        )

                        Button(
                            onClick = {
                                permissionLauncher.launch(mediaPermission)
                            },
                            modifier = Modifier.padding(top = 16.dp * s)
                        ) {
                            Text(
                                "Grant Permission",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                                )
                            )
                        }
                    }
                }

                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.error != null -> {
                    Text(
                        text = uiState.error ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize * s
                        ),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.photos.isEmpty() -> {
                    Text(
                        text = "No photos found",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize * s
                        ),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    GalleryRefreshContainer(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = { viewModel.refreshPhotos() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (uiState.openedGroup != null) {
                            val group = uiState.groups.firstOrNull {
                                it.label == uiState.openedGroup
                            }

                            if (group != null) {
                                PhotoGrid(
                                    photos = group.photos,
                                    selectedPhotos = uiState.selectedPhotos,
                                    isMultiSelectMode = uiState.isMultiSelectMode,
                                    onPhotoClick = { photo ->
                                        if (uiState.isMultiSelectMode) {
                                            viewModel.togglePhotoSelection(photo.id)
                                        } else {
                                            onPhotoSelected(photo.uri)
                                        }
                                    },
                                    onPhotoLongClick = { photo ->
                                        if (!uiState.isMultiSelectMode) {
                                            viewModel.toggleMultiSelectMode()
                                            viewModel.togglePhotoSelection(photo.id)
                                        }
                                    }
                                )
                            }
                        } else if (uiState.groups.isNotEmpty()) {
                            GroupGrid(groups = uiState.groups) { group ->
                                viewModel.openGroup(group.label)
                            }
                        } else {
                            PhotoGrid(
                                photos = uiState.displayedPhotos,
                                selectedPhotos = uiState.selectedPhotos,
                                isMultiSelectMode = uiState.isMultiSelectMode,
                                onPhotoClick = { photo ->
                                    if (uiState.isMultiSelectMode) {
                                        viewModel.togglePhotoSelection(photo.id)
                                    } else {
                                        onPhotoSelected(photo.uri)
                                    }
                                },
                                onPhotoLongClick = { photo ->
                                    if (!uiState.isMultiSelectMode) {
                                        viewModel.toggleMultiSelectMode()
                                        viewModel.togglePhotoSelection(photo.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (uiState.isMultiSelectMode && uiState.selectedPhotos.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp * s),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        val selectedUris = viewModel.getSelectedPhotos().map { it.uri }
                        onPhotosSelected(selectedUris)
                        viewModel.clearSelection()
                    }
                ) {
                    Text(
                        "Open (${uiState.selectedPhotos.size})",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                        )
                    )
                }
            }
        }
    }

    // Admin login dialog
    if (showAdminLogin) {
        AlertDialog(
            onDismissRequest = {
                showAdminLogin = false
            },
            title = {
                Text("ورود مدیر")
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = adminPassword,
                        onValueChange = {
                            adminPassword = it
                            passwordError = false
                        },
                        label = {
                            Text("رمز عبور")
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )

                    if (passwordError) {
                        Text(
                            text = "رمز واردشده صحیح نیست",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val enteredHash = sha256(adminPassword)

                        if (enteredHash == ADMIN_PASSWORD_HASH) {
                            showAdminLogin = false
                            showAdminPanel = true
                            adminPassword = ""
                            passwordError = false
                        } else {
                            passwordError = true
                        }
                    }
                ) {
                    Text("ورود")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showAdminLogin = false
                        adminPassword = ""
                    }
                ) {
                    Text("انصراف")
                }
            }
        )
    }

    // Admin dashboard
    if (showAdminPanel) {
        AlertDialog(
            onDismissRequest = {
                showAdminPanel = false
            },
            title = {
                Text("پنل مدیریت")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "وضعیت نصب: نصب‌شده",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Text(
                        text = "تعداد اجراها: $launchCount",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Text(
                        text = "آخرین اجرا: $lastLaunchText",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Text(
                        text = "تعداد نصب در این دستگاه: ۱",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Text(
                        text = "این آمار فقط مربوط به همین دستگاه است.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAdminPanel = false
                    }
                ) {
                    Text("بستن")
                }
            }
        )
    }
}

private fun sha256(value: String): String {
    val bytes = MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))

    return bytes.joinToString("") {
        "%02x".format(it)
    }
}

/*
 * This is a local-only password check.
 * A hash embedded in an APK is not secure server authentication.
 */
private const val ADMIN_PASSWORD_HASH =
    "c320c04c61892de6ce4cf3a9a832ff623326f3f9d2ee7bb336bee390801d201a"

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoGrid(
    photos: List<Photo>,
    selectedPhotos: Set<Long>,
    isMultiSelectMode: Boolean,
    onPhotoClick: (Photo) -> Unit,
    onPhotoLongClick: (Photo) -> Unit
) {
    val s = LocalUiScale.current
    val gap = 4.dp * s

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(gap),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
        modifier = Modifier.fillMaxSize()
    ) {
        items(photos, key = { it.id }) { photo ->
            PhotoItem(
                photo = photo,
                isSelected = selectedPhotos.contains(photo.id),
                isMultiSelectMode = isMultiSelectMode,
                onClick = { onPhotoClick(photo) },
                onLongClick = { onPhotoLongClick(photo) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoItem(
    photo: Photo,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box {
        AsyncImage(
            model = photo.uri,
            contentDescription = photo.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        )

        if (isMultiSelectMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupGrid(
    groups: List<PhotoGroup>,
    onGroupClick: (PhotoGroup) -> Unit
) {
    val s = LocalUiScale.current

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(4.dp * s),
        horizontalArrangement = Arrangement.spacedBy(4.dp * s),
        verticalArrangement = Arrangement.spacedBy(8.dp * s),
        modifier = Modifier.fillMaxSize()
    ) {
        items(groups, key = { it.label }) { group ->
            GroupTile(
                group = group,
                onClick = { onGroupClick(group) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupTile(
    group: PhotoGroup,
    onClick: () -> Unit
) {
    val s = LocalUiScale.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
    ) {
        AsyncImage(
            model = group.firstPhoto.uri,
            contentDescription = group.label,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )

        Text(
            text = group.label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = MaterialTheme.typography.labelMedium.fontSize * s
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 4.dp * s,
                    vertical = 2.dp * s
                )
        )
    }
}

@Composable
private fun TextButton(
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    androidx.compose.material3.TextButton(
        colors = colors,
        onClick = onClick
    ) {
        content()
    }
}

@Composable
private fun ViewingOptionsDialog(
    sortField: SortField,
    sortDirection: SortDirection,
    groupDimension: GroupDimension,
    groupDirection: GroupDirection,
    onSortField: (SortField) -> Unit,
    onSortDirection: (SortDirection) -> Unit,
    onGroupDimension: (GroupDimension) -> Unit,
    onGroupDirection: (GroupDirection) -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalUiScale.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Sort / Group",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = MaterialTheme.typography.titleLarge.fontSize * s
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Sort by",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = MaterialTheme.typography.titleSmall.fontSize * s
                    )
                )

                SortField.entries.forEach { field ->
                    SelectableChipRow(
                        label = field.label,
                        selected = field == sortField,
                        onClick = { onSortField(field) }
                    )
                }

                DirectionRow(
                    ascending = sortDirection == SortDirection.ASC,
                    onAscending = {
                        onSortDirection(SortDirection.ASC)
                    },
                    onDescending = {
                        onSortDirection(SortDirection.DESC)
                    }
                )

                Spacer(modifier = Modifier.height(16.dp * s))

                Text(
                    text = "Group by",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = MaterialTheme.typography.titleSmall.fontSize * s
                    )
                )

                GroupDimension.entries.forEach { dimension ->
                    SelectableChipRow(
                        label = dimension.label,
                        selected = dimension == groupDimension,
                        onClick = {
                            onGroupDimension(dimension)
                        }
                    )
                }

                DirectionRow(
                    ascending = groupDirection == GroupDirection.ASC,
                    onAscending = {
                        onGroupDirection(GroupDirection.ASC)
                    },
                    onDescending = {
                        onGroupDirection(GroupDirection.DESC)
                    },
                    enabled = groupDimension != GroupDimension.NONE
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Done",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                    )
                )
            }
        }
    )
}

@Composable
private fun SelectableChipRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val s = LocalUiScale.current

    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                )
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn((32.dp * s).coerceAtLeast(28.dp))
            .padding(vertical = 2.dp * s)
    )
}

@Composable
private fun DirectionRow(
    ascending: Boolean,
    onAscending: () -> Unit,
    onDescending: () -> Unit,
    enabled: Boolean = true
) {
    val s = LocalUiScale.current

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp * s)
    ) {
        FilterChip(
            selected = ascending,
            onClick = onAscending,
            enabled = enabled,
            label = {
                Text(
                    text = "Ascending",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                    )
                )
            },
            modifier = Modifier.heightIn(
                (32.dp * s).coerceAtLeast(28.dp)
            )
        )

        FilterChip(
            selected = !ascending,
            onClick = onDescending,
            enabled = enabled,
            label = {
                Text(
                    text = "Descending",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                    )
                )
            },
            modifier = Modifier.heightIn(
                (32.dp * s).coerceAtLeast(28.dp)
            )
        )
    }
}

private val SortField.label: String
    get() = when (this) {
        SortField.NAME -> "Name"
        SortField.PATH -> "Path"
        SortField.SIZE -> "Size"
        SortField.LAST_MODIFIED -> "Last modified"
        SortField.DATE_TAKEN -> "Date taken"
    }

private val GroupDimension.label: String
    get() = when (this) {
        GroupDimension.NONE -> "None"
        GroupDimension.FOLDER -> "Folder"
        GroupDimension.FILE_TYPE -> "File type"
        GroupDimension.EXTENSION -> "Extension"
        GroupDimension.MODIFIED_DAILY -> "Last modified (daily)"
        GroupDimension.MODIFIED_MONTHLY -> "Last modified (monthly)"
        GroupDimension.TAKEN_DAILY -> "Date taken (daily)"
        GroupDimension.TAKEN_MONTHLY -> "Date taken (monthly)"
    }

@Composable
private fun TagFilterRow(
    availableTags: List<SceneTag>,
    selectedTag: SceneTag?,
    onTagSelected: (SceneTag?) -> Unit
) {
    val s = LocalUiScale.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(
                horizontal = 12.dp * s,
                vertical = 4.dp * s
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp * s)
    ) {
        if (selectedTag != null) {
            FilterChip(
                selected = false,
                onClick = { onTagSelected(null) },
                label = {
                    Text(
                        text = "All",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                        )
                    )
                },
                modifier = Modifier.heightIn(
                    (32.dp * s).coerceAtLeast(28.dp)
                ),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }

        availableTags.forEach { tag ->
            FilterChip(
                selected = selectedTag == tag,
                onClick = {
                    onTagSelected(
                        if (selectedTag == tag) null else tag
                    )
                },
                label = {
                    Text(
                        text = tag.displayName,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = MaterialTheme.typography.labelLarge.fontSize * s
                        )
                    )
                },
                modifier = Modifier.heightIn(
                    (32.dp * s).coerceAtLeast(28.dp)
                ),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

internal val IndicatorAlphaKey =
    SemanticsPropertyKey<Float>("indicatorAlpha")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GalleryRefreshContainer(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    pullState: PullToRefreshState = rememberPullToRefreshState(),
    indicatorTestTag: String? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val distanceFraction = pullState.distanceFraction

    val indicatorAlpha = remember {
        Animatable(0f)
    }

    var exitFadeRunning by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            exitFadeRunning = false
            indicatorAlpha.snapTo(1f)
        } else if (indicatorAlpha.value > 0f) {
            exitFadeRunning = true
            indicatorAlpha.animateTo(
                0f,
                tween(durationMillis = 220)
            )
            pullState.snapTo(0f)
            exitFadeRunning = false
        }
    }

    LaunchedEffect(
        distanceFraction,
        isRefreshing,
        exitFadeRunning
    ) {
        if (isRefreshing || exitFadeRunning) {
            return@LaunchedEffect
        }

        indicatorAlpha.snapTo(
            if (distanceFraction > 0f) 1f else 0f
        )
    }

    Box(
        modifier = modifier.pullToRefresh(
            state = pullState,
            isRefreshing = isRefreshing,
            onRefresh = onRefresh
        )
    ) {
        content()

        if (indicatorAlpha.value > 0f || isRefreshing) {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = isRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        val progress = indicatorAlpha.value
                        alpha = progress
                        scaleX = progress
                        scaleY = progress
                    }
                    .then(
                        if (indicatorTestTag != null) {
                            Modifier
                                .semantics {
                                    this[IndicatorAlphaKey] =
                                        indicatorAlpha.value
                                }
                                .testTag(indicatorTestTag)
                        } else {
                            Modifier
                        }
                    )
            )
        }
    }
}
