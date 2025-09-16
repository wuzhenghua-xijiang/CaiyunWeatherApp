package com.example.caiyunweather;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class IconDemoActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_icon_demo);
        
        // 设置标题
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("天气图标演示");
        }
        
        // 由于已经在XML中设置了tint属性，不再需要动态设置着色
    }
}