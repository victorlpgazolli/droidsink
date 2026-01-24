# DroidSink

DroidSink is an audio streaming tool built with [Kotlin Multiplatform](https://kotlinlang.org/multiplatform/) that **forwards [PCM](https://en.wikipedia.org/wiki/Pulse-code_modulation) audio from a computer to a Android device**. It utilizes the [Android Open Accessory (AOA)](https://source.android.com/docs/core/interaction/accessories/protocol) protocol to establish a direct USB data link.

The project is designed to **capture audio from a virtual device** on the computer and **stream it directly to a connected Android peripheral**. Currently, it supports macOS as the host platform, with plans to extend support to Windows and Linux in the future.

The project consists of two parts:

- cli binary to manage the USB connection and audio streaming.

- android device to receive and play the audio stream.

## Features
- pipe audio from virtual audio devices to Android devices over USB.
- currently macOS only. aims to be cross-platform.
- only 1 device at a time for now.

## Prerequisites
To run DroidSink, your system must have the following software installed:

- [`libusb`](https://formulae.brew.sh/formula/libusb): Used for USB communication and AOA handshaking.

- [`sox`](https://formulae.brew.sh/formula/sox): Required for audio processing and piping raw data.

- [`BlackHole 2ch`](https://formulae.brew.sh/cask/blackhole-2ch): A virtual audio driver required to route system audio to DroidSink.
    
- [`adb`](https://formulae.brew.sh/cask/android-platform-tools): Required for initial device communication and app installation.

#### Android Device Requirements

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
./droidsink internal:list
# List all connected USB devices with their details.
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


### Troubleshooting
LibUsb Errors: Ensure no other application (like a tethering manager) is locking the USB device.

Device not found: Ensure USB Debugging is enabled on the Android device and that it is properly connected.

`Sox or BlackHole 2ch or adb` Not Found: Ensure they are in your PATH.

If the device fails to switch to Accessory Mode, try reconnecting the USB cable.
