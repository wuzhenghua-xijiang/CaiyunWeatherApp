package com.example.caiyunweather.model;

public class HourlyWeather {
    private String time;
    private double temperature;
    private String weather;
    private String weatherIcon;
    private double humidity;  // 湿度
    private double pressure;  // 气压
    private double windSpeed; // 风速
    
    public HourlyWeather(String time, double temperature, String weather, String weatherIcon) {
        this.time = time;
        this.temperature = temperature;
        this.weather = weather;
        this.weatherIcon = weatherIcon;
    }
    
    public HourlyWeather(String time, double temperature, String weather, String weatherIcon, 
                        double humidity, double pressure, double windSpeed) {
        this.time = time;
        this.temperature = temperature;
        this.weather = weather;
        this.weatherIcon = weatherIcon;
        this.humidity = humidity;
        this.pressure = pressure;
        this.windSpeed = windSpeed;
    }
    
    public String getTime() {
        return time;
    }
    
    public void setTime(String time) {
        this.time = time;
    }
    
    public double getTemperature() {
        return temperature;
    }
    
    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }
    
    public String getWeather() {
        return weather;
    }
    
    public void setWeather(String weather) {
        this.weather = weather;
    }
    
    public String getWeatherIcon() {
        return weatherIcon;
    }
    
    public void setWeatherIcon(String weatherIcon) {
        this.weatherIcon = weatherIcon;
    }
    
    public double getHumidity() {
        return humidity;
    }
    
    public void setHumidity(double humidity) {
        this.humidity = humidity;
    }
    
    public double getPressure() {
        return pressure;
    }
    
    public void setPressure(double pressure) {
        this.pressure = pressure;
    }
    
    public double getWindSpeed() {
        return windSpeed;
    }
    
    public void setWindSpeed(double windSpeed) {
        this.windSpeed = windSpeed;
    }
}