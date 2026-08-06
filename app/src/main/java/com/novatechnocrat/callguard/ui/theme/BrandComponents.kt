package studio.ainovations.callguard.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val AINOVATIONS_WORDMARK_TEST_TAG = "ainovations_wordmark"
const val AINOVATIONS_FOOTER_TEST_TAG = "ainovations_footer"

fun Modifier.irisBackdrop(): Modifier = this
    .background(BrandColors.Canvas)
    .drawBehind {
        drawIrisMesh()
    }

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.White,
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 0.5.dp,
            color = Color(0xFFE2E0DC),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        content = content,
    )
}

@Composable
fun AInovationsWordmark(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.testTag(AINOVATIONS_WORDMARK_TEST_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "AInovations",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.02).sp,
            ),
        )
        Text(
            text = ".",
            style = TextStyle(
                brush = Brush.horizontalGradient(
                    listOf(BrandColors.IrisIndigo, BrandColors.IrisCyan, BrandColors.IrisLime),
                ),
                fontSize = MaterialTheme.typography.titleMedium.fontSize,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
fun BrandFooter(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .testTag(AINOVATIONS_FOOTER_TEST_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        AInovationsWordmark()
        Text(
            text = "AI systems studio",
            style = MaterialTheme.typography.labelSmall,
            color = BrandColors.InkSoft,
        )
    }
}

@Composable
fun BrandMeshPreview(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(1.dp)) {
        drawIrisMesh()
    }
}

private fun DrawScope.drawIrisMesh() {
    drawCircle(
        color = BrandColors.IrisIndigo.copy(alpha = 0.10f),
        radius = size.maxDimension * 0.58f,
        center = Offset(size.width * 0.12f, size.height * 0.10f),
    )
    drawCircle(
        color = BrandColors.IrisCyan.copy(alpha = 0.09f),
        radius = size.maxDimension * 0.46f,
        center = Offset(size.width * 0.92f, size.height * 0.28f),
    )
    drawCircle(
        color = BrandColors.IrisLime.copy(alpha = 0.11f),
        radius = size.maxDimension * 0.44f,
        center = Offset(size.width * 0.70f, size.height * 0.98f),
    )
    drawCircle(
        color = BrandColors.IrisRose.copy(alpha = 0.10f),
        radius = size.maxDimension * 0.38f,
        center = Offset(size.width * 0.08f, size.height * 0.82f),
    )
}
