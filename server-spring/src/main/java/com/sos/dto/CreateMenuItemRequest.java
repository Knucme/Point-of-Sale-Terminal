package com.sos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class CreateMenuItemRequest {

    @NotBlank(message = "name is required.")
    private String name;

    @NotBlank(message = "category is required.")
    private String category;

    @NotNull(message = "price is required.")
    private BigDecimal price;

    private Integer inventoryItemId;

    public CreateMenuItemRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Integer getInventoryItemId() { return inventoryItemId; }
    public void setInventoryItemId(Integer inventoryItemId) { this.inventoryItemId = inventoryItemId; }
}
