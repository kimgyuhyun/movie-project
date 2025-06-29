package com.movie.movie_backend.controller;

import com.movie.movie_backend.dto.UserJoinRequestDto;
import com.movie.movie_backend.entity.PasswordResetToken;
import com.movie.movie_backend.entity.User;
import com.movie.movie_backend.repository.PasswordResetTokenRepository;
import com.movie.movie_backend.repository.USRUserRepository;
import com.movie.movie_backend.service.MailService;
import com.movie.movie_backend.service.USRUserService;
import com.movie.movie_backend.constant.Provider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class UserController {
    
    private final USRUserService userService;
    private final USRUserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    
    // REST API - 회원가입
    @PostMapping("/api/users/join")
    public ResponseEntity<Map<String, Object>> joinApi(@Valid @RequestBody UserJoinRequestDto requestDto) {
        Map<String, Object> response = new HashMap<>();
        try {
            log.info("=== 회원가입 요청 시작 ===");
            log.info("요청 DTO: {}", requestDto);
            log.info("아이디: {}", requestDto.getLoginId());
            log.info("이메일: {}", requestDto.getEmail());
            log.info("닉네임: {}", requestDto.getNickname());
            
            userService.join(requestDto);
            
            log.info("회원가입 성공: {}", requestDto.getLoginId());
            response.put("success", true);
            response.put("message", "회원가입이 완료되었습니다.");
            response.put("nickname", requestDto.getNickname());
            response.put("loginId", requestDto.getLoginId());
            response.put("email", requestDto.getEmail());
            response.put("redirect", "/login"); // 로그인 페이지로 리다이렉트 안내
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("회원가입 실패: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("회원가입 중 예상치 못한 오류: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "회원가입 중 오류가 발생했습니다.");
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    // REST API - 아이디 중복 확인
    @GetMapping("/api/users/check-login-id")
    public ResponseEntity<Map<String, Object>> checkLoginId(@RequestParam String loginId) {
        Map<String, Object> response = new HashMap<>();
        boolean isDuplicate = userService.checkLoginIdDuplicate(loginId);
        response.put("duplicate", isDuplicate);
        response.put("available", !isDuplicate);
        response.put("message", isDuplicate ? "이미 사용 중인 아이디입니다." : "사용 가능한 아이디입니다.");
        return ResponseEntity.ok(response);
    }
    
    // REST API - 이메일 중복 확인
    @GetMapping("/api/users/check-email")
    public ResponseEntity<Map<String, Object>> checkEmail(@RequestParam String email) {
        Map<String, Object> response = new HashMap<>();
        boolean isDuplicate = userService.checkEmailDuplicate(email);
        response.put("duplicate", isDuplicate);
        response.put("available", !isDuplicate);
        response.put("message", isDuplicate ? "이미 사용 중인 이메일입니다." : "사용 가능한 이메일입니다.");
        return ResponseEntity.ok(response);
    }
    
    // REST API - 닉네임 중복 확인
    @GetMapping("/api/users/check-nickname")
    public ResponseEntity<Map<String, Object>> checkNickname(@RequestParam String nickname) {
        Map<String, Object> response = new HashMap<>();
        boolean isDuplicate = userService.checkNicknameDuplicate(nickname);
        response.put("duplicate", isDuplicate);
        response.put("available", !isDuplicate);
        response.put("message", isDuplicate ? "이미 사용 중인 닉네임입니다." : "사용 가능한 닉네임입니다.");
        return ResponseEntity.ok(response);
    }
    
    // REST API - 닉네임 추천
    @GetMapping("/api/users/recommend-nickname")
    public ResponseEntity<Map<String, Object>> recommendNickname() {
        Map<String, Object> response = new HashMap<>();
        response.put("nicknames", userService.recommendNicknames());
        return ResponseEntity.ok(response);
    }

    // REST API - 아이디 찾기
    @PostMapping("/api/find-id")
    public ResponseEntity<Map<String, Object>> findIdApi(@RequestBody Map<String, String> req) {
        Map<String, Object> response = new HashMap<>();
        String email = req.get("email");
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            response.put("success", false);
            response.put("message", "가입된 이메일이 아닙니다.");
            return ResponseEntity.ok(response);
        }
        if (user.getProvider() != null && !user.getProvider().name().equals("LOCAL")) {
            response.put("success", false);
            response.put("message", "이 이메일은 '" + user.getProvider().getDisplayName() + "' 소셜 계정입니다. 해당 소셜 로그인 버튼을 이용해 주세요.");
            return ResponseEntity.ok(response);
        }
        String maskedLoginId = maskLoginId(user.getLoginId());
        response.put("success", true);
        response.put("maskedLoginId", maskedLoginId);
        response.put("message", "아이디를 찾았습니다.");
        return ResponseEntity.ok(response);
    }

    // 로그인 ID 마스킹 유틸
    private String maskLoginId(String loginId) {
        if (loginId == null || loginId.length() <= 2) return loginId;
        return loginId.substring(0, 2) + "***" + loginId.substring(loginId.length() - 1);
    }

    // REST API: 비밀번호 찾기(소셜/자체 분기)
    @PostMapping("/api/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPasswordApi(@RequestBody Map<String, String> req) {
        Map<String, Object> response = new HashMap<>();
        String email = req.get("email");
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            response.put("type", "NOT_FOUND");
            response.put("message", "가입된 이메일이 아닙니다.");
            return ResponseEntity.ok(response);
        }
        if (user.getProvider() != null && user.getProvider().name().equals("LOCAL") == false) {
            response.put("type", "SOCIAL_ONLY");
            response.put("provider", user.getProvider().name());
            response.put("email", user.getEmail());
            response.put("nickname", user.getNickname());
            response.put("message", user.getProvider().getDisplayName() + " 소셜 로그인 전용 계정입니다. 자체 로그인(비밀번호)도 사용하시겠습니까?");
            return ResponseEntity.ok(response);
        }
        PasswordResetToken token = userService.createPasswordResetToken(email);
        String resetLink = "http://localhost:8080/reset-password?token=" + token.getToken();
        mailService.sendResetPasswordEmail(email, resetLink);
        response.put("type", "NORMAL");
        response.put("message", "비밀번호 재설정 링크가 이메일로 발송되었습니다.");
        return ResponseEntity.ok(response);
    }

    // REST API: 소셜 전용 계정 → 자체 로그인 통합(비밀번호 설정)
    @PostMapping("/api/social-password-join")
    public ResponseEntity<Map<String, Object>> socialPasswordJoin(@RequestBody Map<String, String> req) {
        Map<String, Object> response = new HashMap<>();
        String email = req.get("email");
        String nickname = req.get("nickname");
        String password = req.get("password");
        String passwordConfirm = req.get("passwordConfirm");
        if (!password.equals(passwordConfirm)) {
            response.put("success", false);
            response.put("message", "비밀번호가 일치하지 않습니다.");
            return ResponseEntity.ok(response);
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            response.put("success", false);
            response.put("message", "가입된 이메일이 아닙니다.");
            return ResponseEntity.ok(response);
        }
        if (user.getProvider() == null || user.getProvider().name().equals("LOCAL")) {
            response.put("success", false);
            response.put("message", "이미 자체 로그인 계정입니다.");
            return ResponseEntity.ok(response);
        }
        user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user);
        response.put("success", true);
        response.put("message", "비밀번호가 설정되었습니다. 이제 자체 로그인도 가능합니다.");
        return ResponseEntity.ok(response);
    }

    // REST API - 비밀번호 재설정 토큰 검증
    @PostMapping("/api/reset-password/validate-token")
    public ResponseEntity<Map<String, Object>> validateResetToken(@RequestParam String token) {
        Map<String, Object> response = new HashMap<>();
        try {
            userService.validatePasswordResetToken(token);
            response.put("success", true);
            response.put("message", "유효한 토큰입니다.");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // REST API - 비밀번호 재설정
    @PostMapping("/api/reset-password")
    public ResponseEntity<Map<String, Object>> resetPasswordApi(@RequestParam String token, 
                                                               @RequestParam String newPassword, 
                                                               @RequestParam String newPasswordConfirm) {
        Map<String, Object> response = new HashMap<>();
        
        if (!newPassword.equals(newPasswordConfirm)) {
            response.put("success", false);
            response.put("message", "비밀번호가 일치하지 않습니다.");
            return ResponseEntity.badRequest().body(response);
        }
        
        if (!isValidPassword(newPassword)) {
            response.put("success", false);
            response.put("message", "비밀번호는 8자 이상, 영문/숫자/특수문자를 모두 포함해야 합니다.");
            return ResponseEntity.badRequest().body(response);
        }
        
        try {
            userService.resetPassword(token, newPassword);
            response.put("success", true);
            response.put("message", "비밀번호가 성공적으로 변경되었습니다.");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 비밀번호 유효성 검사
    private boolean isValidPassword(String password) {
        if (password == null || password.length() < 8) return false;
        boolean hasLetter = password.matches(".*[a-zA-Z].*");
        boolean hasDigit = password.matches(".*\\d.*");
        boolean hasSpecial = password.matches(".*[!@#$%^&*(),.?\":{}|<>].*");
        return hasLetter && hasDigit && hasSpecial;
    }

    // REST API - 소셜 회원가입 추가 정보(닉네임, 약관동의)
    @PostMapping("/api/social-join-complete")
    public ResponseEntity<Map<String, Object>> socialJoinComplete(@RequestBody Map<String, Object> req) {
        Map<String, Object> response = new HashMap<>();
        String nickname = (String) req.get("nickname");
        Boolean agree = (Boolean) req.get("agree");
        if (nickname == null || nickname.isBlank()) {
            response.put("success", false);
            response.put("message", "닉네임을 입력해 주세요.");
            return ResponseEntity.ok(response);
        }
        if (agree == null || !agree) {
            response.put("success", false);
            response.put("message", "약관에 동의해야 가입이 완료됩니다.");
            return ResponseEntity.ok(response);
        }
        
        // Spring Security Authentication에서 소셜 사용자 정보 추출
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || 
            !(authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.user.DefaultOAuth2User)) {
            response.put("success", false);
            response.put("message", "소셜 로그인이 필요합니다.");
            return ResponseEntity.ok(response);
        }
        
        org.springframework.security.oauth2.core.user.DefaultOAuth2User oauth2User = 
            (org.springframework.security.oauth2.core.user.DefaultOAuth2User) authentication.getPrincipal();
        
        String email = oauth2User.getAttribute("email");
        String provider = oauth2User.getAttribute("provider");
        String providerId = oauth2User.getAttribute("providerId");
        
        if (email == null || provider == null || providerId == null) {
            response.put("success", false);
            response.put("message", "소셜 로그인 정보가 올바르지 않습니다.");
            return ResponseEntity.ok(response);
        }
        
        // 해당 유저 찾기
        Provider providerEnum = Provider.valueOf(provider.toUpperCase());
        User user = userRepository.findByProviderAndProviderId(providerEnum, providerId).orElse(null);
        if (user == null) {
            response.put("success", false);
            response.put("message", "사용자를 찾을 수 없습니다. 다시 로그인해 주세요.");
            return ResponseEntity.ok(response);
        }
        
        // 닉네임 중복 체크
        if (userRepository.existsByNickname(nickname)) {
            response.put("success", false);
            response.put("message", "이미 사용 중인 닉네임입니다.");
            return ResponseEntity.ok(response);
        }
        
        user.setNickname(nickname);
        user.setSocialJoinCompleted(true);
        userRepository.save(user);
        response.put("success", true);
        response.put("message", "소셜 회원가입이 완료되었습니다. 이제 로그인하세요.");
        return ResponseEntity.ok(response);
    }

    // REST API - 로그아웃
    @PostMapping("/api/logout")
    public ResponseEntity<Map<String, Object>> logoutApi() {
        Map<String, Object> response = new HashMap<>();
        
        // Spring Security 컨텍스트 클리어
        SecurityContextHolder.clearContext();
        
        response.put("success", true);
        response.put("message", "로그아웃 성공");
        return ResponseEntity.ok(response);
    }

    /**
     * 현재 로그인한 사용자 정보 조회
     */
    @GetMapping("/api/current-user")
    public ResponseEntity<Map<String, Object>> getCurrentUser(HttpServletRequest request) {
        log.info("=== /api/current-user 호출됨 ===");
        
        try {
            // 세션에서 직접 사용자 정보 확인
            HttpSession session = request.getSession(false);
            if (session != null) {
                String sessionLoginId = (String) session.getAttribute("USER_LOGIN_ID");
                log.info("세션에서 USER_LOGIN_ID: {}", sessionLoginId);
                
                if (sessionLoginId != null) {
                    User sessionUser = userRepository.findByLoginId(sessionLoginId).orElse(null);
                    if (sessionUser != null) {
                        log.info("세션에서 사용자 조회 성공: {}", sessionUser.getLoginId());
                        return ResponseEntity.ok()
                            .header("Cache-Control", "no-cache, no-store, must-revalidate")
                            .header("Pragma", "no-cache")
                            .header("Expires", "0")
                            .body(Map.of(
                                "success", true,
                                "user", Map.of(
                                    "id", sessionUser.getId(),
                                    "loginId", sessionUser.getLoginId(),
                                    "email", sessionUser.getEmail(),
                                    "nickname", sessionUser.getNickname(),
                                    "role", sessionUser.getRole().name(),
                                    "isAdmin", sessionUser.isAdmin(),
                                    "isUser", sessionUser.isUser()
                                )
                            ));
                    }
                }
            }
            
            // Spring Security Authentication에서 사용자 정보 가져오기
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            log.info("Authentication: {}", authentication);
            log.info("Authentication Principal: {}", authentication.getPrincipal());
            log.info("Authentication Principal Type: {}", authentication.getPrincipal().getClass().getName());
            log.info("Authentication Name: {}", authentication.getName());
            log.info("Authentication isAuthenticated: {}", authentication.isAuthenticated());
            
            User currentUser = null;
            
            if (authentication != null && authentication.isAuthenticated() && 
                !"anonymousUser".equals(authentication.getName())) {
                
                // OAuth2 사용자인 경우
                if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.user.DefaultOAuth2User) {
                    org.springframework.security.oauth2.core.user.DefaultOAuth2User oauth2User = 
                        (org.springframework.security.oauth2.core.user.DefaultOAuth2User) authentication.getPrincipal();
                    
                    String email = oauth2User.getAttribute("email");
                    String provider = oauth2User.getAttribute("provider");
                    String providerId = oauth2User.getAttribute("providerId");
                    
                    log.info("OAuth2 사용자 정보 - email: {}, provider: {}, providerId: {}", email, provider, providerId);
                    
                    if (email != null && provider != null && providerId != null) {
                        try {
                            Provider providerEnum = Provider.valueOf(provider.toUpperCase());
                            currentUser = userRepository.findByProviderAndProviderId(providerEnum, providerId).orElse(null);
                            log.info("OAuth2 사용자 조회 결과: {}", currentUser);
                        } catch (Exception e) {
                            log.error("OAuth2 사용자 조회 실패", e);
                        }
                    }
                }
                // Spring Security로 로그인한 사용자인 경우 (User 엔티티가 Principal)
                else if (authentication.getPrincipal() instanceof User) {
                    currentUser = (User) authentication.getPrincipal();
                    log.info("Spring Security 사용자 조회: {}", currentUser);
                }
                // 기타 경우 (loginId로 조회) - Spring Security의 UserDetails 구현체
                else {
                    String loginId = authentication.getName();
                    log.info("loginId로 사용자 조회 시도: {}", loginId);
                    currentUser = userRepository.findByLoginId(loginId).orElse(null);
                    log.info("loginId로 사용자 조회 결과: {}", currentUser);
                }
            }
            
            if (currentUser == null) {
                log.warn("인증된 사용자 정보가 없음 - Authentication: {}", authentication);
                return ResponseEntity.status(401)
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .header("Pragma", "no-cache")
                    .header("Expires", "0")
                    .body(Map.of(
                        "success", false,
                        "message", "로그인이 필요합니다."
                    ));
            }
            
            log.info("사용자 정보 조회 성공: {}", currentUser.getLoginId());
            log.info("사용자 역할: {}", currentUser.getRole());
            log.info("관리자 여부: {}", currentUser.isAdmin());
            
            return ResponseEntity.ok()
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .body(Map.of(
                    "success", true,
                    "user", Map.of(
                        "id", currentUser.getId(),
                        "loginId", currentUser.getLoginId(),
                        "email", currentUser.getEmail(),
                        "nickname", currentUser.getNickname(),
                        "role", currentUser.getRole().name(),
                        "isAdmin", currentUser.isAdmin(),
                        "isUser", currentUser.isUser()
                    )
                ));
        } catch (Exception e) {
            log.error("현재 사용자 정보 조회 실패", e);
            return ResponseEntity.badRequest()
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .body(Map.of(
                    "success", false,
                    "message", "사용자 정보 조회에 실패했습니다: " + e.getMessage()
                ));
        }
    }

    // REST API - 자체 로그인
    @PostMapping("/api/user-login")
    public ResponseEntity<Map<String, Object>> loginApi(@RequestBody Map<String, String> loginRequest, HttpServletRequest request) {
        log.info("=== /api/user-login 호출됨 ===");
        
        Map<String, Object> response = new HashMap<>();
        String loginId = loginRequest.get("loginId");
        String password = loginRequest.get("password");
        
        log.info("로그인 시도: {}", loginId);
        
        try {
            // Spring Security AuthenticationManager를 사용한 인증
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginId, password)
            );
            
            log.info("인증 성공: {}", authentication);
            log.info("인증 Principal: {}", authentication.getPrincipal());
            log.info("인증 Principal Type: {}", authentication.getPrincipal().getClass().getName());
            
            // 인증 성공 시 SecurityContext에 설정
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            // 세션에 인증 정보 저장
            HttpSession session = request.getSession(true);
            session.setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            
            // 세션을 즉시 저장
            session.setMaxInactiveInterval(3600); // 1시간
            session.setAttribute("USER_LOGIN_ID", loginId);
            
            log.info("세션 ID: {}", session.getId());
            log.info("세션에 SPRING_SECURITY_CONTEXT 저장됨");
            log.info("세션에 USER_LOGIN_ID 저장됨: {}", loginId);
            
            // Authentication에서 User 정보 가져오기
            User user = (User) authentication.getPrincipal();
            
            if (user != null) {
                log.info("로그인 성공: {}", user.getLoginId());
                log.info("사용자 역할: {}", user.getRole());
                log.info("관리자 여부: {}", user.isAdmin());
                
                response.put("success", true);
                response.put("message", "로그인 성공");
                response.put("user", Map.of(
                    "id", user.getId(),
                    "loginId", user.getLoginId(),
                    "nickname", user.getNickname(),
                    "email", user.getEmail(),
                    "role", user.getRole().name(),
                    "isAdmin", user.isAdmin()
                ));
                return ResponseEntity.ok(response);
            } else {
                log.warn("사용자 정보를 찾을 수 없음: {}", loginId);
                response.put("success", false);
                response.put("message", "사용자 정보를 찾을 수 없습니다.");
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            log.error("로그인 실패: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "아이디 또는 비밀번호가 올바르지 않습니다.");
            return ResponseEntity.badRequest().body(response);
        }
    }
} 
