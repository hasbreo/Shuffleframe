package com.hasbreo.shuffleframe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.SharedPreferences;
import android.util.LruCache;
import android.widget.ProgressBar;
import java.util.HashMap;
import java.util.Map;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    static final int BG=0xff091710, PANEL=0xff173026, MINT=0xff93edbd, INK=0xffedfff2, DIM=0xffadbdaf;

    private final ArrayList<Album> albums=new ArrayList<>();
    private final ExecutorService worker=Executors.newFixedThreadPool(3);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private ArrayList<String> order=new ArrayList<>(), history=new ArrayList<>();
    private int position=0, photoGeneration=0;
    private boolean viewer=false, playing=false;
    private ImageView photoImage;
    private ProgressBar spinner;
    private TextView countText, playText;
    private int seconds=5;
    private FrameLayout imageLayer;
    private LinearLayout topOverlay,bottomOverlay;
    private TextView cacheSummary;
    private final Map<Album,TextView> albumCacheLabels=new HashMap<>();
    private boolean overlaysVisible=true;
    private final LruCache<String,Bitmap> memory=new LruCache<String,Bitmap>(32*1024) {
        @Override protected int sizeOf(String key,Bitmap value){return Math.max(1,value.getByteCount()/1024);}
    };
    private final Runnable cacheTicker=new Runnable() {
        @Override public void run(){if(!viewer){updateCacheStatus();handler.postDelayed(this,1750);}}
    };
    private final Runnable nextTimer=new Runnable() { @Override public void run(){if(viewer && playing){next(); schedule();}} };

    static final class Album {
        String title,url;
        boolean enabled=true, loading=false;
        ArrayList<String> photos=new ArrayList<>();
        Album(String title,String url){this.title=title;this.url=url;}
        JSONObject json() throws Exception {
            JSONObject o=new JSONObject();o.put("title",title);o.put("url",url);
            o.put("enabled",enabled);o.put("photos",new JSONArray(photos));return o;
        }
        static Album from(JSONObject o){Album a=new Album(o.optString("title","Shared album"),o.optString("url",""));
            a.enabled=o.optBoolean("enabled",true);JSONArray ar=o.optJSONArray("photos");
            if(ar!=null)for(int i=0;i<ar.length();i++) {String p=ar.optString(i);if(!p.isEmpty())a.photos.add(p);}
            return a;
        }
    }
    @Override public void onCreate(Bundle b){
        super.onCreate(b); getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        // Upgrade in place from v0.2: preserve albums already added by the user.
        SharedPreferences preferences=PhotoCacheWorker.prefs(this);
        if(!preferences.contains("albums"))
            preferences.edit().putString("albums",getPreferences(0).getString("albums","[]")).apply();
        load();
        PhotoCacheWorker.schedule(this);
        home();
    }
    @Override protected void onPause(){super.onPause();handler.removeCallbacks(nextTimer);}
    @Override protected void onResume(){super.onResume();if(viewer&&playing)schedule();else if(!viewer){handler.removeCallbacks(cacheTicker);handler.post(cacheTicker);}}
    @Override protected void onDestroy(){handler.removeCallbacks(nextTimer);handler.removeCallbacks(cacheTicker);worker.shutdownNow();super.onDestroy();}
    @Override public void onBackPressed(){if(viewer)leave();else super.onBackPressed();}
    int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    TextView text(String s,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextColor(color);v.setTextSize(size);
        if(bold)v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return v;}
    GradientDrawable shape(int bg){GradientDrawable gd=new GradientDrawable();gd.setColor(bg);gd.setCornerRadius(dp(16));return gd;}
    TextView button(String s,boolean filled,View.OnClickListener listener){
        TextView t=text(s,15,filled?BG:MINT,true);t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12),dp(15),dp(12),dp(15));t.setBackground(shape(filled?MINT:PANEL));t.setOnClickListener(listener);return t;
    }
    void addTop(LinearLayout parent,View child,int top){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(top);parent.addView(child,lp);
    }
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    void home(){
        if(viewer)return;
        albumCacheLabels.clear();handler.removeCallbacks(cacheTicker);
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setBackgroundColor(BG);
        LinearLayout body=vertical();body.setPadding(dp(20),dp(30),dp(20),dp(32));sv.addView(body);
        body.addView(text("◈  SHUFFLEFRAME",14,MINT,true));
        addTop(body,text("Your photos.\nOut of order.",34,INK,true),22);
        addTop(body,text("Pick one album or combine several. Every photo appears once before the next shuffle.",15,DIM,false),12);
        LinkedHashSet<String> included=photos();
        int selected=0;for(Album a:albums)if(a.enabled)selected++;
        addTop(body,text(included.size()+" photos  ·  "+selected+" selected albums",13,MINT,true),26);
        cacheSummary=text("",13,DIM,false);
        addTop(body,cacheSummary,6);
        addTop(body,button("Start random shuffle  →",true,v->start()),12);
        addTop(body,button("+  Add shared Google Photos album",false,v->addAlbum()),10);
        addTop(body,text("Albums",22,INK,true),30);
        if(albums.isEmpty()) addTop(body,text("Paste a Google Photos shared-album link to begin.",15,DIM,false),12);
        for(Album a:new ArrayList<>(albums)){
            LinearLayout card=vertical();card.setPadding(dp(14),dp(15),dp(14),dp(15));card.setBackground(shape(PANEL));
            LinearLayout top=horizontal();CheckBox check=new CheckBox(this);check.setButtonTintList(ColorStateList.valueOf(MINT));
            check.setChecked(a.enabled);check.setOnCheckedChangeListener((v,c)->{a.enabled=c;save();home();});
            top.addView(check);LinearLayout name=vertical();name.addView(text(a.title,17,INK,true));
            name.addView(text(a.loading?"Loading photos…":a.photos.size()+" photos",13,DIM,false));
            TextView cacheLabel=text("",12,MINT,false);name.addView(cacheLabel);
            albumCacheLabels.put(a,cacheLabel);
            top.addView(name,new LinearLayout.LayoutParams(0,-2,1));card.addView(top);
            TextView url=text(a.url,11,DIM,false);url.setMaxLines(1);url.setEllipsize(android.text.TextUtils.TruncateAt.END);
            addTop(card,url,6);
            LinearLayout controls=horizontal();
            TextView refresh=button(a.loading?"Loading…":"↻  Refresh",false,v->refresh(a));
            controls.addView(refresh,new LinearLayout.LayoutParams(0,-2,1));
            TextView remove=button("Remove",false,v->new AlertDialog.Builder(this)
                .setTitle("Remove album?").setMessage("No photos will be deleted from Google Photos.")
                .setNegativeButton("Cancel",null).setPositiveButton("Remove",(d,w)->{
                    albums.remove(a);save();PhotoCacheWorker.schedule(this);
                    LinkedHashSet<String> keep=allPhotos();
                    worker.execute(()->PhotoStorage.prune(getApplicationContext(),keep));home();
                }).show());
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-2,1);rp.leftMargin=dp(8);controls.addView(remove,rp);
            addTop(card,controls,12);addTop(body,card,12);
        }
        addTop(body,button("↻  Refresh all albums",false,v->{for(Album a:new ArrayList<>(albums))refresh(a);}),20);
        addTop(body,text("Photos download privately into this app's storage in the background. Once cached, browsing works offline. Album refreshes need internet.",12,DIM,false),18);
        setContentView(sv);updateCacheStatus();handler.postDelayed(cacheTicker,1750);
    }
    void addAlbum(){
        EditText e=new EditText(this);e.setHint("https://photos.app.goo.gl/…");e.setSingleLine(false);e.setMinLines(2);
        LinearLayout holder=vertical();holder.setPadding(dp(19),0,dp(19),0);holder.addView(e);
        new AlertDialog.Builder(this).setTitle("Add a shared album").setView(holder)
            .setNegativeButton("Cancel",null).setPositiveButton("Add",(d,w)->{
                String url=e.getText().toString().trim();
                if(!SharedAlbumReader.allowed(url)){toast("Paste a Google Photos shared album link");return;}
                for(Album a:albums)if(a.url.equals(url)){toast("Already added");return;}
                Album a=new Album("Shared album "+(albums.size()+1),url);albums.add(a);save();home();refresh(a);
            }).show();
    }
    void refresh(Album a){
        if(a.loading)return; a.loading=true;home();
        worker.execute(()->{
            try {
                SharedAlbumReader.Result result=SharedAlbumReader.fetch(a.url);
                runOnUiThread(()->{
                    if(!albums.contains(a))return;
                    a.loading=false;
                    if(result.images.isEmpty()){toast("No photos found. Check album link or try again.");home();return;}
                    a.photos=new ArrayList<>(result.images);
                    if(a.title.startsWith("Shared album")||a.title.equals("My first album")){
                        if(result.title!=null&&!result.title.isEmpty())a.title=result.title;
                    }
                    save();PhotoCacheWorker.schedule(this);home();
                    if(result.partial)toast("Loaded "+a.photos.size()+" photos, but there may be more pages to load.");
                });
            } catch(Exception e){
                runOnUiThread(()->{a.loading=false;home();toast("Album refresh failed: "+e.getMessage());});
            }
        });
    }
    LinkedHashSet<String> allPhotos(){
        LinkedHashSet<String> result=new LinkedHashSet<>();
        for(Album a:albums)result.addAll(a.photos);
        return result;
    }
    void updateCacheStatus(){
        if(viewer)return;
        LinkedHashSet<String> all=allPhotos();
        int cached=PhotoStorage.count(this,all);
        if(cacheSummary!=null){
            long mb=PhotoStorage.bytes(this)/(1024*1024);
            cacheSummary.setText("Saved on phone: "+cached+" / "+all.size()+" photos · "+mb+" MB"+
                (cached<all.size()?"  ·  Downloading in background":"  ·  Ready offline"));
        }
        for(Map.Entry<Album,TextView> entry:albumCacheLabels.entrySet()){
            Album a=entry.getKey();int count=PhotoStorage.count(this,new LinkedHashSet<>(a.photos));
            entry.getValue().setText(count+" / "+a.photos.size()+" cached offline");
        }
    }
    LinkedHashSet<String> photos(){
        LinkedHashSet<String> result=new LinkedHashSet<>();
        for(Album a:albums)if(a.enabled)result.addAll(a.photos);
        return result;
    }
    void start(){
        LinkedHashSet<String> all=photos();if(all.isEmpty()){toast("Refresh a shared album first");return;}
        order=new ArrayList<>(all);Collections.shuffle(order);history=new ArrayList<>(order);
        position=0;viewer=true;playing=false;showViewer();
    }

    void showViewer(){
        handler.removeCallbacks(cacheTicker);
        overlaysVisible=true;
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
        imageLayer=new FrameLayout(this);
        root.addView(imageLayer,new FrameLayout.LayoutParams(-1,-1));
        photoImage=makeImage();
        imageLayer.addView(photoImage,new FrameLayout.LayoutParams(-1,-1));

        spinner=new ProgressBar(this);spinner.setIndeterminateTintList(ColorStateList.valueOf(MINT));
        root.addView(spinner,new FrameLayout.LayoutParams(dp(34),dp(34),Gravity.CENTER));

        // Gesture layer covers the complete picture; the actual controls sit above it.
        View gestures=new View(this);gestures.setClickable(true);
        final float[] down={0,0};final long[] downAt={0};
        gestures.setOnTouchListener((v,event)->{
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
                down[0]=event.getX();down[1]=event.getY();downAt[0]=System.currentTimeMillis();return true;
            }
            if(event.getActionMasked()==MotionEvent.ACTION_UP){
                float dx=event.getX()-down[0],dy=event.getY()-down[1];
                if(Math.abs(dx)>dp(50) && Math.abs(dx)>Math.abs(dy)*1.1f){
                    // Both horizontal swipe directions advance through the random sequence.
                    next(dx<0?1:-1);
                }else if(Math.abs(dx)<dp(24)&&Math.abs(dy)<dp(24)){
                    float fraction=event.getX()/Math.max(1,v.getWidth());
                    if(fraction<=0.25f)next(-1);
                    else if(fraction>=0.75f)next(1);
                    else setOverlayVisible(!overlaysVisible);
                }
                return true;
            }
            return true;
        });
        root.addView(gestures,new FrameLayout.LayoutParams(-1,-1));

        topOverlay=vertical();
        topOverlay.setPadding(dp(18),dp(18),dp(18),dp(22));
        topOverlay.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0xe50a1712,0xb3081c13,0x00000000}));
        LinearLayout top=horizontal();
        TextView back=text("‹  ALBUMS",13,INK,true);
        back.setPadding(dp(5),dp(15),dp(15),dp(15));back.setOnClickListener(v->leave());
        top.addView(back);
        TextView brand=text("SHUFFLEFRAME",13,MINT,true);brand.setGravity(Gravity.CENTER);
        top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        countText=text("",13,INK,true);countText.setGravity(Gravity.END);
        countText.setPadding(dp(6),dp(12),dp(5),dp(12));top.addView(countText);
        topOverlay.addView(top);
        ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(Math.max(1,order.size()));progress.setProgress(1);
        progress.setProgressTintList(ColorStateList.valueOf(MINT));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(0x668ca99a));
        LinearLayout.LayoutParams prog=new LinearLayout.LayoutParams(-1,dp(2));prog.topMargin=dp(4);
        topOverlay.addView(progress,prog);
        slideshowProgress=progress;
        root.addView(topOverlay,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));

        bottomOverlay=vertical();
        bottomOverlay.setPadding(dp(18),dp(42),dp(18),dp(24));
        bottomOverlay.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
            new int[]{0xe8091711,0xb3081c13,0x00000000}));
        TextView tip=text("SWIPE TO EXPLORE   ·   TAP EDGES TO ADVANCE",10,0xffb2c5b9,true);
        tip.setGravity(Gravity.CENTER);bottomOverlay.addView(tip);
        LinearLayout controls=horizontal();
        LinearLayout.LayoutParams controlsLp=new LinearLayout.LayoutParams(-1,dp(54));controlsLp.topMargin=dp(12);
        bottomOverlay.addView(controls,controlsLp);
        addControl(controls,"‹  Back",1,v->previous());
        addControl(controls,"⤨  Reshuffle",2,v->reshuffle());
        playText=addControl(controls,"▶  Auto",2,v->togglePlay());
        root.addView(bottomOverlay,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        setContentView(root);
        loadPhoto(1);
    }
    private ProgressBar slideshowProgress;
    ImageView makeImage(){
        ImageView image=new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackgroundColor(Color.BLACK);
        return image;
    }
    TextView addControl(LinearLayout parent,String label,int weight,View.OnClickListener click){
        TextView t=text(label,14,MINT,true);t.setGravity(Gravity.CENTER);
        t.setBackground(shape(0x9e183426));
        t.setOnClickListener(click);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,weight);
        lp.leftMargin=dp(3);lp.rightMargin=dp(3);parent.addView(t,lp);
        return t;
    }
    void setOverlayVisible(boolean visible){
        overlaysVisible=visible;
        for(View overlay:new View[]{topOverlay,bottomOverlay}){
            overlay.animate().cancel();
            if(visible){
                overlay.setVisibility(View.VISIBLE);
                overlay.animate().alpha(1f).setDuration(230).start();
            }else{
                overlay.animate().alpha(0f).setDuration(230).withEndAction(()->{
                    if(!overlaysVisible)overlay.setVisibility(View.GONE);
                }).start();
            }
        }
    }
    void appendPass(){
        ArrayList<String> another=new ArrayList<>(order);Collections.shuffle(another);
        if(another.size()>1&&another.get(0).equals(history.get(history.size()-1)))
            Collections.swap(another,0,1);
        history.addAll(another);
    }
    void next(){next(1);}
    void next(int direction){
        if(!viewer)return;
        if(position==history.size()-1)appendPass();
        position++;loadPhoto(direction);
    }
    void previous(){
        if(!viewer)return;
        if(position>0){position--;loadPhoto(-1);}
        else toast("This is the first photo in your shuffle");
    }
    void reshuffle(){
        if(!viewer)return;
        history=new ArrayList<>(order);Collections.shuffle(history);
        position=0;loadPhoto(1);
    }
    void togglePlay(){
        playing=!playing;
        playText.setText(playing?"❚❚  Pause":"▶  Auto");
        schedule();
    }
    void schedule(){
        handler.removeCallbacks(nextTimer);
        if(viewer&&playing&&hasWindowFocus())handler.postDelayed(nextTimer,seconds*1000L);
    }
    void showBitmap(Bitmap bitmap,int direction){
        // The old image remains visible until the next image has actually decoded.
        for(int i=imageLayer.getChildCount()-1;i>=0;i--){
            View child=imageLayer.getChildAt(i);child.animate().cancel();
            if(child!=photoImage)imageLayer.removeView(child);
        }
        ImageView old=photoImage;
        ImageView incoming=makeImage();incoming.setImageBitmap(bitmap);
        imageLayer.addView(incoming,new FrameLayout.LayoutParams(-1,-1));
        float width=Math.max(dp(300),imageLayer.getWidth());
        incoming.setTranslationX(width*direction);
        incoming.animate().translationX(0).setDuration(280)
            .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        old.animate().translationX(-width*direction).setDuration(280)
            .setInterpolator(new android.view.animation.DecelerateInterpolator())
            .withEndAction(()->{if(old.getParent()==imageLayer)imageLayer.removeView(old);}).start();
        photoImage=incoming;
    }
    void loadPhoto(int direction){
        if(!viewer||order.isEmpty())return;
        int generation=++photoGeneration;
        countText.setText((position%order.size()+1)+" / "+order.size());
        slideshowProgress.setProgress(position%order.size()+1);
        spinner.setVisibility(View.VISIBLE);
        String url=history.get(position);
        Bitmap ram=memory.get(url);
        if(ram!=null){
            spinner.setVisibility(View.GONE);
            showBitmap(ram,direction);schedule();return;
        }
        int width=getResources().getDisplayMetrics().widthPixels;
        int height=getResources().getDisplayMetrics().heightPixels;
        worker.execute(()->{
            Bitmap bitmap=null;String error=null;
            try{bitmap=PhotoStorage.decode(getApplicationContext(),url,width,height);}
            catch(Exception|OutOfMemoryError e){error=e.getMessage();}
            Bitmap ready=bitmap;String failure=error;
            runOnUiThread(()->{
                if(!viewer||generation!=photoGeneration)return;
                spinner.setVisibility(View.GONE);
                if(ready!=null){
                    memory.put(url,ready);
                    showBitmap(ready,direction);
                }else{
                    toast("Photo not available"+(failure==null?"":": "+failure));
                }
                schedule();
            });
        });
    }
    void leave(){
        handler.removeCallbacks(nextTimer);handler.removeCallbacks(cacheTicker);viewer=false;playing=false;photoGeneration++;
        getWindow().getDecorView().setSystemUiVisibility(0);home();
    }
    void load(){
        try{JSONArray arr=new JSONArray(PhotoCacheWorker.prefs(this).getString("albums","[]"));
            for(int i=0;i<arr.length();i++)albums.add(Album.from(arr.getJSONObject(i)));
        }catch(Exception ignored){}
    }
    void save(){
        try{JSONArray arr=new JSONArray();for(Album a:albums)arr.put(a.json());
            PhotoCacheWorker.prefs(this).edit().putString("albums",arr.toString()).apply();
        }catch(Exception ignored){}
    }
}
