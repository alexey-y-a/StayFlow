package ru.alexey.stayflow.booking.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import ru.alexey.stayflow.booking.client.HotelGateway;
import ru.alexey.stayflow.booking.client.HttpHotelGateway;

@Configuration(proxyBeanMethods = false)
public class HotelClientConfig {

    @Bean
    @LoadBalanced
    public RestClient.Builder hotelRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public HotelGateway hotelGateway(
            @Qualifier("hotelRestClientBuilder") RestClient.Builder builder,
            @Value("${stayflow.hotel.base-url:http://hotel-service}") String baseUrl,
            @Value("${stayflow.security.internal-api-key}") String internalApiKey) {
        return new HttpHotelGateway(builder, baseUrl, internalApiKey);
    }
}
