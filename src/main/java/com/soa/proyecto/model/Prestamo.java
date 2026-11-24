package com.soa.proyecto.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
