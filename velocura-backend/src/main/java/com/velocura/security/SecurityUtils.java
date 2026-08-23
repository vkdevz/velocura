package com.velocura.security;

import com.velocura.model.Role;
import com.velocura.model.User;
import com.velocura.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

@Component
public class SecurityUtils {

    private final UserRepository userRepository;

    @Autowired
    public SecurityUtils(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "ANONYMOUS";
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        }
        return authentication.getName();
    }

    public Optional<User> getCurrentUser() {
        String email = getCurrentUserEmail();
        if ("ANONYMOUS".equalsIgnoreCase(email)) {
            return Optional.empty();
        }
        return userRepository.findByEmailIgnoreCase(email);
    }

    public Long getCurrentUserId() {
        return getCurrentUser().map(User::getId).orElse(null);
    }

    public String getCurrentUserRole() {
        return getCurrentUser().map(u -> u.getRole().name()).orElse("ANONYMOUS");
    }

    public boolean isCurrentPatient(Long targetPatientId) {
        if (targetPatientId == null) return false;
        Optional<User> userOpt = getCurrentUser();
        if (userOpt.isEmpty()) return false;

        User user = userOpt.get();
        if (user.getRole() == Role.ADMIN) return true;
        return user.getRole() == Role.PATIENT && user.getId().equals(targetPatientId);
    }

    public boolean isCurrentDoctor(Long targetDoctorId) {
        if (targetDoctorId == null) return false;
        Optional<User> userOpt = getCurrentUser();
        if (userOpt.isEmpty()) return false;

        User user = userOpt.get();
        if (user.getRole() == Role.ADMIN) return true;
        return user.getRole() == Role.DOCTOR && user.getId().equals(targetDoctorId);
    }

    public String getCurrentClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String xfHeader = request.getHeader("X-Forwarded-For");
                if (xfHeader != null && !xfHeader.isEmpty() && !xfHeader.equalsIgnoreCase("unknown")) {
                    return xfHeader.split(",")[0].trim();
                }
                String realIp = request.getHeader("X-Real-IP");
                if (realIp != null && !realIp.isEmpty() && !realIp.equalsIgnoreCase("unknown")) {
                    return realIp.trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception ignored) {
        }
        return "UNKNOWN";
    }
}
