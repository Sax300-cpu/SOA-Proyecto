package com.soa.proyecto.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "equipos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Equipo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "equId")
    private Long id;

    // Código de inventario autogenerado (ej. EQUIPO-001). Único.
    @Column(name = "equCodigo", length = 30, unique = true)
    private String codigo;

    @Column(name = "equNombre", length = 50, nullable = false)
    private String nombre;

    @Column(name = "equDescripcion", length = 200)
    private String descripcion;

    @Column(name = "equDisponible", nullable = false)
    private Boolean disponible;

    @Column(name = "equActivo", nullable = false)
    private Boolean activo;
}
