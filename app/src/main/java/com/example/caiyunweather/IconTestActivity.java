package com.example.caiyunweather;

import android.os.Bundle;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class IconTestActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_icon_test);
        
        // 设置标题
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("图标测试");
        }
        
        // 测试加载图标，由于已经在XML中设置了tint属性，不再需要动态设置着色
        testIcon();
    }
    
    private void testIcon() {
        ImageView iconView = findViewById(R.id.test_icon);
        if (iconView != null) {
            iconView.setImageResource(R.drawable.ic_clear_day);
            // 由于已经在XML中设置了tint属性，不再需要动态设置着色
        }
    }
}