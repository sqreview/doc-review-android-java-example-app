[Home](../index.md) / [Implementation Guide](B1_Implementation_Guide.md) / Message Handling

# Message Handling

## Overview

This section covers receiving messages from the SitePoint peripheral.

## About structured messages

The SitePoint peripheral uses a custom transport protocol to send status and location messages over a single characteristic. The SignalQuest API converts these messages into developer-friendly objects.

## Reading Messages

Follow these steps to read messages from the SitePoint peripheral:

1. Get message data using the `onCharacteristicChanged` method of the `BluetoothGattCallback` class.
2. Pass the data to the `parse` method of the `MessageHandler` class.
3. Receive the resulting `Status` and `Location` messages with a `MessageReceiver` instance.

Examples of this code are included in the `BleManager` class.

The `LocationParcelable` and `StatusParcelable` classes are also provided. These classes are useful for broadcasting the received data using Android Intents.

<hr>

## Read next

- [NTRIP](B5_NTRIP.md) - Receive NTRIP data from the internet and send it to the SitePoint peripheral for aiding.
