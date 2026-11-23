package com.soa.proyecto.repository;

import com.soa.proyecto.model.Usuario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Usuario> findUsuarioByUsername(String username);

    boolean existsByUsername(String username);
}
