package ru.alexey.stayflow.hotel.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.alexey.stayflow.hotel.model.ProcessedRequest;

public interface ProcessedRequestRepository extends JpaRepository<ProcessedRequest, Long> {

    Optional<ProcessedRequest> findByRequestId(String requestId);

    boolean existsByRequestId(String requestId);
}
