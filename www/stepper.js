var exec = require("cordova/exec");

var Stepper = function () {
  this.name = "Stepper";
};

// IOS & Android - Documented
Stepper.prototype.isStepCountingAvailable = function (onSuccess, onError) {
  let promise = new Promise(function (resolve, reject) {
    exec(resolve, reject, "Stepper", "isStepCountingAvailable", []);
  });
  if (onSuccess) promise = promise.then(onSuccess);
  if (onError) promise = promise.catch(onError);
  return promise;
};

// IOS & Android - Documented
Stepper.prototype.requestPermission = function (onSuccess, onError) {
  let promise = new Promise(function (resolve, reject) {
    if (!/^android|amazon/i.test(device.platform)) {
      return resolve(true);
    }
    exec(resolve, reject, "Stepper", "requestPermission", []);
  });
  if (onSuccess) promise = promise.then(onSuccess);
  if (onError) promise = promise.catch(onError);
  return promise;
};

// IOS & Android - Documented
Stepper.prototype.disableBatteryOptimizations = function (onSuccess, onError) {
  let promise = new Promise(function (resolve) {
    if (!/^android|amazon/i.test(device.platform)) {
      return resolve(false);
    }
    exec(
      resolve,
      () => resolve(false),
      "Stepper",
      "disableBatteryOptimizations",
      [],
    );
  });
  if (onSuccess) promise = promise.then(onSuccess);
  if (onError) promise = promise.catch(onError);
  return promise;
};

// Android - Documented
// Resolves true on MIUI/HyperOS, where the AOSP battery dialog is replaced by an obscure multi-mode picker and the
// app must instead route the user through the vendor Autostart / battery screens.
Stepper.prototype.isVendorBatteryRestricted = function (onSuccess, onError) {
  let promise = new Promise(function (resolve) {
    if (!/^android|amazon/i.test(device.platform)) {
      return resolve(false);
    }
    exec(
      resolve,
      () => resolve(false),
      "Stepper",
      "isVendorBatteryRestricted",
      [],
    );
  });
  if (onSuccess) promise = promise.then(onSuccess);
  if (onError) promise = promise.catch(onError);
  return promise;
};

// Android - Documented
// Returns { vendor, autostartAllowed, batteryUnrestricted } describing the MIUI/HyperOS background-execution state.
// autostartAllowed / batteryUnrestricted are true/false when the state could be read, null when unknown.
Stepper.prototype.getVendorBackgroundStatus = function (onSuccess, onError) {
  let promise = new Promise(function (resolve) {
    if (!/^android|amazon/i.test(device.platform)) {
      return resolve({ vendor: false });
    }
    exec(
      resolve,
      () => resolve({ vendor: false }),
      "Stepper",
      "getVendorBackgroundStatus",
      [],
    );
  });
  if (onSuccess) promise = promise.then(onSuccess);
  if (onError) promise = promise.catch(onError);
  return promise;
};

// Android - Documented
// Opens the vendor "Autostart" management screen (MIUI/HyperOS). Resolves true once the user returns.
Stepper.prototype.requestVendorAutostart = function (onSuccess, onError) {
  let promise = new Promise(function (resolve) {
    if (!/^android|amazon/i.test(device.platform)) {
      return resolve(false);
    }
    exec(resolve, () => resolve(false), "Stepper", "requestVendorAutostart", []);
  });
  if (onSuccess) promise = promise.then(onSuccess);
  if (onError) promise = promise.catch(onError);
  return promise;
};

// Android - Documented
// Opens the vendor per-app battery screen (MIUI/HyperOS), falling back to the app details settings.
Stepper.prototype.openVendorBatterySettings = function (onSuccess, onError) {
  let promise = new Promise(function (resolve) {
    if (!/^android|amazon/i.test(device.platform)) {
      return resolve(false);
    }
    exec(
      resolve,
      () => resolve(false),
      "Stepper",
      "openVendorBatterySettings",
      [],
    );
  });
  if (onSuccess) promise = promise.then(onSuccess);
  if (onError) promise = promise.catch(onError);
  return promise;
};

