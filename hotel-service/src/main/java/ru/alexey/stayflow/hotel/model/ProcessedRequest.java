package ru.alexey.stayflow.hotel.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "processed_requests",
        uniqueConstraints =
        @UniqueConstraint(
                name = "uk_processed_requests_request",
                columnNames = "request_id"),
        indexes = @Index(name = "idx_processed_requests_booking", columnList = "booking_id"))
public class ProcessedRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, unique = true, length = 100)
    private String requestId;

    @Column(nullable = false, length = 40)
    private String operation;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "result_code", nullable = false, length = 40)
    private String resultCode;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}