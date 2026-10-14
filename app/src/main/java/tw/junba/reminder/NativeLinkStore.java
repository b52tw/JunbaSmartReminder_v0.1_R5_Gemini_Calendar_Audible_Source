package tw.junba.reminder;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** A local audit log of requests to vivo Clock; NOT a readable list of vivo's alarms. */
public final class NativeLinkStore {
    private NativeLinkStore() {}
    static JSONArray all(Context c){
        try{return new JSONArray(c.getSharedPreferences("junba",0).getString("native_links","[]"));}
        catch(Exception e){return new JSONArray();}
    }
    static void save(Context c,JSONArray a){c.getSharedPreferences("junba",0).edit().putString("native_links",a.toString()).apply();}
    static boolean has(Context c,String key){return find(c,key)!=null;}
    static JSONObject find(Context c,String key){
        JSONArray a=all(c);for(int i=0;i<a.length();i++){
            JSONObject o=a.optJSONObject(i);if(o!=null&&key.equals(o.optString("key")))return o;
        }
        return null;
    }
    static void requested(Context c,String key,String source,String title,long when){
        JSONArray a=all(c),b=new JSONArray();
        for(int i=0;i<a.length();i++){
            JSONObject o=a.optJSONObject(i);if(o!=null&&!key.equals(o.optString("key")))b.put(o);
        }
        try{
            JSONObject n=new JSONObject();n.put("key",key);n.put("source",source);
            n.put("title",title);n.put("when",when);n.put("requested",System.currentTimeMillis());
            n.put("status","pending");b.put(n);
        }catch(JSONException ignored){}
        save(c,b);
    }
    static void confirmed(Context c,String key){
        JSONArray a=all(c);for(int i=0;i<a.length();i++){
            JSONObject o=a.optJSONObject(i);if(o!=null&&key.equals(o.optString("key"))){
                try{o.put("status","manual-confirmed");o.put("confirmed",System.currentTimeMillis());}catch(JSONException ignored){}
            }
        }
        save(c,a);
    }
    static void unconfirm(Context c,String key){
        JSONArray a=all(c);for(int i=0;i<a.length();i++){
            JSONObject o=a.optJSONObject(i);if(o!=null&&key.equals(o.optString("key"))){
                try{o.put("status","pending");}catch(JSONException ignored){}
            }
        }
        save(c,a);
    }
    static String status(Context c,String key){
        JSONObject o=find(c,key);if(o==null)return "尚未連動";
        return "manual-confirmed".equals(o.optString("status"))?"藍點・本人已核對":"紅點・等待原廠核對";
    }
}
