package tw.junba.reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.content.ContentUris;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/**
 * Opt-in Android AlarmManager schedules derived from authorized local calendars.
 * These are NOT vendor Clock entries and do not promise vivo snooze capabilities.
 * Only explicit local reminder minutes (not ambiguous "default") are mirrored.
 */
public final class CalendarMirrorStore {
    private static final String PREF="junba_calendar_mirror";
    private static final String KEY="entries";
    private static final int MAX_ALARMS=250;
    private static final long DAY=86400000L;
    private CalendarMirrorStore(){}

    private static JSONArray read(Context c){
        try{return new JSONArray(c.getSharedPreferences(PREF,0).getString(KEY,"[]"));}
        catch(Exception ignored){return new JSONArray();}
    }
    private static void save(Context c,JSONArray list){
        c.getSharedPreferences(PREF,0).edit().putString(KEY,list.toString()).apply();
    }
    static int count(Context c){return read(c).length();}
    private static PendingIntent pending(Context c,JSONObject item,int flags){
        int id=item.optInt("id",1);
        Intent i=new Intent(c,AlarmReceiver.class).setAction("tw.junba.reminder.CALENDAR_REMINDER");
        i.putExtra("title",item.optString("title","日曆提醒"));i.putExtra("id",id);
        return PendingIntent.getBroadcast(c,id,i,flags | PendingIntent.FLAG_IMMUTABLE);
    }
    private static void cancel(Context c,JSONObject item){
        PendingIntent p=pending(c,item,PendingIntent.FLAG_NO_CREATE);
        if(p!=null){((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(p);p.cancel();}
    }
    private static boolean schedule(Context c,JSONObject item){
        long t=item.optLong("time",0);
        if(t<=System.currentTimeMillis() || !ReminderStore.canExact(c))return false;
        try{
            AlarmManager manager=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,t,
                pending(c,item,PendingIntent.FLAG_UPDATE_CURRENT));
            return true;
        }catch(SecurityException ex){return false;}
    }
    static int reschedule(Context c){
        if(!c.getSharedPreferences("MainActivity",0).getBoolean("calendar_mirror_enabled",false))return 0;
        JSONArray items=read(c);int n=0;
        for(int i=0;i<items.length();i++){
            JSONObject item=items.optJSONObject(i);if(item!=null&&schedule(c,item))n++;
        }
        return n;
    }
    static void clear(Context c){
        JSONArray items=read(c);for(int i=0;i<items.length();i++){
            JSONObject item=items.optJSONObject(i);if(item!=null)cancel(c,item);
        }
        save(c,new JSONArray());
    }
    /** Called on app launch / manual sync only; never changes Google Calendar records. */
    static int refresh(Context c,ArrayList<Long> selectedCalendars){
        if(!c.getSharedPreferences("MainActivity",0).getBoolean("calendar_mirror_enabled",false))return 0;
        if(selectedCalendars.isEmpty()){clear(c);return 0;}
        long now=System.currentTimeMillis();long horizon=now+45L*DAY;
        LinkedHashMap<String,JSONObject> old=new LinkedHashMap<>();
        JSONArray previous=read(c);
        int nextId=c.getSharedPreferences(PREF,0).getInt("nextId",1000000);
        for(int i=0;i<previous.length();i++){
            JSONObject entry=previous.optJSONObject(i);if(entry!=null)old.put(entry.optString("key"),entry);
        }
        LinkedHashMap<String,JSONObject> upcoming=new LinkedHashMap<>();
        HashMap<Long,ArrayList<Integer>> remindersCache=new HashMap<>();
        boolean queryOk=false;
        try{
            Uri.Builder uri=CalendarContract.Instances.CONTENT_URI.buildUpon();
            ContentUris.appendId(uri,now);ContentUris.appendId(uri,horizon+6L*DAY);
            String[] cols={CalendarContract.Instances.EVENT_ID,CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.TITLE,CalendarContract.Instances.CALENDAR_ID};
            try(Cursor cursor=c.getContentResolver().query(uri.build(),cols,null,null,
                    CalendarContract.Instances.BEGIN+" ASC")){
                if(cursor==null)return count(c);
                queryOk=true;int events=0;
                while(cursor.moveToNext()&&events++<1500&&upcoming.size()<MAX_ALARMS){
                    long eventId=cursor.getLong(0),begin=cursor.getLong(1),calendar=cursor.getLong(3);
                    if(!selectedCalendars.contains(calendar))continue;
                    String title=cursor.getString(2);
                    ArrayList<Integer> minutes=remindersCache.get(eventId);
                    if(minutes==null){
                        TreeSet<Integer> unique=new TreeSet<>();
                        try(Cursor rem=c.getContentResolver().query(CalendarContract.Reminders.CONTENT_URI,
                            new String[]{CalendarContract.Reminders.MINUTES,CalendarContract.Reminders.METHOD},
                            CalendarContract.Reminders.EVENT_ID+"=?",new String[]{String.valueOf(eventId)},null)){
                            if(rem!=null)while(rem.moveToNext()){
                                int m=rem.getInt(0),method=rem.getInt(1);
                                if(m>=0 && (method==CalendarContract.Reminders.METHOD_DEFAULT ||
                                    method==CalendarContract.Reminders.METHOD_ALERT ||
                                    method==CalendarContract.Reminders.METHOD_ALARM))unique.add(m);
                            }
                        }catch(Exception ignored){}
                        minutes=new ArrayList<>(unique);remindersCache.put(eventId,minutes);
                    }
                    for(int minute:minutes){
                        long trigger=begin-minute*60000L;
                        if(trigger<=now+3000L||trigger>horizon)continue;
                        String key="cal:"+eventId+":"+begin+":"+minute;
                        JSONObject prior=old.get(key);
                        JSONObject obj=new JSONObject();
                        obj.put("key",key);obj.put("id",prior!=null?prior.optInt("id"):nextId++);
                        obj.put("title","[日曆] "+(title==null?"未命名活動":title)+"（提前 "+minute+" 分鐘）");
                        obj.put("time",trigger);
                        upcoming.put(key,obj);
                        if(upcoming.size()>=MAX_ALARMS)break;
                    }
                }
            }
        }catch(Exception ignored){return count(c);}
        if(!queryOk)return count(c);
        JSONArray updated=new JSONArray();int scheduled=0;
        for(Map.Entry<String,JSONObject> p:old.entrySet()){
            if(!upcoming.containsKey(p.getKey()))cancel(c,p.getValue());
        }
        for(Map.Entry<String,JSONObject> entry:upcoming.entrySet()){
            JSONObject obj=entry.getValue();updated.put(obj);
            // A newly imported, modified, or rebooted reminder must be explicitly scheduled.
            if(!old.containsKey(entry.getKey()))schedule(c,obj);
            if(ReminderStore.canExact(c))scheduled++;
        }
        save(c,updated);
        c.getSharedPreferences(PREF,0).edit().putInt("nextId",nextId).apply();
        return scheduled;
    }
}
