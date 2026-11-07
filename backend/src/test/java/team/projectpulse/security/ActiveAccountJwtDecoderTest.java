package team.projectpulse.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import team.projectpulse.user.PeerEvaluationUser;
import team.projectpulse.user.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ActiveAccountJwtDecoderTest {

    @Mock JwtDecoder signedJwtDecoder;
    @Mock UserRepository userRepository;

    private Jwt issuedToken(Object userId) {
        return Jwt.withTokenValue("signed-token")
                .header("alg", "RS256")
                .claim("userId", userId)
                .build();
    }

    @Test
    void acceptsValidSignedTokenForActiveUser() {
        Jwt jwt = issuedToken(42);
        PeerEvaluationUser account = mock(PeerEvaluationUser.class);
        given(account.isEnabled()).willReturn(true);
        given(signedJwtDecoder.decode("signed-token")).willReturn(jwt);
        given(userRepository.findById(42)).willReturn(Optional.of(account));

        assertThat(new ActiveAccountJwtDecoder(signedJwtDecoder, userRepository)
                .decode("signed-token")).isSameAs(jwt);
    }

    @Test
    void deniesPreviouslyIssuedTokenAfterAccountDeactivation() {
        Jwt jwt = issuedToken(42);
        PeerEvaluationUser disabled = mock(PeerEvaluationUser.class);
        given(signedJwtDecoder.decode("signed-token")).willReturn(jwt);
        given(userRepository.findById(42)).willReturn(Optional.of(disabled));

        assertThatThrownBy(() -> new ActiveAccountJwtDecoder(signedJwtDecoder, userRepository)
                .decode("signed-token")).isInstanceOf(BadJwtException.class);
    }

    @Test
    void deniesDeletedAccountEvenForOtherwiseValidToken() {
        given(signedJwtDecoder.decode("signed-token")).willReturn(issuedToken(42));
        given(userRepository.findById(42)).willReturn(Optional.empty());

        assertThatThrownBy(() -> new ActiveAccountJwtDecoder(signedJwtDecoder, userRepository)
                .decode("signed-token")).isInstanceOf(BadJwtException.class);
    }

    @Test
    void rejectsMissingUserIdWithoutAccountLookup() {
        Jwt jwt = Jwt.withTokenValue("signed-token").header("alg", "RS256").subject("someone").build();
        given(signedJwtDecoder.decode("signed-token")).willReturn(jwt);

        assertThatThrownBy(() -> new ActiveAccountJwtDecoder(signedJwtDecoder, userRepository)
                .decode("signed-token")).isInstanceOf(BadJwtException.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    void rejectsMalformedUserIdWithoutAccountLookup() {
        given(signedJwtDecoder.decode("signed-token")).willReturn(issuedToken("not-an-id"));

        assertThatThrownBy(() -> new ActiveAccountJwtDecoder(signedJwtDecoder, userRepository)
                .decode("signed-token")).isInstanceOf(BadJwtException.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    void rejectsBadSignatureBeforeLookingUpAnyAccount() {
        given(signedJwtDecoder.decode("signed-token")).willThrow(new BadJwtException("Invalid signature"));

        assertThatThrownBy(() -> new ActiveAccountJwtDecoder(signedJwtDecoder, userRepository)
                .decode("signed-token")).isInstanceOf(BadJwtException.class);
        verifyNoInteractions(userRepository);
    }
}
