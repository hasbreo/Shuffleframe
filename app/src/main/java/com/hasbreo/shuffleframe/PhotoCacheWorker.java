package com.hasbreo.shuffleframe;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class PhotoCacheWorker extends Worker {
    static final String NAME="shuffleframe-photo-downloads";
    static final String PREFS="shuffleframe";
    public PhotoCacheWorker(@NonNull Context context,@NonNull WorkerParameters params){super(context,params);}
    static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    static LinkedHashSet<String> urls(Context c){
        // Round-robin: mix albums so each album progresses, instead of finishing album 1 first.
        LinkedHashSet<String> result=new LinkedHashSet<>();
        ArrayList<ArrayList<String>> albums=new ArrayList<>();
        try{
            JSONArray data=new JSONArray(prefs(c).getString("albums","[]"));
            for(int i=0;i<data.length();i++){
                JSONArray media=data.getJSONObject(i).optJSONArray("photos");
                ArrayList<String> items=new ArrayList<>();
                if(media!=null)for(int j=0;j<media.length();j++){
                    String item=media.optString(j);
                    if(!item.isEmpty())items.add(item);
                }
                albums.add(items);
            }
        }catch(Exception ignored){}
        boolean more;
        int i=0;
        do {
            more=false;
            for(ArrayList<String> album:albums)if(i<album.size()){
                result.add(album.get(i));more=true;
            }
            i++;
        }while(more);
        return result;
    }
    static void schedule(Context c){
        LinkedHashSet<String> all=urls(c);
        if(all.isEmpty()){WorkManager.getInstance(c).cancelUniqueWork(NAME);return;}
        boolean missing=false;
        for(String u:all)if(!PhotoStorage.cached(c,u)){missing=true;break;}
        if(!missing)return;
        Constraints constraints=new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresStorageNotLow(true).build();
        OneTimeWorkRequest request=new OneTimeWorkRequest.Builder(PhotoCacheWorker.class)
            .setConstraints(constraints)
            .build();
        WorkManager.getInstance(c).enqueueUniqueWork(NAME,ExistingWorkPolicy.REPLACE,request);
    }
    @NonNull @Override public Result doWork(){
        Context c=getApplicationContext();
        ArrayList<String> missing=new ArrayList<>();
        for(String u:urls(c))if(!PhotoStorage.cached(c,u))missing.add(u);
        if(missing.isEmpty())return Result.success();
        // No artificial six-download ceiling: each pending item may run concurrently.
        // Android, available memory, Wi-Fi and Google servers still impose physical limits.
        ExecutorService pool=Executors.newCachedThreadPool(r->{
            Thread t=new Thread(r,"shuffleframe-cache");t.setDaemon(true);return t;
        });
        CompletionService<Boolean> completed=new ExecutorCompletionService<>(pool);
        AtomicInteger failures=new AtomicInteger(0);
        int submitted=0;
        try{
            for(String url:missing){
                if(isStopped())break;
                submitted++;
                completed.submit(()->{
                    if(isStopped() || Thread.currentThread().isInterrupted())return false;
                    try{PhotoStorage.cacheItem(c,url);return true;}
                    catch(Exception e){return false;}
                });
            }
            int done=0;
            for(int i=0;i<submitted;i++){
                if(isStopped())break;
                try{
                    boolean ok=completed.take().get();
                    done++;
                    if(!ok)failures.incrementAndGet();
                    if(done%4==0 || done==submitted)
                        setProgressAsync(new Data.Builder()
                            .putInt("completed",done)
                            .putInt("total",submitted)
                            .putInt("failed",failures.get()).build());
                }catch(InterruptedException ex){
                    Thread.currentThread().interrupt();break;
                }catch(ExecutionException ex){failures.incrementAndGet();}
            }
            if(isStopped())return Result.retry();
        }finally{
            pool.shutdownNow();
        }
        PhotoStorage.prune(c,urls(c));
        // No unbounded retries. Failed media can retry on refresh or next app launch.
        return Result.success(new Data.Builder()
            .putInt("failed",failures.get()).build());
    }
}
