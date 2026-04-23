package com.sos.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "\"Inventory\"")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "\"itemName\"", nullable = false)
    private String itemName;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false)
    private String unit;

    @Column(name = "\"lowStockThreshold\"", nullable = false, precision = 10, scale = 3)
    private BigDecimal lowStockThreshold;

    @Column(name = "\"restockAmount\"", nullable = false, precision = 10, scale = 3)
    private BigDecimal restockAmount = BigDecimal.ONE;

    @JsonIgnore
    @OneToMany(mappedBy = "inventoryItem")
    private List<MenuItem> menuItems;

    public Inventory() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getLowStockThreshold() { return lowStockThreshold; }
    public void setLowStockThreshold(BigDecimal lowStockThreshold) { this.lowStockThreshold = lowStockThreshold; }
    public BigDecimal getRestockAmount() { return restockAmount; }
    public void setRestockAmount(BigDecimal restockAmount) { this.restockAmount = restockAmount; }
    public List<MenuItem> getMenuItems() { return menuItems; }
    public void setMenuItems(List<MenuItem> menuItems) { this.menuItems = menuItems; }
}
