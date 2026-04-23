package com.sos.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateAlertRequest {

    @NotBlank(message = "message is required.")
    private String message;

    @NotBlank(message = "recipientScope (BROADCAST | SPECIFIC) is required.")
    private String recipientScope;

    private Integer targetUserId;

    public CreateAlertRequest() {}

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getRecipientScope() { return recipientScope; }
    public void setRecipientScope(String recipientScope) { this.recipientScope = recipientScope; }
    public Integer getTargetUserId() { return targetUserId; }
    public void setTargetUserId(Integer targetUserId) { this.targetUserId = targetUserId; }
}
