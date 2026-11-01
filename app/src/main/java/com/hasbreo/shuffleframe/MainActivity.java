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
        load();

        home();
    }
    @Override protected void onPause(){super.onPause();handler.removeCallbacks(nextTimer);}
    @Override protected void onResume(){super.onResume();if(viewer&&playing)schedule();}
    @Override protected void onDestroy(){handler.removeCallbacks(nextTimer);worker.shutdownNow();super.onDestroy();}
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
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setBackgroundColor(BG);
        LinearLayout body=vertical();body.setPadding(dp(20),dp(30),dp(20),dp(32));sv.addView(body);
        body.addView(text("◈  SHUFFLEFRAME",14,MINT,true));
        addTop(body,text("Your photos.\nOut of order.",34,INK,true),22);
        addTop(body,text("Pick one album or combine several. Every photo appears once before the next shuffle.",15,DIM,false),12);
        LinkedHashSet<String> included=photos();
        int selected=0;for(Album a:albums)if(a.enabled)selected++;
        addTop(body,text(included.size()+" photos  ·  "+selected+" selected albums",13,MINT,true),26);
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
            top.addView(name,new LinearLayout.LayoutParams(0,-2,1));card.addView(top);
            TextView url=text(a.url,11,DIM,false);url.setMaxLines(1);url.setEllipsize(android.text.TextUtils.TruncateAt.END);
            addTop(card,url,6);
            LinearLayout controls=horizontal();
            TextView refresh=button(a.loading?"Loading…":"↻  Refresh",false,v->refresh(a));
            controls.addView(refresh,new LinearLayout.LayoutParams(0,-2,1));
            TextView remove=button("Remove",false,v->new AlertDialog.Builder(this)
                .setTitle("Remove album?").setMessage("No photos will be deleted from Google Photos.")
                .setNegativeButton("Cancel",null).setPositiveButton("Remove",(d,w)->{albums.remove(a);save();home();}).show());
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-2,1);rp.leftMargin=dp(8);controls.addView(remove,rp);
            addTop(card,controls,12);addTop(body,card,12);
        }
        addTop(body,button("↻  Refresh all albums",false,v->{for(Album a:new ArrayList<>(albums))refresh(a);}),20);
        addTop(body,text("Photos load from shared album links on your phone. An internet connection is required. Google can change its shared-album pages, so link imports are best-effort.",12,DIM,false),18);
        setContentView(sv);
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
                    save();home();
                    if(result.partial)toast("Loaded "+a.photos.size()+" photos, but there may be more pages to load.");
                });
            } catch(Exception e){
                runOnUiThread(()->{a.loading=false;home();toast("Album refresh failed: "+e.getMessage());});
            }
        });
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
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
        photoImage=new ImageView(this);photoImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(photoImage,new FrameLayout.LayoutParams(-1,-1));
        float[] startX={0},startY={0};
        photoImage.setOnTouchListener((v,e)->{
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){startX[0]=e.getX();startY[0]=e.getY();return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_UP){
                float dx=e.getX()-startX[0];
                if(Math.abs(dx)>dp(55)&&Math.abs(dx)>Math.abs(e.getY()-startY[0])){if(dx<0)next();else previous();}
                return true;
            }return true;
        });
        spinner=new ProgressBar(this);spinner.setIndeterminateTintList(ColorStateList.valueOf(MINT));
        root.addView(spinner,new FrameLayout.LayoutParams(dp(40),dp(40),Gravity.CENTER));
        LinearLayout bar=horizontal();bar.setPadding(dp(13),dp(18),dp(13),dp(18));bar.setBackgroundColor(0xbb091710);
        TextView back=text("‹  Albums",15,INK,true);back.setPadding(dp(6),dp(7),dp(14),dp(7));back.setOnClickListener(v->leave());
        bar.addView(back);countText=text("",14,MINT,true);countText.setGravity(Gravity.RIGHT);
        bar.addView(countText,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(bar,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        LinearLayout controls=horizontal();controls.setPadding(dp(9),dp(16),dp(9),dp(25));
        controls.setBackgroundColor(0xbb091710);
        addControl(controls,"‹",1,v->previous());addControl(controls,"›",1,v->next());
        addControl(controls,"Shuffle",2,v->reshuffle());
        playText=addControl(controls,"▶  Auto",2,v->togglePlay());
        root.addView(controls,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        setContentView(root);loadPhoto();
    }
    TextView addControl(LinearLayout parent,String label,int weight,View.OnClickListener click){
        TextView t=text(label,15,MINT,true);t.setGravity(Gravity.CENTER);t.setOnClickListener(click);
        parent.addView(t,new LinearLayout.LayoutParams(0,dp(45),weight));return t;
    }
    void appendPass(){
        ArrayList<String> another=new ArrayList<>(order);Collections.shuffle(another);
        if(another.size()>1&&another.get(0).equals(history.get(history.size()-1)))
            Collections.swap(another,0,1);
        history.addAll(another);
    }
    void next(){if(!viewer)return;if(position==history.size()-1)appendPass();position++;loadPhoto();}
    void previous(){if(!viewer)return;if(position>0)position--;loadPhoto();schedule();}
    void reshuffle(){if(!viewer)return;history=new ArrayList<>(order);Collections.shuffle(history);position=0;loadPhoto();schedule();}
    void togglePlay(){playing=!playing;playText.setText(playing?"❚❚  Pause":"▶  Auto");schedule();}
    void schedule(){handler.removeCallbacks(nextTimer);if(viewer&&playing&&hasWindowFocus())handler.postDelayed(nextTimer,seconds*1000L);}
    void loadPhoto(){
        if(!viewer)return;int gen=++photoGeneration;
        countText.setText((position%order.size()+1)+" / "+order.size());
        photoImage.setImageDrawable(null);spinner.setVisibility(View.VISIBLE);
        String url=history.get(position);
        worker.execute(()->{
            Bitmap bitmap=null;String error=null;
            try{
                if(!url.startsWith("https://lh3.googleusercontent.com/") &&
                   !url.startsWith("https://lh4.googleusercontent.com/") &&
                   !url.startsWith("https://lh5.googleusercontent.com/") &&
                   !url.startsWith("https://lh6.googleusercontent.com/"))
                   throw new IllegalArgumentException("Image URL not allowed");
                HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();
                conn.setConnectTimeout(15000);conn.setReadTimeout(25000);
                conn.setInstanceFollowRedirects(true);
                try(InputStream in=conn.getInputStream()){bitmap=BitmapFactory.decodeStream(in);}
                finally{conn.disconnect();}
                if(bitmap==null)error="Image decode failed";
            }catch(Exception|OutOfMemoryError ex){error=ex.getMessage();}
            final Bitmap result=bitmap;final String message=error;
            runOnUiThread(()->{
                if(!viewer||gen!=photoGeneration)return;
                spinner.setVisibility(View.GONE);
                if(result!=null){photoImage.setImageBitmap(result);photoImage.setAlpha(0.4f);photoImage.animate().alpha(1f).setDuration(200).start();}
                else toast("Couldn't load this photo"+(message==null?"":": "+message));
            });
        });
    }
    void leave(){
        handler.removeCallbacks(nextTimer);viewer=false;playing=false;photoGeneration++;
        getWindow().getDecorView().setSystemUiVisibility(0);home();
    }
    void load(){
        try{JSONArray arr=new JSONArray(getPreferences(0).getString("albums","[]"));
            for(int i=0;i<arr.length();i++)albums.add(Album.from(arr.getJSONObject(i)));
        }catch(Exception ignored){}
    }
    void save(){
        try{JSONArray arr=new JSONArray();for(Album a:albums)arr.put(a.json());
            getPreferences(0).edit().putString("albums",arr.toString()).apply();
        }catch(Exception ignored){}
    }
}
