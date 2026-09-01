# cordova-plugin-stepper

Lightweight pedometer Cordova/Phonegap plugin for Android using the hardware step sensor, with notifications.

Plugin using the hardware step-sensor for minimal battery consumption. This app is designed to be kept running all the time without having any impact on your battery life! Therefore the app does not drain any additional battery. Unlike other pedometer apps, this app does not track your movement or your location so it doesn't need to turn on your GPS sensor - no impact on your battery.

The plugin also creates a background service with a neat and nice notification (in Android platform) to continue working even after the application is closed and the device is restarted.

## Installation

#### Latest published version on npm (with Cordova CLI >= 5.0.0)

```
cordova plugin add @felicienfrancois/cordova-plugin-stepper
```

#### Latest version from GitHub

```
cordova plugin add https://github.com/@felicienfrancois/cordova-plugin-stepper
```
## Usage

#### isStepCountingAvailable () => Promise
Check if Pedometer sensor is available on the device

```js
stepper.isStepCountingAvailable().then((result) => {
  if(result) console.log("Available !");
  else console.log("Not available :-S");
}).catch((err) => {
  console.error(err);
});

```

#### requestPermission (options) => Promise [Android only]
Android: request for permission by anticipation
IOS: return true and do nothing
This can be helpful to request permission before starting the stepper.
It can also prevent unexpected detachment at first start (Permission popup trigger a pause/resume cycle which can leads to service detachment)

Requests `ACTIVITY_RECOGNITION`, plus `POST_NOTIFICATIONS` on Android 13+ for the background service notification.

