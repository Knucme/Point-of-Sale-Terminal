package com.sos.dto;

import java.math.BigDecimal;

public class UpdateMenuItemRequest {
    private String name;
    private String category;
    private BigDecimal price;
    private Integer inventoryItemId;
    private String availabilityStatus;

    public UpdateMenuItemRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Integer getInventoryItemId() { return inventoryItemId; }
    public void setInventoryItemId(Integer inventoryItemId) { this.inventoryItemId = inventoryItemId; }
    public String getAvailabilityStatus() { return availabilityStatus; }
    public void setAvailabilityStatus(String availabilityStatus) { this.availabilityStatus = availabilityStatus; }
}