// IOS & Android - Documented
Stepper.prototype.startStepperUpdates = function (
  options,
  onSuccess,
  onError,
  extra,
) {
  let opts = extra || {};
  if (typeof options === "object") {
    opts = options;
  }
  const now = new Date();
  let endOfDay;
  try {
    const parts = new Intl.DateTimeFormat("en-US", {
      timeZone: options.timeZone || undefined,
      hour: "numeric",
      minute: "numeric",
      second: "numeric",
      hour12: false,
    }).formatToParts(now);
    const getPart = (type) => {
      const part = parts.find((p) => p.type === type);
      return part ? parseInt(part.value, 10) : 0;
    };
    let h = getPart("hour");
    if (h === 24) h = 0;
    const m = getPart("minute");
    const s = getPart("second");
    endOfDay = new Date(
      now.getTime() - h * 3600000 - m * 60000 - s * 1000 + 24 * 3600000,
    );
  } catch (e) {
    endOfDay = new Date(
      now.getFullYear(),
      now.getMonth(),
      now.getDate() + 1,
      0,
      0,
      0,
      0,
    );
  }
  exec(
    (result) => {
      if (result && result.startDate && new Date() >= endOfDay) {
        this.stopStepperUpdates(
          false,
          this.startStepperUpdates.bind(
            this,
            options,
            onSuccess,
            onError,
            extra,
          ),
          this.startStepperUpdates.bind(
            this,
            options,
            onSuccess,
            onError,
            extra,
          ),
        );
        return;
      }
      return onSuccess(result);
    },
    onError,
    "Stepper",
    "startStepperUpdates",
    [opts],
  );
};

// IOS & Android - Documented
Stepper.prototype.stopStepperUpdates = function (
  clearDatabase,
  onSuccess,
  onError,
) {
  if (typeof clearDatabase !== "boolean") {
    onError = onSuccess;
    onSuccess = clearDatabase;
    clearDatabase = false;
  }
  let promise = new Promise(function (resolve, reject) {
    exec(resolve, reject, "Stepper", "stopStepperUpdates", [clearDatabase]);
  });
  if (onSuccess) promise = promise.then(onSuccess);
  if (onError) promise = promise.catch(onError);
  return promise;
};

// IOS & Android - Documented
Stepper.prototype.destroy = function (onSuccess, onError) {
  return this.stopStepperUpdates(true, onSuccess, onError);
};

// IOS & Android - Documented
Stepper.prototype.getSteps = function (date, onSuccess, onError) {
  const startDate = new Date(date || new Date());
  startDate.setHours(0, 0, 0, 0);
  const endDate = new Date(date || new Date());
  endDate.setHours(23, 59, 59, 999);
  return this.getStepsByPeriod(startDate, endDate, onSuccess, onError);
};

// IOS & Android - Documented
Stepper.prototype.getStepsByPeriod = function (start, end, onSuccess, onError) {
  const startDate = start instanceof Date ? start : new Date(start);
  const endDate = end instanceof Date ? end : new Date(end);
  let promise = new Promise(function (resolve, reject) {
    exec(resolve, reject, "Stepper", "getStepsByPeriod", [
      startDate.toISOString(),
      endDate.toISOString(),
    ]);
  });
  if (onSuccess) promise = promise.then(onSuccess);
  if (onError) promise = promise.catch(onError);
  return promise;
};

// Android - Behave wierd - Documented
Stepper.prototype.getLastEntries = function (num, onSuccess, onError) {
  let promise = new Promise(function (resolve, reject) {
    exec(resolve, reject, "Stepper", "getLastEntries", [num]);
  });
  if (onSuccess) promise = promise.then(onSuccess);
  if (onError) promise = promise.catch(onError);
  return promise;
};

// Android - Documented
Stepper.prototype.setNotificationLocalizedStrings = function (
  keyValueObj,
  onSuccess,
  onError,
) {
  let promise = new Promise(function (resolve, reject) {
    exec(resolve, reject, "Stepper", "setNotificationLocalizedStrings", [
      keyValueObj,
    ]);
  });
  if (onSuccess) promise = promise.then(onSuccess);
  if (onError) promise = promise.catch(onError);
  return promise;
};

// Android - Documented
Stepper.prototype.setGoal = function (num, onSuccess, onError) {
  let promise = new Promise(function (resolve, reject) {
    exec(resolve, reject, "Stepper", "setGoal", [num]);
  });
  if (onSuccess) promise = promise.then(onSuccess);
  if (onError) promise = promise.catch(onError);
  return promise;
};

module.exports = new Stepper();
