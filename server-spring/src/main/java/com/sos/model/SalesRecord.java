package com.sos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "\"SalesRecord\"")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SalesRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "\"orderId\"", nullable = false, unique = true, insertable = false, updatable = false)
    private Integer orderId;

    @JsonIgnoreProperties({"salesRecord"})
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "\"orderId\"", nullable = false, unique = true)
    private Order order;

    @Column(name = "\"totalAmount\"", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "\"paymentMethod\"", nullable = false)
    private String paymentMethod = "CASH";

    @Column(name = "\"cardLast4\"")
    private String cardLast4;

    @Column(name = "\"completedAt\"", nullable = false)
    private Instant completedAt;

    @PrePersist
    void prePersist() {
        if (completedAt == null) completedAt = Instant.now();
    }

    public SalesRecord() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getOrderId() { return orderId; }
    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getCardLast4() { return cardLast4; }
    public void setCardLast4(String cardLast4) { this.cardLast4 = cardLast4; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
