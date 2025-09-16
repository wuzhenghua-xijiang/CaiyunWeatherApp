package com.example.caiyunweather.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.caiyunweather.R;
import com.example.caiyunweather.model.HourlyWeather;

import java.util.List;

public class HourlyWeatherAdapter extends RecyclerView.Adapter<HourlyWeatherAdapter.ViewHolder> {
    private List<HourlyWeather> hourlyWeatherList;
    
    public HourlyWeatherAdapter(List<HourlyWeather> hourlyWeatherList) {
        this.hourlyWeatherList = hourlyWeatherList;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_hourly_weather, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HourlyWeather hourlyWeather = hourlyWeatherList.get(position);
        holder.bind(hourlyWeather);
    }
    
    @Override
    public int getItemCount() {
        return hourlyWeatherList.size();
    }
    
    // ViewHolder内部类
    class ViewHolder extends RecyclerView.ViewHolder {
        private TextView timeText;
        private TextView temperatureText;
        private TextView additionalInfoText;
        private ImageView weatherIcon;
        
        ViewHolder(View itemView) {
            super(itemView);
            timeText = itemView.findViewById(R.id.time_text);
            temperatureText = itemView.findViewById(R.id.temperature_text);
            additionalInfoText = itemView.findViewById(R.id.additional_info_text);
            weatherIcon = itemView.findViewById(R.id.weather_icon);
        }
        
        void bind(HourlyWeather hourlyWeather) {
            timeText.setText(hourlyWeather.getTime());
            temperatureText.setText(String.format("%.0f°C", hourlyWeather.getTemperature()));
            
            // 设置额外信息（如果可用）
            if (hourlyWeather.getHumidity() > 0) {
                additionalInfoText.setText(String.format("湿度: %.0f%%", hourlyWeather.getHumidity()));
            } else {
                additionalInfoText.setText("");
            }
            
            // 根据天气情况设置图标
            int iconRes = getWeatherIcon(hourlyWeather.getWeatherIcon());
            weatherIcon.setImageResource(iconRes);
            
            // 由于已经在XML中设置了tint属性，不再需要动态设置着色
        }
    }
    
    private int getWeatherIcon(String weatherIcon) {
        switch (weatherIcon) {
            case "CLEAR_DAY": return R.drawable.ic_clear_day;
            case "CLEAR_NIGHT": return R.drawable.ic_clear_night;
            case "PARTLY_CLOUDY_DAY": return R.drawable.ic_partly_cloudy_day;
            case "PARTLY_CLOUDY_NIGHT": return R.drawable.ic_partly_cloudy_night;
            case "CLOUDY": return R.drawable.ic_cloudy;
            case "LIGHT_HAZE": return R.drawable.ic_light_haze;
            case "MODERATE_HAZE": return R.drawable.ic_haze;
            case "HEAVY_HAZE": return R.drawable.ic_heavy_haze;
            case "LIGHT_RAIN": return R.drawable.ic_light_rain;
            case "RAIN": return R.drawable.ic_rain;
            case "MODERATE_RAIN": return R.drawable.ic_heavy_rain;
            case "HEAVY_RAIN": return R.drawable.ic_heavy_rain;
            case "STORM_RAIN": return R.drawable.ic_storm_rain;
            case "FOG": return R.drawable.ic_fog;
            case "LIGHT_SNOW": return R.drawable.ic_light_snow;
            case "SNOW": return R.drawable.ic_snow;
            case "MODERATE_SNOW": return R.drawable.ic_snow;
            case "HEAVY_SNOW": return R.drawable.ic_heavy_snow;
            case "STORM_SNOW": return R.drawable.ic_storm_snow;
            case "DUST": return R.drawable.ic_dust;
            case "SAND": return R.drawable.ic_sand;
            case "WIND": return R.drawable.ic_wind;
            case "THUNDERSTORM": return R.drawable.ic_thunderstorm;
            default: return R.drawable.ic_clear_day;
        }
    }
    
    public void updateData(List<HourlyWeather> newData) {
        this.hourlyWeatherList = newData;
        notifyDataSetChanged();
    }
}