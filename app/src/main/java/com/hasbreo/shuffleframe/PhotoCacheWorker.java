package com.hasbreo.shuffleframe;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.*;
import org.json.*;
import java.util.*;

public class PhotoCacheWorker extends Worker {
    static final String NAME="shuffleframe-photo-downloads";
    static final String PREFS="shuffleframe";
    public PhotoCacheWorker(@NonNull Context context,@NonNull WorkerParameters params){super(context,params);}
    static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    static LinkedHashSet<String> urls(Context c){
        LinkedHashSet<String> list=new LinkedHashSet<>();
        try{
            JSONArray albums=new JSONArray(prefs(c).getString("albums","[]"));
            for(int i=0;i<albums.length();i++){
                JSONArray a=albums.getJSONObject(i).optJSONArray("photos");
                if(a!=null)for(int j=0;j<a.length();j++) {
                    String u=a.optString(j);if(!u.isEmpty())list.add(u);
                }
            }
        }catch(Exception ignored){}
        return list;
    }
    static void schedule(Context c){
        LinkedHashSet<String> all=urls(c);
        if(all.isEmpty()){WorkManager.getInstance(c).cancelUniqueWork(NAME);return;}
        boolean missing=false;
        for(String u:all)if(!PhotoStorage.cached(c,u)){missing=true;break;}
        if(!missing)return;
        Constraints constraints=new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresStorageNotLow(true)
            .build();
        OneTimeWorkRequest request=new OneTimeWorkRequest.Builder(PhotoCacheWorker.class)
            .setConstraints(constraints)
            .build();
        WorkManager.getInstance(c).enqueueUniqueWork(NAME,ExistingWorkPolicy.REPLACE,request);
    }
    @NonNull @Override public Result doWork(){
        Context c=getApplicationContext();
        LinkedHashSet<String> all=urls(c);
        int done=0,failed=0;
        for(String u:all){
            if(isStopped())return Result.retry();
            try {PhotoStorage.ensure(c,u);done++;}
            catch(Exception ignored){failed++;}
            if((done+failed)%4==0)
                setProgressAsync(new Data.Builder().putInt("downloaded",done).putInt("total",all.size()).build());
        }
        PhotoStorage.prune(c,all);
        // Failed items are picked up on the next refresh or app launch; no infinite retry loop.
        return Result.success(new Data.Builder().putInt("downloaded",done).putInt("failed",failed).build());
    }
}
