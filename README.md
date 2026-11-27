# SmoothCam Pro — Android starter project

A native Kotlin/CameraX starter for experimenting with a faster camera UI on the OnePlus Nord CE 4.

## Open and build
1. Install Android Studio on a computer.
2. Extract this folder.
3. In Android Studio choose **Open** and select the `SmoothCamPro` folder.
4. Allow Gradle sync and install any requested Android SDK 35 components.
5. Connect the OnePlus Nord CE 4 with USB debugging enabled.
6. Select the phone as the run target and press Run.

This repository is source code, not a prebuilt APK. It has not been compiled or tested on the Nord CE 4 yet.

## Current starter features
- CameraX preview using `PreviewView` performance mode.
- Photo capture saved to `Pictures/SmoothCamPro`.
- Video capture saved to `Movies/SmoothCamPro`.
- Rear camera, 1080p-first quality selection and optional UHD request.
- Runtime permission requests.
- A lighter fallback if the combined preview/photo/video stream is rejected.

## Important limitations
- The selected video quality is a request; CameraX may choose a supported fallback.
- A 4K option is not proof that the phone supports that exact camera/encoder combination in this app.
- This starter does not yet expose a measured FPS counter, manual shutter/ISO, lens switching, or OEM image-processing features.
- Third-party apps cannot force access to every proprietary OnePlus camera feature. Results must be tested on the physical phone.
- The current photo capture mode prioritises latency. Compare its image quality with the stock camera before deciding whether to use quality-priority mode.

## Next development milestones
1. Test camera stream combinations on the Nord CE 4.
2. Add a capability screen that queries actual camera/encoder support before showing modes.
3. Add measured frame pacing and recording diagnostics.
4. Add rear/front lens selection, zoom, tap-to-focus, exposure compensation and flash.
5. Benchmark cold launch, warm launch, shutter-to-file time, and long-recording thermal behaviour.
6. Tune quality/latency trade-offs based on real device results.

## Official references
- CameraX overview: https://developer.android.com/media/camera/camerax
- CameraX architecture: https://developer.android.com/media/camera/camerax/architecture
- Image capture: https://developer.android.com/media/camera/camerax/take-photo
- Video capture: https://developer.android.com/media/camera/camerax/video-capture


## Build APK on GitHub (phone-friendly)

This repository includes a GitHub Actions workflow at `.github/workflows/android.yml`. It installs JDK 17 and Gradle 8.9 in the GitHub runner, then builds the debug APK without requiring a Gradle Wrapper in this starter ZIP.

1. Upload the contents of this folder to the root of your GitHub repository.
2. Open the repository's **Actions** tab.
3. Select **Build SmoothCam Pro APK** and tap **Run workflow** (or push/commit to `main` or `master`).
4. When the run finishes, open it and download the `SmoothCamPro-debug-apk` artifact.
5. Extract the artifact ZIP; install `app-debug.apk` on your Android phone.

The APK is a debug build and may show Android's normal install warning. The project still needs to be built and tested on the target phone; successful workflow execution is not a guarantee of device-specific camera performance.
