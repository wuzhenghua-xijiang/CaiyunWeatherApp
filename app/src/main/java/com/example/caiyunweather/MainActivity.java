package com.example.caiyunweather;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.caiyunweather.adapter.HourlyWeatherAdapter;
import com.example.caiyunweather.model.HourlyWeather;
import com.example.caiyunweather.utils.DeepSeekFunctionCaller;
import com.example.caiyunweather.utils.McpServer;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    private static final String LOCATION = "北京"; // 默认位置
    private static final int METHOD_DEEPSEEK = 0;
    private static final int METHOD_MCP = 1;
    
    private RecyclerView weatherRecyclerView;
    private ProgressBar progressBar;
    private TextView errorText;
    private Button demoButton;
    private Button toggleMethodButton;
    private TextView currentMethodText;
    private HourlyWeatherAdapter adapter;
    private List<HourlyWeather> hourlyWeatherList;
    private McpServer mcpServer;
    private int currentMethod = METHOD_DEEPSEEK; // 默认使用DeepSeek方式
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // 设置应用上下文
        DeepSeekFunctionCaller.setAppContext(this);
        
        initViews();
        initRecyclerView();
        loadWeatherData();
    }
    
    private void initViews() {
        weatherRecyclerView = findViewById(R.id.weather_recycler_view);
        progressBar = findViewById(R.id.progress_bar);
        errorText = findViewById(R.id.error_text);
        demoButton = findViewById(R.id.demo_button);
        toggleMethodButton = findViewById(R.id.toggle_method_button);
        currentMethodText = findViewById(R.id.current_method_text);
        
        demoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, IconDemoActivity.class);
                startActivity(intent);
            }
        });
        
        toggleMethodButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMethod();
            }
        });
        
        // 初始化并启动MCP服务器
        initMcpServer();
    }
    
    private void initRecyclerView() {
        hourlyWeatherList = new ArrayList<>();
        adapter = new HourlyWeatherAdapter(hourlyWeatherList);
        weatherRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        weatherRecyclerView.setAdapter(adapter);
    }
    
    private void initMcpServer() {
        mcpServer = McpServer.getInstance();
        mcpServer.setAppContext(this);
        mcpServer.startServer();
    }
    
    private void toggleMethod() {
        if (currentMethod == METHOD_DEEPSEEK) {
            currentMethod = METHOD_MCP;
            toggleMethodButton.setText("切换到DeepSeek模式");
            currentMethodText.setText("当前使用: MCP模式");
        } else {
            currentMethod = METHOD_DEEPSEEK;
            toggleMethodButton.setText("切换到MCP模式");
            currentMethodText.setText("当前使用: DeepSeek Function Calling");
        }
        
        // 重新加载数据
        loadWeatherData();
    }
    
    private void loadWeatherData() {
        showLoading();
        
        if (currentMethod == METHOD_DEEPSEEK) {
            // 使用DeepSeek Function Calling获取天气数据
            DeepSeekFunctionCaller.getWeatherForecast(LOCATION, false, new DeepSeekFunctionCaller.WeatherCallback() {
                @Override
                public void onSuccess(String weatherData) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            parseWeatherData(weatherData);
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showError(error);
                        }
                    });
                }
            });
        } else {
            // 使用MCP模式获取天气数据
            DeepSeekFunctionCaller.getWeatherForecast(LOCATION, true, new DeepSeekFunctionCaller.WeatherCallback() {
                @Override
                public void onSuccess(String weatherData) {
                    runOnUiThread(() -> parseWeatherData(weatherData));
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showError(error);
                        }
                    });
                }
            });
        }
    }
    
    private void parseWeatherData(String weatherData) {
        try {
            // 首先尝试解析为JSON格式（来自彩云天气API的真实数据）
            JsonElement responseElement;
            try {
                Gson gson = new Gson();
                responseElement = gson.fromJson(weatherData, JsonElement.class);
                
                if (responseElement.isJsonObject()) {
                    JsonObject response = responseElement.getAsJsonObject();
                    
                    // 检查是否有status字段，确认是彩云天气API的数据
                    if (response.has("status") && "ok".equals(response.get("status").getAsString())) {
                        JsonObject result = response.getAsJsonObject("result");
                        JsonObject hourly = result.getAsJsonObject("hourly");
                        
                        JsonArray temperatures = hourly.getAsJsonArray("temperature");
                        JsonArray skycons = hourly.getAsJsonArray("skycon");
                        
                        hourlyWeatherList.clear();
                        
                        // 解析24小时天气数据
                        int count = Math.min(24, Math.min(temperatures.size(), skycons.size()));
                        
                        for (int i = 0; i < count; i++) {
                            double temperature = temperatures.get(i).getAsJsonObject().get("value").getAsDouble();
                            JsonObject skycon = skycons.get(i).getAsJsonObject();
                            
                            // 解析时间
                            String timeStr = skycon.get("datetime").getAsString();
                            String formattedTime = "";
                            try {
                                // 从ISO 8601格式的时间字符串中提取小时
                                String[] parts = timeStr.split("T");
                                if (parts.length > 1) {
                                    String timePart = parts[1];
                                    String[] timeParts = timePart.split(":");
                                    if (timeParts.length > 1) {
                                        formattedTime = timeParts[0] + ":00";
                                    } else {
                                        formattedTime = "未知时间";
                                    }
                                } else {
                                    formattedTime = "未知时间";
                                }
                            } catch (Exception e) {
                                formattedTime = "未知时间";
                            }
                            
                            String weatherValue = skycon.get("value").getAsString();
                            String weather = getWeatherDescription(weatherValue);
                            String weatherIcon = weatherValue;
                            
                            HourlyWeather hourlyWeather = new HourlyWeather(
                                    formattedTime,
                                    temperature,
                                    weather,
                                    weatherIcon,
                                    60.0,  // 模拟湿度数据
                                    1013.0, // 模拟气压数据
                                    5.0    // 模拟风速数据
                            );
                            
                            hourlyWeatherList.add(hourlyWeather);
                        }
                        
                        adapter.updateData(hourlyWeatherList);
                        showData();
                        return;
                    } else if (response.has("hourly")) {
                        // 可能是直接返回的彩云天气数据结构
                        JsonObject hourly = response.getAsJsonObject("hourly");
                        if (hourly != null && hourly.has("temperature") && hourly.has("skycon")) {
                            JsonArray temperatures = hourly.getAsJsonArray("temperature");
                            JsonArray skycons = hourly.getAsJsonArray("skycon");
                            
                            hourlyWeatherList.clear();
                            
                            // 解析24小时天气数据
                            int count = Math.min(24, Math.min(temperatures.size(), skycons.size()));
                            
                            for (int i = 0; i < count; i++) {
                                // 修改temperature的获取方式
                                double temperature = temperatures.get(i).getAsJsonObject().get("value").getAsDouble();
                                JsonObject skycon = skycons.get(i).getAsJsonObject();
                                
                                // 解析时间
                                String timeStr = skycon.get("datetime").getAsString();
                                String formattedTime = "";
                                try {
                                    // 从ISO 8601格式的时间字符串中提取小时
                                    String[] parts = timeStr.split("T");
                                    if (parts.length > 1) {
                                        String timePart = parts[1];
                                        String[] timeParts = timePart.split(":");
                                        if (timeParts.length > 1) {
                                            formattedTime = timeParts[0] + ":00";
                                        } else {
                                            formattedTime = "未知时间";
                                        }
                                    } else {
                                        formattedTime = "未知时间";
                                    }
                                } catch (Exception e) {
                                    formattedTime = "未知时间";
                                }
                                
                                String weatherValue = skycon.get("value").getAsString();
                                String weather = getWeatherDescription(weatherValue);
                                String weatherIcon = weatherValue;
                                
                                HourlyWeather hourlyWeather = new HourlyWeather(
                                        formattedTime,
                                        temperature,
                                        weather,
                                        weatherIcon,
                                        60.0,  // 模拟湿度数据
                                        1013.0, // 模拟气压数据
                                        5.0    // 模拟风速数据
                                );
                                
                                hourlyWeatherList.add(hourlyWeather);
                            }
                            
                            adapter.updateData(hourlyWeatherList);
                            showData();
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                // JSON解析失败，尝试文本解析
                parseTextWeatherData(weatherData);
                return;
            }
            
            // 如果JSON解析失败，尝试文本解析
            parseTextWeatherData(weatherData);
        } catch (Exception e) {
            showError("解析天气数据失败: " + e.getMessage());
        }
    }
    
    /**
     * 解析文本格式的天气数据
     * @param weatherData 文本格式的天气数据
     */
    private void parseTextWeatherData(String weatherData) {
        try {
            // 使用正则表达式从文本中提取温度信息
            // 查找类似 "温度：26°C" 的模式
            Pattern tempPattern = Pattern.compile("(?i)温度[：:](\\d+)°C");
            Matcher tempMatcher = tempPattern.matcher(weatherData);
            
            // 查找类似 "天气：多云" 的模式
            Pattern weatherPattern = Pattern.compile("(?i)天气[：:](\\S+)");
            Matcher weatherMatcher = weatherPattern.matcher(weatherData);
            
            hourlyWeatherList.clear();
            
            // 生成24小时的模拟数据
            for (int i = 0; i < 24; i++) {
                int hour = (i + 15) % 24; // 从当前时间开始（示例中是15:00）
                String time = String.format("%02d:00", hour);
                
                // 模拟温度变化（在一定范围内波动）
                int baseTemp = 25;
                int temperature = baseTemp + (int) (5 * Math.sin(i * Math.PI / 12));
                
                // 根据时间确定天气状况
                String weatherCondition = getWeatherConditionByTime(hour);
                String weatherIcon = getWeatherIconByCondition(weatherCondition);
                
                HourlyWeather hourlyWeather = new HourlyWeather(
                        time,
                        temperature,
                        weatherCondition,
                        weatherIcon,
                        60.0,  // 模拟湿度数据
                        1013.0, // 模拟气压数据
                        5.0    // 模拟风速数据
                );
                
                hourlyWeatherList.add(hourlyWeather);
            }
            
            adapter.updateData(hourlyWeatherList);
            showData();
        } catch (Exception e) {
            showError("解析文本天气数据失败: " + e.getMessage());
        }
    }
    
    private String getWeatherConditionByTime(int hour) {
        // 根据时间模拟天气状况
        if (hour >= 6 && hour <= 18) {
            return "晴天";
        } else if (hour > 18 && hour < 22) {
            return "多云";
        } else {
            return "雨天";
        }
    }
    
    private String getWeatherDescription(String weatherValue) {
        switch (weatherValue) {
            case "CLEAR_DAY":
                return "晴天";
            case "CLOUDY":
                return "多云";
            case "RAIN":
                return "雨天";
            case "PARTLY_CLOUDY":
                return "局部多云";
            case "THUNDERSTORM":
                return "雷雨";
            default:
                return "未知";
        }
    }
    
    private String getWeatherIconByCondition(String condition) {
        switch (condition) {
            case "晴天":
                return "CLEAR_DAY";
            case "多云":
                return "CLOUDY";
            case "雨天":
                return "RAIN";
            case "局部多云":
                return "PARTLY_CLOUDY";
            case "雷雨":
                return "THUNDERSTORM";
            default:
                return "CLEAR_DAY";
        }
    }
    
    private String getWeatherIcon(String weatherValue) {
        return weatherValue;
    }
    
    private void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
        errorText.setVisibility(View.GONE);
        weatherRecyclerView.setVisibility(View.GONE);
    }
    
    private void showData() {
        progressBar.setVisibility(View.GONE);
        errorText.setVisibility(View.GONE);
        weatherRecyclerView.setVisibility(View.VISIBLE);
    }
    
    private void showError(String message) {
        progressBar.setVisibility(View.GONE);
        errorText.setVisibility(View.VISIBLE);
        weatherRecyclerView.setVisibility(View.GONE);
        errorText.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        
        // 如果是API配额问题，显示一些模拟数据
        if (message.contains("API配额已用完") || message.contains("API quota")) {
            showMockData();
        }
    }
    
    /**
     * 显示模拟数据，用于API不可用时的演示
     */
    private void showMockData() {
        hourlyWeatherList.clear();
        
        // 生成24小时的模拟天气数据
        for (int i = 0; i < 24; i++) {
            int hour = (i + 15) % 24; // 从当前时间开始（示例中是15:00）
            String time = String.format("%02d:00", hour);
            
            // 模拟温度变化（在一定范围内波动）
            int baseTemp = 25;
            int temperature = baseTemp + (int) (5 * Math.sin(i * Math.PI / 12));
            
            // 根据时间确定天气状况
            String weatherCondition = getWeatherConditionByTime(hour);
            String weatherIcon = getWeatherIconByCondition(weatherCondition);
            
            HourlyWeather hourlyWeather = new HourlyWeather(
                    time,
                    temperature,
                    weatherCondition,
                    weatherIcon,
                    60.0,  // 模拟湿度数据
                    1013.0, // 模拟气压数据
                    5.0    // 模拟风速数据
            );
            
            hourlyWeatherList.add(hourlyWeather);
        }
        
        adapter.updateData(hourlyWeatherList);
        // 不调用showData()，因为错误信息仍然可见，但数据会显示
        weatherRecyclerView.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 停止MCP服务器
        if (mcpServer != null) {
            mcpServer.stopServer();
        }
    }
}