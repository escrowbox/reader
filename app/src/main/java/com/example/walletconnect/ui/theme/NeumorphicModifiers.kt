package com.example.walletconnect.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.neumorphicElevated(
    cornerRadius: Dp = 16.dp,
    lightShadowColor: Color = NeumorphicLightShadow,
    darkShadowColor: Color = NeumorphicDarkShadow,
    shadowBlur: Dp = 10.dp,
    offsetX: Dp = 6.dp,
    offsetY: Dp = 6.dp
): Modifier = this.then(
    drawBehind {
        val shadowBlurPx = shadowBlur.toPx()
        val offsetXPx = offsetX.toPx()
        val offsetYPx = offsetY.toPx()
        
        drawRect(
            color = darkShadowColor.copy(alpha = 0.3f),
            topLeft = Offset(offsetXPx, offsetYPx),
            size = size
        )
        
        drawRect(
            color = lightShadowColor.copy(alpha = 0.7f),
            topLeft = Offset(-offsetXPx / 2, -offsetYPx / 2),
            size = size
        )
    }
)

fun Modifier.neumorphicPressed(
    cornerRadius: Dp = 16.dp,
    lightShadowColor: Color = NeumorphicLightShadow,
    darkShadowColor: Color = NeumorphicDarkShadow,
    shadowBlur: Dp = 6.dp,
    offsetX: Dp = 3.dp,
    offsetY: Dp = 3.dp
): Modifier = this.then(
    drawBehind {
        val shadowBlurPx = shadowBlur.toPx()
        val offsetXPx = offsetX.toPx()
        val offsetYPx = offsetY.toPx()
        
        drawRect(
            color = darkShadowColor.copy(alpha = 0.4f),
            topLeft = Offset(-offsetXPx, -offsetYPx),
            size = size
        )
        
        drawRect(
            color = lightShadowColor.copy(alpha = 0.5f),
            topLeft = Offset(offsetXPx / 2, offsetYPx / 2),
            size = size
        )
    }
)




