package com.lunartag.app.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.util.Date;

/**
 * A data model class that represents a photo record in the local Room database.
 * This object is saved locally on the device for every captured photo.
 */
@Entity(tableName = "photos")
public class Photo {

    @PrimaryKey(autoGenerate = true)
    public long id;

    private String filePath;
    private long assignedTimestamp; // Stored as long (milliseconds) for Room
    private long captureTimestampReal; // Stored as long (milliseconds) for Room
    private double lat;
    private double lon;
    private double accuracyMeters;
    private String addressHuman;
    private String shiftStart;
    private String shiftEnd;
    private String watermarkName;
    private String companyName;
    private long sendScheduledAt; // Stored as long (milliseconds) for Room
    private String status; // e.g., "PENDING", "SENT", "FAILED"
    private long createdAt; // Stored as long (milliseconds) for Room

    // --- Getters and Setters for all fields ---

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public long getAssignedTimestamp() {
        return assignedTimestamp;
    }

    public void setAssignedTimestamp(long assignedTimestamp) {
        this.assignedTimestamp = assignedTimestamp;
    }

    public long getCaptureTimestampReal() {
        return captureTimestampReal;
    }

    public void setCaptureTimestampReal(long captureTimestampReal) {
        this.captureTimestampReal = captureTimestampReal;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLon() {
        return lon;
    }

    public void setLon(double lon) {
        this.lon = lon;
    }

    public double getAccuracyMeters() {
        return accuracyMeters;
    }

    public void setAccuracyMeters(double accuracyMeters) {
        this.accuracyMeters = accuracyMeters;
    }

    public String getAddressHuman() {
        return addressHuman;
    }

    public void setAddressHuman(String addressHuman) {
        this.addressHuman = addressHuman;
    }

    public String getShiftStart() {
        return shiftStart;
    }

    public void setShiftStart(String shiftStart) {
        this.shiftStart = shiftStart;
    }

    public String getShiftEnd() {
        return shiftEnd;
    }

    public void setShiftEnd(String shiftEnd) {
        this.shiftEnd = shiftEnd;
    }

    public String getWatermarkName() {
        return watermarkName;
    }

    public void setWatermarkName(String watermarkName) {
        this.watermarkName = watermarkName;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public long getSendScheduledAt() {
        return sendScheduledAt;
    }

    public void setSendScheduledAt(long sendScheduledAt) {
        this.sendScheduledAt = sendScheduledAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}