package com.hasbreo.shuffleframe;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

final class SharedAlbumReader {
 static final class Result {ArrayList<String> images=new ArrayList<>();String title="";boolean partial=false;}
 static boolean allowed(String url){try{URI u=new URI(url);return "https".equals(u.getScheme()) && ("photos.app.goo.gl".equals(u.getHost())||"photos.google.com".equals(u.getHost()));}catch(Exception e){return false;}}
 static String get(String url)throws Exception{
   for(int i=0;i<5;i++){
    if(!allowed(url))throw new IOException("Unexpected redirect host");
    HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
    c.setConnectTimeout(20000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(false);
    c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/128.0.0.0 Mobile Safari/537.36");
    int status=c.getResponseCode();
    if(status>=300&&status<400){String location=c.getHeaderField("Location");url=new URL(new URL(url),location).toString();c.disconnect();continue;}
    if(status!=200)throw new IOException("HTTP "+status);
    try(InputStream in=c.getInputStream()){return read(in);}finally{c.disconnect();}
   }throw new IOException("Too many redirects");
 }
 static String read(InputStream in)throws Exception{
  ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[16384];int n;
  while((n=in.read(buf))!=-1){b.write(buf,0,n);if(b.size()>15_000_000)throw new IOException("Album page too large");}
  return b.toString("UTF-8");
 }
 static String quoted(String s){return s.replace("\\/","/").replace("\\u003d","=").replace("\\u0026","&");}
 static Result fetch(String url)throws Exception{
  String html=get(url);Result result=new Result();
  Matcher title=Pattern.compile("<title>([^<]*)</title>").matcher(html);
  if(title.find())result.title=title.group(1).replace(" - Google Photos","").replace("&amp;","&");
  Set<String> unique=new LinkedHashSet<>();
  // Google's public album page embeds image base URLs in AF_initDataCallback JSON-like scripts.
  Matcher scripts=Pattern.compile("AF_initDataCallback\\((\\{[\\s\\S]*?\\})\\);").matcher(html);
  String longest="";
  while(scripts.find())if(scripts.group(1).length()>longest.length())longest=scripts.group(1);
  if(!longest.isEmpty()){
   Matcher entries=Pattern.compile("\\[\\s*\"([A-Za-z0-9_-]{12,})\"\\s*,\\s*\\[\\s*\"(https://lh[3-6]\\.googleusercontent\\.com/[^\"\\\\]+)\"\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)").matcher(longest);
   while(entries.find()){
     String base=quoted(entries.group(2));int width=Integer.parseInt(entries.group(3)),height=Integer.parseInt(entries.group(4));
     // Request screen-sized previews rather than full originals to keep memory bounded.
     if(width>0&&height>0)unique.add(base+"=w1600-h1600");
   }
   // This first version deliberately reports partial if pagination is present; never silently claim full import.
   Matcher token=Pattern.compile("\"data\"\\s*:\\s*\\[[\\s\\S]{0,300}").matcher(longest);
   result.partial=longest.contains("snAcKc") || (unique.size()>=400);
  }
  result.images.addAll(unique);
  if(result.images.isEmpty())throw new IOException("Could not read public album photos; Google's page format may have changed.");
  return result;
 }
}
