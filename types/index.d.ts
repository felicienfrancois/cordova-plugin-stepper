// Type definitions for @felicienfrancois/cordova-plugin-stepper
// Project: https://github.com/felicienfrancois/cordova-plugin-stepper
//
// The plugin clobbers the global `stepper`, so no import is needed at runtime. To pull these declarations in, either
// add the package to `compilerOptions.types` in your tsconfig, or reference it once from any file:
//
//     /// <reference types="@felicienfrancois/cordova-plugin-stepper" />
//
// The interfaces are also exported, so `import type { StepperEntry } from '...'` works.
//
// Every promise-returning method also accepts legacy (onSuccess, onError) callbacks. When callbacks are passed the
// returned promise is `promise.then(onSuccess).catch(onError)`, so it settles with whatever the callback returns and
// never rejects. Those overloads are typed as `Promise<void>` because the value is not meant to be consumed.

/**
 * Rejection value.
 *
 * Android rejects with a {@link StepperNativeError} object. iOS rejects with the `NSError` localized description as a
 * plain string. Handle both shapes in cross-platform code.
 */
export type StepperError = StepperNativeError | string;

/** Android rejection payload. */
export interface StepperNativeError {
  /** See {@link StepperErrorCode}. */
  code: number;
  /** Free-form diagnostic string. Match on `code`, never on `message`. */
  message: string;
}

/**
 * Error codes actually emitted on Android:
 *
 * - `0` — unexpected native exception; `message` carries the Java exception message.
 * - `3` — `ERROR_NO_PERMISSION`. A runtime permission was denied; `message` names it. Since plugin 1.8.1 a denied
 *   `POST_NOTIFICATIONS` no longer produces this, counting starts anyway.
 * - `6` — `ERROR_BATTERY_OPTIMIZATION`. `disableBatteryOptimizations` could not run: Android below 6.0, or the
 *   settings screen failed to open.
 *
 * Other values in the native `Status` class are internal state and are never sent to JavaScript.
 */
export type StepperErrorCode = 0 | 3 | 6;

export type StepperErrorCallback = (error: StepperError) => void;

/** Options for {@link Stepper.requestPermission}. */
export interface StepperPermissionOptions {
  /**
   * Skip the `POST_NOTIFICATIONS` request (Android 13+). Defaults to `false`.
   *
   * `POST_NOTIFICATIONS` is a single app-wide permission shared by every notification source in the app, push SDKs
   * included, and Android permanently denies it after two refusals. Set this to `true` when your app already prompts
   * for notifications elsewhere, so the pedometer does not show a second identical-looking dialog and burn the last
   * refusal. Step counting works either way; only the progress notification stays hidden.
   */
  skipNotificationPermission?: boolean;
}

/** Options for {@link Stepper.startStepperUpdates}. */
export interface StepperStartOptions extends StepperPermissionOptions {
  /**
   * Step goal. Omit to keep a goal previously set through {@link Stepper.setGoal}; pass `0` to clear it. With no goal
   * the notification shows no progress bar. Android only.
   */
  goal?: number;
  /** IANA timezone forced for the daily and hourly aggregation boundaries, e.g. `"Europe/Paris"`. Android only. */
  timeZone?: string;
  /** Notification title. Android only. */
  pedometerIsCountingText?: string;
  /**
   * Notification text while below the goal. Available placeholders, in order: `stepsToGo`, `todaySteps`, `goal` —
   * insert with `%1$s`, `%2$s`, `%3$s`. Android only.
   */
  pedometerStepsToGoFormatText?: string;
  /** Notification text before the first step value is known. Android only. */
  pedometerYourProgressFormatText?: string;
  /**
   * Notification text once the goal is reached, and the only text used when no goal is set. Available placeholders,
   * in order: `todaySteps`, `goal` — insert with `%1$s`, `%2$s`. Android only.
   */
  pedometerGoalReachedFormatText?: string;
}

/**
 * Argument of {@link Stepper.setNotificationLocalizedStrings}.
 *
 * Every field is required: the native side reads all four with `getString`, and a single missing key makes the whole
 * call a silent no-op.
 *
 * Note that these key names differ from the equivalent {@link StepperStartOptions} fields.
 */
export interface StepperNotificationStrings {
  /** Notification title. Same target as `pedometerIsCountingText`. */
  pedometerIsCounting: string;
  /** Same target as `pedometerStepsToGoFormatText`. */
  stepsToGo: string;
  /** Same target as `pedometerYourProgressFormatText`. */
  yourProgress: string;
  /** Same target as `pedometerGoalReachedFormatText`. */
  goalReached: string;
}

