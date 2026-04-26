package model;

public class Event {
    private int id;
    private String title;
    private String venue;
    private int capacity;
    private int available_seats;
    private int createdBy;
    private int categoryId;
    private java.sql.Timestamp eventDate;

    public Event(int id, String title, String venue, int capacity, int createdBy) {
        this(id, title, venue, capacity, capacity, createdBy, 0, null);
    }

    public Event(int id, String title, String venue, int capacity, int availableSeats, int createdBy, int categoryId, java.sql.Timestamp eventDate) {
        this.id = id;
        this.title = title;
        this.venue = venue;
        this.capacity = capacity;
        this.available_seats = availableSeats;
        this.createdBy = createdBy;
        this.categoryId = categoryId;
        this.eventDate = eventDate;
    }

    public Event(int id, String title, String venue, int capacity, int createdBy, int categoryId, java.sql.Timestamp eventDate) {
        this(id, title, venue, capacity, capacity, createdBy, categoryId, eventDate);
    }

    public Event(int id, String title, String venue, int capacity) {
        this(id, title, venue, capacity, capacity, 0, 0, null);
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getVenue() { return venue; }
    public int getCapacity() { return capacity; }
    public int getAvailableSeats() { return available_seats; }
    public int getCreatedBy() { return createdBy; }
    public int getCategoryId() { return categoryId; }
    public java.sql.Timestamp getEventDate() { return eventDate; }
    
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; }
    public void setEventDate(java.sql.Timestamp eventDate) { this.eventDate = eventDate; }
}
