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

    @Column(name = "equNombre", length = 50, nullable = false)
    private String nombre;

    @Column(name = "equDescripcion", length = 200)
    private String descripcion;

    @Column(name = "equDisponible", nullable = false)
    private Boolean disponible;

    @Column(name = "equActivo", nullable = false)
    private Boolean activo;
}
