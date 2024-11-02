# Raspberry Pi Controller App
This project is an Android app designed to serve as a remote controller for a Raspberry Pi device. 
The app enables users to interact with a connected Raspberry Pi over a WebSocket connection by sending keyboard inputs and various mouse events directly from an Android device. 
The app is useful for remote control of Raspberry Pi-based applications, particularly when a physical keyboard or mouse isn't available.

## Features
- Keyboard Input: Send single-character keyboard inputs to the Raspberry Pi.
- Mouse Events: Simulate mouse actions like left-click, middle-click, right-click, and mouse move with a touch-and-drag gesture.

## Android App
The source of the Android app is located in `app/`.

The app need some configuration to work. 
Add this 2 lines to the file `local.properties` :
```
URL="wss://domain.name:port"
TOKEN="AUTH_TOKEN"
```

### Configuration
The app need some configuration to work. 

## Server 
The Python server is located in `server/`. 

The server need some configuration to work.
Add this lines to the file `keys.txt` located to the folder `server/` :
```
0.0.0.0
PORT
/path/to/fullchain.pem
/path/to/privkey.pem
PASSWORD
AUTH_TOKEN
```

## How It Works
The app communicates with a WebSocket server running on the Raspberry Pi. 
It captures keyboard and button inputs from the Android device and sends corresponding commands over the WebSocket connection, allowing the Raspberry Pi to interpret these commands and perform specific actions.

How to Use the App
Connection: Once configured, the Android app attempts to connect to the WebSocket server on the Raspberry Pi automatically. 
A button will appear to allow reconnection if needed.

Authentication: After establishing the connection, the app sends an authentication token to the server. 
If the token is validated, the connection is fully established, enabling the app to send commands to the server.

Authorization: To proceed, use the Android app keyboard to enter the PASSWORD and press the ENTER button.

Control Mode: If the password is verified, the Android app gains control of the Raspberry Pi's mouse and keyboard directly, allowing you to send inputs like mouse movements and clicks.
