package com.logisparktech.doctrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.logisparktech.doctrack.domain.model.Category

object CategoryIcons {
    val all: Map<String, ImageVector> = linkedMapOf(
        "car" to Icons.Outlined.DirectionsCar,
        "bike" to Icons.Outlined.TwoWheeler,
        "passport" to Icons.Outlined.Flight,
        "badge" to Icons.Outlined.Badge,
        "id" to Icons.Outlined.CreditCard,
        "book" to Icons.AutoMirrored.Outlined.MenuBook,
        "shield_car" to Icons.Outlined.Shield,
        "health" to Icons.Outlined.HealthAndSafety,
        "heart" to Icons.Outlined.FavoriteBorder,
        "business" to Icons.Outlined.Business,
        "school" to Icons.Outlined.School,
        "receipt" to Icons.AutoMirrored.Outlined.ReceiptLong,
        "subscription" to Icons.Outlined.Autorenew,
        "key" to Icons.Outlined.Key,
        "folder" to Icons.Outlined.Folder,
        "description" to Icons.Outlined.Description,
        "home" to Icons.Outlined.Home,
        "work" to Icons.Outlined.Work,
        "bank" to Icons.Outlined.AccountBalance,
        "bolt" to Icons.Outlined.ElectricBolt,
        "wifi" to Icons.Outlined.Wifi,
        "pets" to Icons.Outlined.Pets,
        "star" to Icons.Outlined.StarOutline,
    )

    fun of(key: String): ImageVector = all[key] ?: Icons.Outlined.Folder

    val palette: List<Long> = listOf(
        0xFF4F46E5, 0xFF2563EB, 0xFF0891B2, 0xFF059669, 0xFF65A30D, 0xFFCA8A04,
        0xFFEA580C, 0xFFDC2626, 0xFFDB2777, 0xFF9333EA, 0xFF475569, 0xFF0F766E,
    )
}

@Composable
fun CategoryBadge(category: Category, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    CategoryBadge(icon = category.icon, color = category.color, size = size, modifier = modifier)
}

@Composable
fun CategoryBadge(icon: String, color: Long, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val tint = Color(color.toInt())
    val isDark = MaterialTheme.colorScheme.background.luminanceApprox() < 0.5f
    Box(
        modifier = modifier
            .size(size)
            .background(tint.copy(alpha = if (isDark) 0.24f else 0.12f), RoundedCornerShape(size * 0.32f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            CategoryIcons.of(icon),
            contentDescription = null,
            tint = if (isDark) lerpToWhite(tint) else tint,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

private fun Color.luminanceApprox(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

private fun lerpToWhite(c: Color): Color = Color(
    red = c.red + (1f - c.red) * 0.35f,
    green = c.green + (1f - c.green) * 0.35f,
    blue = c.blue + (1f - c.blue) * 0.35f,
    alpha = 1f,
)
