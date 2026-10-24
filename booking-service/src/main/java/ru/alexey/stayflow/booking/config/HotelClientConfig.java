package ru.alexey.stayflow.booking.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import ru.alexey.stayflow.booking.client.HotelGateway;
import ru.alexey.stayflow.booking.client.HttpHotelGateway;
import ru.alexey.stayflow.booking.exception.HotelClientException;
import ru.alexey.stayflow.booking.exception.RoomNotAvailableException;
import ru.alexey.stayflow.common.error.RemoteServiceException;

@Configuration(proxyBeanMethods = false)
public class HotelClientConfig {

    @Bean
    @LoadBalanced
    public RestClient.Builder hotelRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public Retry hotelRetry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(200, 2))
                .retryExceptions(RemoteServiceException.class)
                .ignoreExceptions(RoomNotAvailableException.class, HotelClientException.class)
                .build();
        return Retry.of("hotelClient", config);
    }

    @Bean
    public CircuitBreaker hotelCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(3)
                .ignoreExceptions(RoomNotAvailableException.class, HotelClientException.class)
                .build();
        return CircuitBreaker.of("hotelClient", config);
    }

    @Bean
    public TimeLimiter hotelTimeLimiter() {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(4))
                .cancelRunningFuture(true)
                .build();
        return TimeLimiter.of("hotelClient", config);
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService hotelCallExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public HotelGateway hotelGateway(
            @Qualifier("hotelRestClientBuilder") RestClient.Builder builder,
            @Value("${stayflow.hotel.base-url:http://hotel-service}") String baseUrl,
            @Value("${stayflow.security.internal-api-key}") String internalApiKey,
            Retry hotelRetry,
            CircuitBreaker hotelCircuitBreaker,
            TimeLimiter hotelTimeLimiter,
            ExecutorService hotelCallExecutor) {
        return new HttpHotelGateway(
                builder, baseUrl, internalApiKey, hotelRetry, hotelCircuitBreaker, hotelTimeLimiter, hotelCallExecutor);
    }
}
