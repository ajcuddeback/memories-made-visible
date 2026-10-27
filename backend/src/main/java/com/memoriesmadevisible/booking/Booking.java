package com.memoriesmadevisible.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** A session request from the website; becomes a confirmed session once approved. */
@Entity
@Table(name = "booking")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_type_key", nullable = false, length = 40)
    private String sessionTypeKey;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 200)
    private String email;

    @Column(length = 40)
    private String phone;

    @Column(length = 200)
    private String location;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "session_date", nullable = false)
    private LocalDate date;

    /** Display label ("9:00 AM"); kept for emails and old rows. */
    /** The package the customer picked (its name at the time); null on older rows. */
    @Column(name = "package_name", length = 80)
    private String packageName;

    /** Hours booked for an hourly package; null for flat packages. */
    @Column
    private Integer hours;

    /** Chosen add-on names, comma-separated; null when none. */
    @Column(name = "addons_text", length = 300)
    private String addonsText;

    public enum Role { LEAD, SECOND }

    /** Staff on this session: sign-in name → LEAD or SECOND. Employees only see sessions they're on. */
    @jakarta.persistence.ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @jakarta.persistence.CollectionTable(name = "booking_assignment", joinColumns = @jakarta.persistence.JoinColumn(name = "booking_id"))
    @jakarta.persistence.MapKeyColumn(name = "staff_name", length = 60)
    @Column(name = "role", length = 10)
    private java.util.Map<String, String> assignments = new java.util.HashMap<>();

    /** True when the chosen package/add-ons call for two photographers. */
    @Column(name = "needs_second", nullable = false)
    private boolean needsSecond;

    @Column(name = "session_time", nullable = false, length = 40)
    private String time;

    /** The slot this booking occupies on the calendar; null only on rows from before scheduling existed. */
    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.PENDING;

    @Column(precision = 10, scale = 2)
    private BigDecimal quote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getSessionTypeKey() { return sessionTypeKey; }
    public void setSessionTypeKey(String v) { this.sessionTypeKey = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getPhone() { return phone; }
    public void setPhone(String v) { this.phone = v; }
    public String getLocation() { return location; }
    public void setLocation(String v) { this.location = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate v) { this.date = v; }
    /** Everyone on the session, lead first. */
    public java.util.List<String> getAssignees() {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (getLead() != null) out.add(getLead());
        if (getSecond() != null) out.add(getSecond());
        return out;
    }
    public String getLead() { return assignments.entrySet().stream().filter(e -> "LEAD".equals(e.getValue())).map(java.util.Map.Entry::getKey).findFirst().orElse(null); }
    public String getSecond() { return assignments.entrySet().stream().filter(e -> "SECOND".equals(e.getValue())).map(java.util.Map.Entry::getKey).findFirst().orElse(null); }
    public void setTeam(String lead, String second) {
        assignments.clear();
        if (lead != null) assignments.put(lead, "LEAD");
        if (second != null && (lead == null || !second.equalsIgnoreCase(lead))) assignments.put(second, "SECOND");
    }
    public boolean isAssignedTo(String name) { return assignments.keySet().stream().anyMatch(a -> a.equalsIgnoreCase(name)); }
    public boolean isNeedsSecond() { return needsSecond; }
    public void setNeedsSecond(boolean v) { this.needsSecond = v; }
    public Integer getHours() { return hours; }
    public void setHours(Integer v) { this.hours = v; }
    public java.util.List<String> getAddons() {
        return addonsText == null || addonsText.isBlank() ? java.util.List.of()
                : java.util.Arrays.stream(addonsText.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
    public void setAddons(java.util.List<String> names) { this.addonsText = names == null || names.isEmpty() ? null : String.join(", ", names); }
    /** "Event coverage · 3 hrs + Second photographer" — what the customer actually chose. */
    public String packageSummary() {
        if (packageName == null) return null;
        StringBuilder sb = new StringBuilder(packageName);
        if (hours != null) sb.append(" · ").append(hours).append(hours == 1 ? " hr" : " hrs");
        var a = getAddons();
        if (!a.isEmpty()) sb.append(" + ").append(String.join(" + ", a));
        return sb.toString();
    }
    public String getPackageName() { return packageName; }
    public void setPackageName(String v) { this.packageName = v; }
    public String getTime() { return time; }
    public void setTime(String v) { this.time = v; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime v) { this.startTime = v; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime v) { this.endTime = v; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus v) { this.status = v; }
    public BigDecimal getQuote() { return quote; }
    public void setQuote(BigDecimal v) { this.quote = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
}
