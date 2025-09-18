# CaiyunWeatherApp

## 项目概述
这是一个基于Android的天气应用，使用彩云天气API获取天气数据，并结合DeepSeek AI的Function Calling功能来处理天气预报请求。应用主要展示24小时天气预报信息。

## 核心功能
1. **天气预报展示**：显示24小时内的天气预报信息，包括时间、温度、天气状况等
2. **天气图标演示**：提供所有天气图标的可视化展示界面
3. **AI集成**：通过DeepSeek的Function Calling功能调用彩云天气API
4. **数据可视化**：使用RecyclerView展示结构化的天气数据

## 技术架构
- **开发语言**：Java
- **最低SDK版本**：24 (Android 7.0)
- **目标SDK版本**：34 (Android 14)
- **UI框架**：Material Design 3
- **网络请求**：Retrofit + OkHttp
- **JSON解析**：Gson
- **异步处理**：OkHttp异步请求

## 主要组件

### 核心类
- `MainActivity`：主界面，负责展示24小时天气预报
- `IconDemoActivity`：天气图标演示界面
- `DeepSeekFunctionCaller`：处理与DeepSeek AI的交互和Function Calling
- `WeatherService`：管理彩云天气和DeepSeek API的服务类

### 数据模型
- `HourlyWeather`：小时天气数据模型
- `WeatherResponse`：彩云天气API响应数据模型

### UI组件
- `HourlyWeatherAdapter`：RecyclerView适配器，用于展示小时天气数据
- 使用CardView展示每个小时的天气信息
- 包含丰富的天气图标资源（晴天、多云、雨天、雪天等）

### 网络API
- `CaiyunWeatherApi`：彩云天气API接口
- `DeepSeekApi`：DeepSeek AI API接口

## 项目特点
1. **Material Design 3**：遵循最新的Material Design设计规范
2. **图标着色**：使用tint属性为不同天气类型设置专门颜色
3. **错误处理**：完善的网络错误处理和重试机制
4. **容错设计**：当API不可用时显示模拟数据
5. **响应式UI**：使用RecyclerView实现流畅的列表展示

## 依赖库
- AndroidX AppCompat
- Material Design Components
- RecyclerView
- Retrofit 2.9.0
- Gson Converter 2.9.0
- OkHttp Logging Interceptor 4.9.0