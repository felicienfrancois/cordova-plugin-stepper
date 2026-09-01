package org.apache.cordova.stepper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import org.apache.cordova.stepper.util.API26Wrapper;

public class BootReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(final Context context, final Intent intent) {
		SharedPreferences prefs = context.getSharedPreferences("pedometer", Context.MODE_PRIVATE);
		Log.i("STEPPER", "BootReceiver.onReceive enabled:" + prefs.getBoolean("enabled", false) + ", intent_action="
				+ intent.getAction());
		if (!prefs.getBoolean("enabled", false)) {
			return;
		}
		// No action check: the manifest filter declares BOOT_COMPLETED and QUICKBOOT_POWERON, and both mean the same
		// thing here. Testing only for BOOT_COMPLETED left the declared quickboot action dead, so on ROMs that send
		// it instead the service never came back after a reboot - and it also dereferenced a nullable getAction().
		if (Build.VERSION.SDK_INT >= 26) {
			API26Wrapper.startForegroundService(context, new Intent(context, SensorListener.class));
		} else {
			context.startService(new Intent(context, SensorListener.class));
		}
	}
}
