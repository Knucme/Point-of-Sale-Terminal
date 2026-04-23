package com.sos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "\"Alert\"")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "\"senderId\"", nullable = false, insertable = false, updatable = false)
    private Integer senderId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "\"senderId\"", nullable = false)
    private User sender;

    @Enumerated(EnumType.STRING)
    @Column(name = "\"recipientScope\"", nullable = false)
    private RecipientScope recipientScope;

    @Column(nullable = false)
    private String message;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(name = "\"isRead\"", nullable = false)
    private Boolean isRead = false;

    @PrePersist
    void prePersist() {
        if (timestamp == null) timestamp = Instant.now();
    }

    public Alert() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getSenderId() { return senderId; }
    public User getSender() { return sender; }
    public void setSender(User sender) { this.sender = sender; }
    public RecipientScope getRecipientScope() { return recipientScope; }
    public void setRecipientScope(RecipientScope recipientScope) { this.recipientScope = recipientScope; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public Boolean getIsRead() { return isRead; }
    public void setIsRead(Boolean isRead) { this.isRead = isRead; }
}
