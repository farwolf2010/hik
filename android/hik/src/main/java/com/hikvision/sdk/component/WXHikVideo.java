package com.hikvision.sdk.component;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.farwolf.util.ActivityManager;
import com.farwolf.util.DateTool;
import com.farwolf.util.ScreenTool_;
import com.farwolf.weex.annotation.WeexComponent;
import com.farwolf.weex.util.Const;
import com.farwolf.weex.view.LoadingView;
import com.hik.mcrsdk.rtsp.RtspClient;
import com.hikvision.sdk.VMSNetSDK;
import com.hikvision.sdk.consts.SDKConstant;
import com.hikvision.sdk.net.bean.CameraInfo;
import com.hikvision.sdk.net.bean.CustomRect;
import com.hikvision.sdk.net.bean.RecordInfo;
import com.hikvision.sdk.net.bean.RecordSegment;
import com.hikvision.sdk.net.business.OnVMSNetSDKBusiness;
import com.hikvision.sdk.utils.FileUtils;
import com.hikvision.sdk.utils.SDKUtil;
import com.hikvision.sdk.widget.CustomSurfaceView;
import com.taobao.weex.WXSDKInstance;
import com.taobao.weex.annotation.JSMethod;
import com.taobao.weex.bridge.JSCallback;
import com.taobao.weex.ui.action.BasicComponentData;
import com.taobao.weex.ui.component.WXComponent;
import com.taobao.weex.ui.component.WXVContainer;

import org.apache.calcite.linq4j.Linq4j;
import org.apache.calcite.linq4j.function.Function1;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

@WeexComponent(name="hikvideo")
public class WXHikVideo extends WXComponent<FrameLayout> implements SurfaceHolder.Callback{

//    private SubResourceNodeBean mCamera;

    int videoType=0;

    String recordVidepath;
    LoadingView loading;

    String id;
    String date;
    String level;
    ViewGroup parent;
    boolean isFullScreen;
    CustomSurfaceView videoView;
    FrameLayout container;


    private int PLAY_WINDOW_ONE = 1;

    public WXHikVideo(WXSDKInstance instance, WXVContainer parent, BasicComponentData basicComponentData) {
        super(instance, parent, basicComponentData);
//        PLAY_WINDOW_ONE=new Random().nextInt(10000);
    }



    @Override
    protected FrameLayout initComponentHostView(@NonNull Context context) {
        FrameLayout f=new FrameLayout(context);
        FrameLayout container=new FrameLayout(context);
        CustomSurfaceView c= new CustomSurfaceView(context);
        c.getHolder().addCallback(this);
        container.addView(c,new FrameLayout.LayoutParams(-1, -1));
        f.addView(container,new FrameLayout.LayoutParams(-1, -1));
        container.setBackgroundColor(Color.BLACK);
        videoView=c;
        this.container=container;
        LoadingView l=new LoadingView(context);
        int size=ScreenTool_.getInstance_(context).toDip(30);
        FrameLayout.LayoutParams lpx=new FrameLayout.LayoutParams(size,size);
        lpx.gravity= Gravity.CENTER;
        l.setLayoutParams(lpx);
        l.setStyle(LoadingView.BallSpinFadeLoader);
        f.addView(l);
        l.setVisibility(View.GONE);
        loading=l;
        return f;
    }

    @Override
    protected void onHostViewInitialized(FrameLayout host) {
        super.onHostViewInitialized(host);
        container.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(isFullScreen())
                quitWindowFullscreen();
            }
        });
