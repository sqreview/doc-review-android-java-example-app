# Permissions

Add permissions for Bluetooth and Location services.

## Adding permissions to your app

### Add permissions to AndroidManifest.xml

In the AndroidManifest.xml file, add the following permissions and adjust their text values to match your app's use case. These will be shown to end users when requesting their permission.

```
TODO: Update with latest permissions code
```

### Request permissions from an Activity

An example of this is shown in the [PermissionsActivity.java](../../app/src/main/java/com/signalquest/example/PermissionsActivity.java) file in the example app. This Activity acts as the app's splash screen and initiates the permissions requests.

> **_Note:_** Both Javadoc comments and inline comments are provided throughout the file that contain helpful information regarding permission requests.

<hr>

## Read Next

- [Bluetooth Setup](B3_Bluetooth_Setup.md) - Set up BLEManager to handle Bluetooth Low-Energy (BLE) scanning and connections.
