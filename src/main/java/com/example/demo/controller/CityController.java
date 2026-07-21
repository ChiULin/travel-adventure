package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.entity.City;
import com.example.demo.service.CityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cities")
public class CityController {

    private final CityService cityService;

    public CityController(CityService cityService) {
        this.cityService = cityService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<City>>> list() {
        return ResponseEntity.ok(ApiResponse.success("取得城市列表成功", cityService.listAll()));
    }
}
