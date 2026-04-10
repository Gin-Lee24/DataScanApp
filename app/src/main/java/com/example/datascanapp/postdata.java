package com.example.datascanapp;

import com.google.gson.annotations.SerializedName;

public class postdata {

    @SerializedName("key")
    private String key;

    @SerializedName("team")
    private String team;

    @SerializedName("sensor")
    private String sensor;

    @SerializedName("mac")
    private String mac;

    @SerializedName("temp")
    private double temp;

    @SerializedName("humidity")
    private double humidity;

    @SerializedName("AQI")
    private int AQI;

    @SerializedName("TVOC")
    private int TVOC;

    @SerializedName("eCO2")
    private int eCO2;

    @SerializedName("timestamp")
    private long timestamp;

    @SerializedName("lat")
    private double lat;

    @SerializedName("lon")
    private double lon;

    @SerializedName("sender")
    private String sender;

    public void setData(String key, String team, String sensor, String mac,
                        double temp, double humidity, int AQI,
                        int TVOC, int eCO2, long timestamp,
                        double lat, double lon, String sender) {

        this.key = key;
        this.team = team;
        this.sensor = sensor;
        this.mac = mac;
        this.temp = temp;
        this.humidity = humidity;
        this.AQI = AQI;
        this.TVOC = TVOC;
        this.eCO2 = eCO2;
        this.timestamp = timestamp;
        this.lat = lat;
        this.lon = lon;
        this.sender = sender;
    }
}