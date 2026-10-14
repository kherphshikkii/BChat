# Bluetooth Chat (BChat)

A simple Android chat app that sends and receives messages between nearby phones over Bluetooth, with no internet, Wi-Fi, or mobile data.

Built with native Android (Kotlin) and Bluetooth Classic RFCOMM sockets.

## Features

- Scan for nearby Bluetooth devices, with a live list and "Close by" / "Nearby" signal labels
- Connect and disconnect, with a reconnect button when the link drops
- Real-time two-way text messaging
- Chat bubbles with timestamps and day labels
- Usernames shared automatically when two phones connect
- Connection status in the chat header (connected, connecting, disconnected)
- Recent chats on the home screen, saved on the phone
- Settings: change your name, visibility, chat history, and approval before connecting
- "Clear all chats" with a confirmation dialog
- Permission screens that explain why access is needed, plus a recovery path if it is denied
- Error handling for failed connections, failed sends, and Bluetooth turned off

## Screens

Home, Nearby devices, Chat, Permission, Permission denied, Settings.

Add screenshots to a `docs/` folder and link them here:

```markdown
![Home](docs/home.png)
```

## How it works

Both phones run the same app and use the same service UUID. While the app is open, each phone listens for incoming connections. When you tap **Connect** on a discovered device, your phone opens an RFCOMM socket to the other phone, which behaves like a continuous two-way pipe.

Every message is sent as a type byte followed by the text. Type 1 carries the sender's username and type 2 carries a chat message. Reading and listening run on background threads, and all results are handed back to the main thread.

### Why Bluetooth Classic (RFCOMM)

For a chat app, RFCOMM is the simplest fit: open a socket, then read and write text. Bluetooth Low Energy (BLE) uses far less battery, but it needs GATT services, characteristics, and small packet sizes, which adds a lot of complexity.

## Requirements

- Android 8.0 (API 26) or newer
- Two physical Android phones (Bluetooth does not work in the emulator)
- Android Studio, with the Android SDK installed

## Permissions

| Android version | Permissions | Notes |
|---|---|---|
| 8 to 11 (API 26-30) | `BLUETOOTH`, `BLUETOOTH_ADMIN` | Granted at install |
| | `ACCESS_FINE_LOCATION` | Required by Android for scanning. The app never reads your location. The Location switch must be on while scanning. |
| 12 and newer (API 31+) | `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE` | Shown as one "Nearby devices" prompt. `BLUETOOTH_SCAN` is marked `neverForLocation`. |

## Getting started

1. Clone the repository:

   ```bash
   git clone https://github.com/kherphshikkii/BChat.git
   ```

2. Open the project in Android Studio and let Gradle sync.
3. On each phone, enable **Developer options** and **USB debugging**.
4. Select a phone in the device dropdown and click **Run**. Repeat for the second phone.

To share a build with friends, use **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

## Testing with two phones

1. Open the app on both phones and grant the permissions.
2. On Phone B, tap the target icon on the home screen and choose **Allow** to become visible.
3. On Phone A, tap **Find devices**, then **Connect** next to Phone B.
4. The first time, confirm the pairing code on both phones.
5. Both phones open the chat. Send a message each way.

## Project structure

| File | Purpose |
|---|---|
| `PermissionActivity` | Explains and requests Bluetooth permissions |
| `HomeActivity` | Bluetooth status card and recent chats |
| `DevicesActivity` | Scanning and device list |
| `MainActivity` | The chat screen |
| `SettingsActivity` | Name and privacy settings |
| `ChatConnection` | Shared Bluetooth connection, threads, and message protocol |
| `ChatStore` | Saves chat history as a small JSON file |
| `Prefs` | Saves username and switch settings |
| `BtPermissions` | Version-aware permission checks |
| `MessageAdapter`, `DeviceAdapter`, `ConversationAdapter` | RecyclerView adapters |
| `NameDialog`, `RequestDialog` | Username prompt and connection approval prompt |

## Troubleshooting

| Problem | Fix |
|---|---|
| The other phone never appears | Make it visible with the target icon, or keep its Bluetooth settings screen open |
| "Couldn't connect" every time | Choose **Forget** for the other phone in Bluetooth settings on both phones, then try again |
| Connecting hangs | A pairing request is waiting on the other phone, so check its screen and notifications |
| Nothing found on Android 11 or older | Turn on the Location switch |
| Messages don't arrive | Install the same version of the app on both phones |
| Connection drops when the screen locks | Known limit of this version, see below |

## Known limitations

- The connection only works while the app is open. A foreground service is needed to survive a locked screen.
- One chat connection at a time.
- Text only.
- Typical Bluetooth Classic range, roughly 10 meters in the open.
- Tested on Android 14. Other versions should work but have had less testing.

## Roadmap

- Foreground service for background connections
- Message delivery status
- Dark mode
- Image sharing
- BLE version

## Tech stack

- Kotlin
- Android Views with View Binding
- Material Components
- Bluetooth Classic RFCOMM
- SharedPreferences and a JSON file for local storage

## License

Released under the [MIT License](LICENSE).
