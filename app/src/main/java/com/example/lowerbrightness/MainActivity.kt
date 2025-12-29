package com.example.lowerbrightness

import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lowerbrightness.ui.theme.LowerBrightnessTheme

enum class FilterColor(val displayName: String, val emoji: String, val description: String, val r: Int, val g: Int, val b: Int) {
    BLACK("Đen", "⚫", "Xem video", 0, 0, 0),
    YELLOW("Vàng", "🟡", "Đọc chữ", 255, 200, 0),
    RED("Đỏ", "🔴", "Trước khi ngủ", 255, 0, 0)
}

class MainActivity : ComponentActivity() {
    private var overlayView: android.view.View? = null
    private var currentFilterColor = FilterColor.BLACK
    private val overlayWindowManager by lazy {
        getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Check overlay permission
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
        
        setContent {
            LowerBrightnessTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BrightnessControl(
                        modifier = Modifier.padding(innerPadding),
                        onBrightnessChange = { opacity, filterColor ->
                            currentFilterColor = filterColor
                            updateOverlay(opacity)
                        }
                    )
                }
            }
        }
    }

    private fun updateOverlay(opacity: Float) {
        if (!Settings.canDrawOverlays(this)) {
            return
        }

        // Remove existing overlay
        overlayView?.let {
            try {
                overlayWindowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (opacity > 0f) {
            // Create new overlay
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
                },
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
                        WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                x = 0
                y = 0
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
            }

            overlayView = android.view.View(this).apply {
                setBackgroundColor(
                    android.graphics.Color.argb(
                        (opacity * 255).toInt(),
                        currentFilterColor.r,
                        currentFilterColor.g,
                        currentFilterColor.b
                    )
                )
            }

            overlayWindowManager.addView(overlayView, params)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let {
            try {
                overlayWindowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

@Composable
fun BrightnessControl(
    modifier: Modifier = Modifier,
    onBrightnessChange: (Float, FilterColor) -> Unit
) {
    var brightness by remember { mutableStateOf(0f) }
    var isActive by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(FilterColor.BLACK) }

    val animatedBrightness by animateFloatAsState(
        targetValue = brightness,
        animationSpec = tween(300),
        label = "brightness"
    )

    val buttonColor by animateColorAsState(
        targetValue = if (isActive) 
            MaterialTheme.colorScheme.error 
        else 
            MaterialTheme.colorScheme.primary,
        animationSpec = tween(300),
        label = "buttonColor"
    )

    val iconScale by animateFloatAsState(
        targetValue = if (isActive) 1.2f else 1f,
        animationSpec = tween(300),
        label = "iconScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon với animation
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(iconScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (brightness < 0.3f) "☀️"
                          else if (brightness < 0.7f) "🌤️"
                          else "🌙",
                    fontSize = 56.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Giảm Độ Sáng",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Bảo vệ mắt ban đêm",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Brightness percentage display
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                                    )
                                )
                            )
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${(brightness * 100).toInt()}%",
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 56.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Filter color selection
                    Text(
                        text = "Chọn màu lọc",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FilterColor.entries.forEach { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = {
                                    selectedFilter = filter
                                    if (isActive) {
                                        onBrightnessChange(brightness, filter)
                                    }
                                },
                                label = {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                                    ) {
                                        Text(
                                            text = filter.emoji,
                                            fontSize = 24.sp
                                        )
                                        Text(
                                            text = filter.displayName,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (selectedFilter == filter) FontWeight.Bold else FontWeight.Normal
                                        )
                                        Text(
                                            text = filter.description,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Slider with labels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "☀️",
                            fontSize = 24.sp
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))

                        Slider(
                            value = brightness,
                            onValueChange = { 
                                brightness = it
                                if (isActive) {
                                    onBrightnessChange(it, selectedFilter)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = "🌙",
                            fontSize = 24.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Toggle button
                    Button(
                        onClick = {
                            isActive = !isActive
                            if (isActive) {
                                onBrightnessChange(brightness, selectedFilter)
                            } else {
                                onBrightnessChange(0f, selectedFilter)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonColor
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 4.dp,
                            pressedElevation = 8.dp
                        )
                    ) {
                        Text(
                            text = if (isActive) "🌙" else "☀️",
                            fontSize = 24.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isActive) "TẮT BỘ LỌC" else "BẬT BỘ LỌC",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Features list
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FeatureRow("✨", "Giảm sáng sâu hơn mức hệ thống")
                    FeatureRow("🌙", "Hoàn hảo cho ban đêm")
                    FeatureRow("👁️", "Bảo vệ thị lực")
                }
            }
        }
    }
}

@Composable
fun FeatureRow(icon: String, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = icon,
            fontSize = 20.sp,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
