package model;

/**
 * Model class representing a QR code record in the qr_codes table.
 * Each QR code is linked to a specific user-event registration.
 */
public class QRCode {
    private int id;
    private int userId;
    private int eventId;
    private String qrData;    // SHA-256 hash (64 char hex string)
    private boolean isUsed;
    private String scannedAt;

    // Full constructor (for loading from database)
    public QRCode(int id, int userId, int eventId, String qrData, boolean isUsed, String scannedAt) {
        this.id = id;
        this.userId = userId;
        this.eventId = eventId;
        this.qrData = qrData;
        this.isUsed = isUsed;
        this.scannedAt = scannedAt;
    }

    // Constructor for new records (no id or scannedAt yet)
    public QRCode(int userId, int eventId, String qrData) {
        this.userId = userId;
        this.eventId = eventId;
        this.qrData = qrData;
        this.isUsed = false;
        this.scannedAt = null;
    }

    // Getters
    public int getId() { return id; }
    public int getUserId() { return userId; }
    public int getEventId() { return eventId; }
    public String getQrData() { return qrData; }
    public boolean isUsed() { return isUsed; }
    public String getScannedAt() { return scannedAt; }

    @Override
    public String toString() {
        return "QRCode{" +
                "id=" + id +
                ", userId=" + userId +
                ", eventId=" + eventId +
                ", qrData='" + qrData + '\'' +
                ", isUsed=" + isUsed +
                ", scannedAt='" + scannedAt + '\'' +
                '}';
    }
}
