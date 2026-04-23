package com.sos.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "\"User\"")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String username;

    @JsonIgnore
    @Column(name = "\"passwordHash\"", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "\"role\"", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "\"status\"", nullable = false)
    private UserStatus status;

    @Column(name = "\"createdAt\"", nullable = false)
    private Instant createdAt;

    @JsonIgnore
    @OneToMany(mappedBy = "submittedBy")
    private List<Order> orders;

    @JsonIgnore
    @OneToMany(mappedBy = "sender")
    private List<Alert> alerts;

    @JsonIgnore
    @OneToMany(mappedBy = "user")
    private List<SecurityLog> securityLogs;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = UserStatus.ACTIVE;
    }

    public User() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public List<Order> getOrders() { return orders; }
    public void setOrders(List<Order> orders) { this.orders = orders; }
    public List<Alert> getAlerts() { return alerts; }
    public void setAlerts(List<Alert> alerts) { this.alerts = alerts; }
    public List<SecurityLog> getSecurityLogs() { return securityLogs; }
    public void setSecurityLogs(List<SecurityLog> securityLogs) { this.securityLogs = securityLogs; }
}