/** Payload delivered to the {@link Stepper.startStepperUpdates} handler. */
export interface StepperUpdate {
  /** Steps counted today. Both platforms. */
  steps_today: number;
  /** iOS only. Milliseconds since epoch, serialised as a stringified float, e.g. `"1756894997793.000000"`. */
  startDate?: string;
  /** iOS only. Same encoding as {@link StepperUpdate.startDate}. */
  endDate?: string;
  /** iOS only. Metres. */
  distance?: number;
  /** iOS only. */
  floorsAscended?: number;
  /** iOS only. */
  floorsDescended?: number;
}

/** Result of {@link Stepper.getSteps} and {@link Stepper.getStepsByPeriod}. */
export interface StepperStepsResult {
  /** Steps over the requested period. Both platforms. */
  steps: number;
  /** iOS only. Metres. */
  distance?: number;
  /** iOS only. */
  floorsAscended?: number;
  /** iOS only. */
  floorsDescended?: number;
}

/** One entry of {@link StepperEntriesResult}. */
export interface StepperEntry {
  /**
   * Android: the entry start timestamp in milliseconds since epoch, same value as
   * {@link StepperEntry.startTimestamp}. iOS: a formatted date string.
   */
  data: number | string;
  /** Steps recorded in this entry. Both platforms. */
  steps: number;
  /** Android only. Milliseconds since epoch. */
  startTimestamp?: number;
  /** Android only. Hardware step counter value at the start of the entry. */
  startIndex?: number;
  /** Android only. Milliseconds since epoch. */
  endTimestamp?: number;
  /** Android only. Hardware step counter value at the end of the entry. */
  endIndex?: number;
  /** iOS only. Metres. */
  distance?: number;
}

/** Result of {@link Stepper.getLastEntries}. */
export interface StepperEntriesResult {
  entries: StepperEntry[];
}

/** Result of {@link Stepper.getVendorBackgroundStatus}. */
export interface StepperVendorBackgroundStatus {
  /** `true` on MIUI/HyperOS devices. When `false`, the other fields are absent. */
  vendor: boolean;
  /** `true`/`false` when the proprietary Autostart state could be read, `null` when unknown. */
  autostartAllowed?: boolean | null;
  /**
   * `true` when the PowerKeeper per-app battery mode is "No restrictions", `false` when it is another mode, `null`
   * when the provider is absent or refuses the query.
   */
  batteryUnrestricted?: boolean | null;
}

/** Date accepted wherever the plugin takes a date. Strings and numbers are passed to `new Date()`. */
export type StepperDateInput = Date | string | number;

export interface Stepper {
  readonly name: string;

  /** Resolves `true` when the device exposes a hardware step counter. A missing sensor is not an error. */
  isStepCountingAvailable(): Promise<boolean>;
  isStepCountingAvailable(
    onSuccess: (available: boolean) => void,
    onError?: StepperErrorCallback,
  ): Promise<void>;

  /**
   * Requests `ACTIVITY_RECOGNITION`, plus `POST_NOTIFICATIONS` on Android 13+ unless
   * {@link StepperPermissionOptions.skipNotificationPermission} is set. Resolves `true` on iOS without doing
   * anything.
   */
  requestPermission(options?: StepperPermissionOptions): Promise<boolean>;
  requestPermission(
    onSuccess: (granted: boolean) => void,
    onError?: StepperErrorCallback,
  ): Promise<void>;
  requestPermission(
    options: StepperPermissionOptions,
    onSuccess: (granted: boolean) => void,
    onError?: StepperErrorCallback,
  ): Promise<void>;

  /**
   * Asks the user to exempt the app from battery optimizations. Required for the background service to be restarted
   * reliably after a kill. Resolves `false` on iOS, and never rejects — failures resolve `false`.
   */
  disableBatteryOptimizations(): Promise<boolean>;
  disableBatteryOptimizations(
    onSuccess: (granted: boolean) => void,
    onError?: StepperErrorCallback,
  ): Promise<void>;

  /**
   * Resolves `true` on MIUI/HyperOS devices that still need the vendor configuration, where the AOSP battery dialog
   * is replaced by a multi-mode picker and the Doze whitelist alone does not stop the OS freezing background work.
   * Resolves `false` on iOS, on stock Android, and when both vendor settings are verified as already configured.
   */
  isVendorBatteryRestricted(): Promise<boolean>;
  isVendorBatteryRestricted(
    onSuccess: (restricted: boolean) => void,
    onError?: StepperErrorCallback,
  ): Promise<void>;

  /** Reads the MIUI/HyperOS background-execution state, to list only the steps still needed. */
  getVendorBackgroundStatus(): Promise<StepperVendorBackgroundStatus>;
  getVendorBackgroundStatus(
    onSuccess: (status: StepperVendorBackgroundStatus) => void,
    onError?: StepperErrorCallback,
  ): Promise<void>;

