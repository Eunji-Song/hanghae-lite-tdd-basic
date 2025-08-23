package io.hhplus.tdd.config;

import io.hhplus.tdd.lock.InMemoryKeyLock;
import io.hhplus.tdd.lock.KeyLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConcurrencyConfig {
    @Bean
    public KeyLock keyLock() {
        // 공정락(fair=true). stripes 개수는 키 개수/트래픽에 맞춰 조절.
        return new InMemoryKeyLock(1024, true);
    }
}