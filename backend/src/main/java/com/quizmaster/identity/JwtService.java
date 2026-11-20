package com.quizmaster.identity;

import java.time.Clock;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final Clock clock;

    public JwtService(JwtEncoder encoder, Clock clock) {
        this.encoder = encoder;
        this.clock = clock;
    }

    Token issue(AccountView account) {
        var now = clock.instant();
        var claims = JwtClaimsSet.builder().issuer("quizmaster-qa")
                .audience(java.util.List.of("quizmaster-api"))
                .subject(account.id().toString()).issuedAt(now).expiresAt(now.plusSeconds(900)).build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new Token(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), "Bearer", 900);
    }

    record Token(String accessToken, String tokenType, long expiresIn) {
        @Override public String toString() { return "Token[redacted]"; }
    }
}
