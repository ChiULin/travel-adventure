package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

@Data
public class AuthRequest {
    @NotBlank(message = "請輸入玩家名稱")
    @Size(min = 3, max = 20, message = "玩家名稱需為 3 到 20 個字元")
    private String username;

    @NotBlank(message = "請輸入密碼")
    @Size(min = 8, max = 72, message = "密碼需為 8 到 72 個字元")
    @ToString.Exclude
    private String password;
}
