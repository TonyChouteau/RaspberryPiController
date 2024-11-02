import asyncio
import ssl
import websockets

from pynput.mouse import Button, Controller
from pyautogui import press, write
# from datetime import datetime, timedelta

with open("keys.txt", "r") as f:
    keys = f.readlines()
    SERVER_IP = keys[0].replace("\n", "")
    SERVER_PORT = keys[1].replace("\n", "")
    CERT_FILE = keys[2].replace("\n", "")
    KEY_FILE = keys[3].replace("\n", "")
    PASSWORD = keys[4].replace("\n", "")
    TOKEN = keys[5].replace("\n", "")

# Define the WebSocket server address and port
SERVER_ADDRESS = SERVER_IP
SERVER_PORT = SERVER_PORT

# Dictionary to store connected clients
connected_clients = {}
mouse = Controller()


async def handle_message(websocket, path):
    global mouse

    # Register client connection
    connected_client = {
        "websocket": websocket,
        "auth": False,
        "entry": ""
    }
    connected_clients[websocket.id] = connected_client
    print(f"Client connected: {websocket.remote_address}")

    try:
        async for message in websocket:
            # Print received message from client
            print(f"Message from client {websocket.remote_address}: {message}")

            # Handle different types of messages
            if message.startswith("DRAG"):
                if not connected_client["auth"]:
                    print(f"Not authorized")
                    continue
                # Example message: "DRAG x y"
                _, x, y = message.split()
                print(f"Received drag movement: x={x}, y={y}")
                mouse.position = (mouse.position[0] + float(x), mouse.position[1] + float(y))

            elif message == "ENTER":
                print("Received enter event")
                if connected_client["entry"] == PASSWORD:
                    connected_client["auth"] = True
                    connected_client["entry"] = ""
                    print("Authent OK")
                    continue
                if not connected_client["auth"]:
                    print(f"Not authorized")
                    continue

            elif message == "LEFT_CLICK":
                if not connected_client["auth"]:
                    print(f"Not authorized")
                    continue
                print("Received left click event")
                mouse.press(Button.left)
                mouse.release(Button.left)

            elif message == "MIDDLE_CLICK":
                if not connected_client["auth"]:
                    print(f"Not authorized")
                    continue
                print("Received middle click event")
                mouse.press(Button.middle)
                mouse.release(Button.middle)

            elif message == "RIGHT_CLICK":
                if not connected_client["auth"]:
                    print(f"Not authorized")
                    continue
                print("Received right click event")
                mouse.press(Button.right)
                mouse.release(Button.right)

            elif message.startswith("KEYBOARD"):
                # Additional keyboard processing can be added here
                char = message.replace("KEYBOARD_INPUT ", "")
                if char == '287762808832':
                    connected_client["entry"] = connected_client["entry"][:-1]
                else:
                    connected_client["entry"] += char
                print(connected_client["entry"])

                if not connected_client["auth"]:
                    print(f"Not authorized")
                    continue

                if char == '287762808832':
                    press("backspace")
                else:
                    write(char)

            # Echo message back to the client (optional)
            await websocket.send(f"Server received: {message}")

    except websockets.ConnectionClosed:
        print(f"Client disconnected: {websocket.remote_address}")

    finally:
        # Unregister client connection
        del connected_clients[websocket.id]


async def main():
    # Create an SSL context
    ssl_context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ssl_context.load_cert_chain(certfile=CERT_FILE, keyfile=KEY_FILE)

    # Start the WebSocket server
    async with websockets.serve(handle_message, SERVER_ADDRESS, SERVER_PORT, ssl=ssl_context):
        print(f"WebSocket server running on wss://{SERVER_ADDRESS}:{SERVER_PORT}")
        await asyncio.Future()  # Run forever

# Run the WebSocket server
if __name__ == "__main__":
    asyncio.run(main())