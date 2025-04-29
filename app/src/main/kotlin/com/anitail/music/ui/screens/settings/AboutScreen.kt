package com.anitail.music.ui.screens.settings

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.anitail.music.BuildConfig
import com.anitail.music.LocalPlayerAwareWindowInsets
import com.anitail.music.R
import com.anitail.music.ui.component.IconButton
import com.anitail.music.ui.utils.backToMain

@Composable
fun shimmerEffect(): Brush {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    )

    val transition = rememberInfiniteTransition(label = "shimmerEffect")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "shimmerEffect"
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))
        Spacer(Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .size(90.dp)
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp)
                )
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(NavigationBarDefaults.Elevation))
        ) {
            Image(
                painter = painterResource(R.drawable.ic_ani),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground, BlendMode.SrcIn),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(90.dp)
                    .align(Alignment.Center)
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = shimmerEffect(),
                        shape = RoundedCornerShape(12.dp)
                    )
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Build,
                contentDescription = stringResource(R.string.build_type),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.build_type))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = BuildConfig.BUILD_TYPE)
        }

        // Version İnfo for Users
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically

        ){
            Icon(
                imageVector = Icons.Rounded.Verified,
                contentDescription = stringResource(R.string.version),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.version))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = BuildConfig.VERSION_NAME)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically

        ){
            Icon(
                imageVector = Icons.Rounded.VerifiedUser,
                contentDescription = stringResource(R.string.official_build),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.official_build))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = BuildConfig.VERSION_CODE.toString()) // Burası düzeltildi
        }

        // Device İnfo for Users
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically

        ){
            Icon(
                imageVector = Icons.Rounded.Devices,
                contentDescription = stringResource(R.string.device),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.device))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "${Build.BRAND} ${Build.DEVICE} (${Build.MODEL})")
        }

        // Developer Card
        Spacer(Modifier.height(5.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.person),
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.developer_about),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        UserCards(uriHandler) // Developer Card Call
        Spacer(Modifier.height(5.dp))

        // Beta Testers Section
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.person),
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.beta_testers),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        BetaTestersSection(uriHandler)
        Spacer(Modifier.height(5.dp))

        // Links and Buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.links),
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.links_about),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(5.dp))

        CardItem(
            icon = R.drawable.discord,
            title = stringResource(R.string.my_channel),
            subtitle = stringResource(R.string.my_channel_info),
            onClick = { uriHandler.openUri("https://discord.gg/fvskrQZb9j") }
        )


        Spacer(Modifier.height(20.dp))

        CardItem(
            icon = R.drawable.babel_software_apps,
            title = stringResource(R.string.other_apps),
            subtitle = stringResource(R.string.other_apps_info),
            onClick = { uriHandler.openUri("https://github.com/Animetailapp") }
        )

        Spacer(Modifier.height(20.dp))

        CardItem(
            icon = R.drawable.patreon,
            title = stringResource(R.string.patreon),
            subtitle = stringResource(R.string.patreon_info),
            onClick = { uriHandler.openUri("https://www.patreon.com/abydev") }
        )

        Spacer(Modifier.height(20.dp))

    }

    TopAppBar(
        title = { Text(stringResource(R.string.about)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}

@Composable
fun UserCards(uriHandler: UriHandler) {
    Column {
        UserCard(
            imageUrl = "https://avatars.githubusercontent.com/u/21024973?v=4",
            name = "[̲̅A̲̅][̲̅b̲̅][̲̅y̲̅]",
            role = stringResource(R.string.info_dev),
            onClick = { uriHandler.openUri("https://github.com/Dark25") }
        )
    }
}

@Composable
fun UserCard(
    imageUrl: String,
    name: String,
    role: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .height(140.dp)
            .shadow(8.dp, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = rememberAsyncImagePainter(model = imageUrl),
                    contentDescription = null,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = role,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            // Decorative element
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(40.dp)
                    .offset(x = 20.dp, y = (-20).dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f),
                        CircleShape
                    )
            )
        }
    }
}
@Composable
fun CardItem(
    icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
//            .shadow(8.dp, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

            }

        }
    }

}

