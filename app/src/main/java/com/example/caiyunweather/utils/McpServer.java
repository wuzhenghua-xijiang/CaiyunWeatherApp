package com.example.caiyunweather.utils;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import fi.iki.elonen.NanoHTTPD;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public class McpServer extends NanoHTTPD {
    private static final String TAG = "McpServer";
    private static final int PORT = 8080;
    private static McpServer instance;
    private final Gson gson = new Gson();
    private final OkHttpClient client = new OkHttpClient();
    private ScheduledExecutorService scheduler;
    
    private McpServer() {
        super("127.0.0.1", PORT);  // 明确指定绑定地址
    }
    
    public static synchronized McpServer getInstance() {
        if (instance == null) {
            instance = new McpServer();
        }
        return instance;
    }
    
    public void startServer() {
        try {
            if (!isAlive()) {
                start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
                Log.d(TAG, "MCP服务器启动成功，端口: " + PORT);
                
                // 启动定期任务来保持服务器运行
                startKeepAliveTask();
            }
        } catch (IOException e) {
            Log.e(TAG, "MCP服务器启动失败", e);
        }
    }
    
    public void stopServer() {
        if (isAlive()) {
            stop();
            if (scheduler != null) {
                scheduler.shutdown();
            }
            Log.d(TAG, "MCP服务器已停止");
        }
    }
    
    private void startKeepAliveTask() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            // 定期任务，保持服务器活跃
            Log.d(TAG, "MCP服务器运行中...");
        }, 30, 30, TimeUnit.SECONDS);
    }
    
    @Override
    public Response serve(IHTTPSession session) {
        try {
            String uri = session.getUri();
            Log.d(TAG, "收到请求: " + uri);
            
            // 处理CORS预检请求
            if (session.getMethod() == Method.OPTIONS) {
                return createCorsResponse();
            }
            
            // 读取请求体
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);
            String body = files.get("postData");
            
            Log.d(TAG, "请求体: " + body);
            
            // 解析JSON请求
            JsonObject request = new JsonParser().parse(body).getAsJsonObject();
            String method = request.get("method").getAsString();
            
            JsonObject response;
            switch (method) {
                case "initialize":
                    response = handleInitialize();
                    break;
                case "tools/list":
                    response = handleListTools();
                    break;
                case "tools/call":
                    response = handleCallTool(request);
                    break;
                default:
                    response = createErrorResponse(-32601, "Method not found: " + method);
            }
            
            return createJsonResponse(response.toString());
        } catch (Exception e) {
            Log.e(TAG, "处理请求时出错", e);
            JsonObject errorResponse = createErrorResponse(-32603, "Internal error: " + e.getMessage());
            return createJsonResponse(errorResponse.toString());
        }
    }
    
    private JsonObject handleInitialize() {
        JsonObject response = new JsonObject();
        response.addProperty("protocolVersion", "2024-01-01");
        
        JsonObject capabilities = new JsonObject();
        capabilities.add("tools", new JsonObject());
        response.add("capabilities", capabilities);
        
        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", "CaiyunWeather MCP Server");
        serverInfo.addProperty("version", "1.0.0");
        response.add("serverInfo", serverInfo);
        
        return response;
    }
    
    private JsonObject handleListTools() {
        JsonObject response = new JsonObject();
        JsonArray tools = new JsonArray();
        
        // 添加获取天气工具
        JsonObject getWeatherTool = new JsonObject();
        getWeatherTool.addProperty("name", "get_weather_forecast");
        getWeatherTool.addProperty("description", "获取指定位置的24小时天气预报");
        
        JsonObject inputSchema = new JsonObject();
        inputSchema.addProperty("type", "object");
        
        JsonObject properties = new JsonObject();
        JsonObject locationProp = new JsonObject();
        locationProp.addProperty("type", "string");
        locationProp.addProperty("description", "地理位置，例如：北京、上海等");
        properties.add("location", locationProp);
        
        inputSchema.add("properties", properties);
        
        JsonArray required = new JsonArray();
        required.add("location");
        inputSchema.add("required", required);
        
        getWeatherTool.add("inputSchema", inputSchema);
        tools.add(getWeatherTool);
        
        response.add("tools", tools);
        return response;
    }
    
    private JsonObject handleCallTool(JsonObject request) {
        JsonObject params = request.getAsJsonObject("params");
        String toolName = params.get("name").getAsString();
        JsonObject arguments = params.getAsJsonObject("arguments");
        
        switch (toolName) {
            case "get_weather_forecast":
                String location = arguments.has("location") ? arguments.get("location").getAsString() : "北京";
                return callWeatherForecast(location);
            default:
                return createErrorResponse(-32601, "Tool not found: " + toolName);
        }
    }
    
    private JsonObject callWeatherForecast(String location) {
        try {
            Log.d(TAG, "callWeatherForecast: location " + location);
            // 获取位置坐标
            double[] coordinates = getLocationCoordinates(location);
            double longitude = coordinates[0];
            double latitude = coordinates[1];
            
            // 彩云天气API token (需要替换为实际的token)
            String token = "QcevZCCHjrbDtgsP";
            
            // 构建API URL
            String url = String.format("https://api.caiyunapp.com/v2.5/%s/%f,%f/weather.json", 
                                     token, longitude, latitude);
            
            // 创建请求
            Request httpRequest = new Request.Builder()
                    .url(url)
                    .build();
            
            // 同步执行请求
            okhttp3.Response response = client.newCall(httpRequest).execute();
            
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                
                // 解析响应
                JsonObject result = new JsonObject();
                result.addProperty("status", "success");
                result.add("data", new JsonParser().parse(responseBody).getAsJsonObject());
                
                JsonObject responseObj = new JsonObject();
                responseObj.add("result", result);

                Log.d(TAG, "callWeatherForecast: response " + responseObj);
                return responseObj;
            } else {
                return createErrorResponse(-32000, "Failed to get weather data: " + response.code());
            }
        } catch (Exception e) {
            return createErrorResponse(-32001, "Error calling weather API: " + e.getMessage());
        }
    }
    
    private double[] getLocationCoordinates(String location) {
        // 简化的位置映射，实际应用中可以使用地理编码API
        Map<String, double[]> locations = new HashMap<>();
        locations.put("北京", new double[]{116.4074, 39.9042});
        locations.put("上海", new double[]{121.4737, 31.2304});
        locations.put("广州", new double[]{113.2644, 23.1291});
        locations.put("深圳", new double[]{114.0579, 22.5431});
        locations.put("杭州", new double[]{120.1551, 30.2741});
        
        return locations.getOrDefault(location, new double[]{116.4074, 39.9042}); // 默认北京
    }
    
    private JsonObject createErrorResponse(int code, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        
        JsonObject response = new JsonObject();
        response.add("error", error);
        return response;
    }
    
    private Response createJsonResponse(String json) {
        Response response = NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", json);
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type");
        return response;
    }
    
    private Response createCorsResponse() {
        Response response = NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/plain", "");
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type");
        return response;
    }
}