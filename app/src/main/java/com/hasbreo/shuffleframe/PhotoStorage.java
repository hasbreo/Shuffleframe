package com.hasbreo.shuffleframe;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;

final class PhotoStorage {
    private static final Object[] LOCKS = new Object[128];
    static { for(int i=0;i<LOCKS.length;i++)LOCKS[i]=new Object(); }
    private PhotoStorage(){}

    static File directory(Context c){
        File f=new File(c.getFilesDir(),"photos");
        if(!f.exists())f.mkdirs();
        return f;
    }
    static String key(String url){
        try{
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(url.getBytes("UTF-8"));
            StringBuilder s=new StringBuilder();
            for(byte b:hash)s.append(String.format(Locale.US,"%02x",b&255));
            return s.toString();
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    static File file(Context c,String media){return new File(directory(c),key(media)+".img");}
    static File videoFile(Context c,String media){return new File(directory(c),key(media)+".mp4");}
    static File videoPartial(Context c,String media){return new File(directory(c),key(media)+".mp4.part");}
    static boolean isVideo(String media){return media!=null&&media.startsWith("v:");}
    static String posterUrl(String media){
        return isVideo(media)?media.substring(2)+"=w1600-h1600-no":media;
    }
    static String streamUrl(String media){
        if(!isVideo(media))throw new IllegalArgumentException("Not a video");
        return media.substring(2)+"=dv";
    }
    static boolean cached(Context c,String media){
        File f=isVideo(media)?videoFile(c,media):file(c,media);
        return f.isFile()&&f.length()>0;
    }
    static boolean posterCached(Context c,String media){
        File f=file(c,media);return f.isFile()&&f.length()>0;
    }
    static int count(Context c,Collection<String> items){
        int n=0;for(String m:items)if(cached(c,m))n++;return n;
    }
    static long bytes(Context c){
        long sum=0;File[] files=directory(c).listFiles();
        if(files!=null)for(File f:files){
            if(f.isFile()&&(f.getName().endsWith(".img")||
                f.getName().endsWith(".mp4")||f.getName().endsWith(".part")))sum+=f.length();
        }
        return sum;
    }
    static String validateUrl(String url,boolean video)throws IOException{
        try{
            URI u=new URI(url);
            String h=u.getHost();
            boolean googleImage=h!=null&&h.matches("lh[3-6]\\.googleusercontent\\.com");
            boolean googleVideo=h!=null&&(h.equals("googlevideo.com")||h.endsWith(".googlevideo.com"));
            if(!"https".equalsIgnoreCase(u.getScheme())||!(googleImage||(video&&googleVideo)))
                throw new IOException("Unsupported Google media URL");
            return url;
        }catch(URISyntaxException ex){throw new IOException("Malformed media URL",ex);}
    }
    static Object lock(String item){
        return LOCKS[(key(item).hashCode()&0x7fffffff)%LOCKS.length];
    }
    static HttpURLConnection connection(String url,boolean video,long offset)throws IOException{
        String target=url;
        for(int i=0;i<7;i++){
            validateUrl(target,video);
            HttpURLConnection conn=(HttpURLConnection)new URL(target).openConnection();
            conn.setConnectTimeout(15000);conn.setReadTimeout(35000);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent","Mozilla/5.0 (Android) Shuffleframe/0.5");
            if(offset>0)conn.setRequestProperty("Range","bytes="+offset+"-");
            int status=conn.getResponseCode();
            if(status>=300&&status<400){
                String redirected=conn.getHeaderField("Location");
                if(redirected==null){conn.disconnect();throw new IOException("Media redirect has no destination");}
                target=new URL(new URL(target),redirected).toString();
                conn.disconnect();continue;
            }
            if(status!=200&&status!=206){
                conn.disconnect();throw new IOException("Google media HTTP "+status);
            }
            return conn;
        }
        throw new IOException("Too many media redirects");
    }
    static File ensure(Context c,String media)throws IOException {
        File result=file(c,media);
        if(result.isFile()&&result.length()>0)return result;
        synchronized(lock(media)){
            if(result.isFile()&&result.length()>0)return result;
            if(directory(c).getUsableSpace()<100L*1024*1024)
                throw new IOException("Not enough free storage");
            File part=File.createTempFile("photo-", ".part",directory(c));
            try{
                HttpURLConnection conn=connection(posterUrl(media),false,0);
                try(InputStream in=new BufferedInputStream(conn.getInputStream());
                    OutputStream out=new BufferedOutputStream(new FileOutputStream(part))){
                    byte[] buffer=new byte[32768];int n;long size=0;
                    while((n=in.read(buffer))!=-1){
                        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Cancelled");
                        size+=n;
                        if(size>24L*1024*1024)throw new IOException("Photo exceeds 24 MB");
                        out.write(buffer,0,n);
                    }
                }finally{conn.disconnect();}
                BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;
                BitmapFactory.decodeFile(part.getAbsolutePath(),o);
                if(o.outWidth<1||o.outHeight<1)throw new IOException("Invalid photo");
                if(!part.renameTo(result))throw new IOException("Couldn't save photo");
                return result;
            }finally{if(part.exists())part.delete();}
        }
    }
    static File ensureVideo(Context c,String media)throws IOException {
        if(!isVideo(media))throw new IOException("Not a video");
        File result=videoFile(c,media);
        if(result.isFile()&&result.length()>0)return result;
        synchronized(lock(media+"#video")){
            if(result.isFile()&&result.length()>0)return result;
            File part=videoPartial(c,media);
            // Download may resume when Google supports HTTP Range; otherwise restart.
            long offset=part.isFile()?part.length():0;
            if(directory(c).getUsableSpace()<100L*1024*1024)
                throw new IOException("Insufficient storage for video");
            HttpURLConnection conn=connection(streamUrl(media),true,offset);
            try{
                int response=conn.getResponseCode();
                boolean append=offset>0&&response==206;
                if(!append)offset=0;
                long expected=conn.getContentLengthLong();
                if(expected>=0&&expected>directory(c).getUsableSpace()-80L*1024*1024)
                    throw new IOException("Not enough free storage for this video");
                long downloaded=0;
                try(InputStream in=new BufferedInputStream(conn.getInputStream());
                    OutputStream out=new BufferedOutputStream(new FileOutputStream(part,append))){
                    byte[] buffer=new byte[65536];int n;
                    while((n=in.read(buffer))!=-1){
                        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Cancelled");
                        out.write(buffer,0,n);downloaded+=n;
                        if(downloaded%(8L*1024*1024)<65536&&directory(c).getUsableSpace()<80L*1024*1024)
                            throw new IOException("Phone storage nearly full");
                    }
                }
                if(expected>=0&&downloaded!=expected)
                    throw new IOException("Video download interrupted; will resume later");
                if(part.length()<1)throw new IOException("Empty video download");
                if(!part.renameTo(result))throw new IOException("Cannot store video");
                return result;
            }finally{conn.disconnect();}
        }
    }
    static void cacheItem(Context c,String media)throws IOException{
        if(isVideo(media)){
            // Store poster and complete video; never count a mere poster as an offline video.
            ensure(c,media);
            ensureVideo(c,media);
        }else ensure(c,media);
    }
    static Bitmap decode(Context c,String media,int screenW,int screenH)throws IOException{
        File f=ensure(c,media);
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(f.getAbsolutePath(),o);
        if(o.outWidth<1||o.outHeight<1)throw new IOException("Unrecognised image");
        int targetW=Math.max(600,screenW),targetH=Math.max(900,screenH),sample=1;
        while(o.outWidth/(sample*2)>=targetW&&o.outHeight/(sample*2)>=targetH)sample*=2;
        o.inJustDecodeBounds=false;o.inSampleSize=sample;
        o.inPreferredConfig=Bitmap.Config.RGB_565;
        Bitmap b=BitmapFactory.decodeFile(f.getAbsolutePath(),o);
        if(b==null)throw new IOException("Unable to decode photo");
        return b;
    }
    static void prune(Context c,Collection<String> items){
        Set<String> keep=new HashSet<>();
        for(String item:items){
            String prefix=key(item);
            keep.add(prefix+".img");
            if(isVideo(item)){keep.add(prefix+".mp4");keep.add(prefix+".mp4.part");}
        }
        File[] all=directory(c).listFiles();
        if(all!=null)for(File f:all){
            String name=f.getName();
            if((name.endsWith(".img")||name.endsWith(".mp4")||name.endsWith(".mp4.part"))
                && !keep.contains(name))f.delete();
        }
    }
}
