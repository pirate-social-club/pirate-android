package sc.pirate.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object PhosphorIcons {
    val House = icon("House") {
        moveTo(3f, 11f)
        lineTo(12f, 4f)
        lineTo(21f, 11f)
        moveTo(5f, 10f)
        lineTo(5f, 20f)
        lineTo(10f, 20f)
        lineTo(10f, 15f)
        lineTo(14f, 15f)
        lineTo(14f, 20f)
        lineTo(19f, 20f)
        lineTo(19f, 10f)
    }

    val Wallet = icon("Wallet") {
        moveTo(4f, 7f)
        lineTo(19f, 7f)
        quadTo(21f, 7f, 21f, 9f)
        lineTo(21f, 18f)
        quadTo(21f, 20f, 19f, 20f)
        lineTo(5f, 20f)
        quadTo(3f, 20f, 3f, 18f)
        lineTo(3f, 6f)
        quadTo(3f, 4f, 5f, 4f)
        lineTo(17f, 4f)
        moveTo(16f, 13.5f)
        lineTo(21f, 13.5f)
        moveTo(17.5f, 13.5f)
        lineTo(17.55f, 13.5f)
    }

    val ChatCircle = icon("ChatCircle") {
        moveTo(20f, 11.5f)
        quadTo(20f, 18f, 12f, 18f)
        quadTo(10f, 18f, 8.3f, 17.5f)
        lineTo(4f, 19f)
        lineTo(5.4f, 15.5f)
        quadTo(4f, 13.8f, 4f, 11.5f)
        quadTo(4f, 5f, 12f, 5f)
        quadTo(20f, 5f, 20f, 11.5f)
    }

    val Bell = icon("Bell") {
        moveTo(6f, 10f)
        quadTo(6f, 5f, 12f, 5f)
        quadTo(18f, 5f, 18f, 10f)
        lineTo(18f, 14f)
        lineTo(20f, 17f)
        lineTo(4f, 17f)
        lineTo(6f, 14f)
        lineTo(6f, 10f)
        moveTo(10f, 20f)
        quadTo(12f, 21f, 14f, 20f)
    }

    val UserCircle = icon("UserCircle") {
        moveTo(21f, 12f)
        arcTo(9f, 9f, 0f, false, true, 3f, 12f)
        arcTo(9f, 9f, 0f, false, true, 21f, 12f)
        moveTo(9f, 10f)
        quadTo(9f, 7f, 12f, 7f)
        quadTo(15f, 7f, 15f, 10f)
        quadTo(15f, 13f, 12f, 13f)
        quadTo(9f, 13f, 9f, 10f)
        moveTo(6.5f, 18f)
        quadTo(8.5f, 15f, 12f, 15f)
        quadTo(15.5f, 15f, 17.5f, 18f)
    }

    val List = icon("List") {
        moveTo(4f, 7f)
        lineTo(20f, 7f)
        moveTo(4f, 12f)
        lineTo(20f, 12f)
        moveTo(4f, 17f)
        lineTo(20f, 17f)
    }

    val Plus = icon("Plus") {
        moveTo(12f, 5f)
        lineTo(12f, 19f)
        moveTo(5f, 12f)
        lineTo(19f, 12f)
    }

    val CaretLeft = icon("CaretLeft") {
        moveTo(15f, 5f)
        lineTo(8f, 12f)
        lineTo(15f, 19f)
    }

    val CaretUp = icon("CaretUp") {
        moveTo(6f, 15f)
        lineTo(12f, 9f)
        lineTo(18f, 15f)
    }

    val CaretDown = icon("CaretDown") {
        moveTo(6f, 9f)
        lineTo(12f, 15f)
        lineTo(18f, 9f)
    }
}

private fun icon(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        name = name,
    ).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        )
    }.build()
