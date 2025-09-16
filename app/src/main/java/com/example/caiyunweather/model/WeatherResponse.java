package com.example.caiyunweather.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class WeatherResponse {
    @SerializedName("status")
    private String status;
    
    @SerializedName("result")
    private Result result;
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public Result getResult() {
        return result;
    }
    
    public void setResult(Result result) {
        this.result = result;
    }
    
    public static class Result {
        @SerializedName("hourly")
        private Hourly hourly;
        
        public Hourly getHourly() {
            return hourly;
        }
        
        public void setHourly(Hourly hourly) {
            this.hourly = hourly;
        }
    }
    
    public static class Hourly {
        @SerializedName("temperature")
        private List<Temperature> temperature;
        
        @SerializedName("skycon")
        private List<Skycon> skycon;
        
        public List<Temperature> getTemperature() {
            return temperature;
        }
        
        public void setTemperature(List<Temperature> temperature) {
            this.temperature = temperature;
        }
        
        public List<Skycon> getSkycon() {
            return skycon;
        }
        
        public void setSkycon(List<Skycon> skycon) {
            this.skycon = skycon;
        }
    }
    
    public static class Temperature {
        @SerializedName("value")
        private Double value;
        
        @SerializedName("datetime")
        private String datetime;
        
        public Double getValue() {
            return value;
        }
        
        public void setValue(Double value) {
            this.value = value;
        }
        
        public String getDatetime() {
            return datetime;
        }
        
        public void setDatetime(String datetime) {
            this.datetime = datetime;
        }
    }
    
    public static class Skycon {
        @SerializedName("value")
        private String value;
        
        @SerializedName("datetime")
        private String datetime;
        
        public String getValue() {
            return value;
        }
        
        public void setValue(String value) {
            this.value = value;
        }
        
        public String getDatetime() {
            return datetime;
        }
        
        public void setDatetime(String datetime) {
            this.datetime = datetime;
        }
    }
}