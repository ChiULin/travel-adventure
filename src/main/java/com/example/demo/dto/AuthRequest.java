package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AuthRequest {
    @NotBlank(message = "玩家名稱不可空白")
    @Size(min = 3, max = 20, message = "玩家名稱長度需為 3 到 20 個字元")
    private String username;

    @NotBlank(message = "密碼不可空白")
    @Size(min = 8, max = 72, message = "密碼長度需為 8 到 72 個字元")
    private String password;
}
