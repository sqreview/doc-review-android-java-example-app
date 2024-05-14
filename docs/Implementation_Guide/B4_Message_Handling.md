# Message Handling

## Overview

Receive messages from and send RTCM data to the SitePoint peripheral.

## About structured messages

SignalQuest uses a custom transport protocol to receive device status and location messages from the SitePoint peripheral. These messages are received over a single characteristic and parsed into developer-friendly data types.

## Reading messages

Messages are received through the `onCharacteristicChanged` callback and sent to the `readMessage` method, which are then parsed by an instance of the `MessageHandler` class. The `receive` methods within this `messageHandler` instance accept the `Status` and `Location` message types.

In the example app, these `receive` methods populate the fields in the corresponding sections of the user interface. Another example, for a more complex app, would be to use the `Location` data to update a map, and the `Status` data to display sensor information on a status bar.

## Sending RTCM data

RTCM data does not use the message protocol or characteristic. Instead, it uses a dedicated RTCM characteristic to send an RTCM stream to the SitePoint peripheral for RTK aiding. This RTCM stream is acquired via an NTRIP caster over an internet connection.

See the [NTRIP](B5_NTRIP.md) section for more information on establishing the connection with the NTRIP caster, parsing the RTCM messages from the NTRIP protocol, and sending the RTCM data to the SitePoint peripheral.

<hr>

## Read Next

- [NTRIP](B5_NTRIP.md) - Receive NTRIP data from the internet and send it to the SitePoint peripheral for aiding.
