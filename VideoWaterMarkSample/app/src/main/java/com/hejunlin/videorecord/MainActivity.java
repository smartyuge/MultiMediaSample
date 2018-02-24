package com.hejunlin.videorecord;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private static final int TakeVideoCode = 999;
    private Button mTakeVideoButton;
    private TextView mResultDirTextView;
    private String mTempDir;
    private VideoCodecModel mVideo;
    private ProgressBar mProgressBar;

    private android.os.Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            VideoCodecModel mVideo = (VideoCodecModel) msg.obj;
            switch (msg.what) {
                case VideoCodecTask.PROGRESS:
                    mProgressBar.setMax(msg.arg2);
                    mProgressBar.setProgress(msg.arg1);
                    break;
                case VideoCodecTask.START:
                    Log.d("px", "codec start");
                    Toast.makeText(MainActivity.this, "时间戳水印开始",Toast.LENGTH_LONG).show();
                    break;
                case VideoCodecTask.COMPLETE:
                    Log.d("px", "codec complete");
                    Toast.makeText(MainActivity.this, "时间戳水印已完成",Toast.LENGTH_LONG).show();
                    break;
                case VideoCodecTask.ERROR:
                    Log.d("px", "codec error");
                    Toast.makeText(MainActivity.this, "出错啦！",Toast.LENGTH_LONG).show();
                    break;
                case VideoCodecTask.CANCEL:
                    Log.d("px", "codec cancel");
                    break;
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        initData();
    }

    private void initView() {
        mResultDirTextView = (TextView) findViewById(R.id.dst);
        mTakeVideoButton = (Button) findViewById(R.id.et1);
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
    }

    private void initData() {
        mTempDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/";
        mResultDirTextView.setText("加水印后视频地址:" + mTempDir + "watermark.mp4");
    }

    public void onTakeVideo(View v) {
        String fileCachePath = mTempDir + "test.mp4";
        File file = new File(fileCachePath);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        if (file.exists()) {
            file.delete();
        }
        Intent intent = new Intent(this, TakeVideoActivity.class);
        intent.putExtra("fileCachePath", fileCachePath);
        startActivityForResult(intent, TakeVideoCode);
    }

    public void onClick(View v) {
        if (mVideo == null) {
            Toast.makeText(this, "请拍摄一段视频", Toast.LENGTH_SHORT).show();
            return;
        }
        VideoCodecTask task = new VideoCodecTask(mVideo);
        task.setProgressHandler(handler);
        task.start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case TakeVideoCode:
                if (resultCode == RESULT_OK) {
                    mVideo = new VideoCodecModel();
                    mVideo.dstPath = mTempDir + "watermark.mp4";
                    String srcPath = data.getStringExtra("fileCachePath");
                    long videoCreateTime = data.getLongExtra("videoCreateTime", 0);

                    mVideo.srcPath = srcPath;
                    mVideo.videoCreateTime = videoCreateTime;
                }
                break;
        }
    }

}
