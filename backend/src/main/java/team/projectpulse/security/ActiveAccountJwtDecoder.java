package team.projectpulse.security;

import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import team.projectpulse.user.PeerEvaluationUser;
import team.projectpulse.user.UserRepository;

/**
 * Enforces FR-SEC-active-account for bearer-token requests, not just at login.
 * The delegate must validate the JWT signature, issuer/expiry and other configured
 * claims BEFORE any account lookup; no unsigned data is trusted as an identity.
 */
public final class ActiveAccountJwtDecoder implements JwtDecoder {

    private final JwtDecoder signedJwtDecoder;
    private final UserRepository userRepository;

    public ActiveAccountJwtDecoder(JwtDecoder signedJwtDecoder, UserRepository userRepository) {
        this.signedJwtDecoder = signedJwtDecoder;
        this.userRepository = userRepository;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        Jwt jwt = this.signedJwtDecoder.decode(token);
        Integer userId = parseUserId(jwt.getClaim("userId"));
        if (userId == null || userId <= 0 ||
                this.userRepository.findById(userId).filter(PeerEvaluationUser::isEnabled).isEmpty()) {
            throw new BadJwtException("Account is inactive or unavailable.");
        }
        return jwt;
    }

    private Integer parseUserId(Object claim) {
        try {
            if (claim instanceof Number number) {
                long id = number.longValue();
                return id > 0 && id <= Integer.MAX_VALUE ? (int) id : null;
            }
            if (claim instanceof String value) {
                return Integer.valueOf(value);
            }
        } catch (NumberFormatException ignored) {
            // Treat malformed claims as invalid instead of throwing a 500.
        }
        return null;
    }
}