  /**
   * Opens the vendor Autostart screen (MIUI/HyperOS), falling back to the app details settings. Resolves `true`
   * immediately without opening anything when Autostart is already allowed, otherwise once the user returns.
   */
  requestVendorAutostart(): Promise<boolean>;
  requestVendorAutostart(
    onSuccess: (shown: boolean) => void,
    onError?: StepperErrorCallback,
  ): Promise<void>;

  /**
   * Opens the vendor per-app battery screen (MIUI/HyperOS PowerKeeper), falling back to the app details settings.
   * Resolves `true` immediately when the mode is already "No restrictions", otherwise once the user returns.
   */
  openVendorBatterySettings(): Promise<boolean>;
  openVendorBatterySettings(
    onSuccess: (shown: boolean) => void,
    onError?: StepperErrorCallback,
  ): Promise<void>;

  /**
   * Starts counting and attaches `onStepUpdate`, which is called once immediately and then on every step change.
   *
   * Unlike the other methods this one returns nothing and takes no promise form: the callback is persistent.
   * `onStepUpdate` is required — the implementation calls it without checking.
   *
   * Call it again on `deviceready` and on `resume` to re-attach the callback after the webview is recreated. This
   * does not create a second service.
   *
   * @remarks A legacy four-argument form `(onStepUpdate, onError, undefined, options)` also works, where the options
   * are read from the fourth argument. Prefer the form below.
   */
  startStepperUpdates(
    options: StepperStartOptions,
    onStepUpdate: (data: StepperUpdate) => void,
    onError?: StepperErrorCallback,
  ): void;

  /**
   * Stops counting and detaches the update callback.
   *
   * @param clearDatabase when `true`, wipes the recorded history. Defaults to `false`.
   */
  stopStepperUpdates(clearDatabase?: boolean): Promise<void>;
  stopStepperUpdates(
    onSuccess: () => void,
    onError?: StepperErrorCallback,
  ): Promise<void>;
  stopStepperUpdates(
    clearDatabase: boolean,
    onSuccess: () => void,
    onError?: StepperErrorCallback,
  ): Promise<void>;

  /** Equivalent to `stopStepperUpdates(true)`: stops counting and wipes the recorded history. */
  destroy(): Promise<void>;
  destroy(onSuccess: () => void, onError?: StepperErrorCallback): Promise<void>;

  /**
   * Steps for the whole local day containing `date`, midnight to 23:59:59.999.
   *
   * @param date defaults to now.
   */
  getSteps(date?: StepperDateInput): Promise<StepperStepsResult>;
  getSteps(
    date: StepperDateInput | undefined,
    onSuccess: (result: StepperStepsResult) => void,
    onError?: StepperErrorCallback,
  ): Promise<void>;

  /** Steps between two dates. */
  getStepsByPeriod(
    start: StepperDateInput,
    end: StepperDateInput,
  ): Promise<StepperStepsResult>;
  getStepsByPeriod(
    start: StepperDateInput,
    end: StepperDateInput,
    onSuccess: (result: StepperStepsResult) => void,
    onError?: StepperErrorCallback,
  ): Promise<void>;

  /**
   * The `num` most recent history entries, most recent first.
   *
   * @param num must be a number: a non-numeric value makes the native call reject nothing and the promise never
   * settles.
   */
  getLastEntries(num: number): Promise<StepperEntriesResult>;
  getLastEntries(
    num: number,
    onSuccess: (result: StepperEntriesResult) => void,
    onError?: StepperErrorCallback,
  ): Promise<void>;

  /**
   * Replaces the notification strings and redraws the notification. Android only, no-op on iOS.
   *
   * All four fields of {@link StepperNotificationStrings} are required.
   */
  setNotificationLocalizedStrings(
    strings: StepperNotificationStrings,
  ): Promise<void>;
  setNotificationLocalizedStrings(
    strings: StepperNotificationStrings,
    onSuccess: () => void,
    onError?: StepperErrorCallback,
  ): Promise<void>;

  /**
   * Sets the step goal and redraws the notification. A goal above `0` shows a progress bar; `0` removes it. Negative
   * values are ignored. Android only, no-op on iOS.
   */
  setGoal(num: number): Promise<void>;
  setGoal(
    num: number,
    onSuccess: () => void,
    onError?: StepperErrorCallback,
  ): Promise<void>;
}

declare global {
  /** Installed by the plugin through `<clobbers target="stepper" />`. Available after `deviceready`. */
  const stepper: Stepper;

  interface Window {
    stepper: Stepper;
  }
}
