package com.hasbreo.shuffleframe;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

final class SharedAlbumReader {
    static final class Result {
        ArrayList<String> images=new ArrayList<>();
        String title="";
        boolean partial=false;
    }
    static boolean allowed(String url){
        try{
            URI u=new URI(url);
            return "https".equals(u.getScheme()) &&
                ("photos.app.goo.gl".equals(u.getHost())||"photos.google.com".equals(u.getHost()));
        }catch(Exception e){return false;}
    }
    static String get(String url)throws Exception{
        for(int i=0;i<5;i++){
            if(!allowed(url))throw new IOException("Unexpected redirect host");
            HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
            c.setConnectTimeout(20000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/128.0.0.0 Mobile Safari/537.36");
            try{
                int status=c.getResponseCode();
                if(status>=300&&status<400){
                    String location=c.getHeaderField("Location");
                    if(location==null)throw new IOException("Redirect without location");
                    url=new URL(new URL(url),location).toString();
                    continue;
                }
                if(status!=200)throw new IOException("Album HTTP "+status);
                try(InputStream in=c.getInputStream()){return read(in);}
            }finally{c.disconnect();}
        }
        throw new IOException("Too many album redirects");
    }
    static String read(InputStream in)throws Exception{
        ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[16384];int n;
        while((n=in.read(buf))!=-1){
            b.write(buf,0,n);
            if(b.size()>18_000_000)throw new IOException("Album page too large");
        }
        return b.toString("UTF-8");
    }
    static String quoted(String s){
        return s.replace("\\/","/").replace("\\u003d","=").replace("\\u0026","&");
    }
    // Find the enclosing item, not the next image: video metadata lives in entry[9].
    static int itemEnd(String input,int open){
        int depth=0;boolean quoted=false,escaped=false;
        for(int i=open;i<input.length();i++){
            char c=input.charAt(i);
            if(quoted){
                if(escaped)escaped=false;
                else if(c=='\\')escaped=true;
                else if(c=='"')quoted=false;
            }else{
                if(c=='"')quoted=true;
                else if(c=='[')depth++;
                else if(c==']'){
                    depth--;
                    if(depth==0)return i+1;
                }
            }
        }
        return -1;
    }
    static Result parse(String html)throws IOException{
        Result result=new Result();
        Matcher title=Pattern.compile("<title>([^<]*)</title>").matcher(html);
        if(title.find())result.title=title.group(1).replace(" - Google Photos","").replace("&amp;","&");
        Matcher scripts=Pattern.compile("AF_initDataCallback\\((\\{[\\s\\S]*?\\})\\);").matcher(html);
        String longest="";
        while(scripts.find())if(scripts.group(1).length()>longest.length())longest=scripts.group(1);
        LinkedHashSet<String> unique=new LinkedHashSet<>();
        if(!longest.isEmpty()){
            Pattern entry=Pattern.compile("\\[\\s*\"([A-Za-z0-9_-]{12,})\"\\s*,\\s*\\[\\s*\"(https://lh[3-6]\\.googleusercontent\\.com/[^\"\\\\]+)\"\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)");
            Matcher entries=entry.matcher(longest);
            while(entries.find()){
                int width=Integer.parseInt(entries.group(3)), height=Integer.parseInt(entries.group(4));
                if(width<=0||height<=0)continue;
                String base=quoted(entries.group(2));
                int end=itemEnd(longest,entries.start());
                if(end<0)continue;
                String item=longest.substring(entries.start(),end);
                boolean video=item.contains("76647426");
                if(video)unique.add("v:"+base); // Video: cache poster and play base + "=dv"
                else unique.add(base+"=w1600-h1600");
            }
            // Initial page can omit further pages. Never claim completeness when uncertain.
            result.partial=longest.contains("snAcKc")||unique.size()>=400;
        }
        result.images.addAll(unique);
        if(result.images.isEmpty())throw new IOException("No shared media found; Google may have changed its album page.");
        return result;
    }
    static Result fetch(String url)throws Exception{return parse(get(url));}
}
