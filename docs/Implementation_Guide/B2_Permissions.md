[Home](../index.md) / [Implementation Guide](B1_Implementation_Guide.md) / Permissions

# Permissions

## Overview

This section covers adding permissions for Bluetooth and Location services to your Android application.

## Adding permissions to AndroidManifest.xml

In the app/source/AndroidManifest.xml file, you will find the necessary permissions and features that must be declared. These include:

- *uses-permission* tags for granting specific permissions, such as access to Bluetooth and Location services.
- *uses-features* tags for declaring required hardware or software features, such as Bluetooth capabilities.

Refer to this file to ensure that all required permissions and features are included in your app's manifest.

## Requesting permissions from an Activity

The example app demonstrates how to request permissions from an Activity. The *PermissionsActivity.java* file serves as the app's splash screen and initiates the permissions requests.

This file includes both Javadoc and inline comments to provide helpful information regarding the process of requesting permissions. 

> **_Note:_** It is essential to request the necessary permissions at runtime, as required by the Android platform, to ensure your app functions correctly and adheres to user privacy and security guidelines.

<hr>

## Read next

- [Bluetooth Setup](B3_Bluetooth_Setup.md) - Set up BLEManager to handle Bluetooth Low-Energy (BLE) scanning and connections.
