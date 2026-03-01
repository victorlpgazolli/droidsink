# DroidSink

DroidSink is an audio streaming tool built with [Kotlin Multiplatform](https://kotlinlang.org/multiplatform/) that **forwards [PCM](https://en.wikipedia.org/wiki/Pulse-code_modulation) audio from a computer to a Android device**. It utilizes the [Android Open Accessory (AOA)](https://source.android.com/docs/core/interaction/accessories/protocol) protocol to establish a direct USB data link.

The project is designed to **capture audio from a virtual device** on the computer and **stream it directly to a connected Android peripheral**. Currently, it supports macOS as the host platform, with plans to extend support to Windows and Linux in the future.

I have configured the project to have a [latency of around 100ms-200ms](https://github.com/victorlpgazolli/droidsink/blob/master/droidsink/src/commonMain/kotlin/constants.kt) depending on the system performance.

The project consists of two parts:

- cli binary to manage the USB connection and audio streaming.

- android device to receive and play the audio stream.

## Features
- pipe audio from virtual audio devices to Android devices over USB.
- pipe microphone audio from Android devices to the computer over USB.
- currently support Linux* and macOS. *linux support is experimental.
- support only 1 device at a time.

```bash

Available commands:
   install: Install the accessory app on the connected device.

   start: Start the accessory service on the connected device.
       --skip-app-install: Skip the installation of the .apk on the connected device. (default: false)

   stop: Stop the accessory service on the connected device.
       --skip-app-install: Skip the installation of the .apk on the connected device. (default: false)

   run: Install the app, start the service, and begin streaming data.
       --skip-app-install: Skip the installation of the .apk on the connected device. (default: false)
       --audio-interface: Specify the name of the audio interface to use for streaming. (default: "BlackHole 2ch")
       --run-as-microphone: Run the application in microphone mode, which configures the device to provide audio data as if it were a microphone peripheral. (default: false)
       --use-fake-audio-input: Use a fake audio input stream that generates some audio data instead of reading from the host. This is useful for testing the application without needing to have an actual audio input device connected. (default: false)

   devices: List all connected devices and their statuses.

   purge: Uninstall the app from the connected device, clear its data, and remove the downloaded APK from local storage.

   version: Print the current version of this application.
```

## Prerequisites
To run DroidSink, your system must have the following software installed:

- **libusb**: Used for USB communication and AOA handshaking.
  - [`macOs`](https://formulae.brew.sh/formula/libusb)
  - [`Linux`](https://packages.debian.org/sid/libusb-1.0-0-dev)

- **sox**: Required for audio processing and piping raw data.
  - [`macOs`](https://formulae.brew.sh/formula/sox)
  - [`Linux`](https://packages.debian.org/sid/sox)

- **adb**: Required for initial device communication and app installation.
  - [`macOs`](https://formulae.brew.sh/cask/android-platform-tools)
  - [`Linux`](https://packages.debian.org/sid/android-tools-adb)

- **wget**: Used for downloading the latest APK from GitHub releases (optional if you use --skip-app-install)
  - [`macOs`](https://formulae.brew.sh/formula/wget)
  - [`Linux`](https://packages.debian.org/sid/wget)

- Any virtual audio driver to route system audio to DroidSink, suggestion:
  - MacOs: [`BlackHole 2ch`](https://formulae.brew.sh/cask/blackhole-2ch).

### Android Device Requirements

- [USB Debugging must be enabled.](https://developer.android.com/studio/debug/dev-options#debugging)

- The device must support Accessory Mode (AOA).

> to check if your device supports AOA, connect the device and run `adb shell ls /system/etc/permissions | grep usb.accessory`, if you see a file named something like `usb.accessory.xml`, your device supports AOA.

## Usage

Connect your Android device via USB and ensure it is detected by `adb devices`.

Run (Full Setup)
This command installs the app, triggers Accessory Mode, and starts streaming:

```bash
./droidsink run
# will install the app if not already installed and start the foreground service 
# will turn the device into Accessory Mode if needed
# will start streaming audio from "BlackHole 2ch" to the device

./droidsink run --skip-app-install --audio-interface "BlackHole 2ch"
# will skip the app installation step and start streaming audio from "BlackHole 2ch" to the device

./droidsink run --run-as-microphone
# will install the app if not already installed and start the foreground service in microphone mode
# will start streaming audio from the device's microphone to the computer
```

```bash
./droidsink install
# Install the DroidSink app on the connected device.
```
```bash
./droidsink start
# Start the foreground service on the device.
```
```bash
./droidsink stop
# Stop the foreground service on the device.
```
```bash
./droidsink devices
# List all connected USB devices with their details.
```
```bash
./droidsink purge
# Uninstall the .apk from the connected device, and remove the cached APK from computer (~/.config/droidsink/)
```

## How it Works

DroidSink follows a specific lifecycle to enable audio over USB:

It identifies a connected device via libusb using its Serial Number. Sends vendor strings to the device to request a switch to Accessory Mode.

Once the device switches to Accessory Mode, its identity changes (typically to Vendor ID: 0x18D1). DroidSink tracks this change using the unique Serial Number and establishes a bulk transfer connection.

It triggers `sox` to capture audio from `BlackHole 2ch` and pipes the raw PCM data through a USB bulk transfer to the device.

The Android app reads the incoming audio stream and plays it using `AudioTrack`.

<details><summary>A more detailed sequence diagram</summary>
                                                                                       
                                                                                       
                                                                                       
         ┌──────────────────────────────────────────────────────────────┐              
         │ HOST (PC)     SUPPORTED: macOS ───────► Windows and          │              
         │                                         Linux comming soon   │              
         │                         using a                              │              
         │                    Virtual Audio Driver (blackhole-2ch)      │              
         │                                                              │              
         └───────────────┬──────────────────────────────────────────────┘              
                         │                                                             
                         │ PCM 16-bit LE, 48kHz, 2ch                                   
                         │                                                             
                         ▼                                                             
         ┌──────────────────────────────────────────────────────────────┐              
         │                           SOX                                │              
         │                     (SOund eXchange)                         │              
         └───────────────┬──────────────────────────────────────────────┘              
                         │                                                             
                         │                                                             
                         │ stdout (RAW PCM stream)                                     
                         │                                                             
                         ▼                                                             
         ┌──────────────────────────────────────────────────────────────┐              
         │                    using Kotlin/Native                       │              
         │            and libusb to stream audio over USB               │              
         └───────────────┬──────────────────────────────────────────────┘              
                         │                                                             
                         │                                                             
                         │ USB bulk packets (~20ms audio)                              
                         │                                                             
                         │                                                             
                         ▼                                                             
         ┌──────────────────────────────────────────────────────────────┐              
         │                 USB ACCESSORY MODE (AOA)                     │              
         │  UsbAccessory                                                │              
         │  → DroidSink app will be installed automatically.            │              
         │  → ADB must be enabled!                                      │              
         │  → Device will change to accessory automatically.            │              
         └──────────────────────────────────────────────────────────────┘              
                         │                                                             
                         │ InputStream (Accessory bulk endpoint)                       
                         │                                                             
                         ▼                                                             
         ┌──────────────────────────────────────────────────────────────┐              
         │               DroidSink (Android App)                        │              
         │                                                              │              
         │  AccessoryService (Foreground Service)                       │              
         │        │                                                     │              
         │        ▼                                                     │              
         │  InputStream.read(byte[])                                    │              
         │        │                                                     │              
         │        ▼                                                     │              
         │  AudioTrack (STREAM_MUSIC)                                   │              
         │  PCM 16-bit / 48kHz / Stereo                                 │              
         └──────────────────────────────────────────────────────────────┘              
                                                                       
 </details>


## Troubleshooting

If you see a message saying that there is no application installed on the device to handle the accessory, you could try manually installing the APK.
```bash
./droidsink install
./droidsink run
```

LibUsb Errors: Ensure no other application (like a tethering manager) is locking the USB device, normally changing the usb configuration mode to any other mode and trying running again helps.
- like `USB error: LIBUSB_ERROR_TIMEOUT`

Device not found: Ensure USB Debugging is enabled on the Android device and that it is properly connected.

`Sox or BlackHole 2ch or adb` Not Found: Ensure they are in your PATH.

If the device fails to switch to Accessory Mode, try changing the usb configuration mode to any other mode and run the cli again.

## Development

Install the dependencies listed in the Prerequisites section.
To build and run the project in debug mode, use the following commands:

```bash
# Android App
./gradlew :droidsink:assembleDebug # Build the Android app in debug mode.
./gradlew :droidsink:installDebug # Install the app in debug mode.
# CLI
./gradlew :droidsink:runDebugExecutable # Build & run the CLI in debug mode.
# the final binary will be located at: ./droidsink/build/bin/native/debugExecutable/droidsink.kexe
```