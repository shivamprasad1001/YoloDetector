# YoloDetector 🛸
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-purple.svg?style=flat&logo=kotlin)](https://kotlinlang.org/)
[![TensorFlow Lite](https://img.shields.io/badge/TensorFlow%20Lite-2.14.0-orange?style=flat&logo=tensorflow)](https://www.tensorflow.org/lite)
[![CameraX](https://img.shields.io/badge/CameraX-Jetpack-green?style=flat&logo=android)](https://developer.android.com/jetpack/androidx/releases/camera)
[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat&logo=android&logoColor=white)](https://developer.android.com/)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-24%20(Nougat)-blue?style=flat&logo=android)](https://developer.android.com/about/dashboards)
[![Gradle](https://img.shields.io/badge/Gradle-8.2-02303A?style=flat&logo=gradle)](https://gradle.org/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![GitHub issues](https://img.shields.io/github/issues/shivamprasad1001/YoloDetector)](https://github.com/shivamprasad1001/YoloDetector/issues)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat)](https://makeapullrequest.com)

An advanced, high-performance Android application featuring real-time object detection using a customized **YOLO26n** (float32) model powered by **TensorFlow Lite**. Implemented with a sleek **Sci-Fi HUD overlay** and a clean **Neumorphic design system**, YoloDetector delivers desktop-grade edge computing on mobile devices.

---

![YoloDetector Banner](assets/banner.png)

---

## 🌟 Key Features

*   **Real-Time Camera Detection**: Powered by **Android Jetpack CameraX**, capturing live frames and conducting inference with negligible latency.
*   **Static Photo Analyzer**: Feed images from your gallery or take a new picture using the system camera, and instantly see overlay boxes and confidence scores.
*   **Sci-Fi HUD Interface**: The camera view utilizes a specialized HUD aesthetic complete with edge glow, custom wireframes, and live diagnostic readouts.
*   **GPU Acceleration**: Automated hardware delegate configuration. The app scans device compatibility and selects the **TFLite GPU Delegate** for rapid inference, falling back to CPU multi-threading (4 threads) if unavailable.
*   **Advanced Camera Controls**: Fully integrated pinch-to-zoom gestures, a manual zoom slider, precise increment/decrement buttons, and a front/back camera toggle.
*   **COCO dataset ready**: Supports the detection of 80 standard classes, from people and vehicles to household electronics.
*   **Neumorphic Dashboard**: Modern, soft-shadow neumorphic UI cards on the landing screen, incorporating interactive states for a highly responsive, premium feel.

---

## 📸 Screenshots

| Dashboard (Neumorphic) | Live Detection (Sci-Fi HUD) | Photo Analyzer |
|:---:|:---:|:---:|
| <img src="/app/src/main/assets/screenshot_dashboard.png" width="250" alt="Dashboard"/> | <img src="app/src/main/assets/screenshot_live.png" width="250" alt="Live HUD"/> | <img src="app/src/main/assets/screenshot_photo.png" width="250" alt="Photo Detect"/> |

*(Place screenshots in `assets/` to display them here)*

---

## 🛠️ Architecture & Flow

The project is structured under a clean modular architecture:

```mermaid
graph TD
    A[MainActivity] -->|Real-Time Mode| B[CameraActivity]
    A[MainActivity] -->|Photo Mode| C[PhotoDetectActivity]
    
    B -->|Frames| D[YoloDetector]
    C -->|Images| D
    
    D -->|TFLite Inference| E[yolo26n_float32.tflite]
    E -->|Bounding Boxes| D
    
    D -->|Detections & Latency| F[OverlayView / HUD]
```

### Technical Highlights:
*   **Model Input**: Bounded RGB float32 normalized image of size $640 \times 640 \times 3$.
*   **Non-Maximum Suppression (NMS)**: Handled directly within the TFLite graph (end-to-end export) yielding a `[1, 300, 6]` tensor output `[x1, y1, x2, y2, confidence, class_id]`.
*   **Coordinate Scaling**: Dynamic viewport transformations scale normalized coordinates to fit screen boundaries regardless of device aspect ratio.

---

## ⚙️ Technical Deep Dive

### 📷 CameraX Frame Pipeline & Analysis
* **Non-Blocking Execution**: Configured using `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST` to drop old frames if the inference engine is busy, guaranteeing zero lag in the live viewfinder.
* **Format Conversion**: Custom conversion converts the native `RGBA_8888` `ImageProxy` plane into an Android `Bitmap` while accounting for row stride padding (`rowPadding = rowStride - pixelStride * width`).
* **Rotation Correction**: Matches physical camera orientation to output canvas by querying sensor rotation (`imageProxy.imageInfo.rotationDegrees`) and applying a `Matrix` post-rotation translation.

### 🧠 TFLite Preprocessing & Inference Engine
* **Byte Normalization**: Preallocates a direct JVM `ByteBuffer` mapping 4 bytes per float:
  $$\text{Buffer Size} = 1 \text{ (batch)} \times 640 \text{ (width)} \times 640 \text{ (height)} \times 3 \text{ (channels)} \times 4 \text{ (bytes/float)}$$
* **Standardized Scaling**: Isolates RGB pixel channels using bit shifts and normalizes color values to $[0.0, 1.0]$:
  $$R_{norm} = \frac{(px \text{ shr } 16) \text{ and } 0xFF}{255.0f}$$
* **SoC Accelerators**: Dynamically interrogates the system CPU using `CompatibilityList.isDelegateSupportedOnThisDevice` to allocate `GpuDelegate` for high-performance hardware execution, defaulting to 4 background CPU execution threads if incompatible.

### 🎨 Rendering & HUD Overlay
* **Canvas Mapping**: Custom `OverlayView` translates relative coordinates (`[0.0, 1.0]`) into the actual rendering viewport, scaling and centering bounding boxes to avoid aspect-ratio distortion.
* **HUD Reactive Animation**: Highlights successful object identification by firing alpha animation pulses (`edgeGlow.animate().alpha(0f).setDuration(300)`) onto the custom red/green neon dashboard edge layout.

---

## 🚀 Getting Started

### Prerequisites
*   Android Studio Jellyfish (or newer)
*   Gradle JDK 17+
*   Physical Android device running API 24 (Nougat) or higher (strongly recommended for CameraX and GPU delegate functionality)

### Installation
1.  **Clone the Repository**:
    ```bash
    git clone https://github.com/shivamprasad1001/YoloDetector.git
    cd YoloDetector
    ```
2.  **Open in Android Studio**:
    *   File -> Open -> Select the cloned `YoloDetector` root folder.
3.  **Sync Gradle**:
    *   Wait for Gradle Sync to complete and download TFLite/CameraX dependencies.
4.  **Run the Project**:
    *   Connect your Android device via USB/Wi-Fi debugging and press `Run`.

---

## 📦 Dependencies & Stack

This app leverages state-of-the-art Jetpack libraries and ML engines:

*   **TensorFlow Lite** (`org.tensorflow:tensorflow-lite:2.14.0`): Efficient machine learning runtime on mobile devices.
*   **TFLite GPU Delegate** (`org.tensorflow:tensorflow-lite-gpu:2.14.0`): Empowers high-speed GPU pipeline processing.
*   **CameraX API** (`androidx.camera`): Flexible camera API that handles resolution matching, rotation, and lifecycle state management automatically.
*   **ViewBinding**: Simplifies layout binding and eradicates boilerplate code.

---

## 💻 Developer & Portfolio

Developed and maintained by **Shivam Prasad**. 

*   🌐 **Website & Portfolio**: [shivamprasad1001.in](https://shivamprasad1001.in)
*   🐙 **GitHub**: [@shivamprasad1001](https://github.com/shivamprasad1001)

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
