package ru.alexey.stayflow.hotel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import ru.alexey.stayflow.common.config.CommonWebConfig;
import ru.alexey.stayflow.common.security.JwtConfig;

@Import({CommonWebConfig.class, JwtConfig.class})
@SpringBootApplication
public class HotelServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(HotelServiceApplication.class, args);
    }
}