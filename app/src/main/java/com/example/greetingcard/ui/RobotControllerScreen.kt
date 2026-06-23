package com.example.greetingcard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.greetingcard.components.VideoPlayer
import com.example.greetingcard.network.RobotBatteryState
import com.example.greetingcard.ui.theme.GreetingCardTheme

@Composable
fun RobotControllerScreen(
    modifier: Modifier = Modifier,
    state: G20State = G20State(),
    batteryState: RobotBatteryState = RobotBatteryState(),
    networkStatus: String = "Unknown",
    onSleepToggle: () -> Unit = {}
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ROBOT CONTROL CENTER",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Sleep/Wake Button
                if (batteryState.sleepStatus != 2) {
                    Button(
                        onClick = onSleepToggle,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (batteryState.isSleeping) Color.DarkGray else Color(0xFFE91E63)
                        ),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text(if (batteryState.isSleeping) "WAKE UP" else "SLEEP")
                    }
                } else {
                    // Show "Entering Sleep..." indicator or nothing
                    Text("ENTERING SLEEP...", fontSize = 10.sp, color = Color.Yellow)
                }
                
                Spacer(modifier = Modifier.width(16.dp))

                // Battery Indicators
                BatterySmallDisplay("REAR", batteryState.rear)
                Spacer(modifier = Modifier.width(16.dp))
                BatterySmallDisplay("FRONT", batteryState.front)
                Spacer(modifier = Modifier.width(24.dp))
                
                Text(
                    text = networkStatus,
                    fontSize = 12.sp,
                    color = if (networkStatus.contains("Connected")) Color.Green else Color.Red
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Video Feed Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("FRONT WIDE-ANGLE", fontSize = 10.sp, color = Color.Gray)
                VideoPlayer(
                    url = "rtsp://10.21.33.103:8554/video1",
                    modifier = Modifier.fillMaxSize(),
                    enabled = batteryState.sleepStatus == 0
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("REAR WIDE-ANGLE", fontSize = 10.sp, color = Color.Gray)
                VideoPlayer(
                    url = "rtsp://10.21.33.103:8554/video2",
                    modifier = Modifier.fillMaxSize(),
                    enabled = batteryState.sleepStatus == 0
                )
            }
        }

        // Joysticks Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Swapped CH3 and CH4 as requested. CH3-Y is reversed (2000=UP)
            JoystickDisplay("LEFT STICK (CH4-X, CH3-Y)", state.channels[3], state.channels[2], reverseY = true)
            // CH2-Y is NOT reversed as requested (2000=DOWN)
            JoystickDisplay("RIGHT STICK (CH1-X, CH2-Y)", state.channels[0], state.channels[1], reverseY = false)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Roller and Toggle Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SpecialControl("GIS TOGGLE (CH5)", decodeSwitch(state.channels[4]))
            SpecialControl("ROLLER (CH14)", state.channels[13].toString())
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Buttons Grid (Manual layout to avoid nesting scrollables)
        Text(
            text = "BUTTONS & SWITCHES",
            style = MaterialTheme.typography.titleSmall,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Button rows
        val buttonData = listOf(
            "HOME (CH6)" to state.channels[5],
            "L2 (CH7)" to state.channels[6],
            "CAMERA (CH8)" to state.channels[7],
            "B1 (CH9)" to state.channels[8],
            "B2 (CH10)" to state.channels[9],
            "L1 (CH11)" to state.channels[10],
            "SPRAY (CH12)" to state.channels[11],
            "R1 (CH15)" to state.channels[14],
            "R2 (CH16)" to state.channels[15]
        )

        buttonData.chunked(3).forEach { rowButtons ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowButtons.forEach { (label, value) ->
                    ButtonStateItem(
                        label = label,
                        value = value,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Fill empty slots if row is incomplete
                repeat(3 - rowButtons.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun JoystickDisplay(label: String, axisX: Int, axisY: Int, reverseY: Boolean = true) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = Color.Gray)
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(Color.DarkGray, CircleShape)
                .padding(4.dp)
        ) {
            // Center lines
            HorizontalDivider(modifier = Modifier.align(Alignment.Center).width(110.dp), color = Color.Gray.copy(alpha = 0.3f))
            VerticalDivider(modifier = Modifier.align(Alignment.Center).height(110.dp).width(1.dp), color = Color.Gray.copy(alpha = 0.3f))
            
            // Thumb
            val offsetX = ((axisX - 1500) / 500f) * 50
            val multiplier = if (reverseY) -1 else 1
            val offsetY = multiplier * ((axisY - 1500) / 500f) * 50
            
            Box(
                modifier = Modifier
                    .offset(x = offsetX.dp, y = offsetY.dp)
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .align(Alignment.Center)
            )
        }
        Text("X: $axisX Y: $axisY", fontSize = 12.sp, color = Color.White)
    }
}

@Composable
fun ButtonStateItem(label: String, value: Int, modifier: Modifier = Modifier) {
    val isActive = value > 1700
    Surface(
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else Color(0xFF252525),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.height(60.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(4.dp)
        ) {
            Text(label, fontSize = 10.sp, color = if (isActive) Color.White else Color.Gray)
            Text(
                text = if (isActive) "ON" else "OFF",
                fontWeight = FontWeight.Bold,
                color = if (isActive) Color.White else if (value < 1300) Color.DarkGray else Color.Gray
            )
            Text(value.toString(), fontSize = 9.sp, color = Color.Gray)
        }
    }
}

@Composable
fun SpecialControl(label: String, value: String) {
    Surface(
        color = Color(0xFF252525),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.width(160.dp).height(80.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(label, fontSize = 10.sp, color = Color.Gray)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Cyan)
        }
    }
}

@Composable
fun BatterySmallDisplay(label: String, info: com.example.greetingcard.network.BatteryInfo) {
    val color = when {
        info.level > 60 -> Color.Green
        info.level > 20 -> Color.Yellow
        else -> Color.Red
    }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 8.sp, color = Color.Gray)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(12.dp)
                    .background(Color.DarkGray, RoundedCornerShape(2.dp))
                    .padding(1.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(info.level / 100f)
                        .background(if (info.isCharging) Color.Cyan else color, RoundedCornerShape(1.dp))
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${info.level}%",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Text(
            text = "${"%.1f".format(info.voltage)}V | ${"%.1f".format(info.temperature)}°C",
            fontSize = 9.sp,
            color = Color.LightGray
        )
    }
}

fun decodeSwitch(value: Int): String {
    return when {
        value < 1250 -> "DOWN"
        value > 1750 -> "UP"
        else -> "MIDDLE"
    }
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,orientation=landscape")
@Composable
fun RobotControllerPreview() {
    GreetingCardTheme {
        RobotControllerScreen()
    }
}
