[Home](../index.md) / [Getting Started](../index.md#getting-started) / Android Java Example App

# Android Java Example App

## Source code

The [SitePoint Android Java Example App source code](https://github.com/signalquest/SitePoint-Android-Java-Example) shows a minimal implementation using this SDK to interface with SitePoint devices.

This minimal implementation is focused on highlighting the important, required code, omitting error handling and other best practices for the sake of code clarity.

> **_Note:_** Additional Javadoc documentation and inline comments are included in the example app. These are not generated in this documentation, but may be helpful to review before and during app development as they contain helpful information.

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