// Beta testers section composable
@Composable
fun BetaTestersSection(uriHandler: UriHandler) {
    val testers = listOf(
        BetaTester(
            imageUrl = "https://i.ibb.co/pjkzBvGn/image.png",
            name = "im.shoul",
            onClick = { uriHandler.openUri("https://discord.com/users/237686500567810058") }
        ),
        BetaTester(
            imageUrl = "https://i.ibb.co/DPjf5V78/61fc6cc5422936a8fd81a913fbdf773b.png",
            name = "Lucia (Lú)",
            onClick = { uriHandler.openUri("https://discord.com/users/553307420688908320") }
        ),
        BetaTester(
            imageUrl = "https://i.ibb.co/mrgvc1nb/14f2a18fa8b9e553a048027375db5f81.png",
            name = "ElDeLasTojas",
            onClick = { uriHandler.openUri("https://discord.com/users/444680132393697291") }
        ),
        BetaTester(
            imageUrl = "https://i.ibb.co/gnXkhnJ/be81ecb723cfd4186e85bfe81793f594.png",
            name = "SKHLORDKIRA",
            onClick = { uriHandler.openUri("https://discord.com/users/445310321717018626") }
        ),
        BetaTester(
            imageUrl = "https://i.ibb.co/TDdPq2jF/0f0f47f2a47eca3eda2a433237b4a05d.png",
            name = "\uD835\uDC07\uD835\uDC22\uD835\uDC22\uD835\uDC2B\uD835\uDC1B\uD835\uDC1A\uD835\uDC1F ◣‸◢\n",
            onClick = { uriHandler.openUri("https://discord.com/users/341662495301304323") }
        ),
        BetaTester(
            imageUrl = "https://i.ibb.co/3YPX1wsj/dec881377d42d58473b6d988165406b6.png",
            name = "Jack",
            onClick = { uriHandler.openUri("https://discord.com/users/1166985299885309954") }
        ),
        BetaTester(
            imageUrl = "https://i.ibb.co/htmds91/b514910877f4b585309265fbe922f020.png",
            name = "R4fa3l_2008",
            onClick = { uriHandler.openUri("https://discord.com/users/1318948121782521890") }
        ),
        BetaTester(
            imageUrl = "https://i.ibb.co/mrwz7J7K/165cbedbd96ae35c2489286c8db9777d.png",
            name = "Ryak",
            onClick = { uriHandler.openUri("https://discord.com/users/1075797587770228856") }
        ),
        BetaTester(
            imageUrl = "https://i.ibb.co/LXXWGJCt/e8cdcf2c32ee7056806c5a8bfa607830.png",
            name = "LucianRC",
            onClick = { uriHandler.openUri("https://discord.com/users/420641532446769157") }
        ),
        BetaTester(
            imageUrl = "https://i.ibb.co/8Dc1f67r/image.png",
            name = "Alexx",
            onClick = { uriHandler.openUri("https://discord.com/users/743896907184734268") }
        ),
    )
    // Responsive: 4 columns on large, 2 on small
    val columns = if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 500) 2 else 5
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        testers.chunked(columns).forEach { rowTesters ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                rowTesters.forEach { tester ->
                    UserCardCompact(
                        imageUrl = tester.imageUrl,
                        name = tester.name,
                        role = "Beta Tester",
                        onClick = tester.onClick
                    )
                }
            }
        }
    }
}

@Composable
fun UserCardCompact(
    imageUrl: String,
    name: String,
    role: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(7.dp)
            .width(86.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = rememberAsyncImagePainter(model = imageUrl),
            contentDescription = null,
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = role,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            maxLines = 1
        )
    }
}

data class BetaTester(
    val imageUrl: String,
    val name: String,
    val onClick: () -> Unit
)