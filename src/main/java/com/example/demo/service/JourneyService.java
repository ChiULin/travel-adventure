package com.example.demo.service;

import com.example.demo.entity.Checkin;
import com.example.demo.repository.CheckinRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class JourneyService {
    private final CheckinRepository checkinRepository;

    public JourneyService(CheckinRepository checkinRepository) {
        this.checkinRepository = checkinRepository;
    }

    public List<Checkin> getUserCheckins(Long userId) {
        return checkinRepository.findByUserId(userId);
    }
}
