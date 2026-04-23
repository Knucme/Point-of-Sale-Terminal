package com.sos.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

@Entity
@Table(name = "\"OrderItem\"")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "\"orderId\"", nullable = false, insertable = false, updatable = false)
    private Integer orderId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"orderId\"", nullable = false)
    private Order order;

    @Column(name = "\"menuItemId\"", nullable = false, insertable = false, updatable = false)
    private Integer menuItemId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "\"menuItemId\"", nullable = false)
    private MenuItem menuItem;

    @Column(nullable = false)
    private Integer quantity = 1;

    @Column(name = "\"specialInstructions\"")
    private String specialInstructions;

    @Column(name = "\"prepStatus\"")
    private String prepStatus = "WAITING";

    public OrderItem() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getOrderId() { return orderId; }
    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }
    public Integer getMenuItemId() { return menuItemId; }
    public MenuItem getMenuItem() { return menuItem; }
    public void setMenuItem(MenuItem menuItem) { this.menuItem = menuItem; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getSpecialInstructions() { return specialInstructions; }
    public void setSpecialInstructions(String specialInstructions) { this.specialInstructions = specialInstructions; }
    public String getPrepStatus() { return prepStatus; }
    public void setPrepStatus(String prepStatus) { this.prepStatus = prepStatus; }
}
