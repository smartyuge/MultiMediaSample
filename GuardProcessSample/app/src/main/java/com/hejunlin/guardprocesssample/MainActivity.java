package com.hejunlin.guardprocesssample;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // 启动两个守护服务
        startService(new Intent(this, ServiceA.class));
        startService(new Intent(this, ServiceB.class));
    }
}
