package com.hasbreo.shuffleframe;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.StatFs;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;

final class PhotoStorage {
    private static final Object[] LOCKS = new Object[32];
    static { for (int i=0;i<LOCKS.length;i++) LOCKS[i]=new Object(); }
    private PhotoStorage(){}
    static File directory(Context c) {
        File f=new File(c.getFilesDir(), "photos");
        if (!f.exists()) f.mkdirs();
        return f;
    }
    static String key(String url){
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(url.getBytes("UTF-8"));
            StringBuilder s=new StringBuilder();
            for(byte b:digest) s.append(String.format(java.util.Locale.US,"%02x",b&0xff));
            return s.toString();
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    static File file(Context c,String url){return new File(directory(c),key(url)+".img");}
    static boolean cached(Context c,String url){File f=file(c,url);return f.isFile()&&f.length()>0;}
    static int count(Context c,Collection<String> urls){
        int n=0;for(String u:urls) if(cached(c,u))n++;return n;
    }
    static long bytes(Context c){
        long total=0;File[] files=directory(c).listFiles();
        if(files!=null)for(File f:files)if(f.isFile()&&f.getName().endsWith(".img"))total+=f.length();
        return total;
    }
    static boolean isVideo(String media){return media!=null&&media.startsWith("v:");}
    static String posterUrl(String media){
        if(isVideo(media))return media.substring(2)+"=w1600-h1600-no";
        return media;
    }
    static String streamUrl(String media){
        if(!isVideo(media))throw new IllegalArgumentException("Not a video");
        return media.substring(2)+"=dv";
    }
    static String validateImageUrl(String url) throws IOException {
        try {
            URI u=new URI(url);
            String h=u.getHost();
            if(!"https".equalsIgnoreCase(u.getScheme()) || h==null ||
                !h.matches("lh[3-6]\\.googleusercontent\\.com"))
                throw new IOException("Invalid Google Photos image address");
            return url;
        }catch(URISyntaxException e){throw new IOException("Bad photo address",e);}
    }
    static File ensure(Context c,String url) throws IOException {
        validateImageUrl(posterUrl(url));
        File f=file(c,url);
        if(f.isFile()&&f.length()>0)return f;
        Object lock=LOCKS[(key(url).hashCode()&0x7fffffff)%LOCKS.length];
        synchronized(lock){
            if(f.isFile()&&f.length()>0)return f;
            if(directory(c).getUsableSpace()<50L*1024*1024)
                throw new IOException("Storage is nearly full");
            File temp=File.createTempFile("download-", ".part",directory(c));
            try{
                String current=posterUrl(url);
                boolean downloaded=false;
                for(int redirect=0;redirect<5;redirect++){
                    validateImageUrl(current);
                    HttpURLConnection conn=(HttpURLConnection)new URL(current).openConnection();
                    conn.setConnectTimeout(12000);conn.setReadTimeout(25000);
                    conn.setInstanceFollowRedirects(false);
                    conn.setRequestProperty("User-Agent","Shuffleframe/0.3");
                    try {
                        int status=conn.getResponseCode();
                        if(status>=300&&status<400){
                            String location=conn.getHeaderField("Location");
                            if(location==null)throw new IOException("Image redirect missing location");
                            current=new URL(new URL(current),location).toString();
                            continue;
                        }
                        if(status!=200)throw new IOException("Image HTTP "+status);
                        try(InputStream in=conn.getInputStream();OutputStream out=new BufferedOutputStream(new FileOutputStream(temp))){
                            byte[] chunk=new byte[16384];int n;long length=0;
                            while((n=in.read(chunk))!=-1){
                                length+=n;
                                if(length>24L*1024*1024)throw new IOException("Image too large");
                                out.write(chunk,0,n);
                            }
                        }
                        downloaded=true;break;
                    }finally{conn.disconnect();}
                }
                if(!downloaded||temp.length()==0)throw new IOException("Photo could not be downloaded");
                // Validate the file before it becomes part of the persistent album cache.
                BitmapFactory.Options check=new BitmapFactory.Options();check.inJustDecodeBounds=true;
                BitmapFactory.decodeFile(temp.getAbsolutePath(),check);
                if(check.outWidth<=0||check.outHeight<=0)throw new IOException("Invalid image file");
                if(!temp.renameTo(f))throw new IOException("Unable to save photo");
                return f;
            }finally{if(temp.exists())temp.delete();}
        }
    }
    static Bitmap decode(Context c,String url,int screenW,int screenH) throws IOException {
        File f=ensure(c,url);
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(f.getAbsolutePath(),o);
        if(o.outWidth<=0 || o.outHeight<=0)throw new IOException("Unrecognised photo");
        int targetW=Math.max(600,screenW), targetH=Math.max(900,screenH);
        int sample=1;
        while(o.outWidth/(sample*2)>=targetW && o.outHeight/(sample*2)>=targetH)sample*=2;
        o.inJustDecodeBounds=false;o.inSampleSize=sample;o.inPreferredConfig=Bitmap.Config.RGB_565;
        Bitmap b=BitmapFactory.decodeFile(f.getAbsolutePath(),o);
        if(b==null)throw new IOException("Unable to decode photo");
        return b;
    }
    static void prune(Context c,Collection<String> urls){
        Set<String> keep=new HashSet<>();for(String url:urls)keep.add(key(url)+".img");
        File[] files=directory(c).listFiles();
        if(files!=null)for(File f:files){
            if(f.getName().endsWith(".img")&&!keep.contains(f.getName()))f.delete();
        }
    }
}
