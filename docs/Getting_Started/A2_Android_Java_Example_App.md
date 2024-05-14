# Android Java Example App

Dive right into the Example App to learn how to use the SitePoint SDK.

## Source

Source code for a [SitePoint Android Java Example App](https://github.com/signalquest/SitePoint-Android-Java-Example) is available which shows a minimal implementation using this SDK to interface with SitePoint devices.

This minimal implementation is focused on highlighting the important, required code, eschewing error handling and other best practices in favor of code clarity.

> **_Note:_** Additional Javadoc documentation and inline comments are included in the example app. These are not generated in the documentation, but are recommended to review before developing an app as they contain helpful information.

### Example of commented code:

```java
/**
 * Scans and creates SitePoints from scan results.
 *
 * @param sitePointHandler listens for SitePoint scan results
 */
public void startScanning(SitePointHandler sitePointHandler) {
if (disabled()) {
    return;
  }
  ScanSettings scanSettings =
      new ScanSettings.Builder()
          .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
          .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
          .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
          .build();
...
```

<hr>

## Start Developing

The [Implementation Guide](../Implementation_Guide/B1_Implementation_Guide.md) has step-by-step instructions for interfacing with the SitePoint SDK.
