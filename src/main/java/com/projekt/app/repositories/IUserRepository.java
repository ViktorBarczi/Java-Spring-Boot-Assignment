package com.projekt.app.repositories;

import java.util.Optional;
import com.projekt.app.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IUserRepository extends JpaRepository<User, Long> {
  Optional<User> findByUsername(String username);
}
