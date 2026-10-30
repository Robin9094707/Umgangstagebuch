package app.umgangstagebuch.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import app.umgangstagebuch.data.Photo
import app.umgangstagebuch.util.Fmt
import coil3.compose.AsyncImage
import java.io.File

class PhotoActions(val pickFromGallery: () -> Unit, val takePhoto: () -> Unit)

/** Galerie (Photo Picker, ohne Berechtigung) und Kamera. */
@Composable
fun rememberPhotoActions(onPicked: (List<Uri>) -> Unit): PhotoActions {
    val context = LocalContext.current
    var pendingCamera by rememberSaveable { mutableStateOf<Uri?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) onPicked(uris)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCamera
        if (ok && uri != null) onPicked(listOf(uri))
    }
    return remember {
        PhotoActions(
            pickFromGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            takePhoto = {
                val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                val file = File(dir, "aufnahme_${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                pendingCamera = uri
                camera.launch(uri)
            },
        )
    }
}

@Composable
fun PhotoEditor(
    photos: List<Photo>,
    uris: Map<String, Uri>,
    onAddGallery: () -> Unit,
    onAddCamera: () -> Unit,
    onRemove: (Photo) -> Unit,
    modifier: Modifier = Modifier,
) {
    var viewer by remember { mutableStateOf<Int?>(null) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onAddCamera, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(18.dp)); Text("  Kamera")
            }
            FilledTonalButton(onClick = onAddGallery, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                Icon(Icons.Outlined.AddPhotoAlternate, null, Modifier.size(18.dp)); Text("  Galerie")
            }
        }
        if (photos.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(photos, key = { it.fileName }) { p ->
                    Box {
                        AsyncImage(
                            model = uris[p.fileName],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(110.dp).clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .clickable { viewer = photos.indexOf(p) },
                        )
                        IconButton(
                            onClick = { onRemove(p) },
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.55f), contentColor = Color.White),
                        ) { Icon(Icons.Rounded.Close, "Entfernen", Modifier.size(16.dp)) }
                    }
                }
            }
        }
    }
    viewer?.let { PhotoViewer(photos, uris, it) { viewer = null } }
}

@Composable
fun PhotoGrid(photos: List<Photo>, uris: Map<String, Uri>, modifier: Modifier = Modifier) {
    var viewer by remember { mutableStateOf<Int?>(null) }
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(photos, key = { it.fileName }) { p ->
            AsyncImage(
                model = uris[p.fileName],
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(140.dp).clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable { viewer = photos.indexOf(p) },
            )
        }
    }
    viewer?.let { PhotoViewer(photos, uris, it) { viewer = null } }
}

@Composable
fun PhotoViewer(photos: List<Photo>, uris: Map<String, Uri>, start: Int, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val pager = rememberPagerState(initialPage = start) { photos.size }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                ZoomableImage(uris[photos[page].fileName])
            }
            val current = photos.getOrNull(pager.currentPage)
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss, colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)) {
                    Icon(Icons.Rounded.Close, "Schließen")
                }
                if (current != null) {
                    Text(
                        "Hinzugefügt ${Fmt.dateTime(current.addedAt)} · ${pager.currentPage + 1}/${photos.size}",
                        color = Color.White, style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(uri: Uri?) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    AsyncImage(
        model = uri,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f }) }
            .transformable(state, canPan = { scale > 1f })
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
    )
}

