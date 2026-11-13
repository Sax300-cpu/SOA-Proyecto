package com.soa.proyecto.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "usuarios")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "usuId")
    private Long id;

    @Column(name = "usuNombre", length = 50, nullable = false)
    private String nombre;

    @Column(name = "usuUsername", length = 30, nullable = false, unique = true)
    private String username;

    @Column(name = "usuPassword", length = 100, nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "usuRol", length = 10, nullable = false)
    private Rol rol;
}
