package fr.tonychouteau.raspberrypicontroller

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import fr.tonychouteau.raspberrypicontroller.ui.theme.RaspberryPiControllerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class MainActivity : ComponentActivity() {
    private var webSocket = mutableStateOf<WebSocket?>(null)
    private var connectionStatus = mutableStateOf(ConnectionStatus.DISCONNECTED)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initializeWebSocket()

        setContent {
            RaspberryPiControllerTheme {
                MainScreen(webSocket, connectionStatus) { initializeWebSocket() }
            }
        }
    }

    private fun initializeWebSocket() {
        val client = OkHttpClient()
        val request = Request.Builder().url(BuildConfig.URL).build()
        webSocket.value = client.newWebSocket(request, EchoWebSocketListener(this) { status ->
            connectionStatus.value = status

            runOnUiThread {
                setContent {
                    RaspberryPiControllerTheme {
                        MainScreen(webSocket, connectionStatus) { initializeWebSocket() }
                    }
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocket.value?.close(1000, "Activity Destroyed")
    }
}

class EchoWebSocketListener(
    private val context: Context,
    private val onConnectionStatusChange: (ConnectionStatus) -> Unit
) : WebSocketListener() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
        onConnectionStatusChange(ConnectionStatus.CONNECTED)
        showToast("WebSocket connected")

        webSocket.send("API_KEY ${BuildConfig.TOKEN}")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        println("Message received: $text")
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
        onConnectionStatusChange(ConnectionStatus.DISCONNECTED)
        showToast("WebSocket connection failed: ${t.message}")
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        onConnectionStatusChange(ConnectionStatus.DISCONNECTED)
        showToast("WebSocket closing: $code / $reason")
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        onConnectionStatusChange(ConnectionStatus.DISCONNECTED)
        showToast("WebSocket closed: $code / $reason")
    }

    private fun showToast(message: String) {
        handler.post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}

enum class ConnectionStatus {
    CONNECTED,
    DISCONNECTED,
    WAITING,
    CLICKED;
}

@Composable
fun MainScreen(
    webSocket: MutableState<WebSocket?>,
    connectionStatus: MutableState<ConnectionStatus>,
    onRestartWebSocket: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        println("Key in Box: ${keyEvent.key.keyCode}")
                        CoroutineScope(Dispatchers.IO).launch {
                            webSocket.value?.send("KEYBOARD_INPUT ${keyEvent.key.keyCode}")
                        }
                        true
                    } else false
                }
                .focusRequester(focusRequester)
                .focusable()
        ) {
            val buttonColor = when (connectionStatus.value) {
                ConnectionStatus.CONNECTED -> Color.Green
                ConnectionStatus.DISCONNECTED -> Color.Red
                ConnectionStatus.WAITING, ConnectionStatus.CLICKED -> Color(0xFFFFA500)
            }

            DragArea(webSocket, connectionStatus, Modifier.fillMaxSize())

            Button(
                onClick = {
                    connectionStatus.value = ConnectionStatus.WAITING
                    onRestartWebSocket()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .width(60.dp)
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
            ) {
                Icon(
                    imageVector = Icons.Filled.Wifi,
                    contentDescription = "Connection Icon",
                    tint = Color.White,
                )
                Spacer(modifier = Modifier.width(4.dp))
            }


            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                ButtonRow(
                    webSocket = webSocket.value
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
fun DragArea(
    webSocket: MutableState<WebSocket?>,
    connectionStatus: MutableState<ConnectionStatus>,
    modifier: Modifier = Modifier
) {
    var dragX by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }
    var lastDragTimestamp by remember { mutableStateOf(0L) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Gray)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    dragX += dragAmount.x
                    dragY += dragAmount.y
                    change.consume()

                    println("Mouse moved ${dragAmount.x} ${dragAmount.y}")

                    if (webSocket != null && connectionStatus.value == ConnectionStatus.CONNECTED) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastDragTimestamp > 10) {
                            lastDragTimestamp = currentTime
                            CoroutineScope(Dispatchers.IO).launch {
                                webSocket.value?.send("DRAG ${dragAmount.x} ${dragAmount.y}")
                            }
                        }
                    } else {
                        println("No websocket or connection is off ")
                    }
                }
            }
    )
}

@Composable
fun ButtonRow(
    webSocket: WebSocket?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(1.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        var inputText by remember { mutableStateOf("") }
        val focusRequester = remember { FocusRequester() }

        Box(
            modifier = Modifier
                .width(100.dp)
                .height(50.dp)
                .background(Color(0xff90CAF9), shape = androidx.compose.foundation.shape.RoundedCornerShape(5.dp))
                .focusable()
        ) {
            BasicTextField(
                value = inputText,
                onValueChange = { newText ->
                    if (newText.isNotEmpty()) {
                        val lastLetter = newText.last().toString()

                        CoroutineScope(Dispatchers.IO).launch {
                            webSocket?.send("KEYBOARD_INPUT $lastLetter")
                        }

                        inputText = lastLetter
                    }
                },
                modifier = Modifier
                    .width(80.dp)
                    .height(50.dp)
                    .background(
                        Color(0xff90CAF9),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(5.dp)
                    )
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { keyEvent ->
                        println("Key pressed: ${keyEvent.key.keyCode} ${keyEvent.type}")
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            println("Key pressed: ${keyEvent.key.keyCode}")
                            CoroutineScope(Dispatchers.IO).launch {
                                webSocket?.send("KEYBOARD_INPUT ${keyEvent.key.keyCode}")
                            }
                            true
                        } else false
                    },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White)
            )

            Text(
                "Type here and press keys...",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                color = Color.DarkGray
            )
        }

        Button(
            onClick = { sendMouseEvent(webSocket, "ENTER") },
            modifier = Modifier
                .width(80.dp)
                .height(50.dp)
                .background(
                    Color(0xff90CAF9),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(5.dp)
                ),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.DarkGray
            )
        ) {
            Text(">")
        }

        Button(
            onClick = { sendMouseEvent(webSocket, "LEFT_CLICK") },
            modifier = Modifier
                .width(80.dp)
                .height(50.dp)
                .background(
                    Color(0xff90CAF9),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(5.dp)
                ),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.DarkGray
            )
        ) {
            Text("<-")
        }

        Button(
            onClick = { sendMouseEvent(webSocket, "MIDDLE_CLICK") },
            modifier = Modifier
                .width(80.dp)
                .height(50.dp)
                .background(
                    Color(0xff90CAF9),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(5.dp)
                ),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.DarkGray
            )
        ) {
            Text("/\\")
        }
        Button(
            onClick = { sendMouseEvent(webSocket, "RIGHT_CLICK") },
            modifier = Modifier
                .width(80.dp)
                .height(50.dp)
                .background(
                    Color(0xff90CAF9),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(5.dp)
                ),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.DarkGray
            )
        ) {
            Text("->")
        }
    }
}

fun sendMouseEvent(webSocket: WebSocket?, event: String) {
    CoroutineScope(Dispatchers.IO).launch {
        webSocket?.send(event)
    }
}