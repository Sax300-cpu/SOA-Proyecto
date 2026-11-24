package com.soa.proyecto.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "prestamos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Prestamo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "preId")
    private Long id;

    @Column(name = "preUsuarioId", nullable = false)
    private Long usuarioId;

    @Column(name = "preEquipoId", nullable = false)
    private Long equipoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "preEstado", length = 15, nullable = false)
    private EstadoPrestamo estado;

    @Column(name = "preFechaSolicitud", nullable = false)
    private LocalDateTime fechaSolicitud;

    @Column(name = "preFechaAprobacion")
    private LocalDateTime fechaAprobacion;

    @Column(name = "preFechaEntrega")
    private LocalDateTime fechaEntrega;

    @Column(name = "preFechaLimite")
    private LocalDateTime fechaLimite;

    @Column(name = "preFechaDevolucion")
    private LocalDateTime fechaDevolucion;

    @Column(name = "preObservacion", length = 300)
    private String observacion;

    // Cómo salía el equipo al entregarlo (opcional).
    @Column(name = "preObservacionEntrega", length = 300)
    private String observacionEntrega;

    // Estado del equipo al devolverlo. Se guarda como texto para poder agregar valores sin alterar la tabla.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "preCondicion", length = 15)
    private CondicionEquipo condicionDevolucion;

    // Solo aplica si la devolución fue CON_DETALLES o DANADO: false mientras el admin no la resuelva.
    @Column(name = "preIncidenciaAtendida")
    private Boolean incidenciaAtendida;

    // true mientras el equipo de una incidencia abierta está en mantenimiento (fuera del catálogo).
    @Column(name = "preEnMantenimiento")
    private Boolean enMantenimiento;

    // Plazo de devolución en horas que el administrador fija al aprobar; empieza a contar al entregar.
    @Column(name = "prePlazoHoras")
    private Integer plazoHoras;
}
