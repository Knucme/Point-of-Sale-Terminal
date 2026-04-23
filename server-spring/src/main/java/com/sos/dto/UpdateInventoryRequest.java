package com.sos.dto;

import java.math.BigDecimal;

public class UpdateInventoryRequest {
    private String itemName;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal lowStockThreshold;
    private BigDecimal restockAmount;

    public UpdateInventoryRequest() {}

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getLowStockThreshold() { return lowStockThreshold; }
    public void setLowStockThreshold(BigDecimal lowStockThreshold) { this.lowStockThreshold = lowStockThreshold; }
    public BigDecimal getRestockAmount() { return restockAmount; }
    public void setRestockAmount(BigDecimal restockAmount) { this.restockAmount = restockAmount; }
}
