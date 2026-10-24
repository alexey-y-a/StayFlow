package ru.alexey.stayflow.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.alexey.stayflow.common.config.CommonWebConfig;
import ru.alexey.stayflow.common.security.JwtConfig;

@EnableScheduling
@Import({CommonWebConfig.class, JwtConfig.class})
@SpringBootApplication
public class BookingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingServiceApplication.class, args);
    }
}
