# StorageLens

A lightweight, fully offline Android storage manager built with **Kotlin** and **Jetpack Compose**. StorageLens shows what is using your phone's storage, lets you browse files like a file manager, and helps you free up space by removing junk and large files.

> **Status:** MVP / test build. Core features work; advanced features are planned (see [Roadmap](#roadmap)).

---

## Screenshots

| Dashboard | Cleaner | File Manager |
|:---:|:---:|:---:|
| ![Dashboard](screenshots/dashboard.png) | ![Cleaner](screenshots/cleaner.png) | ![File Manager](screenshots/files.png) |

*(Add your own screenshots to a `screenshots/` folder and update the file names above.)*

---

## Features

### Dashboard
- Total, used and free internal storage shown as a circular progress ring
- Donut chart of storage by category: Images, Videos, Audio, Documents, Other
- Toggle to a treemap view (rectangles sized by storage use)
- Tap any slice or rectangle to open the File Manager filtered to that category
- Rescan button with progress indicator and "Last scanned" timestamp

### Cleaner
- Scans for the app's own cache, temp files (`.tmp`, `.log`), empty folders, leftover APKs in Downloads, and large files (default threshold: 50 MB)
- Results grouped by type, with checkboxes, per-group sizes and "Select all"
- Confirmation dialog showing the total size before anything is deleted
- Summary of the space freed after cleanup
- Shortcut to the system Storage settings (Android does not allow apps to clear other apps' caches)

### File Manager
- Folder browsing with breadcrumb navigation and back button
- File-type icons, size and modified date
- Sort by name, size or date; search by file name
- Open, rename, delete (with confirmation), share, view details, create new folder
- Category shortcuts: Images, Videos, Audio, Documents, Downloads

### General
- Works 100% offline, with no internet permission, ads, analytics or paid APIs
- Material 3 design with dark mode
- Data persists after closing and reopening the app

---

## Tech Stack

| Area | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Architecture | MVVM, one ViewModel per screen |
| State | Kotlin `StateFlow` |
| Navigation | Single-activity Compose Navigation with bottom bar |
| Async | Kotlin Coroutines (`Dispatchers.IO`), cancellable scans |
| Settings | Jetpack DataStore (Preferences) |
| Scan results and history | JSON file in internal storage via `kotlinx.serialization` |
| Charts | Compose Canvas (no chart libraries) |
| Min SDK | 26 (Android 8.0) |

---

## Project Structure

```
app/src/main/java/<your/package>/
├── ui/          # Compose screens, components, theme, navigation
├── viewmodel/   # ViewModels and UI state
├── data/        # DataStore, JSON storage, file scanning logic
└── util/        # Helpers (size formatting, file types, permissions)
```

*(Adjust the package names to match your project.)*

---

## Getting Started

### Prerequisites
- [Android Studio](https://developer.android.com/studio) (latest stable version)
- JDK 17 or newer (bundled with recent Android Studio)
- An Android device or emulator running Android 8.0 (API 26) or higher

### Build and run

1. Clone the repository:
   ```bash
   git clone https://github.com/<your-username>/StorageLens.git
   cd StorageLens
   ```
2. Open the project in Android Studio and wait for Gradle sync to finish.
3. Connect a device (USB debugging on) or start an emulator.
4. Click **Run ▶**, or build from the command line:
   ```bash
   ./gradlew assembleDebug
   ```
   The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

> **Tip:** Test on a real phone if possible. Emulators have little data, so the charts will look nearly empty.

---

## Permissions

StorageLens requests only what it needs, when it needs it, and explains why first.

| Permission | Why it is needed |
|---|---|
| `MANAGE_EXTERNAL_STORAGE` (Android 11+) | Lets the file manager and scanner read all folders |
| `READ/WRITE_EXTERNAL_STORAGE` (Android 10 and below) | Same purpose on older versions |

- **No internet permission** is declared.
- If access is denied, the app shows an explanation with a button to open Settings and continues in a limited mode.
- Nothing is deleted without explicit confirmation, and system files are never touched.

> **Note for publishing:** Google Play restricts `MANAGE_EXTERNAL_STORAGE`. It is fine for personal and test builds, but a Play Store release would need to move to scoped storage (MediaStore and the Storage Access Framework).

---

## Data Storage

Everything is stored locally on the device. There is no server and no account.

- **DataStore (Preferences):** large-file threshold, sort order, last scan time
- **JSON file (`filesDir`):** last scan result (category sizes) and cleanup history (date and space freed)

On launch the saved data loads first, so the Dashboard appears instantly, and you can then rescan.

---

## Known Limitations

- Cannot clear other apps' caches (Android restriction). The app links to system Storage settings instead.
- "All files access" is required for full file manager coverage.
- Large scans on devices with very many files can take time. Scans can be cancelled.
- This is an MVP, so there is no duplicate finder or per-app breakdown yet.

---

## Roadmap

- [ ] Media Cleaner (photos and videos grid, filters)
- [ ] Duplicate photo detection
- [ ] Per-app storage breakdown
- [ ] Scoped storage mode (MediaStore and SAF) for Play Store compatibility
- [ ] Room database for richer scan history
- [ ] Settings screen (theme, units)
- [ ] Onboarding screen

---

## Contributing

Contributions, issues and feature requests are welcome.

1. Fork the repository
2. Create a branch: `git checkout -b feature/your-feature`
3. Commit your changes: `git commit -m "Add your feature"`
4. Push the branch: `git push origin feature/your-feature`
5. Open a Pull Request

---

## Disclaimer

StorageLens permanently deletes files you select. Deleted files cannot be recovered, so review each confirmation carefully. The authors are not responsible for data loss.

---

## License

Distributed under the MIT License. See [`LICENSE`](LICENSE) for details.
*(Add a `LICENSE` file, or change this section to your preferred license.)*
