package vige.land.muchopesos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DirectionsTransit
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import vige.land.muchopesos.domain.model.CategoryGroup
import vige.land.muchopesos.domain.model.ExpenseCategory
import vige.land.muchopesos.ui.theme.MuchoPesosTheme

fun categoryIconFor(iconKey: String): ImageVector = when (iconKey) {
    "shopping_cart" -> Icons.Rounded.ShoppingCart
    "restaurant" -> Icons.Rounded.Restaurant
    "coffee" -> Icons.Rounded.LocalCafe
    "home" -> Icons.Rounded.Home
    "cleaning_services" -> Icons.Rounded.CleaningServices
    "chair" -> Icons.Rounded.Chair
    "directions_car" -> Icons.Rounded.DirectionsCar
    "local_gas_station" -> Icons.Rounded.LocalGasStation
    "directions_transit" -> Icons.Rounded.DirectionsTransit
    "local_parking" -> Icons.Rounded.LocalParking
    "bolt" -> Icons.Rounded.Bolt
    "water_drop" -> Icons.Rounded.WaterDrop
    "wifi" -> Icons.Rounded.Wifi
    "subscriptions" -> Icons.Rounded.Subscriptions
    "movie" -> Icons.Rounded.Movie
    "sports_esports" -> Icons.Rounded.SportsEsports
    "medical_services" -> Icons.Rounded.MedicalServices
    "flight" -> Icons.Rounded.Flight
    "pets" -> Icons.Rounded.Pets
    "card_giftcard" -> Icons.Rounded.CardGiftcard
    "handshake" -> Icons.Rounded.Handshake
    else -> Icons.Rounded.Category
}

fun groupIconFor(group: CategoryGroup): ImageVector = when (group) {
    CategoryGroup.FOOD -> Icons.Rounded.Restaurant
    CategoryGroup.HOUSING -> Icons.Rounded.Home
    CategoryGroup.TRANSPORT -> Icons.Rounded.DirectionsCar
    CategoryGroup.UTILITIES -> Icons.Rounded.Bolt
    CategoryGroup.ENTERTAINMENT -> Icons.Rounded.Movie
    CategoryGroup.OTHER -> Icons.Rounded.Category
    CategoryGroup.SETTLEMENT -> Icons.Rounded.Handshake
}

@Composable
fun categoryColorsFor(group: CategoryGroup): Pair<Color, Color> {
    val finance = MuchoPesosTheme.financeColors
    return when (group) {
        CategoryGroup.FOOD -> finance.categoryFoodContainer to finance.onCategoryFoodContainer
        CategoryGroup.HOUSING -> finance.categoryHousingContainer to finance.onCategoryHousingContainer
        CategoryGroup.TRANSPORT -> finance.categoryTransportContainer to finance.onCategoryTransportContainer
        CategoryGroup.UTILITIES -> finance.categoryUtilitiesContainer to finance.onCategoryUtilitiesContainer
        CategoryGroup.ENTERTAINMENT -> finance.categoryEntertainmentContainer to finance.onCategoryEntertainmentContainer
        CategoryGroup.OTHER -> finance.categoryOtherContainer to finance.onCategoryOtherContainer
        CategoryGroup.SETTLEMENT -> finance.settledContainer to finance.onSettledContainer
    }
}

@Composable
fun CategoryBadge(
    category: ExpenseCategory,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
) {
    val (containerColor, contentColor) = categoryColorsFor(category.group)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = categoryIconFor(category.iconKey),
            contentDescription = category.name,
            tint = contentColor,
            modifier = Modifier.size(iconSize),
        )
    }
}
