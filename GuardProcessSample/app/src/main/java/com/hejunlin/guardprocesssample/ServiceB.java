package com.hejunlin.guardprocesssample;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import com.hejunlin.guardprocesssample.aidl.IBridgeInterface;

/**
 * Created by hejunlin on 2016/10/10.
 */
public class ServiceB extends Service {

    private static final String TAG = ServiceB.class.getSimpleName();
    private MyBinder mBinder;
    private PendingIntent mPendingIntent;
    private MyServiceConnection mServiceConnection;

    @Override
    public void onCreate() {
        super.onCreate();
        if (mBinder == null) {
            mBinder = new MyBinder();
        }
        mServiceConnection = new MyServiceConnection();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        this.bindService(new Intent(this,ServiceA.class), mServiceConnection, Context.BIND_IMPORTANT);
        mPendingIntent =PendingIntent.getService(this, 0, intent, 0);
        Notification.Builder builder = new Notification.Builder(this);
        builder.setTicker("守护服务B启动中")
                .setContentText("我是来守护A不被杀的!")
                .setContentTitle("守护服务B")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(mPendingIntent)
                .setWhen(System.currentTimeMillis());
        Notification notification = builder.build();
        //设置service为前台进程，避免手机休眠时系统自动杀掉该服务
        startForeground(startId, notification);
        return START_STICKY;
    }

    class MyServiceConnection implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder binder) {
            Log.i(TAG, "ServiceB连接成功");
            Toast.makeText(ServiceB.this, "ServiceB连接成功", Toast.LENGTH_LONG).show();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            // 连接出现了异常断开了，LocalCastielService被杀死了
            Toast.makeText(ServiceB.this, "ServiceB被干掉", Toast.LENGTH_LONG).show();
            // 启动ServiceA
            ServiceB.this.startService(new Intent(ServiceB.this, ServiceA.class));
            ServiceB.this.bindService(new Intent(ServiceB.this, ServiceA.class), mServiceConnection, Context.BIND_IMPORTANT);
        }

    }

    class MyBinder extends IBridgeInterface.Stub {

        @Override
        public String getName() throws RemoteException {
            return "ServiceB";
        }

    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
