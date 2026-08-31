package com.velocura.chat.config;

import com.velocura.security.CustomUserDetailsService;
import com.velocura.security.JwtUtils;
import com.velocura.security.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtUtils jwtUtils;
    private final CustomUserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (!StringUtils.hasText(authHeader)) {
                authHeader = accessor.getFirstNativeHeader("token");
            }

            if (StringUtils.hasText(authHeader)) {
                String jwt = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;

                if (tokenBlacklistService != null && tokenBlacklistService.isBlacklisted(jwt)) {
                    log.warn("WebSocket CONNECT rejected: token is blacklisted");
                    return null;
                }

                if (jwtUtils.validateToken(jwt)) {
                    try {
                        String email = jwtUtils.getEmailFromToken(jwt);
                        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                        accessor.setUser(authentication);
                        log.debug("WebSocket connection authenticated for user: {}", email);
                    } catch (Exception e) {
                        log.error("Failed to authenticate WebSocket user from JWT: {}", e.getMessage());
                    }
                } else {
                    log.warn("WebSocket CONNECT rejected: invalid JWT token");
                }
            }
        }
        return message;
    }
}
