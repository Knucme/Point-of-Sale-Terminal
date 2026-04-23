package com.sos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class CreateInventoryRequest {

    @NotBlank(message = "itemName is required.")
    private String itemName;

    @NotNull(message = "quantity is required.")
    private BigDecimal quantity;

    @NotBlank(message = "unit is required.")
    private String unit;

    @NotNull(message = "lowStockThreshold is required.")
    private BigDecimal lowStockThreshold;

    private BigDecimal restockAmount;

    public CreateInventoryRequest() {}

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
}
