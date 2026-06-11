package com.example.lowerbrightness

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
    private var currentFilterColor = FilterColor.BLACK
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var componentName: ComponentName
    private val isAdminActive = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        componentName = ComponentName(this, MyDeviceAdminReceiver::class.java)

        // Check overlay permission
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
        
        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        
        setContent {
            LowerBrightnessTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BrightnessControl(
                        modifier = Modifier.padding(innerPadding),
                        isAdminActive = isAdminActive.value,
                        onRequestAdminPermission = { requestAdminPermission() },
                        onStartTimer = { seconds -> startTimerService(seconds) },
                        onCancelTimer = { cancelTimerService() },
                        onBrightnessChange = { opacity, filterColor ->
                            currentFilterColor = filterColor
                            updateOverlay(opacity)
                        }
                    )
                }
            }
        }
    }

    private fun requestAdminPermission() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Cần quyền quản trị thiết bị để khóa màn hình điện thoại khi hết giờ.")
        }
        startActivity(intent)
    }

    private fun startTimerService(seconds: Int) {
        val intent = Intent(this, BrightnessService::class.java).apply {
            action = "ACTION_START_TIMER"
            putExtra("DURATION_SECONDS", seconds)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun cancelTimerService() {
        val intent = Intent(this, BrightnessService::class.java).apply {
            action = "ACTION_CANCEL_TIMER"
        }
        startService(intent)
    }

    override fun onResume() {
        super.onResume()
        isAdminActive.value = devicePolicyManager.isAdminActive(componentName)
    }

    private fun updateOverlay(opacity: Float) {
        if (!Settings.canDrawOverlays(this)) {
            return
        }

        if (opacity > 0f) {
            val intent = Intent(this, BrightnessService::class.java).apply {
                action = "ACTION_START"
                putExtra("OPACITY", opacity)
                putExtra("COLOR", currentFilterColor.name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } else {
            // Check if timer is not running before stopping service
            val intent = Intent(this, BrightnessService::class.java).apply {
                action = "ACTION_STOP_FILTER"
            }
            startService(intent)
        }
    }
}

@Composable
fun BrightnessControl(
    modifier: Modifier = Modifier,
    isAdminActive: Boolean,
    onRequestAdminPermission: () -> Unit,
    onStartTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onBrightnessChange: (Float, FilterColor) -> Unit
) {
    var brightness by remember { mutableStateOf(0f) }
    var isActive by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(FilterColor.BLACK) }
    val scrollState = rememberScrollState()

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
                .padding(24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
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

            // Timer Lock Card
            TimerControlCard(
                isAdminActive = isAdminActive,
                onRequestAdminPermission = onRequestAdminPermission,
                onStartTimer = onStartTimer,
                onCancelTimer = onCancelTimer
            )

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

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun TimerControlCard(
    isAdminActive: Boolean,
    onRequestAdminPermission: () -> Unit,
    onStartTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit
) {
    val remainingSeconds by BrightnessService.remainingSeconds.collectAsState()
    val isTimerActive by BrightnessService.isTimerActive.collectAsState()

    var selectedMinutes by remember { mutableStateOf(15) }
    var isCustomSelected by remember { mutableStateOf(false) }
    var customMinutes by remember { mutableFloatStateOf(10f) }

    val currentSelectedMinutes = if (isCustomSelected) customMinutes.toInt() else selectedMinutes

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
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "⏱️",
                    fontSize = 28.sp,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Column {
                    Text(
                        text = "Hẹn giờ tắt màn hình",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Tự động khóa thiết bị",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (isTimerActive) {
                val minutes = remainingSeconds / 60
                val seconds = remainingSeconds % 60
                val timeString = String.format("%02d:%02d", minutes, seconds)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Thời gian còn lại",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = timeString,
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onCancelTimer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        text = "HỦY HẸN GIỜ",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            } else {
                if (!isAdminActive) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .clickable { onRequestAdminPermission() },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⚠️", fontSize = 20.sp, modifier = Modifier.padding(end = 8.dp))
                            Text(
                                text = "Chưa cấp quyền Quản trị thiết bị. Nhấn vào đây để bật quyền khóa màn hình.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Text(
                    text = "Chọn thời gian nhanh",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        15 to "15p",
                        20 to "20p",
                        30 to "30p",
                        60 to "1h"
                    ).forEach { (mins, label) ->
                        FilterChip(
                            selected = !isCustomSelected && selectedMinutes == mins,
                            onClick = {
                                isCustomSelected = false
                                selectedMinutes = mins
                            },
                            label = {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = isCustomSelected,
                        onCheckedChange = { isCustomSelected = it }
                    )
                    Text(
                        text = "Tùy chọn thời gian: ${customMinutes.toInt()} phút",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCustomSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }

                if (isCustomSelected) {
                    Slider(
                        value = customMinutes,
                        onValueChange = { customMinutes = it },
                        valueRange = 1f..120f,
                        steps = 119,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (!isAdminActive) {
                            onRequestAdminPermission()
                        } else {
                            onStartTimer(currentSelectedMinutes * 60)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAdminActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text(
                        text = if (isAdminActive) "BẮT ĐẦU HẸN GIỜ" else "KÍCH HOẠT QUYỀN & BẬT",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
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
