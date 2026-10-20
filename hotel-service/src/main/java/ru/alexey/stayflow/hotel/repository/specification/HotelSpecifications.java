package ru.alexey.stayflow.hotel.repository.specification;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;
import ru.alexey.stayflow.hotel.dto.HotelFilter;
import ru.alexey.stayflow.hotel.model.Hotel;

public final class HotelSpecifications {

    private HotelSpecifications() {
    }

    public static Specification<Hotel> byFilter(HotelFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter != null) {
                if (StringUtils.hasText(filter.getCity())) {
                    predicates.add(cb.equal(cb.lower(root.get("city")), filter.getCity().trim().toLowerCase()));
                }
                if (StringUtils.hasText(filter.getName())) {
                    predicates.add(cb.like(
                            cb.lower(root.get("name")),
                            "%" + filter.getName().trim().toLowerCase() + "%"));
                }
                if (filter.getMinRating() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("rating"), filter.getMinRating()));
                }
                if (filter.getMaxRating() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("rating"), filter.getMaxRating()));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
