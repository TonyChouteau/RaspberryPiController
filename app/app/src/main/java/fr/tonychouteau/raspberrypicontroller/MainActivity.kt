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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import fr.tonychouteau.raspberrypicontroller.ui.theme.RaspberryPiControllerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class MainActivity : ComponentActivity() {
    private var webSocket = mutableStateOf<WebSocket?>(null)
    private var connectionStatus = mutableStateOf(ConnectionStatus.DISCONNECTED)
    private var authStatus = mutableStateOf(AuthStatus.NONE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initializeWebSocket()

        setContent {
            RaspberryPiControllerTheme {
                MainScreen(webSocket, connectionStatus, authStatus) { initializeWebSocket() }
            }
        }
    }

    private fun initializeWebSocket() {
        val client = OkHttpClient()
        val request = Request.Builder().url(BuildConfig.URL).build()
        webSocket.value = client.newWebSocket(request, EchoWebSocketListener(
            this,
            onConnectionStatusChange = { status ->
                connectionStatus.value = status

                runOnUiThread {
                    setContent {
                        RaspberryPiControllerTheme {
                            MainScreen(webSocket, connectionStatus, authStatus) { initializeWebSocket() }
                        }
                    }
                }
            },
            onLogin = { status ->
                authStatus.value = status

                runOnUiThread {
                    setContent {
                        RaspberryPiControllerTheme {
                            MainScreen(webSocket, connectionStatus, authStatus) { initializeWebSocket() }
                        }
                    }
                }
            }
        ))
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocket.value?.close(1000, "Activity Destroyed")
    }
}

