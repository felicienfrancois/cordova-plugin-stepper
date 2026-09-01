package org.apache.cordova.stepper;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import org.apache.cordova.stepper.util.API23Wrapper;
import org.apache.cordova.stepper.util.API26Wrapper;
import org.apache.cordova.stepper.util.Util;
import org.apache.cordova.stepper.util.Config;
import org.apache.cordova.stepper.util.Entry;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.TimeZone;
import java.util.List;

/**
 * Background service which keeps the step-sensor listener alive to always get
 * the number of steps since boot.
 * <p/>
 * This service won't be needed any more if there is a way to read the
 * step-value without waiting for a sensor event
 */
public class SensorListener extends Service implements SensorEventListener {

	public final static int NOTIFICATION_ID = 1;
	private final static long SAVE_OFFSET_TIME_MS = 300000;
	private final static int SAVE_OFFSET_STEPS = 30;
	// Kept at the value historically used by the periodic alarm, so an alarm armed by a previous version is replaced
	// rather than left running alongside the new one.
	private final static int ALARM_REQUEST_CODE = 2;

	private static TimeZone timeZone = TimeZone.getDefault();

	private static int todaySavedSteps;
	public static long currentIndex;
	public static long lastSavedIndex = -1l;
	private static long lastSaveTime;

	private static int notificationIconId = 0;
	// Both rebuilt from scratch on every notification update before: the PendingIntent costs a PackageManager intent
	// resolution plus an ActivityManager round trip, and the NumberFormat two or three allocations, up to 30 times a
	// minute while walking. Neither ever changes, apart from the formatter on a system locale change.
	private static PendingIntent contentIntent;
	private static NumberFormat numberFormat;
	private static Locale numberFormatLocale;

	// Running instance, so the plugin can redraw the notification without going through onStartCommand. Cleared in
	// onDestroy, and dies with the process when the service is killed outright.
	private static SensorListener instance;

	private final BroadcastReceiver shutdownReceiver = new ShutdownReceiver();
	private boolean shutdownReceiverRegistered = false;
	private boolean foregroundStarted = false;

	@Override
	public void onAccuracyChanged(final Sensor sensor, int accuracy) {
		Log.d("STEPPER", "SensorListener.onAccuracyChanged " + accuracy);
	}

	private long lastUpdateTime = 0;

	@Override
	public void onSensorChanged(final SensorEvent event) {
		Log.v("STEPPER", "SensorListener.onSensorChanged " + event.values[0]);
		currentIndex = (long) event.values[0];
		long now = System.currentTimeMillis();

		boolean dayChanged = !Util.isSameDay(now, lastSaveTime, timeZone);
		boolean limitsReached = (currentIndex > lastSavedIndex + SAVE_OFFSET_STEPS)
				|| (currentIndex > 0 && now > lastSaveTime + SAVE_OFFSET_TIME_MS);

		if (dayChanged || limitsReached) {
			if (dayChanged)
				todaySavedSteps = 0;
			try {
				StepperPlugin.updateUI(todaySteps());
				updateNotification();
			} finally {
				saveCurrentIndex(getApplicationContext());
			}
			lastUpdateTime = now;
		} else if (now - lastUpdateTime > 2000) { // Debouncer: max update every 2 seconds
			StepperPlugin.updateUI(todaySteps());
			updateNotification();
			lastUpdateTime = now;
		}
	}

	private int todaySteps() {
		int steps = todaySavedSteps;
		int diff = (int) (currentIndex - lastSavedIndex);
		if (lastSavedIndex != -1l && diff > 0 && diff < 10000) {
			steps += diff;
		}
		return steps;
	}

