package com.cosmerearchives.androidimageeditor;

import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.Surface;

/** Up to six hardware-assisted, muted, looping video surfaces owned by the XR GL thread.
 * MediaPlayer decodes directly to an external OpenGL texture, avoiding repeated
 * CPU bitmap extraction. Java SurfaceTexture operations stay on the XR thread.
 */
public final class GalleryVideoPlayer {
    private static final String TAG="GalleryVideo";
    private final MediaPlayer[] players=new MediaPlayer[6];
    private final SurfaceTexture[] frames=new SurfaceTexture[6];

    public synchronized void start(String[] paths,int[] textureIds){
        stop();
        if(paths==null||textureIds==null)return;
        for(int i=0;i<Math.min(6,Math.min(paths.length,textureIds.length));i++){
            if(paths[i]==null||paths[i].isEmpty()||textureIds[i]==0)continue;
            try{
                final int slot=i;
                SurfaceTexture frame=new SurfaceTexture(textureIds[i]);
                frame.setDefaultBufferSize(1024,1024);
                frames[i]=frame;
                Surface output=new Surface(frame);
                MediaPlayer player=new MediaPlayer();
                players[i]=player;
                player.setDataSource(paths[i]);
                player.setSurface(output);
                output.release();
                player.setLooping(true);
                // Several silent videos can play together without stacking audio.
                player.setVolume(0f,0f);
                player.setOnPreparedListener(p->{
                    synchronized(GalleryVideoPlayer.this){
                        if(players[slot]==p){
                            try{p.start();}catch(RuntimeException e){
                                Log.e(TAG,"Video playback start failed",e);
                            }
                        }
                    }
                });
                player.setOnErrorListener((p,what,extra)->{
                    Log.e(TAG,"Video playback failed "+what+"/"+extra);
                    return true;
                });
                player.prepareAsync();
            }catch(Exception failure){
                Log.e(TAG,"Cannot start gallery video "+i,failure);
                if(players[i]!=null){players[i].release();players[i]=null;}
                if(frames[i]!=null){frames[i].release();frames[i]=null;}
            }
        }
    }

    /** Call only with the native OpenGL context current. */
    public synchronized void update(){
        for(SurfaceTexture frame:frames){
            if(frame==null)continue;
            try{frame.updateTexImage();}catch(RuntimeException ignored){
                // Initial decoder frame may not be ready yet.
            }
        }
    }

    public synchronized void stop(){
        for(int i=0;i<players.length;i++){
            if(players[i]!=null){
                try{players[i].release();}catch(RuntimeException ignored){}
                players[i]=null;
            }
            if(frames[i]!=null){
                try{frames[i].release();}catch(RuntimeException ignored){}
                frames[i]=null;
            }
        }
    }
}
