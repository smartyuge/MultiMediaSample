package com.hejunlin.videorecord;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class TakeVideoActivity extends Activity implements SurfaceHolder.Callback, View.OnClickListener {

    private static final String TAG = "TakeVideoActivity";
    private String fileCachePath;
    private long videoCreateTime;
    private SurfaceView surfaceView;
    private Button recordButton;
    private Button unRecordButton;
    private Button previewButton;
    private TextView timeSecondView, curTimeView; //显示时间的文本框
    private ProgressBar progressBar;
    private MediaRecorder mRecorder;

    private Camera mCamera;
    private int mPreviewWidth;
    private int mPreviewHeight;
    private State mState = State.NONE;
    private Camera.Parameters mParameters;

    private enum State {
        NONE, PRIVIEW, RECORDE, COMPLETE;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        //全屏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);   //应用运行时，保持屏幕高亮，不锁屏
        super.onCreate(savedInstanceState);
        getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_take_video);

        //获取控件
        surfaceView = (SurfaceView) findViewById(R.id.surfaceview);
        recordButton = (Button) findViewById(R.id.btn_start_record);
        unRecordButton = (Button) findViewById(R.id.btn_stop_record);
        previewButton = (Button) findViewById(R.id.btn_preview);
        recordButton.setOnClickListener(this);
        unRecordButton.setOnClickListener(this);
        previewButton.setOnClickListener(this);

        timeSecondView = (TextView) findViewById(R.id.time);
        curTimeView = (TextView) findViewById(R.id.tv_time);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        progressBar.setMax(MaxDuring);

        SurfaceHolder holder = surfaceView.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        startTime();

        //获取支持480p视频
        initSupportProfile();
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        //保持预览和视频长宽比一致,不拉伸压缩失真
        surfaceView.setLayoutParams(new FrameLayout.LayoutParams(-1, mVideoHeiht * dm.widthPixels / mVideoWidth, Gravity.TOP));

        fileCachePath = getIntent().getStringExtra("fileCachePath");
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            mCamera = Camera.open(0);//后置摄像头
            mCamera.setDisplayOrientation(90); // Portrait mode
            mCamera.setPreviewDisplay(holder);
            mCamera.startPreview();
        } catch (Exception e) {
            Log.w(TAG, "Can not openDriver: " + e.getMessage());
            mCamera.stopPreview();
            mCamera.release();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "surfaceChanged");
        if (hasResult) {
            return;
        }
        if (holder.getSurface() == null) {
            Log.e(TAG, "Error: preview surface does not exist");
            return;
        }
        mPreviewWidth = mCamera.getParameters().getPreviewSize().width;
        mPreviewHeight = mCamera.getParameters().getPreviewSize().height;
        Log.d(TAG, "mPreviewWidth=" + mPreviewWidth + ",mPreviewHeight=" + mPreviewHeight);
        Log.d(TAG, "surfaceChanged() is called");
        try {
            mCamera.autoFocus(new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean success, Camera camera) {
                    if(success){
                        initCamera();
                        camera.cancelAutoFocus();
                    }
                }
            });
        } catch (Exception e){
            Log.d(TAG, "Error starting camera preview: " + e.getMessage());
        }
    }

    private void initCamera() {
        mParameters = mCamera.getParameters();
        mParameters.setPictureFormat(PixelFormat.JPEG);
        mParameters.setPictureSize(1080,1920);
        mParameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
        mParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        setDispaly(mParameters, mCamera);
        mCamera.setParameters(mParameters);
        mCamera.startPreview();
        mCamera.cancelAutoFocus();
    }

    private void setDispaly(Camera.Parameters parameters,Camera camera) {
        if (Integer.parseInt(Build.VERSION.SDK) >= 8){
            setDisplayOrientation(camera,90);
        } else {
            parameters.setRotation(90);
        }
    }

    private void setDisplayOrientation(Camera camera, int i) {
        Method downPolymorphic;
        try{
            downPolymorphic = camera.getClass().getMethod("setDisplayOrientation", new Class[]{int.class});
            if(downPolymorphic!=null) {
                downPolymorphic.invoke(camera, new Object[]{i});
            }
        }
        catch(Exception e){
            Log.e(TAG, "image error");
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "surfaceDestroyed");
        if (mCamera != null) {
            try {
                mCamera.setPreviewDisplay(null);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mCamera.stopPreview();
            mCamera.release();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            task.cancel();
            timer.cancel();
            if (mCamera != null) {
                mCamera.setPreviewDisplay(null);
                mCamera.stopPreview();
                mCamera.release();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        try {
            if (id == R.id.btn_start_record) {
                startRecorder();
            } else if (id == R.id.btn_stop_record) {
                if (mState == State.RECORDE)
                    stopRecorder();
                else if (mState == State.COMPLETE)
                    skipToCodec();
            } else if (id == R.id.btn_preview) {
                previewVideo();
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (mCamera != null) {
                try {
                    mCamera.setPreviewDisplay(null);
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
                mCamera.stopPreview();
                mCamera.release();
            }
        }
    }

    private void startRecorder() {
        if (mState == State.RECORDE) {
            return;
        }
        if (mState == State.COMPLETE) {
            mCamera.startPreview();//重拍启动预览,这里主要启动对焦程序,如果不启动,则manager不知道已经启动,在stop的时候不会关闭预览
        }
        // 关闭预览并释放资源
        Camera c = mCamera;
        c.unlock();

        mRecorder = new MediaRecorder();
        mRecorder.reset();

        mRecorder.setCamera(c);
        mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mRecorder.setProfile(CamcorderProfile.get(mQuality));

        //设置选择角度，顺时针方向，因为默认是逆向度的，这样图像就是正常显示了,这里设置的是观看保存后的视频的角度
        mRecorder.setOrientationHint(90);
        videoCreateTime = System.currentTimeMillis();
        Log.d(TAG, "video cache path:" + fileCachePath);
        try {
            File file = new File(fileCachePath);
            if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
            if (file.exists()) file.delete();
            file.createNewFile();

            mRecorder.setOutputFile(file.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            mRecorder.prepare();
            mRecorder.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
        mState = State.RECORDE;
        unRecordButton.setVisibility(View.VISIBLE);
        recordButton.setVisibility(View.GONE);
        previewButton.setVisibility(View.GONE);
        unRecordButton.setText("停止");
    }


    private void stopRecorder() {
        if (mState == State.RECORDE) {
            //停止录制
            mRecorder.stop();
            //释放资源
            mRecorder.release();
            mRecorder = null;
            mCamera.stopPreview();

            mState = State.COMPLETE;
            previewButton.setVisibility(View.VISIBLE);
            unRecordButton.setVisibility(View.VISIBLE);
            recordButton.setVisibility(View.VISIBLE);
            unRecordButton.setText("完成");
            recordButton.setText("重拍");
        }
    }

    private void previewVideo() {
        //预览录像
        Intent intent = new Intent(Intent.ACTION_VIEW);
        String type = "video/mp4";
        Uri uri = Uri.parse("file://" + fileCachePath);
        intent.setDataAndType(uri, type);
        startActivity(intent);
    }

    private void skipToCodec() {

        //告诉调用者结果
        Intent intent = new Intent();
        intent.putExtra("fileCachePath", fileCachePath);
        intent.putExtra("videoCreateTime", videoCreateTime);
        String thumpPath = new String(fileCachePath);

        thumpPath = thumpPath.replace(".mp4", ".jpg");
        thumpPath = thumpPath.replace("/video/", "/thump/");
        Log.d(TAG, "thumpPath=" + thumpPath);
        saveBitmapWithName(getVideoThumbnail(fileCachePath), thumpPath);
        intent.putExtra("thumpPath", thumpPath);

        setResult(RESULT_OK, intent);
        finish();

    }

    private boolean hasResult = false;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 0x123 && resultCode == RESULT_OK) {
            hasResult = true;
            Intent intent = new Intent();
            intent.putExtra("fileCachePath", fileCachePath);
            setResult(RESULT_OK, intent);
            new File(fileCachePath).delete();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    finish();
                }
            }, 250);
        }
    }

    //////////////////显示时间信息的代码
    SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    Timer timer = new Timer();
    private int curTimeSecond = MaxDuring;
    private static final int MaxDuring = 60;
    TimerTask task = new TimerTask() {
        @Override
        public void run() {
            String s = format.format(new Date());
            handler.obtainMessage(CHANGE_TIME, s).sendToTarget();
        }
    };

    private static final int CHANGE_TIME = 111;
    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CHANGE_TIME:
                    if (mState == State.RECORDE) {
                        curTimeSecond--;
                        if (curTimeSecond == 0) {
                            stopRecorder();
                            return;
                        }
                        if (timeSecondView.getVisibility() != View.VISIBLE) {
                            timeSecondView.setVisibility(View.VISIBLE);
                        }
                        timeSecondView.setText(secondToText(curTimeSecond));
                        progressBar.setProgress(MaxDuring - curTimeSecond);
                    } else if (timeSecondView.getVisibility() == View.VISIBLE) {
                        timeSecondView.setVisibility(View.INVISIBLE);
                        curTimeSecond = MaxDuring;
                        progressBar.setProgress(0);
                    }
                    curTimeView.setText(msg.obj.toString());
                    break;
            }
        }
    };

    String secondToText(int second) {
        if (second < 10) {
            return " 00:0" + second;
        } else if (second < 60) {
            return " 00:" + second;
        } else {
            return " 01:" + second % 60;
        }
    }

    @Override
    public void onBackPressed() {
        if (mState != State.RECORDE) {
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    void startTime() {
        timer.schedule(task, 0, 1000);
    }

    private int mQuality = -1;

    private void showSupportVideoSize(Camera camera) {
        Camera.Parameters p = camera.getParameters();
        List<Camera.Size> videoSizes = p.getSupportedVideoSizes();
        if (videoSizes != null) {
            StringBuilder sb = new StringBuilder("SupportedVideoSizes:[");
            for (Camera.Size s : videoSizes) {
                sb.append(s.width).append("*").append(s.height).append(",");
            }
            Log.d(TAG, sb.deleteCharAt(sb.length() - 1).toString());
        } else {
            Log.d(TAG, "SupportedVideoSizes:null");
        }
        List<Integer> formats = p.getSupportedPreviewFormats();
        if (formats.contains(ImageFormat.NV21))
            p.setPreviewFormat(ImageFormat.NV21);
    }

    private int mVideoWidth, mVideoHeiht;

    private void initSupportProfile() {

        if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_TIME_LAPSE_480P)) {//无声音的480p
            mQuality = CamcorderProfile.QUALITY_TIME_LAPSE_480P;
        } else if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_TIME_LAPSE_QVGA)) {
            mQuality = CamcorderProfile.QUALITY_TIME_LAPSE_QVGA;
        } else if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_TIME_LAPSE_CIF)) {
            mQuality = CamcorderProfile.QUALITY_TIME_LAPSE_CIF;
        } else if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_TIME_LAPSE_720P)) {
            mQuality = CamcorderProfile.QUALITY_TIME_LAPSE_720P;
        }
        Log.d(TAG, "finally mQuality resolution:" + mQuality);
        CamcorderProfile profile = CamcorderProfile.get(mQuality);
        //因为竖屏被旋转了90度
        mVideoHeiht = profile.videoFrameWidth;
        mVideoWidth = profile.videoFrameHeight;
        Log.d(TAG, "video screen from CamcorderProfile resolution:" + mVideoWidth + "*" + mVideoHeiht);
    }

    public static Bitmap getVideoThumbnail(String filePath) {
        return ThumbnailUtils.createVideoThumbnail(filePath, MediaStore.Images.Thumbnails.MINI_KIND);
    }

    public static void saveBitmapWithName(Bitmap bitmap, String path) {
        FileOutputStream outputStream = null;
        try {
            File file = new File(path);
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            file.createNewFile();
            outputStream = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
    }
}
