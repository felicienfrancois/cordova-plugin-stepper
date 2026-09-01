package org.apache.cordova.stepper;

import android.annotation.SuppressLint;
import java.text.NumberFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.time.OffsetDateTime;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.net.Uri;
import android.Manifest;
import android.app.AppOpsManager;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Process;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.content.SharedPreferences;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.Sensor;
import android.os.PowerManager;
import org.apache.cordova.stepper.util.API26Wrapper;
import org.apache.cordova.stepper.util.Config;
import org.apache.cordova.stepper.util.Status;
import org.apache.cordova.stepper.util.Entry;

import android.os.Build;
import android.util.Log;
import android.util.Pair;

import static android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS;
import static android.content.Context.POWER_SERVICE;

/**
 * This class listens to the pedometer sensor
 */
public class StepperPlugin extends CordovaPlugin {

	public static int REQUEST_DYN_PERMS = 101;
	public static int REQUEST_MAN_PERMS = 102;
	public static int REQUEST_BATTERY_PERMS = 103;
	public static int REQUEST_VENDOR_SETTINGS = 104;

	private int status;

	public final static NumberFormat formatter = NumberFormat.getInstance(Locale.getDefault());

	// temporary callback used for async permission/activity results
	private CallbackContext pendingCallbackContext;
	private static CallbackContext updateCallback; // Keeps track of the persistent callback.

