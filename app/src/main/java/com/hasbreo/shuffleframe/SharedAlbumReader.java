package com.hasbreo.shuffleframe;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

/**
 * Reads public Google Photos shared albums. Google does not provide a supported
 * endpoint for these links; this parser follows the same snAcKc pagination RPC
 * as the Photos web app. Fail closed rather than silently dropping later pages.
 */
final class SharedAlbumReader {
    private static final int MAX_PAGES=150;
    private static final int PAGE_RETRIES=3;
    private static final String RPC="snAcKc";
    private static final String UA="Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/130.0 Mobile Safari/537.36";
    private static final Pattern CALLBACK=Pattern.compile("AF_initDataCallback\\((\\{[\\s\\S]*?\\})\\);");
    private static final Pattern DATA_FIELD=Pattern.compile("(?:^|[,\\{\\s])(?:[\"']?data[\"']?)\\s*:\\s*\\[");
    private static final Pattern REQUEST=Pattern.compile(
        "snAcKc[^}]{0,15000}?request\\s*:\\s*\\[\\s*\"([A-Za-z0-9_-]+)\"\\s*,\\s*null\\s*,\\s*null\\s*,\\s*\"([A-Za-z0-9_-]+)\"",
        Pattern.DOTALL);
    private static final Pattern ALBUM_LINK=Pattern.compile(
        "https://photos\\.google\\.com/share/([A-Za-z0-9_-]+)\\?key=([A-Za-z0-9_-]+)");
    private static final Pattern LEGACY_ENTRY=Pattern.compile(
        "\\[\\s*\"([A-Za-z0-9_-]{12,})\"\\s*,\\s*\\[\\s*\"(https://lh[3-6]\\.googleusercontent\\.com/[^\"\\\\]+)\"\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)");

    interface Progress { void update(int count,int pages); }
    static final class Result {
        ArrayList<String> images=new ArrayList<>();
        String title="";
        boolean partial=false; // Always false on success: incomplete imports throw instead.
        int pages=0;
    }

