package com.soa.proyecto.repository;

import com.soa.proyecto.model.EstadoPrestamo;
import com.soa.proyecto.model.Prestamo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PrestamoRepository extends JpaRepository<Prestamo, Long> {

    List<Prestamo> findByUsuarioId(Long usuarioId);

    List<Prestamo> findByEstado(EstadoPrestamo estado);

    boolean existsByUsuarioIdAndEquipoIdAndEstadoIn(
            Long usuarioId, Long equipoId, Collection<EstadoPrestamo> estados);

    long countByUsuarioIdAndEstadoIn(Long usuarioId, Collection<EstadoPrestamo> estados);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select prestamo from Prestamo prestamo where prestamo.id = :id")
    Optional<Prestamo> findByIdForUpdate(@Param("id") Long id);
}
