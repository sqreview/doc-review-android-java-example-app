# Bluetooth Setup

## Overview

The `BleManager` handles the scanning and connections for the SitePoint devices.

> **_Note:_** SitePoint devices are Bluetooth peripherals. In Bluetooth terms, this means they are connected from a Bluetooth central device — a mobile device running an app, in this case.

## Scanning

The scanning is initiated in `MainActivity.java` from `onResume`.

The scan specifies a filter on service UUID by creating a scan filter and configuring it to only return devices with the SitePoint service UUID of `00000100-34ed-12ef-63f4-317792041d17`.

## Handling discovered SitePoint peripherals

The Android `onScanResult` callback gets the scan result listing each discovered device, which can be used in the Example App's SitePoint class. This class has some example code for accessing the SitePoint data before the connection is made by parsing SignalQuest's manufacturer data (e.g., battery, charging state, number of satellites, connection state).

## Connecting to a SitePoint

When a connection is requested for a SitePoint from the scan results, the BLE Manager's `connect()` method initiates the connection. As part of this connection process, we pass in the Bluetooth GATT callback class which contains functions for interacting with the active device.

## Initiating service and characteristic discovery

When connected, as detected in the `onConnectionStateChange` callback, we request a higher MTU. In the example app, we request the Android maximum MTU of 517.

Next, in the `onMtuChanged` callback, we start the service discovery process. Within the `onServicesDiscovered` callback, we discover the characteristics and set references to them.

## Preparing for reading and writing data

In order to receive messages from the SitePoint, the characteristic notification needs to be enabled. See the `enableNotifications` method for an implementation example. Read events get reported through the `onCharacteristicChanged` method.

Data is written to the SitePoint by writing to a characteristic. An example of this is in the BLE Manager's `writeRtcm` method. Write confirmations get reported through the `onCharacteristicWrite` callback and should be waited upon before writing more data.

<hr>

## Read Next

- [Message Handling](B4_Message_Handling.md) - Send and receive data from the SitePoint peripheral.
