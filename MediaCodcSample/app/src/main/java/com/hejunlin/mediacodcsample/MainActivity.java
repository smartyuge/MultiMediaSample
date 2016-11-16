package com.hejunlin.mediacodcsample;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private static final String SAMPLE = Environment.getExternalStorageDirectory() + "/device-2016-11-15.mp4";
    private static final String TAG = MainActivity.class.getSimpleName();
    private WorkThread mWorkThread = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SurfaceView surfaceView = new SurfaceView(this);
        /*下面设置Surface不维护自己的缓冲区，而是等待屏幕的渲染引擎将内容推送到用户面前*/
        surfaceView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        surfaceView.getHolder().addCallback(this);
        setContentView(surfaceView);
    }

    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (mWorkThread == null) {
            mWorkThread = new WorkThread(holder.getSurface());
            mWorkThread.start();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mWorkThread != null) {
            mWorkThread.interrupt();
        }
    }

    private class WorkThread extends Thread {
        private MediaExtractor mMediaExtractor;
        private MediaCodec mMediaCodec;
        private Surface mSurface;

        public WorkThread(Surface surface) {
            this.mSurface = surface;
        }

        @Override
        public void run() {
            mMediaExtractor = new MediaExtractor();//数据解析器
            try {
                mMediaExtractor.setDataSource(SAMPLE);
            } catch (IOException e) {
                e.printStackTrace();
            }

            for (int i = 0; i < mMediaExtractor.getTrackCount(); i++) {//遍历数据源音视频轨迹
                MediaFormat format = mMediaExtractor.getTrackFormat(i);
                Log.d(TAG, ">> format i " + i + ": " +  format);
                String mime = format.getString(MediaFormat.KEY_MIME);
                Log.d(TAG, ">> mime i " + i + ": " +  mime);
                if (mime.startsWith("video/")) {
                    mMediaExtractor.selectTrack(i);
                    try {
                        mMediaCodec = MediaCodec.createDecoderByType(mime);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    mMediaCodec.configure(format, mSurface, null, 0);
                    break;
                }
            }
            if (mMediaCodec == null) {
                return;
            }
            mMediaCodec.start();//调用start后，如果没有异常信息，就表示成功构建组件
            ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
            ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();
            // 每个buffer的元数据包括具体范围偏移及大小 ，及有效数据中相关解码的buffer
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean isEOS = false;
            long startMs = System.currentTimeMillis();

            while (!Thread.interrupted()) {//只要线程不中断
                if (!isEOS) {
                    //返回用有效输出的buffer的索引,如果没有相关buffer可用，就返回-1
                    //如果传入的timeoutUs为0,将立马返回，如果输入buffer可用，将无限期等待
                    //timeoutUs的单位是微秒
                    int inIndex = mMediaCodec.dequeueInputBuffer(10000);//0.01s
                    if (inIndex >= 0) {
                        ByteBuffer buffer = inputBuffers[inIndex];
                        Log.d(TAG, ">> buffer " + buffer);
                        int sampleSize = mMediaExtractor.readSampleData(buffer, 0);
                        Log.d(TAG, ">> sampleSize " + sampleSize);
                        if (sampleSize < 0) {
                            // We shouldn't stop the playback at this point, just pass the EOS
                            // flag to mMediaCodec, we will get it again from the
                            // dequeueOutputBuffer
                            Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM");
                            mMediaCodec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            isEOS = true;
                        } else {
                            mMediaCodec.queueInputBuffer(inIndex, 0, sampleSize, mMediaExtractor.getSampleTime(), 0);
                            mMediaExtractor.advance();
                        }
                    }
                }

                int outIndex = mMediaCodec.dequeueOutputBuffer(info, 10000);
                switch (outIndex) {
                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED://当buffer变化时，client必须重新指向新的buffer
                        Log.d(TAG, ">> output buffer changed ");
                        outputBuffers = mMediaCodec.getOutputBuffers();
                        break;
                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED://当buffer的封装格式变化,须指向新的buffer格式
                        Log.d(TAG, ">> output buffer changed ");
                        break;
                    case MediaCodec.INFO_TRY_AGAIN_LATER://当dequeueOutputBuffer超时,会到达此case
                        Log.d(TAG, ">> dequeueOutputBuffer timeout ");
                        break;
                    default:
                        ByteBuffer buffer = outputBuffers[outIndex];
                        // We use a very simple clock to keep the video FPS, or the video
                        // playback will be too fast
                        while (info.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
                            try {
                                sleep(10);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                                break;
                            }
                        }
                        mMediaCodec.releaseOutputBuffer(outIndex, true);
                        break;
                }
                // All decoded frames have been rendered, we can stop playing now
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
                    break;
                }
            }
            mMediaCodec.stop();
            mMediaCodec.release();//释放组件
            mMediaExtractor.release();
        }
    }

}