    static boolean allowed(String url){
        try{
            URI u=new URI(url);
            return "https".equalsIgnoreCase(u.getScheme()) &&
                ("photos.app.goo.gl".equalsIgnoreCase(u.getHost())||
                 "photos.google.com".equalsIgnoreCase(u.getHost()));
        }catch(Exception e){return false;}
    }
    static String read(InputStream in)throws IOException{
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        byte[] buffer=new byte[32768];int n;
        while((n=in.read(buffer))!=-1){
            b.write(buffer,0,n);
            if(b.size()>30_000_000)throw new IOException("Album page exceeds 30 MB");
        }
        return b.toString("UTF-8");
    }
    static String get(String url)throws Exception{
        for(int attempt=0;attempt<6;attempt++){
            if(!allowed(url))throw new IOException("Unexpected album redirect");
            HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
            c.setConnectTimeout(20000);c.setReadTimeout(35000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent",UA);
            try{
                int code=c.getResponseCode();
                if(code>=300&&code<400){
                    String target=c.getHeaderField("Location");
                    if(target==null)throw new IOException("Album redirect missing destination");
                    url=new URL(new URL(url),target).toString();
                    continue;
                }
                if(code!=200)throw new IOException("Album HTTP "+code);
                try(InputStream in=c.getInputStream()){return read(in);}
            }finally{c.disconnect();}
        }
        throw new IOException("Too many album redirects");
    }
    private static String balancedArray(String text,int start)throws IOException{
        if(start<0||start>=text.length()||text.charAt(start)!='[')
            throw new IOException("Album data array missing");
        int depth=0;
        char quote=0;
        boolean escape=false;
        for(int i=start;i<text.length();i++){
            char c=text.charAt(i);
            if(quote!=0){
                if(escape)escape=false;
                else if(c=='\\')escape=true;
                else if(c==quote)quote=0;
            }else if(c=='"'||c=='\'')quote=c;
            else if(c=='[')depth++;
            else if(c==']'){
                if(--depth==0)return text.substring(start,i+1);
            }
        }
        throw new IOException("Unclosed Google Photos data array");
    }
    static JSONArray initialData(String html)throws IOException{
        Matcher callbacks=CALLBACK.matcher(html);
        JSONArray best=null;
        int bestCount=-1;
        while(callbacks.find()){
            String script=callbacks.group(1);
            Matcher matches=DATA_FIELD.matcher(script);
            while(matches.find()){
                int start=script.indexOf('[',matches.start());
                try{
                    JSONArray candidate=new JSONArray(balancedArray(script,start));
                    JSONArray media=candidate.optJSONArray(1);
                    if(media!=null&&media.length()>bestCount){
                        best=candidate;
                        bestCount=media.length();
                    }
                }catch(JSONException|IOException ignored){
                    // Other callbacks may contain non-media data. Inspect the next one.
                }
            }
        }
        if(best==null)throw new IOException("Cannot parse Google Photos initial album data");
        return best;
    }
    static String pageToken(JSONArray data){
        String token=data.optString(2,"");
        return token.isEmpty()||"null".equals(token)?null:token;
    }
    static void appendPage(JSONArray data,LinkedHashMap<String,String> unique)throws IOException{
        JSONArray entries=data.optJSONArray(1);
        if(entries==null)throw new IOException("Google Photos returned a page without media items");
        for(int i=0;i<entries.length();i++){
            JSONArray entry=entries.optJSONArray(i);
            if(entry==null)throw new IOException("Unrecognised album entry "+i);
            String uid=entry.optString(0,"");
            JSONArray detail=entry.optJSONArray(1);
            if(uid.isEmpty()||detail==null)throw new IOException("Unrecognised album media entry "+i);
            String base=detail.optString(0,"");
            int width=detail.optInt(1,0),height=detail.optInt(2,0);
            if(!base.startsWith("https://lh")||width<=0||height<=0)
                throw new IOException("Missing photo or video information in entry "+i);
            // Google Photos encodes video info in entry[9] under key 76647426.
            Object metadata=entry.opt(9);
            boolean video=metadata instanceof JSONObject &&
                ((JSONObject)metadata).has("76647426");
            if(!video&&metadata!=null&&metadata!=JSONObject.NULL)
                video=metadata.toString().contains("\"76647426\"");
            unique.put(uid,video?"v:"+base:base+"=w1600-h1600");
        }
    }
    static String[] requestKeys(String html)throws IOException{
        Matcher m=REQUEST.matcher(html);
        if(m.find())return new String[]{m.group(1),m.group(2)};
        // Some Photos pages include a canonical share link instead of request metadata.
        m=ALBUM_LINK.matcher(html.replace("&amp;","&").replace("\\u003d","="));
        if(m.find())return new String[]{m.group(1),m.group(2)};
        throw new IOException("Google Photos didn't supply pagination credentials; old album kept");
    }
    static JSONArray parseBatchResponse(String body)throws IOException{
        String[] lines=body.split("\\r?\\n");
        for(String line:lines){
            String current=line.trim();
            if(!current.startsWith("[["))continue; // skips Google's )]}' prefix & length
            try{
                JSONArray outer=new JSONArray(current);
                for(int i=0;i<outer.length();i++){
                    JSONArray response=outer.optJSONArray(i);
                    if(response==null||!"wrb.fr".equals(response.optString(0))||
                        !RPC.equals(response.optString(1)))continue;
                    String payload=response.optString(2,"");
                    JSONArray data=new JSONArray(payload);
                    if(data.optJSONArray(1)==null)
                        throw new IOException("Paginated response contained no media array");
                    return data;
                }
            }catch(JSONException e){throw new IOException("Invalid Google Photos batch response",e);}
        }
        throw new IOException("Google Photos returned no pagination data");
    }
    static JSONArray fetchPage(String albumKey,String authKey,String token)throws Exception{
        JSONArray inner=new JSONArray()
            .put(albumKey).put(token).put(JSONObject.NULL).put(authKey);
        JSONArray rpc=new JSONArray()
            .put(RPC).put(inner.toString()).put(JSONObject.NULL).put("generic");
        JSONArray envelope=new JSONArray().put(new JSONArray().put(rpc));
        String post="f.req="+URLEncoder.encode(envelope.toString(),"UTF-8");
        String endpoint="https://photos.google.com/u/0/_/PhotosUi/data/batchexecute?rpcids="+RPC+
            "&source-path="+URLEncoder.encode("/share/"+albumKey,"UTF-8");
        IOException last=null;
        for(int attempt=0;attempt<PAGE_RETRIES;attempt++){
            HttpURLConnection c=null;
            try{
                c=(HttpURLConnection)new URL(endpoint).openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(20000);c.setReadTimeout(35000);
                c.setInstanceFollowRedirects(false);
                c.setDoOutput(true);
                c.setRequestProperty("User-Agent",UA);
                c.setRequestProperty("Content-Type","application/x-www-form-urlencoded;charset=UTF-8");
                c.setRequestProperty("Origin","https://photos.google.com");
                c.setRequestProperty("Referer",
                    "https://photos.google.com/share/"+albumKey+"?key="+authKey);
                byte[] bytes=post.getBytes("UTF-8");
                c.setFixedLengthStreamingMode(bytes.length);
                try(OutputStream out=c.getOutputStream()){out.write(bytes);}
                int status=c.getResponseCode();
                if(status==429||status==408||status>=500)
                    throw new IOException("Google Photos temporarily unavailable: HTTP "+status);
                if(status!=200)
                    throw new IOException("Google Photos pagination HTTP "+status);
                try(InputStream in=c.getInputStream()){
                    return parseBatchResponse(read(in));
                }
            }catch(IOException e){
                last=e;
                if(attempt<PAGE_RETRIES-1){
                    try{Thread.sleep((attempt+1)*1000L);}
                    catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IOException("Import interrupted",ex);}
                }
            }finally{if(c!=null)c.disconnect();}
        }
        throw new IOException("Couldn't retrieve next album page: "+(last==null?"unknown error":last.getMessage()),last);
    }
    private static Result legacy(String html)throws IOException{
        Result r=new Result();
        Matcher scripts=CALLBACK.matcher(html);
        String largest="";
        while(scripts.find())if(scripts.group(1).length()>largest.length())
            largest=scripts.group(1);
        Matcher matcher=LEGACY_ENTRY.matcher(largest);
        LinkedHashSet<String> urls=new LinkedHashSet<>();
        while(matcher.find()){
            String base=matcher.group(2);
            int end=matcher.end();
            int outerEnd=largest.indexOf("],",end);
            boolean video=outerEnd>end&&
                largest.substring(end,Math.min(outerEnd+2,largest.length())).contains("76647426");
            urls.add(video?"v:"+base:base+"=w1600-h1600");
        }
        if(urls.isEmpty())throw new IOException("Couldn't recognise shared-album media");
        if(urls.size()>=300)
            throw new IOException("Album may contain more than 300 items, but pagination is unavailable. Existing album preserved.");
        r.images.addAll(urls);r.pages=1;
        return r;
    }
    static Result fetch(String link)throws Exception{return fetch(link,null);}
    static Result fetch(String link,Progress progress)throws Exception{
        String html=get(link);
        Matcher title=Pattern.compile("<title>([^<]*)</title>").matcher(html);
        String albumName=title.find()?title.group(1).replace(" - Google Photos","").replace("&amp;","&"):"";
        JSONArray first;
        try{first=initialData(html);}
        catch(IOException ex){
            Result fallback=legacy(html);
            fallback.title=albumName;
            if(progress!=null)progress.update(fallback.images.size(),1);
            return fallback;
        }
        LinkedHashMap<String,String> unique=new LinkedHashMap<>();
        appendPage(first,unique);
        String token=pageToken(first);
        // A full initial batch with no token could indicate a changed Google response format.
        if(token==null&&first.optJSONArray(1).length()>=300)
            throw new IOException("A full first page has no continuation token. Not replacing existing album.");
        int pages=1;
        if(progress!=null)progress.update(unique.size(),pages);
        if(token!=null){
            String[] keys=requestKeys(html);
            HashSet<String> tokens=new HashSet<>();
            while(token!=null){
                if(pages>=MAX_PAGES)
                    throw new IOException("Album exceeds supported page count; existing album kept");
                if(!tokens.add(token))
                    throw new IOException("Google Photos repeated a pagination token");
                JSONArray next=fetchPage(keys[0],keys[1],token);
                int before=unique.size();
                appendPage(next,unique);
                pages++;
                if(progress!=null)progress.update(unique.size(),pages);
                if(next.optJSONArray(1).length()>0&&unique.size()==before)
                    throw new IOException("Album pagination repeated a full page");
                token=pageToken(next);
            }
        }
        if(unique.isEmpty())throw new IOException("Shared album has no media");
        Result result=new Result();
        result.images.addAll(unique.values());
        result.title=albumName;
        result.pages=pages;
        return result;
    }
}