class EchoWebSocketListener(
    private val context: Context,
    private val onConnectionStatusChange: (ConnectionStatus) -> Unit,
    private val onLogin: (AuthStatus) -> Unit
) : WebSocketListener() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onOpen(webSocket: WebSocket, response: Response) {
        onConnectionStatusChange(ConnectionStatus.CONNECTED)
        showToast("WebSocket connected")

        webSocket.send("API_KEY ${BuildConfig.TOKEN}")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        if (text == "FORBIDDEN") {
            onLogin(AuthStatus.FORBIDDEN)
        } else if (text == "AUTHORIZED") {
            onLogin(AuthStatus.AUTHORIZED)
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
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

enum class AuthStatus {
    NONE,
    FORBIDDEN,
    AUTHORIZED
}

@Composable
fun MainScreen(
    webSocket: MutableState<WebSocket?>,
    connectionStatus: MutableState<ConnectionStatus>,
    authStatus: MutableState<AuthStatus>,
    onRestartWebSocket: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
//                .onKeyEvent { keyEvent ->
//                    if (keyEvent.type == KeyEventType.KeyDown) {
//                        println("Key in Box: ${keyEvent.key.keyCode}")
//                        CoroutineScope(Dispatchers.IO).launch {
//                            webSocket.value?.send("KEYBOARD_INPUT ${keyEvent.key.keyCode}")
//                        }
//                        true
//                    } else false
//                }
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
                    authStatus.value = AuthStatus.NONE
                    onRestartWebSocket()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .absolutePadding(0.dp, 0.dp, 20.dp, 70.dp)
                    .width(80.dp)
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
            ) {
                Icon(
                    imageVector = Icons.Filled.Wifi,
                    contentDescription = "Connection Icon",
                    modifier = Modifier
                        .width(20.dp),
                    tint = Color.White,
                )

                when (authStatus.value) {
                    AuthStatus.FORBIDDEN -> {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Forbidden",
                            modifier = Modifier
                                .width(20.dp),
                            tint = Color.White,
                        )
                    }
                    AuthStatus.AUTHORIZED -> {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = "Authorized",
                            modifier = Modifier
                                .width(20.dp),
                            tint = Color.White,
                        )
                    }
                    else -> {} // No additional icon if not forbidden or authorized
                }
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

                    if (webSocket.value != null && connectionStatus.value == ConnectionStatus.CONNECTED) {
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
                .background(Color(0xff90CAF9), shape = RoundedCornerShape(5.dp))
                .focusable()
        ) {
            BasicTextField(
                value = inputText,
                onValueChange = { newText ->
                    val lastText = inputText
                    inputText = newText

                    if (lastText.length > inputText.length) {
                        CoroutineScope(Dispatchers.IO).launch {
                            webSocket?.send("KEYBOARD_INPUT 287762808832")
                        }
                        if (inputText.isEmpty()) {
                            inputText = ""
                        }
                    } else if (lastText.length < inputText.length && inputText.isNotEmpty()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            webSocket?.send("KEYBOARD_INPUT ${newText[newText.length - 1]}")
                        }
                    }

//                    inputText = ""
//                    if (newText.isNotEmpty()) {
//                        val lastLetter = newText.last().toString()
//
//                        CoroutineScope(Dispatchers.IO).launch {
//                            webSocket?.send("KEYBOARD_INPUT $lastLetter")
//                        }
//
//                    }
                },
                modifier = Modifier
                    .width(60.dp)
                    .height(50.dp)
                    .background(
                        Color(0xff90CAF9),
                        shape = RoundedCornerShape(5.dp)
                    )
                    .focusRequester(focusRequester),
//                    .onPreviewKeyEvent { keyEvent ->
//                        println("Key pressed: ${keyEvent.key.keyCode} ${keyEvent.type}")
//                        if (keyEvent.type == KeyEventType.KeyDown) {
//                            println("Key pressed: ${keyEvent.key.keyCode}")
//                            CoroutineScope(Dispatchers.IO).launch {
//                                webSocket?.send("KEYBOARD_INPUT ${keyEvent.key.keyCode}")
//                            }
//                            true
//                        } else false
//                    },
                singleLine = true,
                textStyle = TextStyle(color = Color.White)
            )

            Text(
                "_",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                color = Color.DarkGray
            )
        }

        Button(
            onClick = { sendMouseEvent(webSocket, "BACK") },
            modifier = Modifier
                .width(60.dp)
                .height(50.dp)
                .background(
                    Color(0xff90CAF9),
                    shape = RoundedCornerShape(5.dp)
                ),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.DarkGray
            )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Backspace,
                contentDescription = "Backspace",
                tint = Color.DarkGray,
            )
        }

        Button(
            onClick = { sendMouseEvent(webSocket, "ENTER") },
            modifier = Modifier
                .width(60.dp)
                .height(50.dp)
                .background(
                    Color(0xff90CAF9),
                    shape = RoundedCornerShape(5.dp)
                ),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.DarkGray
            )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = Color.DarkGray,
            )
        }

        Button(
            onClick = { sendMouseEvent(webSocket, "LEFT_CLICK") },
            modifier = Modifier
                .width(60.dp)
                .height(50.dp)
                .background(
                    Color(0xff90CAF9),
                    shape = RoundedCornerShape(5.dp)
                ),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.DarkGray
            )
        ) {
            Icon(
                imageVector = Icons.Filled.AdsClick,
                contentDescription = "Left click",
                tint = Color.DarkGray,
            )
        }

        Button(
            onClick = { sendMouseEvent(webSocket, "MIDDLE_CLICK") },
            modifier = Modifier
                .width(60.dp)
                .height(50.dp)
                .background(
                    Color(0xff90CAF9),
                    shape = RoundedCornerShape(5.dp)
                ),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.DarkGray
            )
        ) {
            Icon(
                imageVector = Icons.Filled.FilterCenterFocus,
                contentDescription = "Middle click",
                tint = Color.DarkGray,
            )
        }
        Button(
            onClick = { sendMouseEvent(webSocket, "RIGHT_CLICK") },
            modifier = Modifier
                .width(60.dp)
                .height(50.dp)
                .background(
                    Color(0xff90CAF9),
                    shape = RoundedCornerShape(5.dp)
                ),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.DarkGray
            )
        ) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = "Right click",
                tint = Color.DarkGray,
            )
        }
    }
}

fun sendMouseEvent(webSocket: WebSocket?, event: String) {
    CoroutineScope(Dispatchers.IO).launch {
        webSocket?.send(event)
    }
}