The `options` parameter is optional and may contain:
- skipNotificationPermission - _bool_ - Don't request `POST_NOTIFICATIONS` (default `false`, i.e. it is requested).
  See [Skipping the notification permission](#skipping-the-notification-permission)

```js
stepper.requestPermission().then((result) => {
  if(result) console.log("Authorized !");
  else console.log("Denied :-S");
}).catch((err) => {
  console.error(err);
});

// App already asked for notifications through its own push flow
stepper.requestPermission({ skipNotificationPermission: true }).then((result) => {
  if(result) console.log("Authorized !");
});
```

##### Skipping the notification permission

`POST_NOTIFICATIONS` is a single app-wide permission shared by every notification source in the app — push SDKs such
as OneSignal included — and there is no per-channel permission. If your app already prompts for notifications
elsewhere, the pedometer prompt is a second identical-looking dialog for a different purpose, which is misleading;
and since Android permanently denies the permission after two refusals, it also burns the app's last chance to ever
ask again. Pass `skipNotificationPermission: true` in that case and let your own flow own the single request.

Skipping it does not affect step counting: the foreground service still starts and keeps counting, only its progress
notification stays hidden from the shade (the app remains listed in the Android 13+ "Active apps" task manager).

Pass it to **both** `requestPermission` and `startStepperUpdates`, otherwise the one you left out prompts anyway:

```js
const options = { skipNotificationPermission: true };

await stepper.requestPermission(options);
stepper.startStepperUpdates(options, onStepUpdate, onError);
```

#### disableBatteryOptimizations () => Promise [Android only]
Android: request for disabling battery optimizations
IOS: return false and do nothing

```js
stepper.disableBatteryOptimizations().then((result) => {
  if(result) console.log("Authorized !");
  else console.log("Not available or Denied :-S");
}).catch((err) => {
  // Should never happen as error are catched and return false in success
  console.error(err);
});
```

#### isVendorBatteryRestricted () => Promise [Android only]
Resolves `true` on MIUI/HyperOS (Xiaomi/Redmi/POCO) devices that still need the vendor configuration, where the
standard `disableBatteryOptimizations` dialog is replaced by an obscure multi-mode battery picker and the Doze
whitelist alone does not prevent the OS from freezing background work. Resolves `false` on iOS, stock Android,
and on vendor devices where both Autostart and "No restrictions" battery mode could be verified as already
configured. Use it to show your own yes/no popup and route only these devices to the vendor screens below.

#### getVendorBackgroundStatus () => Promise [Android only]
Resolves `{ vendor, autostartAllowed, batteryUnrestricted }`:
- `vendor`: `true` on MIUI/HyperOS (Xiaomi/Redmi/POCO) devices; when `false` the other fields are absent.
- `autostartAllowed`: `true`/`false` when the proprietary Autostart AppOps state could be read, `null` when unknown.
- `batteryUnrestricted`: `true` when the PowerKeeper per-app battery mode is "No restrictions", `false` when it is
  another mode, `null` when the PowerKeeper provider is absent or refuses the query.

Use it to build precise instructions (only list the steps that are still needed) and to skip the popup entirely
when everything is already configured.

#### requestVendorAutostart () => Promise [Android only]
Opens the vendor "Autostart" management screen (MIUI/HyperOS), falling back to the app details settings when the
screen is unavailable. Resolves `true` immediately without opening anything when Autostart is verified as already
allowed. Otherwise resolves `true` once the user returns.

#### openVendorBatterySettings () => Promise [Android only]
Opens the vendor per-app battery mode screen (MIUI/HyperOS PowerKeeper), falling back to the app details settings.
Resolves `true` immediately without opening anything when the battery mode is verified as "No restrictions".
Otherwise resolves `true` once the user returns.

```js
const status = await stepper.getVendorBackgroundStatus();
if (!status.vendor) {
  await stepper.disableBatteryOptimizations();
} else if (status.autostartAllowed !== true || status.batteryUnrestricted !== true) {
  // show your own translated popup listing only the needed steps, then on "yes":
  await stepper.requestVendorAutostart();      // skipped internally if already allowed
  await stepper.openVendorBatterySettings();   // skipped internally if already unrestricted
}
```

#### startStepperUpdates (options, onStepUpdate, onError)
The onStepUpdate handler is called once during the first call and then called from the background thread whenever data is available.

The method also creates a background service with notification (Android only).

The `options` parameter may contain optional parameters. Below parameters recommended for notification localization (in Android platform):
- goal - _int_ - the goal (default to no goal). Omit it to keep a goal previously set through `setGoal`; pass `0` to
  clear it. With no goal, the notification shows no progress bar
- pedometerIsCountingText - _string_ - Set title text for notification
- pedometerStepsToGoFormatText - _string_ - Set description format string with text for notification
- pedometerYourProgressFormatText - _string_ - Set progress description format string with text for notification
- pedometerGoalReachedFormatText - _string_ - Set goal description format string with text for notification when the number of steps reaches the target value
- timeZone - _string_ - Force timezone for aggregation ticks and todays count
- skipNotificationPermission - _bool_ - Don't request `POST_NOTIFICATIONS` (default `false`, i.e. it is requested).
  See [Skipping the notification permission](#skipping-the-notification-permission)

Example:
```js
const options = { 
  pedometerIsCountingText: 'Pedometer is counting', 
  pedometerStepsToGoFormatText: '%s steps to go', // available variables: [stepsToGo, todaySteps, goal]. Insert using %1$s, %2$s, %3$s placeholders
  pedometerYourProgressFormatText: 'Your progress will be shown here soon', 
  pedometerGoalReachedFormatText: '%s steps today', // available variables: [todaySteps, goal]. Insert using %1$s, %2$s placeholders
};
  
stepper.startStepperUpdates(options, (result) => {
  console.log(result.steps_today);
}, (err) => {
  console.error(err);
});

```

_Note: When the application is suspended, the call to handlers is temporarily suspended. When the application is closed, the background service continues to work (in Android platform) but the callbacks to you app may be stopped. The background service continues after the device is restarted._

In order to keep callbacks after restarting or resuming your app you have to reattach background service by calling `startStepperUpdates`
```js
// Reattach on reboot (required)
document.addEventListener("deviceready", () => {  
	stepper.startStepperUpdates(options, onStepUpdate, errorHandler);
});
// Reattach after pause/resume (which can sometimes lead to dettachment)
document.addEventListener("resume", () => {  
	stepper.startStepperUpdates(options, onStepUpdate, errorHandler);
});

```


_To stop the background service, call the method `stopStepperUpdates`. When you open an application and call the launch method again, it joins the current background service._

#### stopStepperUpdates () => Promise 
The method stops the background calls to the success handler of the `startStepperUpdates` method and stops the background service (in Android platform) with remove notification.

Example:
```js
stepper.stopStepperUpdates()
  .then(() => {
    console.error("Stopped");
  })
  .catch((error) => {
    console.error(err);
  });
```

_Note: Background service can only be stopped by this method._

#### destroy () => Promise 
Android Only: Stop pedometer updates and clear database

Example:
```js
stepper.destroy()
  .then(() => {
    console.error("Stopped and cleared");
  })
  .catch((error) => {
    console.error(err);
  });
```

_Note: Background service can only be stopped by this method._

#### setGoal (num) => Promise
Set a goal (number of steps) for a pedometer.
When a goal is set, a progress bar is shown in the notification.

Example:
```js
var goal = 1000;

stepper.setGoal(goal)
  .then(() => {
    console.error("OK");
  })
  .catch((error) => {
    console.error(err);
  });
```

_Note: It is recommended to call the method before calling the method `startStepperUpdates`, but it is allowed to change the target during operation._

#### getSteps (date) => Promise
Gets the number of steps for the specified day. `date` parameter must be start of day and number of milliseconds since the Unix Epoch.

Example:
```js
var interval = 1000 * 60 * 60 * 24, 
  startOfDay = Math.floor(Date.now() / interval) * interval;

stepper.getSteps(startOfDay)
  .then((result) => {
    console.log(result.steps);
  })
  .catch((error) => {
    console.error(err);
  });
```

#### getStepsByPeriod (start, end) => Promise
Gets the number of steps for the specified period.

Example:
```js
// 3 days period 
var interval = 1000 * 60 * 60 * 24, 
  start = Math.floor(Date.now() / interval) * interval - (interval * 3),
  end  = Math.floor(Date.now() / interval) * interval;

stepper.getSteps(start, end)
  .then((result) => {
    console.log(result.steps);
  })
  .catch((error) => {
    console.error(err);
  });
```

#### getLastEntries (num) => Promise
Gets all recent records in the specified limit.

Example:
```js
var limit = 10;

stepper.getLastEntries(limit)
  .then((result) => {
	  var entries = result.entries;
	  for (var i = 0; i < entries.length; i++) {
	    var entry = entries[i], data = entry.data,
	      steps = entry.steps;
	  }
  })
  .catch((error) => {
    console.error(err);
  });
```

## Error codes [Android only]

On Android, a rejection carries an object `{ code, message }`. `message` is a free-form diagnostic string — match on
`code`, never on `message`.

Three codes are actually emitted:

| Code | Name | Meaning |
| ---: | --- | --- |
| 0 | _(unnamed)_ | Unexpected native exception. `message` carries the Java exception message |
| 3 | `ERROR_NO_PERMISSION` | A runtime permission was denied. `message` names it, e.g. `Permission denied: android.permission.ACTIVITY_RECOGNITION`. Since 1.8.1 a denied `POST_NOTIFICATIONS` no longer produces this — counting starts anyway |
| 6 | `ERROR_BATTERY_OPTIMIZATION` | `disableBatteryOptimizations` could not run: Android below 6.0, or the settings screen failed to open |

The remaining values are internal plugin state or reserved, and are never sent to JavaScript: `STOPPED` 0,
`STARTING` 1, `RUNNING` 2, `ERROR_NO_SENSOR_FOUND` 4, `PAUSED` 5, `ERROR_FAILED_TO_START` 7.

Note that a missing step sensor is **not** an error: `isStepCountingAvailable` resolves `false` rather than
rejecting. Call it before starting the stepper.

> `ERROR_FAILED_TO_START` used to be `3`, colliding with `ERROR_NO_PERMISSION`. Only that never-emitted constant was
> renumbered, so code `3` keeps its meaning and no consumer needs updating.

On **iOS** a rejection is a plain string (the `NSError` localized description), with no code. Handle both shapes if
your code is cross-platform:

```js
stepper.disableBatteryOptimizations().catch((err) => {
  if (typeof err === "string") console.error(err);            // iOS
  else console.error(err.code, err.message);                  // Android
});
```

## Platform and device support

- Android
- iOS

## Credits
Icons made by authors from https://www.flaticon.com is licensed by http://creativecommons.org/licenses/by/3.0/

## License

Copyright (c) 2021, Félicien François

Project based on source code and includes parts of source code https://github.com/achubutkin/cordova-plugin-stepper
Copyright (c) 2019, Alexandr Chubutkin

Project based on source code and includes parts of source code https://github.com/j4velin/Pedometer 
Copyright (c) 2013 Thomas Hoffmann - All Rights Reserved

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.