//        mCamera = (SubResourceNodeBean) ((Activity) getInstance().getContext()).getIntent().getSerializableExtra(Constants.IntentKey.CAMERA);
    }


    @JSMethod
    public void play(HashMap param,final JSCallback callback){

        String type=param.get("type")+"";
         if("real".equals(type)){
             this.realPlay(param,callback);
         }else{
             this.playBack(param,callback);
         }
    }

    @JSMethod
    public void capture(JSCallback callback){
        String filename= "/Picture" + System.currentTimeMillis() + ".jpg";
        String path=FileUtils.getPictureDirPath().getAbsolutePath()+filename;
        VMSNetSDK.getInstance().captureLiveOpt(PLAY_WINDOW_ONE, FileUtils.getPictureDirPath().getAbsolutePath(), filename);
        HashMap m=new HashMap();
        path=Const.PREFIX_SDCARD+path;
        m.put("path",path);
        callback.invoke(m);
    }

    @JSMethod
    public void startRecord(JSCallback callback){
        String filename="Video" + System.currentTimeMillis() + ".mp4";
       this.recordVidepath=   FileUtils.getVideoDirPath().getAbsolutePath()+"/"+filename;

        int recordOpt = VMSNetSDK.getInstance().startLiveRecordOpt(PLAY_WINDOW_ONE, FileUtils.getVideoDirPath().getAbsolutePath(), filename);
          HashMap m=new HashMap();
          int code=0;
        switch (recordOpt) {
            case SDKConstant.LiveSDKConstant.SD_CARD_UN_USABLE:
                code=1;
                break;
            case SDKConstant.LiveSDKConstant.SD_CARD_SIZE_NOT_ENOUGH:
                code=2;
                break;
            case SDKConstant.LiveSDKConstant.RECORD_FAILED:
               code=3;

                break;
            case SDKConstant.LiveSDKConstant.RECORD_SUCCESS:
                code=0;
                break;
        }
        m.put("code",0);
        callback.invoke(m);
    }

    @JSMethod
    public void stopRecord(JSCallback callback){
        VMSNetSDK.getInstance().stopLiveRecordOpt(PLAY_WINDOW_ONE);
        HashMap m=new HashMap();
        m.put("path",Const.PREFIX_SDCARD+recordVidepath);
        callback.invoke(m);
    }

    @JSMethod
    public void enterFullScreen(){
        enterWindowFullscreen();
    }


    @JSMethod
    public void queryRecordInfo(HashMap m,final JSCallback callback) {
        final String date=m.get("date")+"";
        final String id=m.get("id")+"";
        getCameraInfo(id,new JSCallback() {
            @Override
            public void invoke(Object data) {

                HashMap m=(HashMap)data;
                CameraInfo mCameraInfo = (CameraInfo)m.get("info");
                int[] mRecordPos = SDKUtil.processStorageType(mCameraInfo);
                String[] mGuids = SDKUtil.processGuid(mCameraInfo);
                int mStorageType=0;
                if (null != mRecordPos && 0 < mRecordPos.length) {
                    mStorageType = mRecordPos[0];
                }
                String mGuid="";
                if (null != mGuids && 0 < mGuids.length) {
                    mGuid = mGuids[0];
                }
                final Calendar calendar = Calendar.getInstance();
                calendar.setTime(DateTool.getDate(date,"yyyy-MM-dd"));
//        calendar.setTime();
                int year = calendar.get(Calendar.YEAR);
                int month = calendar.get(Calendar.MONTH);
                int day = calendar.get(Calendar.DAY_OF_MONTH);
                Calendar queryStartTime = Calendar.getInstance();
                Calendar queryEndTime = Calendar.getInstance();
                queryStartTime.set(year, month, day, 0, 0, 0);
                queryEndTime.set(year, month, day, 23, 59, 59);
                VMSNetSDK.getInstance().queryRecordSegment(PLAY_WINDOW_ONE, mCameraInfo, queryStartTime, queryEndTime, mStorageType, mGuid, new OnVMSNetSDKBusiness() {
                    @Override
                    public void onFailure() {

                    }

                    @Override
                    public void onSuccess(Object obj) {
                        if (obj instanceof RecordInfo) {
                            RecordInfo  mRecordInfo = ((RecordInfo) obj);
                            if (null != mRecordInfo.getSegmentList() && 0 < mRecordInfo.getSegmentList().size()) {
                                List l=  Linq4j.asEnumerable( mRecordInfo.getSegmentList()).select(new Function1<RecordSegment, HashMap>() {
                                    @Override
                                    public HashMap apply(RecordSegment recordSegment) {
                                        HashMap m=new HashMap();
                                        String start=DateTool.Pattern(SDKUtil.convertTimeString(recordSegment.getBeginTime()).getTime(),"yyyy-MM-dd HH:mm:ss");
                                        String end=DateTool.Pattern(SDKUtil.convertTimeString(recordSegment.getEndTime()).getTime(),"yyyy-MM-dd HH:mm:ss");
                                        m.put("start",start);
                                        m.put("end",end);
                                        return m;
                                    }
                                }).toList();
                                HashMap m=new HashMap();
                                m.put("list",l);
                                callback.invoke(m);

                            } else {

                            }
                        }
                    }
                });
            }

            @Override
            public void invokeAndKeepAlive(Object data) {

            }
        });




    }

    @JSMethod
    public void quitFullScrren(){
        quitWindowFullscreen();
    }

    public void   hideStausbar(boolean enable) {
        Activity ac=(Activity)getInstance().getContext();
        Window w=ac.getWindow();
        if (enable) {
            WindowManager.LayoutParams attrs = w.getAttributes();
            attrs.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
           w.setAttributes(attrs);
        } else {
            WindowManager.LayoutParams attrs = w.getAttributes();
            attrs.flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
            w.setAttributes(attrs);
        }
    }
    //全屏

    public void enterWindowFullscreen() {
              this.isFullScreen=true;
            ViewGroup vp = (ViewGroup) container.getParent();
            if (vp != null)
                vp.removeView(container);
            this.parent=vp;
            ViewGroup decorView = (ViewGroup) (ActivityManager.getInstance().getCurrentActivity()).getWindow().getDecorView();
            //.findViewById(Window.ID_ANDROID_CONTENT);
            vp.setBackgroundColor(Color.RED);
            SET_LANDSCAPE(getContext());
            decorView.addView(container, new FrameLayout.LayoutParams(-1, -1));
            HashMap m=new HashMap();
            m.put("id",this.id);
            m.put("level",this.level);
            realPlay(m,null);
             hideStausbar(true);
//            setStateAndMode(currentState, MODE_WINDOW_FULLSCREEN);

    }

    public boolean isFullScreen() {
        ViewGroup decorView = (ViewGroup) (ActivityManager.getInstance().getCurrentActivity()).getWindow().getDecorView();
       return container.getParent()==decorView;

    }

    //竖屏
    public static void SET_PORTRAIT(Context context) {
        scanForActivity(context).setRequestedOrientation
                (ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    public static Activity scanForActivity(Context context) {
        if (context instanceof Activity) {
            Activity a = (Activity) context;
            if (a.getParent() != null)
                return a.getParent();
            else
                return a;
        } else if (context instanceof ContextWrapper) {
            return scanForActivity(((ContextWrapper) context).getBaseContext());
        }
        throw new IllegalStateException("context得不到activity");
    }

    //横屏
    public static void SET_LANDSCAPE(Context context) {
        scanForActivity(context).setRequestedOrientation
                (ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    //退出全屏

    public void quitWindowFullscreen() {
        this.isFullScreen=false;
        if(this.parent==null)
            return;
        ViewGroup vp = (ViewGroup) container.getParent();
        if (vp != null)
            vp.removeView(container);
        SET_PORTRAIT(getContext());
        FrameLayout.LayoutParams lp= new FrameLayout.LayoutParams(-1, -1);
        container.setLayoutParams(lp);
        this.parent.addView(container);
        HashMap m=new HashMap();
        m.put("id",this.id);
        m.put("level",this.level);
        realPlay(m,null);
        hideStausbar(false);
    }

    @Override
    public boolean onActivityBack() {
        if(this.isFullScreen){
            quitWindowFullscreen();
            return false;
        }
        return super.onActivityBack();
    }

    @JSMethod
    public void control(HashMap param){
        final int command=Integer.parseInt(param.get("command")+"");
          int speed=5;
          if(param.containsKey("speed")){
              speed=  Integer.parseInt(param.get("speed")+"");
          }
         final  int tempint=speed;


        final boolean end=Boolean.parseBoolean(param.get("end")+"");
        final boolean stop=Boolean.parseBoolean(param.get("stop")+"");
        new Thread(new Runnable() {
            @Override
            public void run() {

                VMSNetSDK.getInstance().sendPTZCtrlCommand(PLAY_WINDOW_ONE, false, !end?"START":"STOP", command, tempint, new OnVMSNetSDKBusiness() {
                    @Override
                    public void onFailure() {
                        Log.e("hik","fail");
                    }

                    @Override
                    public void onSuccess(Object obj) {
                        Log.e("hik","success");

                    }
                });
            }
        }).start();

    }

    @JSMethod
    public void audio(boolean audio){

       if(videoType==0){
           if(audio){
               VMSNetSDK.getInstance().startLiveAudioOpt(PLAY_WINDOW_ONE);
           }else{
               VMSNetSDK.getInstance().stopLiveAudioOpt(PLAY_WINDOW_ONE);
           }
       }else{
           if(audio){
               VMSNetSDK.getInstance().startPlayBackAudioOpt(PLAY_WINDOW_ONE);
           }else{
               VMSNetSDK.getInstance().stopPlayBackAudioOpt(PLAY_WINDOW_ONE);
           }
       }
    }




    @JSMethod
    public void enableZoom(boolean zoom){
        boolean isZoom =zoom;
        if (isZoom) {
//
            videoView.setOnZoomListener(new CustomSurfaceView.OnZoomListener() {
                @Override
                public void onZoomChange(CustomRect original, CustomRect current) {
                    VMSNetSDK.getInstance().zoomLiveOpt(PLAY_WINDOW_ONE, true, original, current);
                }
            });
        } else {
            videoView.setOnZoomListener(null);
            VMSNetSDK.getInstance().zoomLiveOpt(PLAY_WINDOW_ONE, false, null, null);
        }
    }


    Handler handler = new Handler() {
        @Override
        public void handleMessage(final Message msg) {

            ((Activity)getContext()).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String m=msg.obj+"";
                    loading.setVisibility(showLoading?View.VISIBLE:View.GONE);
//                    loading.setVisibility(View.VISIBLE);
                }
            });
            super.handleMessage(msg);
        }
    };

    boolean showLoading=false;

    public void sendMsg(boolean m){
        showLoading=m;
        handler.sendEmptyMessage(0);

    }

    @JSMethod
    public void realPlay(final HashMap param,final JSCallback state){

        sendMsg(true);
        videoType=0;
        final String id=param.get("id")+"";
        final String level=param.get("level")+"";
        this.id=id;
        this.level=level;
//        if(state!=null){
//            HashMap m=new HashMap<>();
//            m.put("err",-1);
//            state.invokeAndKeepAlive(m);
//        }
        new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                VMSNetSDK.getInstance().startLiveOpt(PLAY_WINDOW_ONE, id, videoView, getVideoLevel(level), new OnVMSNetSDKBusiness() {
                    @Override
                    public void onFailure() {
                        HashMap m=new HashMap<>();
                        m.put("err",1);
                        if(state!=null)
                        state.invoke(m);
                        sendMsg(false);
                    }

                    @Override
                    public void onSuccess(Object obj) {
                        HashMap m=new HashMap<>();
                        m.put("err",0);
                        if(state!=null)
                        state.invoke(m);
                        sendMsg(false);
                    }
                });
                Looper.loop();
            }
        }.start();
    }

    private void queryRecordSegment(String date,final CameraInfo mCameraInfo ,final JSCallback callback) {

        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat sd=new SimpleDateFormat("yyyy-MM-dd");
        try {
            Date d= sd.parse(date);
            calendar.setTime(d);
        }catch (Exception e){

        }
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        Calendar queryStartTime = Calendar.getInstance();
        Calendar queryEndTime = Calendar.getInstance();
        queryStartTime.set(year, month, day, 0, 0, 0);
        queryEndTime.set(year, month, day, 23, 59, 59);
        int[] mRecordPos = SDKUtil.processStorageType(mCameraInfo);
        String[] mGuids = SDKUtil.processGuid(mCameraInfo);

        VMSNetSDK.getInstance().queryRecordSegment(PLAY_WINDOW_ONE, mCameraInfo, queryStartTime, queryEndTime, mRecordPos[0], mGuids[0], new OnVMSNetSDKBusiness() {
            @Override
            public void onFailure() {
                HashMap m=new HashMap();
                m.put("err",1);
                callback.invoke(m);
            }

            @Override
            public void onSuccess(Object obj) {
                if (obj instanceof RecordInfo) {
                    RecordInfo  mRecordInfo = ((RecordInfo) obj);
                    if (null != mRecordInfo.getSegmentList() && 0 < mRecordInfo.getSegmentList().size()) {
                        RecordSegment mRecordSegment = mRecordInfo.getSegmentList().get(0);
                        //级联设备的时候使用录像片段中的时间
                        HashMap m=new HashMap();
                        m.put("recordInfo",mRecordInfo);
                        if (SDKConstant.CascadeFlag.CASCADE == mCameraInfo.getCascadeFlag()) {
                            Calendar  mEndTime = SDKUtil.convertTimeString(mRecordSegment.getEndTime());
                            Calendar mStartTime = SDKUtil.convertTimeString(mRecordSegment.getBeginTime());
                            Calendar  mFirstStartTime = mStartTime;
                            m.put("start",mStartTime);
                            m.put("end",mEndTime);

//                            m.put("start",mStartTime);
                        }
                        callback.invoke(m);
//                        mMessageHandler.sendEmptyMessage(QUERY_SUCCESS);
                    } else {
                        HashMap m=new HashMap();
                        m.put("err",1);
                        callback.invoke(m);
//                        mMessageHandler.sendEmptyMessage(QUERY_FAILURE);
                    }
                }
            }
        });

    }
    /**
     * 获取监控点详细信息
     */
    public void getCameraInfo(String id,final JSCallback callback) {


        VMSNetSDK.getInstance().getPlayBackCameraInfo(PLAY_WINDOW_ONE, id, new OnVMSNetSDKBusiness() {
            @Override
            public void onFailure() {

            }

            @Override
            public void onSuccess(Object obj) {
                if (obj instanceof CameraInfo) {
                    CameraInfo  mCameraInfo = (CameraInfo) obj;
                    HashMap m=new HashMap();
                    m.put("info",mCameraInfo);
                    if(callback!=null)
                    callback.invoke(m);
                }
            }
        });
    }


    @JSMethod
    public void updatePlayBack(final HashMap param,final JSCallback callback){

       this.playBack(param,callback);
    }

    @JSMethod
    public void getPlayTime(JSCallback callback){
        long osd = VMSNetSDK.getInstance().getOSDTimeOpt(PLAY_WINDOW_ONE);
          HashMap m=new HashMap();
          m.put("time",osd);
          callback.invoke(m);

    }


    public void playBack(final HashMap param,final JSCallback callback){
        String id=param.get("id")+"";
        String date=param.get("date")+"";
        this.id=id;
        this.date=date;
        sendMsg(true);
        this.getCameraInfo(id, new JSCallback() {
            @Override
            public void invoke(Object data) {
               final  CameraInfo info=(CameraInfo)((HashMap)data).get("info");
               final String date=param.get("date")+"";
                queryRecordSegment(date, info, new JSCallback() {
                    @Override
                    public void invoke(Object data) {
                        final  CameraInfo info=(CameraInfo)((HashMap)data).get("info");
                        final  RecordInfo recordInfo=(RecordInfo)((HashMap)data).get("recordInfo");
                        Date fdate= DateTool.getDate(date,"yyyy-MM-dd HH:mm:ss");
                        final  Calendar start=Calendar.getInstance();
                        final  Calendar end=Calendar.getInstance();
                         start.setTime(fdate);
                         end.setTime(fdate);

                        VMSNetSDK.getInstance().startPlayBackOpt(PLAY_WINDOW_ONE, videoView, recordInfo.getSegmentListPlayUrl(), start, end, new OnVMSNetSDKBusiness() {
                            @Override
                            public void onFailure() {
//                mMessageHandler.sendEmptyMessage(START_FAILURE);
                                HashMap m=new HashMap<>();
                                m.put("err",1);
                                callback.invoke(m);
                                sendMsg(false);
                            }

                            @Override
                            public void onSuccess(Object obj) {
//                mMessageHandler.sendEmptyMessage(START_SUCCESS);
                                HashMap m=new HashMap<>();
                                m.put("finish",false);
                                m.put("err",0);
                                callback.invoke(m);
                                sendMsg(false);
                            }

                            @Override
                            public void onStatusCallback(int status) {

                                if (status == RtspClient.RTSPCLIENT_MSG_PLAYBACK_FINISH) {
                                    HashMap m=new HashMap<>();
                                    m.put("err",0);
                                    m.put("finish",true);
                                    callback.invoke(m);
                                }
                            }
                        });
                    }

                    @Override
                    public void invokeAndKeepAlive(Object data) {

                    }
                });
            }

            @Override
            public void invokeAndKeepAlive(Object data) {

            }
        });

    }


    public int getVideoLevel(String level){
        if("fluent".equals(level)){
            return   SDKConstant.LiveSDKConstant.SUB_STANDARD_STREAM;
        }else if("clear".equals(level)){
            return   SDKConstant.LiveSDKConstant.SUB_STREAM;
        }else{
            return   SDKConstant.LiveSDKConstant.MAIN_HIGH_STREAM;
        }
    }


    @JSMethod
    public void stop(){
        if(videoType==0){
            VMSNetSDK.getInstance().stopLiveOpt(PLAY_WINDOW_ONE);
        }else{
            VMSNetSDK.getInstance().stopLiveRecordOpt(PLAY_WINDOW_ONE);
        }
    }


    @JSMethod
    public void pause(){
        if(videoType==0)
        VMSNetSDK.getInstance().pausePlayBackOpt(PLAY_WINDOW_ONE);
        else{
            VMSNetSDK.getInstance().pauseLocalVideo();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

    }
}
