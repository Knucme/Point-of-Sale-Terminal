package com.sos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "\"Order\"")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "\"tableNumber\"", nullable = false)
    private Integer tableNumber;

    @Column(name = "\"submittedById\"", nullable = false, insertable = false, updatable = false)
    private Integer submittedById;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"submittedById\"", nullable = false)
    private User submittedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "\"status\"", nullable = false)
    private OrderStatus status;

    @Column(name = "\"estimatedWait\"")
    private Integer estimatedWait;

    @Column(name = "\"bohNote\"")
    private String bohNote;

    @Column(name = "\"receiptNumber\"")
    private String receiptNumber;

    @Column(name = "\"createdAt\"", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems;

    @JsonIgnoreProperties({"order"})
    @OneToOne(mappedBy = "order")
    private SalesRecord salesRecord;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = OrderStatus.PENDING;
    }

    public Order() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getTableNumber() { return tableNumber; }
    public void setTableNumber(Integer tableNumber) { this.tableNumber = tableNumber; }
    public Integer getSubmittedById() { return submittedById; }
    public User getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(User submittedBy) { this.submittedBy = submittedBy; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public Integer getEstimatedWait() { return estimatedWait; }
    public void setEstimatedWait(Integer estimatedWait) { this.estimatedWait = estimatedWait; }
    public String getBohNote() { return bohNote; }
    public void setBohNote(String bohNote) { this.bohNote = bohNote; }
    public String getReceiptNumber() { return receiptNumber; }
    public void setReceiptNumber(String receiptNumber) { this.receiptNumber = receiptNumber; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public List<OrderItem> getOrderItems() { return orderItems; }
    public void setOrderItems(List<OrderItem> orderItems) { this.orderItems = orderItems; }
    public SalesRecord getSalesRecord() { return salesRecord; }
    public void setSalesRecord(SalesRecord salesRecord) { this.salesRecord = salesRecord; }
}
