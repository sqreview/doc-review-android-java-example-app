[Home](../index.md) / [Implementation Guide](B1_Implementation_Guide.md) / NTRIP

# NTRIP

Set up NTRIP connection handling and RTCM parsing for aiding the SitePoint.

## Overview

NTRIP is an internet protocol over which RTCM packets are streamed from a nearby base station. These RTCM messages are then forwarded onto the SitePoint to aid in acquiring a more accurate location solution.

Setting up an NTRIP connection is handled on the app side, and the RTCM messages are then parsed by the SDK and sent to the SitePoint.

In the example app, the NTRIP code is primarily located within Ntrip.swift.

In addition, some NTRIP providers require the device's location to be sent upon connection. Others also require regular location updates to be sent. These locations are assembled into 'GGA Strings'. The example app contains these functions in NtripGga.swift.

### Connecting to an NTRIP service

Refer to the example app for a simplified approach to establish a connection with an NTRIP provider.

Once the connection has been requested, the response is passed into `NtripParser.handleAuthorized` method to parse the authorization request.

Once successful, the RTCM messages are streamed into ``NtripParser.parseRtcm(byte[] data)`` which enables streamed data to be sent to the `rtcmCallback` when received.

### Parsing received RTCM messages

The `rtcmCallback` is called when messages are received over the NTRIP stream. Longer RTCM messages may span multiple callbacks.

These messages are then filtered, ignoring any messages which aren't relevant to SitePoint aiding. They are also cleaned up for consistency (if necessary), and prepared to send to the SitePoint. 

### Sending RTCM messages to the SitePoint

With the RTCM messages preparation above, they are then sent to the SitePoint over the RTCM characteristic.

This is done by calling `peripheral.writeValue` and passing in the prepared RTCM message.

The SitePoint will receive these messages and begin using them to aid in solving a more accurate solution. When successful, and given good satellite view, you should see the status change from `3D` to `Float` and then onto `Fix` where the SitePoint will operate in its highest-accuracy mode.

### Monitoring NTRIP aiding quality

To monitor the success of the RTCM messages being used by the SitePoint, the ``Status/aidingQuality`` is used. This is an array of eight boolean values, with `True` representing a successful RTCM message received and `False` representing a message which was not usable by the SitePoint.

The most recent values start from the left (or first array element) and travel to the right as additional messages are reported upon. 

In the SQ Survey app, this aiding quality indicator is shown as a graphical indicator with `True` showing a solid rectangle and a `False` result showing an empty rectangle. This gives users a visual indicator of successful RTCM messages and aligns 1-to-1 with the array of boolean values.
