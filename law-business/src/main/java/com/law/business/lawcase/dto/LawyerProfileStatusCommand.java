package com.law.business.lawcase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class LawyerProfileStatusCommand
{
    @NotNull(message = "请选择律师") private Long userId;
    @NotBlank(message = "请选择可分案状态") private String assignEnabled;

    public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public String getAssignEnabled(){return assignEnabled;} public void setAssignEnabled(String v){assignEnabled=v;}
}
