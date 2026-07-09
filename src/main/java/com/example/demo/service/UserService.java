package com.example.demo.service;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService implements UserDetailsService {
    private final UserRepository userRepository;
    private final JourneyStateService journeyStateService;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, JourneyStateService journeyStateService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.journeyStateService = journeyStateService;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(String username, String password) {
        String normalizedUsername = username.trim();
        User user = userRepository.findByUsername(normalizedUsername).map(existing -> {
            if (existing.getPassword() != null && !existing.getPassword().isBlank()) {
                throw new IllegalStateException("此玩家名稱已被使用");
            }
            existing.setPassword(passwordEncoder.encode(password));
            return userRepository.save(existing);
        }).orElseGet(() -> userRepository.save(User.builder()
                .username(normalizedUsername)
                .email(normalizedUsername + "@example.com")
                .password(passwordEncoder.encode(password))
                .level(1)
                .exp(0)
                .coins(200)
                .bossPoints(0)
                .title("新手旅行者")
                .build()));
        journeyStateService.progressFor(user);
        return user;
    }

    public User login(String username, String password) {
        User user = userRepository.findByUsername(username.trim())
                .orElseThrow(() -> new IllegalArgumentException("玩家名稱或密碼錯誤"));
        if (user.getPassword() == null || user.getPassword().isBlank()
                || !passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("玩家名稱或密碼錯誤");
        }
        return user;
    }

    public Long userIdFor(String username) {
        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElseThrow(() -> new UsernameNotFoundException("user not found"));
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("user not found"));
        return org.springframework.security.core.userdetails.User.withUsername(user.getUsername())
                .password(user.getPassword() == null ? "" : user.getPassword())
                .authorities("USER")
                .build();
    }
}
