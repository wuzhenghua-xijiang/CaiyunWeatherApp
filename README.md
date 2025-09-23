# CaiyunWeatherApp

彩云天气应用，支持通过DeepSeek Function Calling和MCP两种方式获取天气数据。

## 功能特点

1. **双模式支持**：
   - DeepSeek Function Calling模式：使用DeepSeek AI模型直接调用彩云天气API
   - MCP模式：使用DeepSeek AI模型通过MCP协议调用天气工具

2. **24小时天气预报**：显示未来24小时的天气信息

3. **丰富的天气图标**：提供多种天气状况的图标展示

4. **错误处理机制**：完善的错误处理和重试机制

## 技术架构

- **网络请求**：Retrofit + OkHttp
- **JSON解析**：Gson
- **UI框架**：Material Design Components
- **MCP服务器**：NanoHttpd
- **异步处理**：CompletableFuture (Android兼容)

## 使用方法

1. **切换模式**：
   - 应用默认使用DeepSeek Function Calling模式
   - 点击"切换到MCP模式"按钮切换到MCP模式
   - 点击"切换到DeepSeek模式"按钮切换回DeepSeek模式

2. **查看天气图标**：
   - 点击"查看所有天气图标"按钮查看所有可用的天气图标

## MCP模式工作原理

MCP (Model Context Protocol) 是一种允许AI模型与工具交互的协议。在本应用中：

1. **正确的工作流程**：
   - 用户询问天气："北京最近24小时的天气怎么样？"
   - DeepSeek AI模型识别出需要获取天气信息
   - DeepSeek通过Function Calling识别需要调用get_weather_forecast工具
   - 应用检测到需要调用MCP模式的工具，从工具参数中提取位置信息
   - 应用调用MCP服务器获取指定位置的天气数据
   - MCP服务器调用彩云天气API获取真实数据
   - 天气数据返回给应用，再传递给DeepSeek AI模型
   - DeepSeek AI模型基于数据生成自然语言输出结果
   - AI将结果返回给客户端

2. **MCP生态系统**：
   ```mermaid
   flowchart TD
       A[用户提问<br>北京天气怎么样] --> B[DeepSeek等AI模型]
       
       subgraph MCP生态 [MCP生态系统]
           direction LR
           C[MCP Client<br>集成在AI应用内]
           D[(工具列表<br>动态发现)]
           C <--> D
           C <-- 标准MCP协议 --> E[MCP Server<br>独立进程<br>如: 天气服务器]
       end

       B -- 判断需要调用工具 --> C
       C -- 转发天气请求 --> E
       E -- 返回标准化天气数据 --> C
       C -- 将数据注入模型上下文 --> B
       
       B -- 生成自然语言回复 --> F[回复用户<br>北京今天晴, 25°C...]
   ```

3. **MCP服务器**：
   - 使用NanoHttpd在Android设备上启动HTTP服务器
   - 监听端口8080
   - 提供天气查询工具

4. **工具调用流程**：
   ```
   用户 -> DeepSeek AI -> Function Calling识别 -> 应用调用MCP服务器 -> 彩云天气API -> 返回数据 -> DeepSeek AI -> 自然语言结果 -> 用户
   ```

## 配置说明

1. **DeepSeek API密钥**：
   - 在`DeepSeekFunctionCaller.java`中设置`DEEPSEEK_API_KEY`
   - 请将`YOUR_DEEPSEEK_API_KEY`替换为您自己的DeepSeek API密钥
   - 或者在`app/src/main/assets/api_keys.properties`中配置

2. **彩云天气Token**：
   - 在`McpServer.java`中设置`YOUR_CAIYUN_WEATHER_TOKEN`
   - 请将`YOUR_CAIYUN_WEATHER_TOKEN`替换为您自己的彩云天气API Token
   - 或者在`app/src/main/assets/api_keys.properties`中配置

## 配置文件使用方法

1. 复制示例配置文件：
   ```
   cp app/src/main/assets/api_keys.properties.example app/src/main/assets/api_keys.properties
   ```

2. 编辑`api_keys.properties`文件，填入您的实际API密钥：
   ```
   deepseek.api.key=sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
   caiyun.weather.token=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
   ```

## API密钥管理

本应用使用`ApiKeyManager`工具类来管理API密钥，支持从配置文件中读取密钥。应用会优先从配置文件中读取密钥，如果配置文件中没有设置或设置的是占位符，则使用代码中的默认值。

## 项目结构

```
app/
├── src/main/java/com/example/caiyunweather/
│   ├── MainActivity.java          # 主界面
│   ├── IconDemoActivity.java      # 图标展示界面
│   ├── adapter/                   # RecyclerView适配器
│   ├── api/                       # API接口定义
│   ├── model/                     # 数据模型
│   └── utils/                     # 工具类
│       ├── DeepSeekFunctionCaller.java  # DeepSeek调用器
│       ├── McpServer.java         # MCP服务器
│       ├── McpClient.java         # MCP客户端(用于测试)
│       └── ApiKeyManager.java     # API密钥管理器
├── src/main/assets/
│   ├── api_keys.properties        # API密钥配置文件
│   └── api_keys.properties.example # API密钥配置示例文件
└── src/main/res/                  # 资源文件
```

## 依赖库

- Retrofit: 网络请求库
- Gson: JSON解析库
- NanoHttpd: 轻量级HTTP服务器
- CompletableFuture: 异步编程支持

## 注意事项

1. 应用需要网络权限才能获取天气数据
2. 确保已正确设置API密钥和Token
3. MCP服务器在应用启动时自动启动，在应用销毁时自动停止
4. `api_keys.properties`文件已被添加到`.gitignore`中，不会被提交到代码仓库

## 更新日志

- 移除了McpClient中未使用的getWeatherForecast方法，保持代码整洁
- 优化了MCP客户端实现，提高代码质量
- 完善了API密钥管理机制，使用配置文件和ApiKeyManager管理API密钥，避免个人token泄露
- 更新了MCP模式实现，现在通过MCP客户端的tools/list方法动态获取工具列表，而不是使用写死的工具定义
- 移除了对官方MCP Java SDK的依赖，使用自定义MCP实现以更好地适配Android环境
- 改进了MCP工具调用流程，确保符合MCP协议规范