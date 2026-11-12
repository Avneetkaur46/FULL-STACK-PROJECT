
package com.librahub.service;

import com.librahub.entity.AppUser;
import com.librahub.entity.Member;
import com.librahub.repository.AppUserRepository;
import com.librahub.repository.MemberRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.Map;

@Service
public class AuthService {

    private final AppUserRepository userRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecretKey jwtKey;

    public AuthService(
            AppUserRepository userRepository,
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.jwt.secret}") String secret) {

        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;

        this.jwtKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8));
    }

    @Transactional
    public Map<String, Object> register(
            String name, String email, String phone, String password) {

        email = email.trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException(
                    "An account with this email already exists.");
        }

        if (memberRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException(
                    "This email is already registered as a library member.");
        }

        Member member = new Member();
        member.setName(name.trim());
        member.setEmail(email);
        member.setPhone(phone.trim());

        member = memberRepository.save(member);

        AppUser user = new AppUser(
                email,
                passwordEncoder.encode(password),
                "MEMBER",
                member);

        userRepository.save(user);

        return createToken(user);
    }

    public Map<String, Object> login(String email, String password) {
        AppUser user = userRepository
                .findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid email or password."));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException(
                    "Invalid email or password.");
        }

        return createToken(user);
    }


    private Map<String, Object> createToken(AppUser user) {
        Instant now = Instant.now();

        String token = Jwts.builder()
                .subject(user.getEmail())
                .claim("role", user.getRole())
                .claim("userId", user.getId())
                .claim("memberId", user.getMember() == null
                        ? 0L : user.getMember().getId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(60 * 60 * 8)))
                .signWith(jwtKey)
                .compact();

        return Map.of(
                "token", token,
                "email", user.getEmail(),
                "role", user.getRole(),
                "memberId", user.getMember() == null
                        ? 0L : user.getMember().getId()
        );
    }

}
