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
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lowerbrightness.ui.theme.LowerBrightnessTheme

enum class FilterColor(val displayName: String, val emoji: String, val description: String, val r: Int, val g: Int, val b: Int) {
    BLACK("Đen", "⚫", "Xem video", 0, 0, 0),
    YELLOW("Vàng", "🟡", "Đọc chữ", 255, 200, 0),
    RED("Đỏ", "🔴", "Trước khi ngủ", 255, 0, 0)
}

enum class AppScreen {
    FILTER, TIMER
}

// Custom Premium Dark Color Scheme
private val SlateDarkColorScheme = darkColorScheme(
    primary = Color(0xFFF59E0B),        // Sunset Amber
    onPrimary = Color(0xFF0F172A),       // Slate 900
    primaryContainer = Color(0xFFD97706),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF818CF8),      // Pastel Indigo
    onSecondary = Color(0xFF0F172A),
    background = Color(0xFF0B0F19),     // Deep Obsidian Slate
    onBackground = Color(0xFFF1F5F9),   // Light Slate Text
    surface = Color(0xFF131B2E),        // Premium Deep Blue-Gray Card
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1E293B), // Medium Blue-Gray Card
    onSurfaceVariant = Color(0xFF94A3B8), // Muted Slate Text
    error = Color(0xFFF87171),
    onError = Color(0xFF0F172A)
)

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
            MaterialTheme(colorScheme = SlateDarkColorScheme) {
                var currentScreen by remember { mutableStateOf(AppScreen.FILTER) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp
                        ) {
                            NavigationBarItem(
                                selected = currentScreen == AppScreen.FILTER,
                                onClick = { currentScreen = AppScreen.FILTER },
                                icon = { Text("🌙", fontSize = 22.sp) },
                                label = { Text("Bộ lọc", fontWeight = FontWeight.Bold) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            NavigationBarItem(
                                selected = currentScreen == AppScreen.TIMER,
                                onClick = { currentScreen = AppScreen.TIMER },
                                icon = { Text("⏱️", fontSize = 22.sp) },
                                label = { Text("Hẹn giờ", fontWeight = FontWeight.Bold) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (currentScreen) {
                            AppScreen.FILTER -> {
                                FilterScreen(
                                    onBrightnessChange = { opacity, filterColor ->
                                        currentFilterColor = filterColor
                                        updateOverlay(opacity)
                                    }
                                )
                            }
                            AppScreen.TIMER -> {
                                TimerScreen(
                                    isAdminActive = isAdminActive.value,
                                    onRequestAdminPermission = { requestAdminPermission() },
                                    onStartTimer = { seconds -> startTimerService(seconds) },
                                    onCancelTimer = { cancelTimerService() }
                                )
                            }
                        }
                    }
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
            val intent = Intent(this, BrightnessService::class.java).apply {
                action = "ACTION_STOP_FILTER"
            }
            startService(intent)
        }
    }
}

@Composable
fun FilterScreen(
    onBrightnessChange: (Float, FilterColor) -> Unit
) {
    var brightness by remember { mutableStateOf(0f) }
    var isActive by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(FilterColor.BLACK) }
    val scrollState = rememberScrollState()

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
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(28.dp))
            
            // Premium Radial Percentage Display
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        )
                    )
                    .border(
                        3.dp,
                        Brush.sweepGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary,
                                MaterialTheme.colorScheme.primary
                            )
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (brightness < 0.3f) "☀️" else if (brightness < 0.7f) "🌤️" else "🌙",
                        fontSize = 32.sp,
                        modifier = Modifier.scale(iconScale)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${(brightness * 100).toInt()}%",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Mức giảm sáng",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            Text(
                text = "Giảm Độ Sáng Màn Hình",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Bảo vệ thị lực ban đêm, hỗ trợ ngủ ngon",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // Adjustments Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Filter color selection
                    Text(
                        text = "Màu lọc bảo vệ",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp)
                                    ) {
                                        Text(
                                            text = filter.emoji,
                                            fontSize = 22.sp
                                        )
                                        Text(
                                            text = filter.displayName,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (selectedFilter == filter) FontWeight.Bold else FontWeight.Normal
                                        )
                                        Text(
                                            text = filter.description,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("☀️", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(10.dp))
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
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("🌙", fontSize = 22.sp)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

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
                            defaultElevation = 4.dp
                        )
                    ) {
                        Text(
                            text = if (isActive) "✖" else "⚡",
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isActive) "TẮT BỘ LỌC" else "KÍCH HOẠT BỘ LỌC",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Features list card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FeatureRow("✨", "Giảm sáng sâu hơn mức hệ thống mặc định")
                    FeatureRow("🌙", "Lọc ánh sáng xanh, dễ chịu cho mắt")
                    FeatureRow("👁️", "Giảm mỏi mắt khi sử dụng trong tối")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun TimerScreen(
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
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Screen Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "⏱️",
                    fontSize = 32.sp,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Text(
                    text = "Hẹn Giờ Khóa Máy",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Text(
                text = "Tự động tắt và khóa màn hình giúp tiết kiệm pin",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 28.dp)
            )

            if (isTimerActive) {
                // Active countdown layout
                val minutes = remainingSeconds / 60
                val seconds = remainingSeconds % 60
                val timeString = String.format("%02d:%02d", minutes, seconds)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "ĐANG ĐẾM NGƯỢC",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Pulsing Clock text
                        Text(
                            text = timeString,
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 64.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Hệ thống sẽ khóa màn hình ngay khi hết giờ.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        Button(
                            onClick = onCancelTimer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(
                                text = "HỦY HẸN GIỜ",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White
                            )
                        }
                    }
                }
            } else {
                // Inactive countdown config screen
                if (!isAdminActive) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .clickable { onRequestAdminPermission() },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⚠️", fontSize = 24.sp, modifier = Modifier.padding(end = 12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Yêu cầu cấp quyền Quản trị",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "Nhấp vào đây để cho phép ứng dụng khóa màn hình khi hết giờ đếm ngược.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Thiết lập thời gian tắt",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.align(Alignment.Start)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Grid Presets (15p, 20p, 30p, 1h)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                15 to "15 Phút",
                                20 to "20 Phút",
                                30 to "30 Phút",
                                60 to "1 Giờ"
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
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Custom time toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isCustomSelected = !isCustomSelected }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = isCustomSelected,
                                onCheckedChange = { isCustomSelected = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Text(
                                text = "Tùy chọn thời gian: ${customMinutes.toInt()} phút",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCustomSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCustomSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (isCustomSelected) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Slider(
                                value = customMinutes,
                                onValueChange = { customMinutes = it },
                                valueRange = 1f..120f,
                                steps = 119,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        // Trigger timer button
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
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isAdminActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text(
                                text = if (isAdminActive) "BẮT ĐẦU HẸN GIỜ" else "KÍCH HOẠT & BẮT ĐẦU",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun FeatureRow(icon: String, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier.fillMaxWidth()
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
