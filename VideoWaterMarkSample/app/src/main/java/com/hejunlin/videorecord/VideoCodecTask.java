package com.hejunlin.videorecord;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class VideoCodecTask {

    private final static String TAG = "VideoCodecTask";
    private MediaExtractor mExtractor;
    private MediaMuxer mMediaMuxer;
    private MediaFormat mMediaFormat;

    private int mVideoMaxInputSize = 0, mVideoRotation = 0;
    private long mVideoDuration;
    private int mVideoDecodeColor = 21, mVideoEncodeColor = 21;

    private boolean mDecodeOver = false;
    private boolean mEncoding = false;
    private boolean mCancel;
    private boolean mDelete;

    //视频流在数据流中的序号
    private int mVideoTrackIndex = -1;

    private MediaCodec mMediaDecode;
    private MediaCodec mMediaEncode;
    private ByteBuffer[] mDecodeInputBuffers, mDecodeOutputBuffers;
    private ArrayList<Frame> mTimeDataContainer;//数据块容器
    private MediaCodec.BufferInfo mDecodeBufferInfo;
    private int mSrcWidth;
    private int mSrcHeight;
    private int mDstWidth;
    private int mDstHeight;
    private SimpleDateFormat mVideoTimeFormat;
    private int mProgress, mMax;
    private VideoCodecModel mVideo;

    //绘制时间戳的画笔
    private Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private DecodeRunnable mDecodeRunnable;
    private EncodeRunnable mEncodeRunnable;

    public static final int PROGRESS = 0, CANCEL = 1, ERROR = 2, START = 3, COMPLETE = 4;
    private Handler mProgressHandler;

    public VideoCodecTask(VideoCodecModel video) {
        mVideo = video;
        //视频时间戳显示格式
        mVideoTimeFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        mTimeDataContainer = new ArrayList<>();
        //初始化画笔工具
        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setTextSize(40);
    }

    public void start() {
        if (!new File(mVideo.srcPath).exists()) {
            mProgressHandler.obtainMessage(ERROR, mVideo).sendToTarget();
            return;
        }
        mProgressHandler.obtainMessage(START, mVideo).sendToTarget();
        new Thread(new Runnable() {
            @Override
            public void run() {
                init(mVideo.srcPath, mVideo.dstPath);
                mDecodeRunnable = new DecodeRunnable();
                mDecodeRunnable.start();
                mEncodeRunnable = new EncodeRunnable();
                mEncodeRunnable.start();
            }
        }).start();
    }

    private void init(String srcPath, String dstPath) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        mmr.setDataSource(srcPath);
        try {
            mSrcWidth = Integer.parseInt(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            mSrcHeight = Integer.parseInt(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }

        try {
            mExtractor = new MediaExtractor();
            mExtractor.setDataSource(srcPath);
            String mime = null;
            for (int i = 0; i < mExtractor.getTrackCount(); i++) {
                //获取码流的详细格式/配置信息
                MediaFormat format = mExtractor.getTrackFormat(i);
                mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("video/")) {
                    mVideoTrackIndex = i;
                    mMediaFormat = format;
                } else if (mime.startsWith("audio/")) {
                    continue;
                } else {
                    continue;
                }
            }

            mExtractor.selectTrack(mVideoTrackIndex); //选择读取视频数据

            //创建合成器
            mSrcWidth = mMediaFormat.getInteger(MediaFormat.KEY_WIDTH);
            mSrcHeight = mMediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
            mVideoMaxInputSize = mMediaFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
            mVideoDuration = mMediaFormat.getLong(MediaFormat.KEY_DURATION);
            mVideoRotation = 90;//低版本不支持获取旋转,手动写入了
            if (mVideoRotation == 90) {
                mDstWidth = mSrcHeight;
                mDstHeight = mSrcWidth;
            } else if (mVideoRotation == 0) {
                mDstWidth = mSrcWidth;
                mDstHeight = mSrcHeight;
            }

            mMax = (int) (mVideoDuration / 1000);
            Log.d(TAG, "videoWidth=" + mSrcWidth + ",videoHeight=" + mSrcHeight + ",mVideoMaxInputSize=" + mVideoMaxInputSize + ",mVideoDuration=" + mVideoDuration + ",mVideoRotation=" + mVideoRotation);

            //写入文件的合成器
            mMediaMuxer = new MediaMuxer(dstPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            MediaCodec.BufferInfo videoInfo = new MediaCodec.BufferInfo();
            videoInfo.presentationTimeUs = 0;
            initMediaDecode(mime);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 初始化解码器
     */
    private void initMediaDecode(String mime) {
        try {
            //创建解码器
            mMediaDecode = MediaCodec.createDecoderByType(mime);
            mMediaDecode.configure(mMediaFormat, null, null, 0);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (mMediaDecode == null) {
            Log.e(TAG, "create mMediaDecode failed");
            return;
        }
        mMediaDecode.start();
        mDecodeInputBuffers = mMediaDecode.getInputBuffers();
        mDecodeOutputBuffers = mMediaDecode.getOutputBuffers();
        mDecodeBufferInfo = new MediaCodec.BufferInfo();//用于描述解码得到的byte[]数据的相关信息
    }

    /**
     * 初始化编码器
     */

    private void initMediaEncode() {
        getEncodeColor();
        try {
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, mDstWidth, mDstHeight);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 1024 * 512);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 27);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, mVideoEncodeColor);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            mMediaEncode = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            mMediaEncode.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (mMediaEncode == null) {
            Log.e(TAG, "create mMediaEncode failed");
            return;
        }
        mMediaEncode.start();

    }

    /**
     * 获取颜色格式
     */
    private void getEncodeColor() {
        if (Build.MODEL.startsWith("MI 5")) {//小米
            mVideoEncodeColor = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
        } else if (mVideoDecodeColor == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
            mVideoEncodeColor = mVideoDecodeColor;
        } else {
            mVideoEncodeColor = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
        }
    }

    //抽出每一帧
    private void extract() {
        int inputIndex = mMediaDecode.dequeueInputBuffer(-1);//获取可用的inputBuffer -1代表一直等待，0表示不等待 建议-1,避免丢帧
        if (inputIndex < 0) {
            return;
        }

        ByteBuffer inputBuffer = mDecodeInputBuffers[inputIndex];//拿到inputBuffer
        inputBuffer.clear();

        int length = mExtractor.readSampleData(inputBuffer, 0);  //读取一帧数据,放到解码队列
        if (length < 0) {
            Log.d(TAG, "extract Over");
            mDecodeOver = true;
            return;
        } else {
            MediaCodec.BufferInfo videoInfo = new MediaCodec.BufferInfo();
            videoInfo.offset = 0;
            videoInfo.size = length;
            //获取帧类型，只能识别是否为I帧
            videoInfo.flags = mExtractor.getSampleFlags();
            videoInfo.presentationTimeUs = mExtractor.getSampleTime(); //获取时间戳

            //解码视频
            decode(videoInfo, inputIndex);
            mExtractor.advance(); //移动到下一帧
        }

    }


    private void handleFrameData(byte[] data, MediaCodec.BufferInfo info) {
        //转换Yuv数据成RGB格式的bitmap
        Bitmap image = changeYUV2Bitmap(data);

        //旋转图像
        Bitmap bitmap = rotaingImage(mVideoRotation, image);
        image.recycle();

        //渲染文字及背景 0-1ms
        Canvas canvas = new Canvas(bitmap);
        canvas.drawText(mVideoTimeFormat.format(mVideo.videoCreateTime + info.presentationTimeUs / 1000), 20, 60, mTextPaint);

        //通知进度 0-5ms
        mProgress = (int) (info.presentationTimeUs / 1000);
        mProgressHandler.obtainMessage(PROGRESS, mProgress, mMax, mVideo).sendToTarget();

        synchronized (MediaCodec.class) {//加锁
            mTimeDataContainer.add(new Frame(info, bitmap));
        }
    }

    public Bitmap changeYUV2Bitmap(byte[] data) {
        //YUV420sp转RGB数据 5-60ms
        if (mVideoDecodeColor == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
            return ColorFormatUtil.convertYUV420sp2RGB(data, mSrcWidth, mSrcHeight);
        else if (mVideoDecodeColor == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
            int[] rgb = ColorFormatUtil.convertYUV420p2RGB(data, mSrcWidth, mSrcHeight);
            Bitmap bitmap = Bitmap.createBitmap(mSrcWidth, mSrcHeight, Bitmap.Config.RGB_565);
            bitmap.setPixels(rgb, 0, mSrcWidth, 0, 0, mSrcWidth, mSrcHeight);
            return bitmap;
        } else
            throw new RuntimeException("不支持的手机视频格式");
    }

    /**
     * 获取带时间戳的的数据
     *
     * @return
     */
    private Frame getFrameData() {
        if (mTimeDataContainer.isEmpty()) {
            return null;
        }
        Frame frame;
        synchronized (MediaCodec.class) {//加锁
            //从队列中获取数据
            frame = mTimeDataContainer.remove(0);////取出后将此数据remove掉 既能保证PCM数据块的取出顺序 又能及时释放内存
        }

        frame.data = encodeYUV(mDstWidth, mDstHeight, frame.bitmap);
        return frame;
    }

    public byte[] encodeYUV(int width, int height, Bitmap scaled) {

        int[] argb = new int[width * height];

        scaled.getPixels(argb, 0, width, 0, 0, width, height);
        byte[] yuv;
        if (mVideoEncodeColor == 21) {
            //本地把RGB转成YUV
            yuv = ColorFormatUtil.convertRGB2YUV420sp(argb, width, height);
        } else if (mVideoEncodeColor == 19) {
            yuv = ColorFormatUtil.convertRGB2YUV420p(argb, width, height);
        } else {
            yuv = null;
        }
        scaled.recycle();

        return yuv;
    }

    /*
    * 旋转图片
    */
    public Bitmap rotaingImage(int angle, Bitmap bitmap) {
        //旋转图片 动作
        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        // 创建新的图片
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private void decode(MediaCodec.BufferInfo videoInfo, int inputIndex) {


        mMediaDecode.queueInputBuffer(inputIndex, 0, videoInfo.size, videoInfo.presentationTimeUs, videoInfo.flags);//通知MediaDecode解码刚刚传入的数据

        //获取解码得到的byte[]数据 参数BufferInfo上面已介绍 10000同样为等待时间 同上-1代表一直等待，0代表不等待。此处单位为微秒
        //此处建议不要填-1 有些时候并没有数据输出，那么他就会一直卡在这 等待
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputIndex = mMediaDecode.dequeueOutputBuffer(bufferInfo, 50000);

        switch (outputIndex) {
            case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                mDecodeOutputBuffers = mMediaDecode.getOutputBuffers();
                break;
            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                MediaFormat format = mMediaDecode.getOutputFormat();
                Log.d(TAG, "New mMediaFormat " + format);
                if (format != null && format.containsKey(MediaFormat.KEY_COLOR_FORMAT)) {
                    mVideoDecodeColor = format.getInteger(MediaFormat.KEY_COLOR_FORMAT);
                    Log.d(TAG, "decode extract get mVideoDecodeColor =" + mVideoDecodeColor);//解码得到视频颜色格式
                }
                initMediaEncode();//根据颜色格式初始化编码器
                break;
            case MediaCodec.INFO_TRY_AGAIN_LATER:
                Log.d(TAG, "dequeueOutputBuffer timed out!");
                break;
            default:
                ByteBuffer outputBuffer;
                byte[] frame;
                while (outputIndex >= 0) {//每次解码完成的数据不一定能一次吐出 所以用while循环，保证解码器吐出所有数据
                    outputBuffer = mDecodeOutputBuffers[outputIndex];//拿到用于存放PCM数据的Buffer
                    frame = new byte[bufferInfo.size];//BufferInfo内定义了此数据块的大小
                    outputBuffer.get(frame);//将Buffer内的数据取出到字节数组中
                    outputBuffer.clear();//数据取出后一定记得清空此Buffer MediaCodec是循环使用这些Buffer的，不清空下次会得到同样的数据

                    handleFrameData(frame, videoInfo);//自己定义的方法，供编码器所在的线程获取数据,下面会贴出代码

                    mMediaDecode.releaseOutputBuffer(outputIndex, false);//此操作一定要做，不然MediaCodec用完所有的Buffer后 将不能向外输出数据
                    outputIndex = mMediaDecode.dequeueOutputBuffer(mDecodeBufferInfo, 50000);//再次获取数据，如果没有数据输出则outputIndex=-1 循环结束
                }
                break;
        }
    }


    /**
     * 编码
     */
    private void encode() {
        //获取解码器所在线程输出的数据
        byte[] chunkTime;
        Frame frame = getFrameData();
        if (frame == null) {
            return;
        }
        chunkTime = frame.data;
        int inputIndex = mMediaEncode.dequeueInputBuffer(50000);//同解码器
        if (inputIndex < 0) {
            Log.d(TAG, "dequeueInputBuffer return inputIndex " + inputIndex + ",then break");
            mMediaEncode.signalEndOfInputStream();
        }
        ByteBuffer inputBuffer = mMediaEncode.getInputBuffers()[inputIndex];//同解码器
        inputBuffer.clear();//同解码器
        inputBuffer.put(chunkTime);//PCM数据填充给inputBuffer
        inputBuffer.limit(frame.videoInfo.size);

        mMediaEncode.queueInputBuffer(inputIndex, 0, chunkTime.length, frame.videoInfo.presentationTimeUs, frame.videoInfo.flags);//通知编码器 编码

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputIndex = mMediaEncode.dequeueOutputBuffer(bufferInfo, 50000);//同解码器
        switch (outputIndex) {
            case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                break;
            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                MediaFormat outputFormat = mMediaEncode.getOutputFormat();
                outputFormat.setInteger(MediaFormat.KEY_ROTATION, mVideoRotation);
                Log.d(TAG, "mMediaEncode find New mMediaFormat " + outputFormat);
                //向合成器添加视频轨
                mVideoTrackIndex = mMediaMuxer.addTrack(outputFormat);
                mMediaMuxer.start();
                break;
            case MediaCodec.INFO_TRY_AGAIN_LATER:
                Log.d(TAG, "dequeueOutputBuffer timed out!");
                break;
            default:
                ByteBuffer outputBuffer;
                while (outputIndex >= 0) {//同解码器
                    outputBuffer = mMediaEncode.getOutputBuffers()[outputIndex];//拿到输出Buffer
                    mMediaMuxer.writeSampleData(mVideoTrackIndex, outputBuffer, bufferInfo);
                    
                    mMediaEncode.releaseOutputBuffer(outputIndex, false);

                    outputIndex = mMediaEncode.dequeueOutputBuffer(bufferInfo, 50000);
                }
                break;
        }
    }

    public void onEnd() {
        if (mCancel) {
            Message msg = mProgressHandler.obtainMessage(CANCEL, mVideo);
            Bundle b = new Bundle();
            b.putBoolean("delete", mDelete);
            msg.setData(b);
            msg.sendToTarget();
        } else {
            mProgressHandler.obtainMessage(COMPLETE, mVideo).sendToTarget();
        }
    }


    private void release() {
        mTimeDataContainer.clear();//或中途取消,需要清空解码的数据块
        try {
            //全部写完后释放MediaMuxer和MediaExtractor
            mExtractor.release();
            mMediaDecode.release();
            mMediaEncode.release();
            mMediaMuxer.stop();
            mMediaMuxer.release();

        } catch (IllegalStateException e) {
            e.printStackTrace();
        }

    }

    public void cancel(boolean delete) {
        //主动改变变量,以期望线程自动停止
        mCancel = true;
        mDelete = delete;
        mDecodeOver = true;
        mEncoding = false;

        //如果编码线程在wait中,需要唤醒他
        synchronized (mEncodeRunnable) {
            mEncodeRunnable.notify();
        }
    }

    public void setProgressHandler(Handler handler) {
        mProgressHandler = handler;
    }

    class Frame {
        MediaCodec.BufferInfo videoInfo;
        byte[] data;
        Bitmap bitmap;

        public Frame(MediaCodec.BufferInfo videoInfo, Bitmap bitmap) {
            this.videoInfo = videoInfo;
            this.bitmap = bitmap;
        }
    }

    /**
     * 解码线程
     */
    private class DecodeRunnable extends Thread {

        @Override
        public void run() {
            mDecodeOver = false;
            while (!mDecodeOver) {
                try {
                    extract();
                    synchronized (mEncodeRunnable) {
                        mEncodeRunnable.notify();
                    }
                } catch (Exception e) {
                    //抓住删除文件造成的异常
                    Log.e("px", e.toString());
                }

            }
        }

    }

    /**
     * 编码线程
     */
    private class EncodeRunnable extends Thread {
        @Override
        public void run() {
            mEncoding = true;
            while (mEncoding) {
                if (mTimeDataContainer.isEmpty()) {
                    if (mDecodeOver) {//解码完成,缓存也清空了
                        break;
                    }
                    try {
                        synchronized (mEncodeRunnable) {
                            wait();
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    try {
                        encode();
                    } catch (IllegalStateException e) {
                        e.printStackTrace();
                        mEncoding = false;
                        release();
                        mProgressHandler.obtainMessage(ERROR, mVideo).sendToTarget();
                        mProgressHandler = null;
                        return;
                    }

                }
            }
            release();
            mEncoding = false;
            onEnd();
            mProgressHandler = null;
        }
    }


}

