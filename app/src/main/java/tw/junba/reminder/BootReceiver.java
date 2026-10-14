package tw.junba.reminder;
import android.content.*;
/** Restores only App-owned schedules, never asserts control of stock vivo alarms. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        String action=intent==null?"":intent.getAction();
        if(Intent.ACTION_BOOT_COMPLETED.equals(action)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
            ||Intent.ACTION_TIME_CHANGED.equals(action)||Intent.ACTION_TIMEZONE_CHANGED.equals(action)){
            AlarmReceiver.ensureChannel(context);
            ReminderStore.reschedule(context);
            CalendarMirrorStore.reschedule(context);
        }
    }
}
