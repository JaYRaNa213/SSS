# 4S (SSS) — Smart Screenshot Organizer & Study Notes App for Android

**4S** is a native Android application built with **Kotlin** and **Jetpack Compose**. It allows users to organize screenshots into subject folders in real-time while using other apps, view screenshots with native gallery gesture controls, perform multi-image OCR (Optical Character Recognition) using Google ML Kit, edit structured study notes, and export notes or image sets to formatted A4 PDF documents.

---

## ✨ Key Features

### 1. ⚡ Floating AssistiveTouch 4S Overlay & Folder Switcher
- **AssistiveTouch Style Bubble**: Compact circular overlay bubble (~46dp) that floats over any application (e.g. YouTube, Chrome, PDF readers).
- **Foggy Idle State**: Fades smoothly to `55%` opacity (`alpha = 0.55f`) after 2.5 seconds of inactivity.
- **Long-Press Red Close Control (`🔴`)**: Long-pressing for 500ms reveals a red close dot that hides the overlay without stopping background capture or closing the active session.
- **Left-Sliding Controller Panel**: Tap the 4S bubble to slide out a folder selection panel with a vertically scrollable folder list and quick "New Folder" creation.

### 2. 📂 Smart Folder Organization & Dual Touch Targets
- **Monochrome Minimalist UX**: Sleek black-and-white design system.
- **Dual Touch Region Folder Stack**:
  - **Main Area (~85% width)**: Tapping opens the folder's screenshot gallery; long-pressing opens folder deletion options.
  - **Right Selection Control (~15% width)**: Tapping sets or switches the target capture folder.
- **Bottom Action State Machine**:
  - `Select a Folder` (Disabled)
  - `Start Capture` (Enabled when folder is selected)
  - `Stop Capture` (Active capturing state)

### 3. 🖼️ "Other" Central Device-Wide Virtual Collection
- **Virtual Collection**: Uses Android `MediaStore` as the source of truth to index screenshots taken on the device after app installation without physical file duplication.
- **Automatic Assignment Rules**:
  - Screenshots captured during an active session appear in **both** "Other" and the active subject folder (e.g., *Java Core*, *DSA Code*).
  - Screenshots captured without an active capture session automatically appear in "Other".
  - Hiding or removing screenshots from subject folders keeps them excluded from "Other".

### 4. 🔍 Native Mobile Gallery Gesture Control
- **Full-Screen Image Viewer**: Custom `ZoomableImage` component with exact pan bounds clamping.
- **Gesture Coordination**:
  - Single-finger horizontal drag at `1.0x` scale passes unconsumed touch events to `HorizontalPager` for smooth left/right page swiping.
  - Two-finger pinch-to-zoom continuously zooms around the pinch midpoint.
  - Double-tap toggles between `1.0x` and `2.5x` scale, centered at the tapped touch coordinates.
  - Rotated images (90° / 270°) automatically compute aspect-ratio scale factors (`fitRotationScale`) to prevent side-clipping.

### 5. 📝 Multi-Image ML Kit OCR & Study Notes Editor
- **On-Device Text Recognition**: Sequential Latin OCR using **Google ML Kit Text Recognition** (`com.google.mlkit:text-recognition:16.0.1`).
- **Heading & Format Detection**: Rule-based classification formats extracted text into clean, structured study notes with detected headers.
- **Study Notes Editor**: Integrated multiline note editor (`ExtractedTextScreen`) with auto-save to Room DB (`StudyDocumentEntity`).
- **Native A4 PDF Export**: Formatted multi-page PDF generation with document title, metadata, word wrapping, and page numbers via Android Storage Access Framework (SAF).

---

## 🛠️ Architecture & Tech Stack

- **Language**: 100% Kotlin
- **UI Framework**: Jetpack Compose, Material 3
- **Architecture**: MVVM (Model-View-ViewModel), Clean Data Layer, Hilt Dependency Injection
- **Database**: Room Database (Version 4 with non-destructive migrations `MIGRATION_1_2`, `MIGRATION_2_3`, `MIGRATION_3_4`)
- **Image Loading & Caching**: Coil 3
- **On-Device AI / ML**: Google ML Kit Text Recognition (`16.0.1`)
- **Background Service**: Android Foreground Service (`foregroundServiceType="specialUse"`) with `NotificationCompat` and `ScreenshotObserver` running on a background `HandlerThread`.

---

## 📱 Android Permissions

| Permission | Purpose |
|---|---|
| `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` | Discovering screenshots in MediaStore across device storage. |
| `SYSTEM_ALERT_WINDOW` | Displaying the floating 4S AssistiveTouch bubble overlay. |
| `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_SPECIAL_USE` | Maintaining background screenshot detection while using other apps (Android 14+ compatible). |
| `POST_NOTIFICATIONS` | Foreground service status notification with quick "Stop" action (Android 13+). |

---

## 🚀 Building & Running

### Prerequisites
- Android Studio Ladybug (2024.2) or higher
- JDK 17
- Android SDK 35 (Minimum SDK 26)

### Build Commands
```bash
# Build Debug APK
./gradlew :app:assembleDebug

# Output APK path:
# app/build/outputs/apk/debug/app-debug.apk
```