	private void registerBroadcastReceiver() {
		if (shutdownReceiverRegistered) {
			// onStartCommand runs again on every alarm tick and every app reopen, on the same service instance.
			// Registering again stacks another IntentFilter on the same receiver, and ShutdownReceiver would then
			// fire - and save the index - once per stacked filter.
			return;
		}
		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_SHUTDOWN);
		registerReceiver(shutdownReceiver, filter);
		shutdownReceiverRegistered = true;
	}

	/**
	 * To be called right after the database is wiped. The tracking fields are process-wide statics that outlive the
	 * service, so without this they would keep pointing at entries that no longer exist: saveCurrentIndex would take
	 * the updateLatestEntry branch, which no-ops on an empty table, and nothing would be persisted until the next
	 * hour boundary.
	 * <p/>
	 * currentIndex is deliberately left alone. stopService is asynchronous, so onDestroy - and its saveCurrentIndex
	 * call - runs after this; keeping the last hardware reading makes that save seed a correct zero-step baseline
	 * entry at the current index, instead of one at index 0 that the next sensor reading would see as a huge jump.
	 */
	public static void onDatabaseCleared() {
		Log.i("STEPPER", "SensorListener.onDatabaseCleared");
		todaySavedSteps = 0;
		lastSavedIndex = -1l;
		lastSaveTime = 0;
	}

	public static void saveCurrentIndex(Context context) {
		long currentTime = System.currentTimeMillis();
		if (!Util.isSameDay(currentTime, lastSaveTime, timeZone)) {
			todaySavedSteps = 0;
		}
		Log.i("STEPPER", "SensorListener.saveCurrentIndex lastSavedIndex=" + lastSavedIndex + ", lastSaveTime="
				+ lastSaveTime + ", currentIndex=" + currentIndex + ", currentTime=" + currentTime);
		if (lastSaveTime > currentTime) {
			Log.e("STEPPER", "lastSaveTime > currentTime : " + lastSaveTime + " > " + currentTime);
			return;
		}
		Database db = Database.getInstance(context);
		if (currentTime - lastSaveTime >= 3 * 24 * 3600 * 1000) {
			Log.i("STEPPER", "Last save was long time ago");
			db.createNewEntry(currentTime, currentIndex);
		} else if (currentIndex < lastSavedIndex || (currentIndex - lastSavedIndex > 1000
				&& (currentIndex - lastSavedIndex) * 60000 / (currentTime - lastSaveTime) >= 500)) {
			// index jump detected
			Log.i("STEPPER", "Index jump detected");
			db.createNewEntry(currentTime, currentIndex);
		} else {
			db.updateLatestEntry(currentTime, currentIndex);
			if (!Util.isSameHour(currentTime, lastSaveTime, timeZone)) {
				db.createNewEntry(currentTime, currentIndex);
			}
			todaySavedSteps += currentIndex - lastSavedIndex;
		}
		db.close();
		lastSavedIndex = currentIndex;
		lastSaveTime = currentTime;
	}

	/**
	 * Asserts the foreground service state and posts the notification. Called from onStartCommand, which has to reach
	 * startForeground within a few seconds of startForegroundService.
	 */
	private void showNotification() {
		if (getSharedPreferences("pedometer", Context.MODE_PRIVATE).getBoolean("notification", true)) {
			if (Build.VERSION.SDK_INT >= 34) {
				startForeground(NOTIFICATION_ID, getNotification(), 256); // 256 = FOREGROUND_SERVICE_TYPE_HEALTH
			} else if (Build.VERSION.SDK_INT >= 26) {
				startForeground(NOTIFICATION_ID, getNotification());
			} else {
				((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID,
						getNotification());
			}
			foregroundStarted = true;
		}
	}

	/**
	 * Redraws the ongoing notification. notify() with the same id is the documented way to update a foreground
	 * service notification, and unlike another startForeground it does not go back through ActivityManager to
	 * re-assert a state the service already holds. Same notification, same content, one binder call instead of two.
	 */
	private void updateNotification() {
		if (!foregroundStarted) {
			// no notification posted yet: nothing to redraw, and the service still owes startForeground
			showNotification();
			return;
		}
		if (getSharedPreferences("pedometer", Context.MODE_PRIVATE).getBoolean("notification", true)) {
			((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID,
					getNotification());
		}
	}

	@Override
	public IBinder onBind(final Intent intent) {
		return null;
	}

	@Override
	public int onStartCommand(final Intent intent, int flags, int startId) {
		Log.i("STEPPER", "SensorListener.onStartCommand");

		SharedPreferences prefs = getSharedPreferences("pedometer", Context.MODE_PRIVATE);
		String timeZoneString = prefs.getString(Config.TIMEZONE, null);
		if (timeZoneString != null)
			timeZone = TimeZone.getTimeZone(timeZoneString);

		reRegisterSensor();
		registerBroadcastReceiver();
		showNotification();

		// Load history from db
		Database db = Database.getInstance(getApplicationContext());
		todaySavedSteps = db.getSteps(Util.getToday(timeZone), System.currentTimeMillis());
		if (lastSavedIndex == -1l) {
			// Fresh process only. onStartCommand also runs on every alarm tick and every app reopen, where the
			// in-memory index is ahead of the database by up to SAVE_OFFSET_STEPS steps / SAVE_OFFSET_TIME_MS:
			// reloading would rewind currentIndex to the last saved value, and the notification and the UI would show
			// a stale count until the next sensor event.
			List<Entry> lastEntry = db.getLastEntries(1);
			if (!lastEntry.isEmpty()) {
				currentIndex = lastSavedIndex = lastEntry.get(0).endIndex;
				lastSaveTime = lastEntry.get(0).endTimestamp;
			}
		}
		db.close();
		Log.d("STEPPER", "Loaded history from db todaySavedSteps=" + todaySavedSteps + ", lastSaveTime=" + lastSaveTime
				+ ", lastSavedIndex=" + lastSavedIndex);

		// restart service every fifteen minutes to save the current step count
		long nextUpdate = Math.min(Util.getNextHour(timeZone),
				System.currentTimeMillis() + AlarmManager.INTERVAL_FIFTEEN_MINUTES);
		scheduleStart(nextUpdate);

		return START_STICKY;
	}

	/**
	 * Single request code on purpose. AlarmManager.set* cancels any alarm already scheduled for an equal
	 * IntentSender, and two PendingIntents are equal when the request code, the target and Intent.filterEquals all
	 * match - so reusing the code replaces the pending alarm rather than adding a second one. Using a distinct code
	 * per caller used to leave the periodic alarm and the onTaskRemoved one both armed, delivering two
	 * onStartCommand calls after a swipe away.
	 */
	private void scheduleStart(long timestamp) {
		AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		PendingIntent pi = PendingIntent.getService(this, ALARM_REQUEST_CODE, new Intent(this, SensorListener.class),
				PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
		// RTC_WAKEUP, not RTC: the alarm cuts a new hourly database entry at the next hour boundary, so it has to
		// fire on time even while the device sleeps. A non-wakeup alarm waits for the next natural wake-up, which
		// overnight means the midnight cut is missed and evening steps land in the same entry as morning ones.
		if (Build.VERSION.SDK_INT >= 23) {
			API23Wrapper.setAlarmWhileIdle(am, AlarmManager.RTC_WAKEUP, timestamp, pi);
		} else {
			am.set(AlarmManager.RTC_WAKEUP, timestamp, pi);
		}
	}

	@Override
	public void onCreate() {
		Log.i("STEPPER", "SensorListener.onCreate");
		super.onCreate();
		instance = this;
	}

	/**
	 * Redraws the ongoing notification from the current preferences. The goal and the localized strings only reach
	 * the notification through SharedPreferences, so without this a change would stay invisible until the next
	 * sensor event - which never comes while the user stands still. No-op when the service is not running.
	 */
	public static void refreshNotification() {
		final SensorListener service = instance;
		if (service == null) {
			return;
		}
		// Callers run on the Cordova thread pool. Hop to the main thread, where every other showNotification call
		// and every mutation of the step counters already happens.
		new Handler(service.getMainLooper()).post(new Runnable() {
			public void run() {
				try {
					service.updateNotification();
				} catch (Exception e) {
					Log.e("STEPPER", "refreshNotification FAILED", e);
				}
			}
		});
	}

	private int getNotificationIconId() {
		int drawableId = getResources().getIdentifier("ic_footsteps_silhouette_variant", "drawable",
				getApplicationInfo().packageName);
		if (drawableId == 0) {
			drawableId = getApplicationInfo().icon;
		}
		return drawableId;
	}

	@Override
	public void onTaskRemoved(final Intent rootIntent) {
		Log.i("STEPPER", "SensorListener.onTaskRemoved");
		// Restart service in 2000 ms
		try {
			scheduleStart(System.currentTimeMillis() + 2000);
		} catch (Exception e) {
			Log.e("STEPPER", "scheduleStart FAILED");
		}
		saveCurrentIndex(getApplicationContext());
		super.onTaskRemoved(rootIntent);
	}

	@Override
	public void onDestroy() {
		Log.i("STEPPER", "SensorListener.onDestroy");
		// first, so a concurrent refreshNotification cannot target a service being torn down
		instance = null;
		saveCurrentIndex(getApplicationContext());
		super.onDestroy();
		try {
			SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
			sm.unregisterListener(this);
		} catch (Exception e) {
			e.printStackTrace();
		}
		// separate try block: a failure above must not leave the receiver registered
		try {
			if (shutdownReceiverRegistered) {
				unregisterReceiver(shutdownReceiver);
				shutdownReceiverRegistered = false;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * NumberFormat is not thread safe, and neither is this lazy init. Both are fine because every getNotification
	 * call reaches here on the main thread: onStartCommand, onSensorChanged, and refreshNotification which posts to
	 * the main looper.
	 */
	private static NumberFormat numberFormat() {
		Locale locale = Locale.getDefault();
		if (numberFormat == null || !locale.equals(numberFormatLocale)) {
			numberFormat = NumberFormat.getInstance(locale);
			numberFormatLocale = locale;
		}
		return numberFormat;
	}

	public Notification getNotification() {
		SharedPreferences prefs = getSharedPreferences("pedometer", Context.MODE_PRIVATE);
		int goal = prefs.getInt(Config.GOAL_PREF_INT, Config.DEFAULT_GOAL);
		Notification.Builder notificationBuilder = Build.VERSION.SDK_INT >= 26
				? API26Wrapper.getNotificationBuilder(getApplicationContext())
				: new Notification.Builder(getApplicationContext());
		int todaySteps = todaySteps();
		NumberFormat format = numberFormat();
		if (todaySteps > 0) {
			notificationBuilder.setProgress(goal, todaySteps, false).setContentText(todaySteps >= Math.max(goal, 1)
					? String.format(prefs.getString(Config.PEDOMETER_GOAL_REACHED_FORMAT_TEXT, "%s steps today"),
							format.format(todaySteps),
							format.format(goal))
					: String.format(prefs.getString(Config.PEDOMETER_STEPS_TO_GO_FORMAT_TEXT, "%s steps to go"),
							format.format(goal - todaySteps),
							format.format(todaySteps),
							format.format(goal)));
		} else { // still no step value?
			notificationBuilder.setContentText(prefs.getString(Config.PEDOMETER_YOUR_PROGRESS_FORMAT_TEXT,
					"Your progress will be shown here soon"));
		}

		if (contentIntent == null) {
			PackageManager packageManager = getPackageManager();
			Intent launchIntent = packageManager.getLaunchIntentForPackage(getPackageName());
			contentIntent = PendingIntent.getActivity(this, 0, launchIntent,
					PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
		}

		if (notificationIconId == 0) {
			notificationIconId = getNotificationIconId();
		}

		notificationBuilder.setPriority(Notification.PRIORITY_DEFAULT).setShowWhen(false)
				.setContentTitle(prefs.getString(Config.PEDOMETER_IS_COUNTING_TEXT, "Pedometer is counting"))
				.setContentIntent(contentIntent).setSmallIcon(notificationIconId).setOngoing(true);
		return notificationBuilder.build();
	}

	private void reRegisterSensor() {
		SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
		try {
			sm.unregisterListener(this);
		} catch (Exception e) {
			e.printStackTrace();
		}

		// No batching: this 3 argument overload leaves maxReportLatencyUs at 0, so events are delivered as they are
		// measured. That is deliberate - the notification is a user facing counter that has to stay fresh even while
		// the app is in the background, and batching would freeze it for the whole latency window.
		// getDefaultSensor returns the non wake-up variant, so this does not pull the SoC out of suspend, and
		// TYPE_STEP_COUNTER is cumulative since boot, so no step is ever lost while the AP sleeps.
		sm.registerListener(this, sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER), SensorManager.SENSOR_DELAY_GAME);
	}
}
