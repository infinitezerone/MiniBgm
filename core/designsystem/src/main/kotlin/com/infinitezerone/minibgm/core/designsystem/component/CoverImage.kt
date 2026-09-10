package com.infinitezerone.minibgm.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * 封面或头像在无有效图片 URL 时的占位语义类型
 */
enum class CoverPlaceholder {
    /** 动画/作品条目占位符（胶卷 Movie 图标） */
    Subject,

    /** 人物/角色/声优占位符（人形 Person 图标） */
    Person,

    /** 无占位图标（仅保留背景底色） */
    None,
}

@Composable
fun CoverImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    aspectRatio: Float = 0.7f,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    placeholder: CoverPlaceholder = CoverPlaceholder.Subject,
    fallbackIcon: ImageVector? = null,
) {
    val trimmedUrl = remember(url) { url.trim() }
    val resolvedIcon: ImageVector? =
        fallbackIcon ?: when (placeholder) {
            CoverPlaceholder.Subject -> Icons.Filled.Movie
            CoverPlaceholder.Person -> Icons.Filled.Person
            CoverPlaceholder.None -> null
        }

    Box(
        modifier =
            modifier
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(cornerRadius))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (trimmedUrl.isNotBlank()) {
            AsyncImage(
                model = trimmedUrl,
                contentDescription = contentDescription,
                contentScale = contentScale,
                alignment = alignment,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (resolvedIcon != null) {
            Icon(
                imageVector = resolvedIcon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier =
                    Modifier
                        .fillMaxSize(0.55f)
                        .sizeIn(maxWidth = 36.dp, maxHeight = 36.dp),
            )
        }
    }
}
