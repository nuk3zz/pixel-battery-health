# Pixel Battery Health

Pixel Battery Health is an offline Android app for checking battery health from a Google Pixel bugreport.

It imports a Pixel bugreport ZIP, finds the correct report text automatically, extracts battery data, and shows a clean battery health summary.

![Pixel Battery Health app screenshot](docs/images/app-screenshot.svg)

## Features

- Import a Pixel bugreport ZIP from the Android file picker
- Receive bugreport ZIPs directly from the Android share sheet
- Guided flow for opening Developer Options and creating a new bugreport
- Automatic ZIP extraction and bugreport text detection
- Battery health percentage based on Pixel design capacity
- Estimated capacity, design capacity, cycle count, health status, temperature, and voltage
- Dark mode support
- Fully offline
- No internet permission

## Install

1. Open the **Releases** page.
2. Download the latest APK file.
3. Open the APK on your Pixel.
4. If Android asks, allow installation from that source.
5. Install **Pixel Battery Health**.

## How To Use

1. Open **Pixel Battery Health**.
2. Tap **Create Bugreport**.
3. In Developer Options, tap **Bug report**.
4. Wait for Android to finish generating the bugreport.
5. From the bugreport notification, choose the share action.
6. Select **Pixel Battery Health**.
7. The app imports and analyzes the ZIP automatically.

You can also use **Load Bugreport ZIP** if you already saved a bugreport file.

## Privacy

Pixel Battery Health works offline. It does not request internet access and does not upload your bugreport anywhere.

Bugreports can contain sensitive device logs. Only open bugreports you trust, and avoid sharing them publicly.

## Supported Pixel Models

The app includes Pixel design capacities for Pixel 4 and newer supported models, with additional model aliases where available. If the model cannot be detected, the app still shows parsed battery values but may not calculate a percentage.
