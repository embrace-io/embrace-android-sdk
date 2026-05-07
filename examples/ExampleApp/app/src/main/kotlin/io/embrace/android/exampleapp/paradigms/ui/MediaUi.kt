package io.embrace.android.exampleapp.paradigms.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image as FoundationImage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.embrace.android.exampleapp.paradigms.data.ImageSource
import io.embrace.android.exampleapp.paradigms.data.MediaRef
import io.embrace.android.exampleapp.paradigms.data.VideoSource
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun MediaItem(
    media: MediaRef,
    modifier: Modifier = Modifier,
) {
    when (media) {
        is MediaRef.Image -> ImageItem(source = media.source, modifier = modifier)
        is MediaRef.Video -> VideoItem(source = media.source, modifier = modifier)
    }
}

@Composable
fun ImageItem(
    source: ImageSource,
    modifier: Modifier = Modifier,
) {
    when (source) {
        is ImageSource.Procedural -> ProceduralImage(
            seed = source.seed,
            aspectRatio = source.aspectRatio,
            modifier = modifier,
        )
        is ImageSource.LocalDrawable -> FoundationImage(
            painter = painterResource(id = source.resId),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .fillMaxWidth()
                .aspectRatioIfPositive(source.aspectRatio),
        )
        is ImageSource.Remote -> RemoteImagePlaceholder(
            url = source.url,
            aspectRatio = source.aspectRatio,
            modifier = modifier,
        )
    }
}

@Composable
fun VideoItem(
    source: VideoSource,
    modifier: Modifier = Modifier,
) {
    when (source) {
        is VideoSource.Procedural -> ProceduralVideo(
            seed = source.seed,
            aspectRatio = source.aspectRatio,
            modifier = modifier,
        )
        is VideoSource.LocalRaw -> RemoteVideoPlaceholder(
            label = "local raw video",
            aspectRatio = source.aspectRatio,
            modifier = modifier,
        )
        is VideoSource.Remote -> RemoteVideoPlaceholder(
            label = source.url,
            aspectRatio = source.aspectRatio,
            modifier = modifier,
        )
    }
}

@Composable
fun ProceduralImage(
    seed: Long,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 1f,
) {
    val rng = Random(seed)
    val baseHue = rng.nextFloat() * 360f
    val accentHue = (baseHue + 60f + rng.nextFloat() * 240f) % 360f
    val highlightHue = (baseHue + 180f + rng.nextFloat() * 60f) % 360f
    val shapeCount = 6 + rng.nextInt(8)
    val shapes = List(shapeCount) {
        ShapeBlob(
            cxFraction = rng.nextFloat(),
            cyFraction = rng.nextFloat(),
            radiusFraction = 0.08f + rng.nextFloat() * 0.22f,
            hue = if (rng.nextBoolean()) accentHue else highlightHue,
            saturation = 0.4f + rng.nextFloat() * 0.5f,
            lightness = 0.45f + rng.nextFloat() * 0.4f,
            alpha = 0.35f + rng.nextFloat() * 0.45f,
        )
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatioIfPositive(aspectRatio)
            .clip(RoundedCornerShape(12.dp)),
    ) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    hsl(baseHue, 0.55f, 0.35f),
                    hsl(accentHue, 0.6f, 0.55f),
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
            size = size,
        )
        shapes.forEach { blob ->
            drawCircle(
                color = hsl(blob.hue, blob.saturation, blob.lightness, blob.alpha),
                radius = blob.radiusFraction * size.minDimension,
                center = Offset(blob.cxFraction * size.width, blob.cyFraction * size.height),
            )
        }
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    hsl(highlightHue, 0.6f, 0.2f, 0.35f),
                ),
            ),
            size = size,
        )
    }
}

@Composable
fun ProceduralVideo(
    seed: Long,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 16f / 9f,
) {
    val rng = Random(seed)
    val baseHue = rng.nextFloat() * 360f
    val accentHue = (baseHue + 80f + rng.nextFloat() * 200f) % 360f
    val transition = rememberInfiniteTransition(label = "video-$seed")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "video-phase-$seed",
    )
    val particleCount = 18
    val particles = List(particleCount) {
        ParticleSeed(
            offsetXFraction = rng.nextFloat(),
            offsetYFraction = rng.nextFloat(),
            radiusFraction = 0.04f + rng.nextFloat() * 0.05f,
            phaseShift = rng.nextFloat(),
            hue = if (rng.nextBoolean()) baseHue else accentHue,
        )
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatioIfPositive(aspectRatio),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp)),
        ) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        hsl(baseHue, 0.65f, 0.25f),
                        hsl(accentHue, 0.7f, 0.45f),
                    ),
                    start = Offset(size.width * phase, 0f),
                    end = Offset(size.width * (1f - phase), size.height),
                ),
                size = size,
            )
            particles.forEach { particle ->
                val cx = size.width *
                    (0.5f + 0.5f * cos((phase + particle.phaseShift) * 6.283f) * particle.offsetXFraction)
                val cy = size.height *
                    (0.5f + 0.5f * sin((phase + particle.phaseShift) * 6.283f) * particle.offsetYFraction)
                drawCircle(
                    color = hsl(particle.hue, 0.65f, 0.6f, 0.5f),
                    radius = particle.radiusFraction * size.minDimension,
                    center = Offset(cx, cy),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(color = Color.Black.copy(alpha = 0.45f), shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Play video",
                tint = Color.White,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .align(Alignment.BottomStart),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "%02d:%02d".format(((phase * 60).toInt()) / 60, ((phase * 60).toInt()) % 60),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = "01:00",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun RemoteImagePlaceholder(
    url: String,
    aspectRatio: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatioIfPositive(aspectRatio)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "remote: $url",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RemoteVideoPlaceholder(
    label: String,
    aspectRatio: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatioIfPositive(aspectRatio)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "video: $label",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class ShapeBlob(
    val cxFraction: Float,
    val cyFraction: Float,
    val radiusFraction: Float,
    val hue: Float,
    val saturation: Float,
    val lightness: Float,
    val alpha: Float,
)

private data class ParticleSeed(
    val offsetXFraction: Float,
    val offsetYFraction: Float,
    val radiusFraction: Float,
    val phaseShift: Float,
    val hue: Float,
)

private fun Modifier.aspectRatioIfPositive(ratio: Float): Modifier =
    if (ratio > 0f) {
        this.then(Modifier.aspectRatio(ratio))
    } else {
        this
    }

private fun hsl(hue: Float, saturation: Float, lightness: Float, alpha: Float = 1f): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val c = (1f - abs(2f * lightness - 1f)) * saturation
    val hp = h / 60f
    val x = c * (1f - abs(hp.mod(2f) - 1f))
    val (r1, g1, b1) = when (hp.toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = lightness - c / 2f
    return Color(red = r1 + m, green = g1 + m, blue = b1 + m, alpha = alpha)
}
