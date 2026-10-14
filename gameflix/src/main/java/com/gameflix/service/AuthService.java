package com.gameflix.service;

import com.gameflix.dto.LoginRequest;
import com.gameflix.dto.RegisterRequest;
import com.gameflix.dto.TokenResponse;
import com.gameflix.dto.UserResponse;
import com.gameflix.exception.EmailAlreadyRegisteredException;
import com.gameflix.exception.InvalidCredentialsException;
import com.gameflix.model.User;
import com.gameflix.repository.UserRepository;
import com.gameflix.security.JwtService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    // Valid hash of a random value, used to burn the same CPU time when the email is unknown,
    // so response timing does not reveal which emails are registered.
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.dummyHash = encoder.encode(java.util.UUID.randomUUID().toString());
    }

    public UserResponse register(RegisterRequest req) {
        String email = normalize(req.email());
        User user = new User(email, encoder.encode(req.password()), req.displayName().trim());
        try {
            // saveAndFlush so the INSERT (and the UNIQUE constraint) fires here, inside the try block.
            // The 409 is driven by the database constraint, not by a check-then-insert race.
            return UserResponse.from(users.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyRegisteredException();
        }
    }

    public TokenResponse login(LoginRequest req) {
        if (req == null || req.email() == null || req.password() == null
                || req.email().isBlank() || req.password().isEmpty()) {
            throw new InvalidCredentialsException();
        }
        Optional<User> found = users.findByEmail(normalize(req.email()));
        // Always run one hash comparison, whether or not the user exists.
        String hashToCheck = found.map(User::getPasswordHash).orElse(dummyHash);
        boolean matches = encoder.matches(req.password(), hashToCheck);
        if (found.isEmpty() || !matches) {
            throw new InvalidCredentialsException(); // one generic failure for every cause
        }
        return new TokenResponse(jwt.issueToken(found.get().getId()), "Bearer", jwt.getLifetime().toSeconds());
    }

    public Optional<UserResponse> profile(Long userId) {
        return users.findById(userId).map(UserResponse::from);
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
