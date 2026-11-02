package com.hasbreo.shuffleframe;

import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

public class PhotoStorageTest {
    @Test public void acceptsVideoDownloadRedirectHosts() throws Exception {
        assertEquals("https://video-downloads.googleusercontent.com/Abc",
            PhotoStorage.validateUrl("https://video-downloads.googleusercontent.com/Abc",true));
        assertEquals("https://rr1.googlevideo.com/file",
            PhotoStorage.validateUrl("https://rr1.googlevideo.com/file",true));
        assertEquals("https://lh3.googleusercontent.com/p/Test=dv",
            PhotoStorage.validateUrl("https://lh3.googleusercontent.com/p/Test=dv",true));
    }
    @Test public void preventsRedirectsToUntrustedHosts() throws Exception {
        for(String url:new String[]{
            "https://evil.googleusercontent.com/video",
            "https://video-downloads.googleusercontent.com.evil.com/file",
            "http://video-downloads.googleusercontent.com/file",
            "https://127.0.0.1/secret",
            "https://photos.google.com/album"
        }){
            try{PhotoStorage.validateUrl(url,true);fail("Unexpectedly allowed "+url);}
            catch(IOException expected){}
        }
    }
    @Test public void neverUsesVideoCdnForOrdinaryPhotoRequests() throws Exception {
        try{
            PhotoStorage.validateUrl("https://video-downloads.googleusercontent.com/abc",false);
            fail("Photo must not use video CDN");
        }catch(IOException expected){}
    }
    @Test public void addsVideoDownloadSuffix() throws Exception {
        assertEquals("https://lh3.googleusercontent.com/p/abc=dv",
            PhotoStorage.streamUrl("v:https://lh3.googleusercontent.com/p/abc"));
    }
}
