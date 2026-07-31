package dev.victorlpgazolli.mobilesink

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private var accessoryService by mutableStateOf<AccessoryService?>(null)
    private var isBound by mutableStateOf(false)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AccessoryService.LocalBinder
            accessoryService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            accessoryService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        enableEdgeToEdge()
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color.Black)) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
                    var accessoryConnected by remember { mutableStateOf(usbManager.accessoryList?.isNotEmpty() == true) }

                    // Monitor accessory connection status periodically
                    LaunchedEffect(Unit) {
                        while (true) {
                            val currentAccessory = usbManager.accessoryList?.firstOrNull()
                            accessoryConnected = currentAccessory != null
                            
                            // Requirement: If no accessory is connected, stop the service automatically
                            if (!accessoryConnected && isBound) {
                                stopAudioService()
                            }
                            
                            delay(1000)
                        }
                    }

                    val powerState by (accessoryService?.powerLevel ?: MutableStateFlow(StereoPower(0f, 0f))).collectAsState()
                    val throughput by (accessoryService?.throughputBps ?: MutableStateFlow(0L)).collectAsState()
                    
                    MainDashboard(
                        power = powerState,
                        throughput = throughput,
                        isAccessoryMode = accessoryConnected,
                        isServiceRunning = isBound,
                        onToggleService = {
                            if (isBound) {
                                stopAudioService()
                            } else {
                                val accessory = intent.getParcelableExtra<UsbAccessory>(UsbManager.EXTRA_ACCESSORY)
                                    ?: usbManager.accessoryList?.firstOrNull()
                                accessory?.let { startAudioService(it) }
                            }
                        }
                    )
                }
            }
        }
    }

    private fun startAudioService(accessory: UsbAccessory) {
        val serviceIntent = Intent(this, AccessoryService::class.java).apply {
            putExtra(UsbManager.EXTRA_ACCESSORY, accessory)
        }
        startForegroundService(serviceIntent)
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun stopAudioService() {
        if (isBound) {
            unbindService(connection)
            isBound = false
            accessoryService = null
        }
        val serviceIntent = Intent(this, AccessoryService::class.java)
        stopService(serviceIntent)
    }

    override fun onStart() {
        super.onStart()
        // Try binding to see if service is already running
        Intent(this, AccessoryService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
            accessoryService = null
        }
    }
}

@Composable
fun MainDashboard(
    power: StereoPower,
    throughput: Long,
    isAccessoryMode: Boolean,
    isServiceRunning: Boolean,
    onToggleService: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // VU Meter remains as the dynamic background
        VUMeterScreen(power)

        // Dashboard Overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 64.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "DroidSink Dash",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            StatusInfoRow(
                label = "Accessory Mode",
                value = if (isAccessoryMode) "ACTIVE" else "NOT FOUND",
                color = if (isAccessoryMode) Color(0xFF00E676) else Color(0xFFFF1744)
            )
            
            StatusInfoRow(
                label = "Throughput",
                value = formatBytesPerSecond(throughput),
                color = Color(0xFF00B0FF)
            )

            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = onToggleService,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isServiceRunning) Color(0xFF222222) else Color(0xFF00E676)
                ),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.width(160.dp)
            ) {
                Text(
                    text = if (isServiceRunning) "STOP" else "START",
                    color = if (isServiceRunning) Color.White else Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun StatusInfoRow(label: String, value: String, color: Color) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(text = "$label: ", color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Text(text = value, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

fun formatBytesPerSecond(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format("%.2f MB/s", bytes / (1024f * 1024f))
        bytes >= 1024 -> String.format("%.1f KB/s", bytes / 1024f)
        else -> "$bytes B/s"
    }
}

@Composable
fun VUMeterScreen(power: StereoPower) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val animatedLeft by animateFloatAsState(
        targetValue = power.left,
        animationSpec = tween(durationMillis = 40, easing = LinearEasing),
        label = "vuLeft"
    )
    val animatedRight by animateFloatAsState(
        targetValue = power.right,
        animationSpec = tween(durationMillis = 40, easing = LinearEasing),
        label = "vuRight"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (isLandscape) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 80.dp),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                VUBar(animatedLeft, isVertical = false, modifier = Modifier.weight(1f))
                VUBar(animatedRight, isVertical = false, modifier = Modifier.weight(1f))
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 200.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                VUBar(animatedLeft, isVertical = true, modifier = Modifier.weight(1f))
                VUBar(animatedRight, isVertical = true, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun VUBar(power: Float, isVertical: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize().padding(if (isVertical) 16.dp else 8.dp)) {
        val segmentCount = 60
        val spacing = 2.dp.toPx()

        if (isVertical) {
            val segmentHeight = (size.height - (segmentCount - 1) * spacing) / segmentCount
            for (i in 0 until segmentCount) {
                val threshold = (segmentCount - i).toFloat() / segmentCount * 100f
                val isActive = power >= threshold
                val color = getVUColor(threshold, isActive)

                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, i * (segmentHeight + spacing)),
                    size = Size(size.width, segmentHeight),
                    cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                )
            }
        } else {
            val segmentWidth = (size.width - (segmentCount - 1) * spacing) / segmentCount
            for (i in 0 until segmentCount) {
                val threshold = (i + 1).toFloat() / segmentCount * 100f
                val isActive = power >= threshold
                val color = getVUColor(threshold, isActive)

                drawRoundRect(
                    color = color,
                    topLeft = Offset(i * (segmentWidth + spacing), 0f),
                    size = Size(segmentWidth, size.height),
                    cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                )
            }
        }
    }
}

private fun getVUColor(threshold: Float, isActive: Boolean): Color {
    return when {
        threshold > 92f -> if (isActive) Color(0xFFFF1744) else Color(0x08FF1744)
        threshold > 75f -> if (isActive) Color(0xFFFFEA00) else Color(0x08FFEA00)
        else -> if (isActive) Color(0xFF00E676) else Color(0x0800E676)
    }
}
