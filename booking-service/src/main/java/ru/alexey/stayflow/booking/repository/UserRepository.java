package ru.alexey.stayflow.booking.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.alexey.stayflow.booking.model.Role;
import ru.alexey.stayflow.booking.model.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    long countByRole(Role role);
}
