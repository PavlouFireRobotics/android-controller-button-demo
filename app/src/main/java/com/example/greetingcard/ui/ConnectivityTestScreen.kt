package com.example.greetingcard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.greetingcard.network.NetworkTester
import com.example.greetingcard.network.TestResult
import kotlinx.coroutines.launch

@Composable
fun ConnectivityTestScreen(
    onBack: () -> Unit
) {
    val tester = remember { NetworkTester() }
    val scope = rememberCoroutineScope()
    var results by remember { mutableStateOf(emptyList<TestResult>()) }
    var isTesting by remember { mutableStateOf(false) }

    val robotIp = "10.21.33.103"
    val alternativeIp = "10.21.33.11"
    val udpPort = 30000
    val tcpPort = 30001

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Button(onClick = onBack) {
                Text("Back")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Network Diagnostics",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF252525)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Targets:", color = Color.Gray, fontSize = 12.sp)
                Text("Robot: $robotIp (UDP:$udpPort, TCP:$tcpPort)", color = Color.White)
                Text("Gateway/Alternative: $alternativeIp", color = Color.White)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                enabled = !isTesting,
                onClick = {
                    scope.launch {
                        isTesting = true
                        results = listOf(tester.testUdp(robotIp, udpPort)) + results
                        isTesting = false
                    }
                }
            ) {
                Text("Test UDP", fontSize = 12.sp)
            }
            Button(
                modifier = Modifier.weight(1f),
                enabled = !isTesting,
                onClick = {
                    scope.launch {
                        isTesting = true
                        results = listOf(tester.testHeartbeat(robotIp, udpPort)) + results
                        isTesting = false
                    }
                }
            ) {
                Text("Heartbeat", fontSize = 12.sp)
            }
            Button(
                modifier = Modifier.weight(1f),
                enabled = !isTesting,
                onClick = {
                    scope.launch {
                        isTesting = true
                        results = listOf(tester.testTcp(robotIp, tcpPort)) + results
                        isTesting = false
                    }
                }
            ) {
                Text("Test TCP", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !isTesting,
            onClick = {
                scope.launch {
                    isTesting = true
                    val newResults = mutableListOf<TestResult>()
                    newResults.add(tester.testPing(robotIp))
                    newResults.add(tester.testPing(alternativeIp))
                    newResults.add(tester.testHeartbeat(robotIp, udpPort))
                    newResults.add(tester.testTcp(robotIp, tcpPort))
                    results = newResults.reversed() + results
                    isTesting = false
                }
            }
        ) {
            Text("Test All")
        }

        if (isTesting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = "Results:",
            style = MaterialTheme.typography.titleSmall,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(results) { result ->
                ResultItem(result)
            }
        }
    }
}

@Composable
fun ResultItem(result: TestResult) {
    Surface(
        color = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(if (result.isSuccess) Color.Green else Color.Red, RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${result.type} - ${result.destination}",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Text(text = result.startTime, fontSize = 10.sp, color = Color.Gray)
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (result.response != null) {
                Text(text = result.response, color = Color.LightGray, fontSize = 13.sp)
            }

            if (result.error != null) {
                Text(
                    text = "Error: ${result.error}",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
            }
            
            Text(
                text = if (result.isSuccess) "SUCCESS" else "FAILED",
                color = if (result.isSuccess) Color.Green else Color.Red,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