	/**
	 * Executes the request.
	 *
	 * @param action          the action to execute.
	 * @param args            the exec() arguments.
	 * @param callbackContext the callback context used when calling back into
	 *                        JavaScript.
	 * @return whether the action was valid.
	 */
	public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext)
			throws JSONException {
		Log.i("STEPPER", "StepperPlugin.execute(\"" + action + "\")");

		if (action.equals("isStepCountingAvailable") || action.equals("requestPermission")
				|| action.equals("disableBatteryOptimizations")
				|| action.equals("isVendorBatteryRestricted") || action.equals("requestVendorAutostart")
				|| action.equals("openVendorBatterySettings") || action.equals("getVendorBackgroundStatus")
				|| action.equals("startStepperUpdates") || action.equals("stopStepperUpdates")
				|| action.equals("setNotificationLocalizedStrings")
				|| action.equals("setGoal") || action.equals("getStepsByPeriod") || action.equals("getLastEntries")) {

			final CallbackContext cc = callbackContext; // capture for runnable
			// we will reply asynchronously, tell Cordova to keep the callback
			answerLater(cc);
			cordova.getThreadPool().execute(new Runnable() {
				public void run() {
					try {
						if (action.equals("isStepCountingAvailable")) {
							isStepCountingAvailable(cc);
						} else if (action.equals("requestPermission")) {
							requestPermission(args, cc);
						} else if (action.equals("disableBatteryOptimizations")) {
							disableBatteryOptimizations(cc);
						} else if (action.equals("isVendorBatteryRestricted")) {
							win(cc, isVendorBatteryRestricted());
						} else if (action.equals("requestVendorAutostart")) {
							requestVendorAutostart(cc);
						} else if (action.equals("openVendorBatterySettings")) {
							openVendorBatterySettings(cc);
						} else if (action.equals("getVendorBackgroundStatus")) {
							getVendorBackgroundStatus(cc);
						} else if (action.equals("startStepperUpdates")) {
							updateCallback = cc;
							start(args, cc);
						} else if (action.equals("stopStepperUpdates")) {
							stop(args, cc);
						} else if (action.equals("setNotificationLocalizedStrings")) {
							setNotificationLocalizedStrings(args);
							win(cc);
						} else if (action.equals("setGoal")) {
							setGoal(args);
							win(cc);
						} else if (action.equals("getStepsByPeriod")) {
							getStepsByPeriod(args, cc);
						} else if (action.equals("getLastEntries")) {
							getLastEntries(args, cc);
						}
					} catch (Exception e) {
						fail(cc, 0, e.getMessage());
					}
				}
			});
			return true;
		}
		return false;
	}

	/**
	 * Disables battery optimizations for the app. Requires
	 * permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS to function.
	 */
	@SuppressLint("BatteryLife")
	private void disableBatteryOptimizations(CallbackContext cc) {
		try {
			Intent intent = new Intent();
			String pkgName = getActivity().getPackageName();
			PowerManager pm = (PowerManager) getActivity().getSystemService(POWER_SERVICE);

			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
				fail(cc, Status.ERROR_BATTERY_OPTIMIZATION, "Permission not relevant on this device");
				return;
			}

			if (pm.isIgnoringBatteryOptimizations(pkgName)) {
				win(cc, true);
				return;
			}

			// remember for onActivityResult
			pendingCallbackContext = cc;
			intent.setAction(ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
			intent.setData(Uri.parse("package:" + pkgName));

			cordova.startActivityForResult(this, intent, REQUEST_BATTERY_PERMS);
			answerLater(cc);
		} catch (Exception e) {
			fail(cc, Status.ERROR_BATTERY_OPTIMIZATION, e.getMessage());
		}
	}

	/**
	 * On MIUI/HyperOS the AOSP "ignore battery optimizations" dialog is hijacked into an obscure 4-mode picker, and
	 * the Doze whitelist alone does not stop the OS from freezing background work. These devices need their own
	 * "Autostart" + per-app battery screens instead. Lets JS show a plain yes/no popup and route only those devices
	 * to the vendor screens.
	 */
	private boolean isVendorDevice() {
		String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase(Locale.ROOT);
		if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco")) {
			return true;
		}
		return !getSystemProperty("ro.miui.ui.version.name").isEmpty()
				|| !getSystemProperty("ro.mi.os.version.name").isEmpty();
	}

	private boolean isVendorBatteryRestricted() {
		if (!isVendorDevice()) {
			return false;
		}
		return !(Boolean.TRUE.equals(isVendorAutostartAllowed()) && Boolean.TRUE.equals(isVendorBatteryUnrestricted()));
	}

	private void getVendorBackgroundStatus(CallbackContext cc) throws JSONException {
		JSONObject status = new JSONObject();
		boolean vendor = isVendorDevice();
		status.put("vendor", vendor);
		if (vendor) {
			Boolean autostart = isVendorAutostartAllowed();
			Boolean battery = isVendorBatteryUnrestricted();
			status.put("autostartAllowed", autostart == null ? JSONObject.NULL : autostart);
			status.put("batteryUnrestricted", battery == null ? JSONObject.NULL : battery);
		}
		win(cc, status);
	}

	/**
	 * MIUI gates autostart behind a proprietary AppOps op (10008, OP_AUTO_START). No public API: reflection on
	 * checkOpNoThrow, null when the op does not exist (non-MIUI or future removal).
	 */
	private Boolean isVendorAutostartAllowed() {
		try {
			AppOpsManager appOps = (AppOpsManager) getActivity().getSystemService(Context.APP_OPS_SERVICE);
			java.lang.reflect.Method checkOp = AppOpsManager.class.getMethod("checkOpNoThrow", int.class, int.class,
					String.class);
			int mode = (Integer) checkOp.invoke(appOps, 10008, Process.myUid(), getActivity().getPackageName());
			return mode == AppOpsManager.MODE_ALLOWED;
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * MIUI PowerKeeper stores the per-app battery mode in a world-readable provider; bgControl is "noRestrict" when
	 * the user picked "No restrictions". A missing row means the default restricted mode; null when the provider is
	 * absent (HyperOS variants) or refuses the query.
	 */
	private Boolean isVendorBatteryUnrestricted() {
		Uri uri = Uri.parse("content://com.miui.powerkeeper.configure/userTable");
		try (Cursor cursor = getActivity().getContentResolver().query(uri, new String[] { "pkgName", "bgControl" },
				"pkgName = ?", new String[] { getActivity().getPackageName() }, null)) {
			if (cursor == null) {
				return null;
			}
			if (cursor.moveToFirst()) {
				return "noRestrict".equals(cursor.getString(cursor.getColumnIndexOrThrow("bgControl")));
			}
			return false;
		} catch (Exception e) {
			return null;
		}
	}

	private void requestVendorAutostart(CallbackContext cc) {
		if (Boolean.TRUE.equals(isVendorAutostartAllowed())) {
			win(cc, true);
			return;
		}
		ComponentName autostart = new ComponentName("com.miui.securitycenter",
				"com.miui.permcenter.autostart.AutoStartManagementActivity");
		if (startVendorActivity(autostart, cc)) {
			return;
		}
		openAppDetails(cc);
	}

	private void openVendorBatterySettings(CallbackContext cc) {
		if (Boolean.TRUE.equals(isVendorBatteryUnrestricted())) {
			win(cc, true);
			return;
		}
		Intent intent = new Intent();
		intent.setComponent(new ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"));
		intent.putExtra("package_name", getActivity().getPackageName());
		intent.putExtra("package_label", getAppLabel());
		try {
			pendingCallbackContext = cc;
			cordova.startActivityForResult(this, intent, REQUEST_VENDOR_SETTINGS);
			answerLater(cc);
		} catch (Exception e) {
			pendingCallbackContext = null;
			intent.setComponent(null);
			intent.setAction("miui.intent.action.HIDDEN_APPS_CONFIG_ACTIVITY");
			try {
				pendingCallbackContext = cc;
				cordova.startActivityForResult(this, intent, REQUEST_VENDOR_SETTINGS);
				answerLater(cc);
			} catch (Exception e2) {
				pendingCallbackContext = null;
				openAppDetails(cc);
			}
		}
	}

	private boolean startVendorActivity(ComponentName component, CallbackContext cc) {
		Intent intent = new Intent();
		intent.setComponent(component);
		try {
			pendingCallbackContext = cc;
			cordova.startActivityForResult(this, intent, REQUEST_VENDOR_SETTINGS);
			answerLater(cc);
			return true;
		} catch (Exception e) {
			pendingCallbackContext = null;
			return false;
		}
	}

	private void openAppDetails(CallbackContext cc) {
		try {
			Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
			intent.setData(Uri.parse("package:" + getActivity().getPackageName()));
			pendingCallbackContext = cc;
			cordova.startActivityForResult(this, intent, REQUEST_VENDOR_SETTINGS);
			answerLater(cc);
		} catch (Exception e) {
			fail(cc, Status.ERROR_BATTERY_OPTIMIZATION, e.getMessage());
		}
	}

	private String getAppLabel() {
		try {
			PackageManager pm = getActivity().getPackageManager();
			return pm.getApplicationLabel(pm.getApplicationInfo(getActivity().getPackageName(), 0)).toString();
		} catch (Exception e) {
			return "";
		}
	}

	private String getSystemProperty(String key) {
		try {
			@SuppressLint("PrivateApi")
			Class<?> systemProperties = Class.forName("android.os.SystemProperties");
			String value = (String) systemProperties.getMethod("get", String.class).invoke(null, key);
			return value == null ? "" : value;
		} catch (Exception e) {
			return "";
		}
	}

	@Override
	public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		if (requestCode == REQUEST_BATTERY_PERMS) {
			if (pendingCallbackContext != null) {
				win(pendingCallbackContext, resultCode == cordova.getActivity().RESULT_OK);
				pendingCallbackContext = null;
			}
			return;
		}
		if (requestCode == REQUEST_VENDOR_SETTINGS) {
			// Vendor autostart/battery screens always return RESULT_CANCELED and expose no API to read the
			// resulting state, so we can only report that the screen was shown and the user came back.
			if (pendingCallbackContext != null) {
				win(pendingCallbackContext, true);
				pendingCallbackContext = null;
			}
			return;
		}
		// Handle other results if exists.
		super.onActivityResult(requestCode, resultCode, data);
	}

	private void setNotificationLocalizedStrings(JSONArray args) {
		String pedometerIsCounting;
		String stepsToGo;
		String yourProgress;
		String goalReached;

		try {
			JSONObject joStrings = args.getJSONObject(0);
			pedometerIsCounting = joStrings.getString("pedometerIsCounting");
			stepsToGo = joStrings.getString("stepsToGo");
			yourProgress = joStrings.getString("yourProgress");
			goalReached = joStrings.getString("goalReached");
		} catch (JSONException e) {
			e.printStackTrace();
			return;
		}

		SharedPreferences prefs = cordova.getContext().getSharedPreferences("pedometer", Context.MODE_PRIVATE);

		if (pedometerIsCounting != null) {
			prefs.edit().putString(Config.PEDOMETER_IS_COUNTING_TEXT, pedometerIsCounting).apply();
		}
		if (stepsToGo != null) {
			prefs.edit().putString(Config.PEDOMETER_STEPS_TO_GO_FORMAT_TEXT, stepsToGo).apply();
		}
		if (yourProgress != null) {
			prefs.edit().putString(Config.PEDOMETER_YOUR_PROGRESS_FORMAT_TEXT, yourProgress).apply();
		}
		if (goalReached != null) {
			prefs.edit().putString(Config.PEDOMETER_GOAL_REACHED_FORMAT_TEXT, goalReached).apply();
		}

		SensorListener.refreshNotification();
	}

	private void setGoal(JSONArray args) {
		int goal;
		try {
			goal = args.getInt(0);
		} catch (JSONException e) {
			e.printStackTrace();
			return;
		}

		SharedPreferences prefs = cordova.getContext().getSharedPreferences("pedometer", Context.MODE_PRIVATE);
		if (goal >= 0) {
			prefs.edit().putInt(Config.GOAL_PREF_INT, goal).apply();
			SensorListener.refreshNotification();
		}
	}

	private void getStepsByPeriod(JSONArray args, CallbackContext cc) {
		long startdate = 0;
		long endate = 0;
		try {
			startdate = OffsetDateTime.parse(args.getString(0)).toEpochSecond() * 1000;
			endate = OffsetDateTime.parse(args.getString(1)).toEpochSecond() * 1000;
		} catch (JSONException e) {
			e.printStackTrace();
			// execute() already sent answerLater with keepCallback: returning without win or fail would leave the
			// JavaScript promise pending forever.
			fail(cc, 0, e.getMessage());
			return;
		}

		Database db = Database.getInstance(getActivity());
		int steps = db.getSteps(startdate, endate);
		db.close();

		if (startdate <= System.currentTimeMillis() && endate >= System.currentTimeMillis()) {
			int diff = (int) (SensorListener.currentIndex - SensorListener.lastSavedIndex);
			if (SensorListener.lastSavedIndex != -1l && diff > 0 && diff < 10000) {
				steps += diff;
			}
		}

		JSONObject joresult = new JSONObject();
		try {
			joresult.put("steps", steps);
		} catch (JSONException e) {
			e.printStackTrace();
			fail(cc, 0, e.getMessage());
			return;
		}
		win(cc, joresult);
	}

	private void getLastEntries(JSONArray args, CallbackContext cc) {
		int num = 0;
		try {
			num = args.getInt(0);
		} catch (JSONException e) {
			e.printStackTrace();
			// The only one of these reachable from www/stepper.js, which forwards num unvalidated: getLastEntries()
			// called with undefined, null, NaN or a non numeric string lands here.
			fail(cc, 0, e.getMessage());
			return;
		}

		Database db = Database.getInstance(getActivity());
		List<Entry> entries = db.getLastEntries(num);
		db.close();

		JSONObject joresult = new JSONObject();
		try {
			JSONArray jaEntries = new JSONArray();
			for (int i = 0; i < entries.size(); i++) {
				JSONObject joEntry = new JSONObject();
				joEntry.put("data", entries.get(i).startTimestamp);
				joEntry.put("steps", entries.get(i).endIndex - entries.get(i).startIndex);
				joEntry.put("startTimestamp", entries.get(i).startTimestamp);
				joEntry.put("startIndex", entries.get(i).startIndex);
				joEntry.put("endTimestamp", entries.get(i).endTimestamp);
				joEntry.put("endIndex", entries.get(i).endIndex);
				jaEntries.put(joEntry);
			}
			joresult.put("entries", jaEntries);
		} catch (JSONException e) {
			e.printStackTrace();
			fail(cc, 0, e.getMessage());
			return;
		}
		win(cc, joresult);
	}

	public void onStart() {
		Log.i("STEPPER", "StepperPlugin.onStart");
		// TODO : check that SensorListener is running
	}

	public void onPause(boolean multitasking) {
		Log.i("STEPPER", "StepperPlugin.onPause");
		status = Status.PAUSED;
	}

	/**
	 * Called by the Broker when listener is to be shut down. Stop listener.
	 */
	public void onDestroy() {
		Log.i("STEPPER", "StepperPlugin.onDestroy");
		StepperPlugin.updateCallback = null;
	}

	/**
	 * Called when the view navigates.
	 */
	@Override
	public void onReset() {
		Log.i("STEPPER", "StepperPlugin.onReset");
		StepperPlugin.updateCallback = null;
	}

	private boolean skipNotificationPermission(JSONObject options) {
		return options != null && options.optBoolean(Config.SKIP_NOTIFICATION_PERMISSION_BOOL, false);
	}

	/**
	 * POST_NOTIFICATIONS is a single app-wide permission shared with every other notification source in the app, so
	 * an app that already prompts for notifications elsewhere (push registration) can pass skipNotificationPermission
	 * to avoid a second, identical-looking dialog. The service keeps counting either way, its notification is just
	 * not displayed.
	 */
	private void requestPermission(JSONArray args, CallbackContext cc) {
		final JSONObject options = args.optJSONObject(0);
		List<String> perms = new ArrayList<>();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
				&& !cordova.hasPermission(Manifest.permission.ACTIVITY_RECOGNITION)) {
			perms.add(Manifest.permission.ACTIVITY_RECOGNITION);
		}
		if (!skipNotificationPermission(options) && Build.VERSION.SDK_INT >= 33
				&& !cordova.hasPermission("android.permission.POST_NOTIFICATIONS")) {
			perms.add("android.permission.POST_NOTIFICATIONS");
		}

		if (!perms.isEmpty()) {
			pendingCallbackContext = cc;
			cordova.requestPermissions(StepperPlugin.this, REQUEST_MAN_PERMS, perms.toArray(new String[0]));
			answerLater(cc);
		} else {
			win(cc, true);
		}
	}

	private void isStepCountingAvailable(CallbackContext cc) {
		if (((SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE))
				.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null) {
			win(cc, true);
		} else {
			status = Status.ERROR_NO_SENSOR_FOUND;
			win(cc, false);
		}
	}

	// called when the dynamic permissions are asked
	@Override
	public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults)
			throws JSONException {
		if (requestCode == REQUEST_DYN_PERMS || requestCode == REQUEST_MAN_PERMS) {
			CallbackContext cc = pendingCallbackContext;
			pendingCallbackContext = null;
			for (int i = 0; i < grantResults.length; i++) {
				if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
					if ("android.permission.POST_NOTIFICATIONS".equals(permissions[i])) {
						Log.i("STEPPER", "POST_NOTIFICATIONS denied, counting without a visible notification");
						continue;
					}
					String errmsg = "Permission denied ";
					for (String perm : permissions) {
						errmsg += " " + perm;
					}
					this.status = Status.ERROR_NO_PERMISSION;
					if (cc != null) {
						fail(cc, Status.ERROR_NO_PERMISSION, "Permission denied: " + permissions[i]);
					}
					return;
				}
			}
			// all dynamic permissions accepted!
			Log.i("STEPPER", "Dynamic permissions accepted");
			if (requestCode == REQUEST_MAN_PERMS) {
				if (cc != null) {
					win(cc, true);
				}
			} else {
				// dynamic permissions granted, continue starting service
				start();
			}
		}
	}

	private void start(JSONArray args, CallbackContext cc) throws JSONException {
		final JSONObject options = args.getJSONObject(0);

		SharedPreferences prefs = getActivity().getSharedPreferences("pedometer", Context.MODE_PRIVATE);

		// If already starting or running, then return
		if ((status == Status.RUNNING) || (status == Status.STARTING)) {
			prefs.edit().putBoolean("enabled", true).commit();
			return;
		}

		if (options.has(Config.TIMEZONE)) {
			prefs.edit().putString(Config.TIMEZONE, options.getString(Config.TIMEZONE)).commit();
		}

		// Set options
		if (options.has(Config.PEDOMETER_GOAL_REACHED_FORMAT_TEXT)) {
			prefs.edit().putString(Config.PEDOMETER_GOAL_REACHED_FORMAT_TEXT,
					options.getString(Config.PEDOMETER_GOAL_REACHED_FORMAT_TEXT)).commit();
		}

		if (options.has(Config.PEDOMETER_IS_COUNTING_TEXT)) {
			prefs.edit()
					.putString(Config.PEDOMETER_IS_COUNTING_TEXT, options.getString(Config.PEDOMETER_IS_COUNTING_TEXT))
					.commit();
		}

		if (options.has(Config.PEDOMETER_STEPS_TO_GO_FORMAT_TEXT)) {
			prefs.edit().putString(Config.PEDOMETER_STEPS_TO_GO_FORMAT_TEXT,
					options.getString(Config.PEDOMETER_STEPS_TO_GO_FORMAT_TEXT)).commit();
		}

		if (options.has(Config.PEDOMETER_YOUR_PROGRESS_FORMAT_TEXT)) {
			prefs.edit().putString(Config.PEDOMETER_YOUR_PROGRESS_FORMAT_TEXT,
					options.getString(Config.PEDOMETER_YOUR_PROGRESS_FORMAT_TEXT)).commit();
		}

		// has() rather than a plain optInt default: omitting the option must keep a goal previously set through
		// setGoal, while passing 0 explicitly means "no goal".
		if (options.has(Config.GOAL_OPTION_INT)) {
			int goal = options.optInt(Config.GOAL_OPTION_INT, -1);
			if (goal >= 0) {
				prefs.edit().putInt(Config.GOAL_PREF_INT, goal).apply();
			}
		}

		List<String> perms = new ArrayList<>();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
				&& !cordova.hasPermission(Manifest.permission.ACTIVITY_RECOGNITION)) {
			perms.add(Manifest.permission.ACTIVITY_RECOGNITION);
		}
		if (!skipNotificationPermission(options) && Build.VERSION.SDK_INT >= 33
				&& !cordova.hasPermission("android.permission.POST_NOTIFICATIONS")) {
			perms.add("android.permission.POST_NOTIFICATIONS");
		}

		if (!perms.isEmpty()) {
			// dynamic permissions required - keep callback until result
			pendingCallbackContext = cc;
			cordova.requestPermissions(this, REQUEST_DYN_PERMS, perms.toArray(new String[0]));
			answerLater(cc);
			return;
		}

		start();
	}

	private void start() {
		Log.i("STEPPER", "StepperPlugin.start");
		SharedPreferences prefs = getActivity().getSharedPreferences("pedometer", Context.MODE_PRIVATE);
		prefs.edit().putBoolean("enabled", true).commit();
		if (Build.VERSION.SDK_INT >= 26) {
			API26Wrapper.startForegroundService(getActivity(), new Intent(getActivity(), SensorListener.class));
		} else {
			getActivity().startService(new Intent(getActivity(), SensorListener.class));
		}
	}

	private void stop(JSONArray args, CallbackContext cc) {
		Log.i("STEPPER", "StepperPlugin.stop");
		boolean clearDatabase = false;
		try {
			clearDatabase = args.getBoolean(0);
		} catch (JSONException e) {
			e.printStackTrace();
			fail(cc, 0, e.getMessage());
			return;
		}

		SharedPreferences prefs = getActivity().getSharedPreferences("pedometer", Context.MODE_PRIVATE);
		prefs.edit().putBoolean("enabled", false).commit();

		if (clearDatabase) {
			Database db = Database.getInstance(getActivity());
			db.clear();
			db.close();
			SensorListener.onDatabaseCleared();
		}

		getActivity().stopService(new Intent(getActivity(), SensorListener.class));
		status = Status.STOPPED;

		win(cc);
	}

	public static void updateUI(int todaySteps) {
		Log.v("STEPPER",
				"StepperPlugin.updateUI updateCallback=" + (updateCallback != null) + " todaySteps=" + todaySteps);
		if (updateCallback != null) {
			JSONObject result = new JSONObject();
			try {
				result.put("steps_today", todaySteps);
			} catch (JSONException e) {
				e.printStackTrace();
			}

			PluginResult r = new PluginResult(PluginResult.Status.OK, result);
			r.setKeepCallback(true);
			updateCallback.sendPluginResult(r);
		}
	}

	/* helpers which take an explicit context rather than using a shared field */
	private void answerLater(CallbackContext cc) {
		PluginResult r = new PluginResult(PluginResult.Status.NO_RESULT);
		r.setKeepCallback(true);
		cc.sendPluginResult(r);
	}

	private void win(CallbackContext cc, JSONObject message) {
		PluginResult result;
		if (message != null) {
			result = new PluginResult(PluginResult.Status.OK, message);
		} else {
			result = new PluginResult(PluginResult.Status.OK);
		}
		cc.sendPluginResult(result);
	}

	private void win(CallbackContext cc, boolean success) {
		PluginResult result = new PluginResult(PluginResult.Status.OK, success);
		cc.sendPluginResult(result);
	}

	private void win(CallbackContext cc) {
		PluginResult result = new PluginResult(PluginResult.Status.OK);
		cc.sendPluginResult(result);
	}

	private void fail(CallbackContext cc, int code, String message) {
		// Error object
		JSONObject errorObj = new JSONObject();
		try {
			errorObj.put("code", code);
			errorObj.put("message", message);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		PluginResult err = new PluginResult(PluginResult.Status.ERROR, errorObj);
		cc.sendPluginResult(err);
	}

	private Activity getActivity() {
		return cordova.getActivity();
	}
}
