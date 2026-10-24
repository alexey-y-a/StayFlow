package ru.alexey.stayflow.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class JwtConfigTest {

    private static final String SECRET =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(JwtConfig.class);

    @Test
    void createsDecoderWhenSecretIsPresent() {
        contextRunner
                .withPropertyValues("stayflow.security.jwt.secret=" + SECRET)
                .run(context -> {
                    assertThat(context).hasSingleBean(JwtDecoder.class);
                    assertThat(context).doesNotHaveBean(JwtEncoder.class);
                    assertThat(context).doesNotHaveBean(JwtTokenService.class);
                });
    }

    @Test
    void createsEncoderAndTokenServiceWhenIssuingEnabled() {
        contextRunner
                .withPropertyValues("stayflow.security.jwt.secret=" + SECRET, "stayflow.security.jwt.issue=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(JwtDecoder.class);
                    assertThat(context).hasSingleBean(JwtEncoder.class);
                    assertThat(context).hasSingleBean(JwtTokenService.class);
                });
    }

    @Test
    void doesNotCreateJwtBeansWithoutSecret() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(JwtDecoder.class);
            assertThat(context).doesNotHaveBean(JwtEncoder.class);
        });
    }
}
