package com.memoriesmadevisible.photo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A photo placed into one of the website's named slots (home hero, portfolio
 * cards, …). The bytes live in S3; this row maps a slot to its object key.
 */
@Entity
@Table(name = "site_photo")
public class SitePhoto {

    @Id
    @Column(name = "slot_id", length = 60)
    private String slotId;

    @Column(name = "s3_key", nullable = false, length = 300)
    private String s3Key;

    @Column(name = "content_type", nullable = false, length = 80)
    private String contentType;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SitePhoto() {}

    public SitePhoto(String slotId, String s3Key, String contentType) {
        this.slotId = slotId;
        this.s3Key = s3Key;
        this.contentType = contentType;
        this.updatedAt = Instant.now();
    }

    public String getSlotId() { return slotId; }
    public String getS3Key() { return s3Key; }
    public void setS3Key(String v) { this.s3Key = v; this.updatedAt = Instant.now(); }
    public String getContentType() { return contentType; }
    public void setContentType(String v) { this.contentType = v; }
    public Instant getUpdatedAt() { return updatedAt; }
}
