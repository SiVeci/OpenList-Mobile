package com.example.alist.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.alist.data.remote.model.AListFile
import java.util.Locale

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItemCard(
    file: AListFile,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fileIconInfo = getFileIconAndTint(file)
    val icon = fileIconInfo.icon

    val iconTint = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else if (file.is_dir) {
        MaterialTheme.colorScheme.secondary
    } else {
        fileIconInfo.tint
    }

    // Format: Folder • 2026/5/27 23:26 or 32.9 MB • 2026/5/28 08:23
    val prefixText = if (file.is_dir) "Folder" else formatFileSize(file.size)
    val timeString = try {
        val datePart = file.modified.substringBefore("T").replace("-", "/")
        val timePart = file.modified.substringAfter("T").take(5)
        "$datePart $timePart"
    } catch (e: Exception) {
        file.modified
    }
    val metaText = "$prefixText • $timeString"

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bounceScale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            else 
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    interactionSource = interactionSource,
                    indication = androidx.compose.foundation.LocalIndication.current
                )
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}


fun formatFileSize(size: Long): String {
    if (size <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

data class FileIconInfo(
    val icon: ImageVector,
    val tint: Color
)

@Composable
fun getFileIconAndTint(file: AListFile): FileIconInfo {
    if (file.is_dir) {
        return FileIconInfo(
            icon = Icons.Rounded.Folder,
            tint = MaterialTheme.colorScheme.secondary
        )
    }

    val ext = file.name.substringAfterLast('.', "").lowercase()

    return when (ext) {
        // Audio
        "mp3", "wav", "flac", "ogg", "m4a", "aac", "wma", "ape" -> FileIconInfo(
            icon = Icons.Rounded.MusicNote,
            tint = Color(0xFF9C27B0) // Purple
        )
        // Video
        "mp4", "mkv", "avi", "mov", "flv", "webm", "rmvb", "3gp", "ts" -> FileIconInfo(
            icon = Icons.Rounded.Movie,
            tint = Color(0xFFE91E63) // Pink/Red
        )
        // Image
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "ico", "heic", "tiff" -> FileIconInfo(
            icon = Icons.Rounded.Image,
            tint = Color(0xFF00BCD4) // Cyan
        )
        // Code / Config
        "json", "yaml", "yml", "xml", "kt", "java", "py", "js", "html", "css", "sh", "bat", "c", "cpp", "h", "cs", "go", "rs", "sql" -> FileIconInfo(
            icon = Icons.Rounded.Code,
            tint = Color(0xFFFF9800) // Amber/Orange
        )
        // PDF
        "pdf" -> FileIconInfo(
            icon = Icons.Rounded.PictureAsPdf,
            tint = Color(0xFFF44336) // Red
        )
        // Word Document
        "doc", "docx", "odt", "rtf" -> FileIconInfo(
            icon = Icons.Rounded.Description,
            tint = Color(0xFF2196F3) // Blue
        )
        // Excel / Spreadsheet
        "xls", "xlsx", "csv", "ods" -> FileIconInfo(
            icon = Icons.Rounded.TableChart,
            tint = Color(0xFF4CAF50) // Green
        )
        // PowerPoint / Slides
        "ppt", "pptx", "odp" -> FileIconInfo(
            icon = Icons.Rounded.Slideshow,
            tint = Color(0xFFFF5722) // Deep Orange
        )
        // Archive / Compressed
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz" -> FileIconInfo(
            icon = Icons.Rounded.FolderZip,
            tint = Color(0xFFFFC107) // Yellow-amber
        )
        // App / Executable
        "apk" -> FileIconInfo(
            icon = Icons.Rounded.Android,
            tint = Color(0xFF8BC34A) // Lime green
        )
        // Text / General Document
        "txt", "md", "log", "ini", "conf" -> FileIconInfo(
            icon = Icons.Rounded.Description,
            tint = Color(0xFF757575) // Gray
        )
        // Default File
        else -> FileIconInfo(
            icon = Icons.AutoMirrored.Rounded.InsertDriveFile,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}
