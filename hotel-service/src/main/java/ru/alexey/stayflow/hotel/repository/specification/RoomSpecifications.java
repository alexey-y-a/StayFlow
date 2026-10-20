package ru.alexey.stayflow.hotel.repository.specification;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import ru.alexey.stayflow.hotel.dto.RoomFilter;
import ru.alexey.stayflow.hotel.model.Room;

public final class RoomSpecifications {

    private RoomSpecifications() {
    }

    public static Specification<Room> byFilter(RoomFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter != null) {
                if (filter.getHotelId() != null) {
                    predicates.add(cb.equal(root.get("hotel").get("id"), filter.getHotelId()));
                }
                if (filter.getType() != null) {
                    predicates.add(cb.equal(root.get("type"), filter.getType()));
                }
                if (filter.getMinCapacity() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("capacity"), filter.getMinCapacity()));
                }
                if (filter.getMaxPrice() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("pricePerNight"), filter.getMaxPrice()));
                }
                if (filter.getAvailable() != null) {
                    predicates.add(cb.equal(root.get("available"), filter.getAvailable()));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
