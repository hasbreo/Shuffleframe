package com.hasbreo.shuffleframe;

import org.junit.Test;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.HashSet;
import static org.junit.Assert.*;

public class SharedAlbumReaderTest {
    private static JSONArray entry(int id,boolean video) throws Exception {
        JSONArray e=new JSONArray();
        e.put(String.format(java.util.Locale.US,"unique_album_%07d",id));
        e.put(new JSONArray().put("https://lh3.googleusercontent.com/media"+id).put(1600).put(1200));
        for(int i=2;i<=9;i++)e.put(i,JSONObject.NULL);
        if(video)e.put(9,new JSONObject().put("76647426",new JSONArray().put("video")));
        return e;
    }
    private static JSONArray page(int start,int count,String next) throws Exception {
        JSONArray items=new JSONArray();
        for(int i=start;i<start+count;i++)items.put(entry(i,i%17==0));
        return new JSONArray().put(JSONObject.NULL).put(items).put(next==null?JSONObject.NULL:next);
    }
    @Test public void loads1500ItemsAcrossFivePagesWithoutDroppingVideos() throws Exception {
        LinkedHashMap<String,String> items=new LinkedHashMap<>();
        for(int n=0;n<5;n++){
            JSONArray data=page(n*300,300,n==4?null:"token"+n);
            SharedAlbumReader.appendPage(data,items);
            assertEquals((n+1)*300,items.size());
            assertEquals(n==4?null:"token"+n,SharedAlbumReader.pageToken(data));
        }
        assertEquals(1500,items.size());
        assertEquals(89,items.values().stream().filter(v->v.startsWith("v:")).count());
    }
    @Test public void keepsUniqueIDsEvenIfPageRepeated() throws Exception {
        LinkedHashMap<String,String> result=new LinkedHashMap<>();
        SharedAlbumReader.appendPage(page(0,300,"NEXT"),result);
        SharedAlbumReader.appendPage(page(299,300,null),result);
        assertEquals(599,result.size());
    }
    @Test public void findsTokenInEmbeddedAlbumHtml() throws Exception {
        JSONArray first=page(0,300,"NEXT-PAGE");
        String html="<script>AF_initDataCallback({key:'ds:2',data:"+first.toString()+",sideChannel:{}});</script>";
        JSONArray decoded=SharedAlbumReader.initialData(html);
        assertEquals(300,decoded.getJSONArray(1).length());
        assertEquals("NEXT-PAGE",SharedAlbumReader.pageToken(decoded));
    }
    @Test public void parsesGoogleBatchRpcPayload() throws Exception {
        JSONArray data=page(300,300,"PAGE-3");
        JSONArray response=new JSONArray().put("wrb.fr").put("snAcKc").put(data.toString()).put(JSONObject.NULL);
        String body=")]}'\n555\n"+new JSONArray().put(response).toString()+"\n";
        JSONArray decoded=SharedAlbumReader.parseBatchResponse(body);
        assertEquals(300,decoded.getJSONArray(1).length());
        assertEquals("PAGE-3",SharedAlbumReader.pageToken(decoded));
    }
    @Test public void findsContinuationRequestCredentials() throws Exception {
        String html="...snAcKc,request:[\"album_123\",null,null,\"key_456\"],...}";
        assertArrayEquals(new String[]{"album_123","key_456"},SharedAlbumReader.requestKeys(html));
    }
    @Test public void rejectsMalformedPagedDataRatherThanDroppingItems() throws Exception {
        JSONArray invalid=new JSONArray().put(JSONObject.NULL)
            .put(new JSONArray().put(new JSONArray().put("bad").put(JSONObject.NULL)));
        try {
            SharedAlbumReader.appendPage(invalid,new LinkedHashMap<>());
            fail("Invalid entries must abort incomplete imports");
        }catch(java.io.IOException expected){
            assertTrue(expected.getMessage().contains("media entry"));
        }
    }
}
