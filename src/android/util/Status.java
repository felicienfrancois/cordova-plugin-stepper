package org.apache.cordova.stepper.util;

/**
 * Codes sent to JavaScript in the error object ({ code, message }), and used for the internal plugin state.
 * <p/>
 * They are a public API contract: never renumber a code that is already emitted, or apps checking for it will
 * silently misclassify errors.
 */
public class Status {

  public static int STOPPED = 0;
  public static int STARTING = 1;
  public static int RUNNING = 2;
  public static int ERROR_NO_PERMISSION = 3;
  public static int ERROR_NO_SENSOR_FOUND = 4;
  public static int PAUSED = 5;
  public static int ERROR_BATTERY_OPTIMIZATION = 6;
  // Was 3, colliding with ERROR_NO_PERMISSION. Moved rather than the other way around because it is never emitted,
  // while code 3 is already in the wild meaning "permission denied".
  public static int ERROR_FAILED_TO_START = 7;

}
