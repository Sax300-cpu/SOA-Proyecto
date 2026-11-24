package com.soa.proyecto.repository;

import com.soa.proyecto.model.Equipo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EquipoRepository extends JpaRepository<Equipo, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Equipo> findEquipoById(Long id);

    // Códigos que empiezan con "CAB-", para calcular el siguiente número de ese prefijo.
    List<Equipo> findByCodigoStartingWith(String inicioDeCodigo);
}
