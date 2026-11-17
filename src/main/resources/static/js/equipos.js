'use strict';

const elemento = id => document.getElementById(id);
let administrador = false;
let editando = null;
let ocupado = false;

function mensaje(texto, tipo = 'danger') {
    elemento('mensaje').textContent = texto;
    elemento('mensaje').className = `alert alert-${tipo}`;
    elemento('mensaje').hidden = false;
}

async function peticion(ruta, opciones = {}) {
    const respuesta = await fetch(ruta, { credentials: 'same-origin', ...opciones });
    if (respuesta.status === 401) {
        throw new Error('La sesión no está activa. Inicia sesión y vuelve al catálogo.');
    }
    const datos = await respuesta.json().catch(() => null);
    if (!respuesta.ok) {
        throw new Error(datos?.error || datos?.detail || datos?.message || `Error HTTP ${respuesta.status}`);
    }
    return datos;
}

async function escribir(ruta, metodo, datos) {
    const csrf = await peticion('/api/auth/csrf');
    return peticion(ruta, {
        method: metodo,
        headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
        ...(datos === undefined ? {} : { body: JSON.stringify(datos) })
    });
}

function cancelarEdicion() {
    editando = null;
    elemento('formEquipo').reset();
    elemento('tituloFormulario').textContent = 'Registrar equipo';
    elemento('guardar').textContent = 'Registrar';
    elemento('cancelar').hidden = true;
}

function celda(fila, texto) {
    const td = document.createElement('td');
    td.textContent = texto ?? '—';
    fila.append(td);
    return td;
}

async function accion(tarea) {
    if (ocupado) return;
    ocupado = true;
    document.querySelectorAll('button').forEach(boton => { boton.disabled = true; });
    try {
        await tarea();
    } catch (error) {
        mensaje(error.message);
    } finally {
        ocupado = false;
        document.querySelectorAll('button').forEach(boton => { boton.disabled = false; });
    }
}

async function cargar() {
    const equipos = await peticion(`/api/equipos?disponibles=${elemento('disponibles').checked}`);
    const tabla = elemento('equipos');
    tabla.replaceChildren();
    if (equipos.length === 0) {
        const fila = document.createElement('tr');
        celda(fila, 'No hay equipos que coincidan con el filtro.').colSpan = 5;
        tabla.append(fila);
    }
    equipos.forEach(equipo => {
        const fila = document.createElement('tr');
        celda(fila, equipo.id);
        celda(fila, equipo.nombre);
        celda(fila, equipo.descripcion);
        celda(fila, equipo.disponible ? 'Disponible' : 'No disponible');
        const acciones = celda(fila, '');
        if (administrador) {
            const editar = document.createElement('button');
            editar.className = 'btn btn-sm btn-outline-primary me-2';
            editar.textContent = 'Editar';
            editar.addEventListener('click', () => {
                editando = equipo.id;
                elemento('nombre').value = equipo.nombre;
                elemento('descripcion').value = equipo.descripcion ?? '';
                elemento('tituloFormulario').textContent = `Editar equipo #${equipo.id}`;
                elemento('guardar').textContent = 'Guardar cambios';
                elemento('cancelar').hidden = false;
                elemento('nombre').focus();
            });
            const desactivar = document.createElement('button');
            desactivar.className = 'btn btn-sm btn-outline-danger';
            desactivar.textContent = 'Desactivar';
            desactivar.addEventListener('click', () => {
                if (!confirm(`¿Desactivar «${equipo.nombre}»? Se conservará su historial.`)) return;
                accion(async () => {
                    await escribir(`/api/equipos/${equipo.id}/desactivar`, 'PUT');
                    if (editando === equipo.id) cancelarEdicion();
                    await cargar();
                    mensaje('Equipo desactivado. Se conservó su historial.', 'success');
                });
            });
            acciones.append(editar, desactivar);
        } else if (equipo.disponible) {
            const enlace = document.createElement('a');
            enlace.href = '/mis-solicitudes.html';
            enlace.className = 'btn btn-sm btn-outline-primary';
            enlace.textContent = `Solicitar equipo #${equipo.id}`;
            acciones.append(enlace);
        } else {
            acciones.textContent = 'No disponible para solicitar';
        }
        tabla.append(fila);
    });
}

elemento('formEquipo').addEventListener('submit', evento => {
    evento.preventDefault();
    accion(async () => {
        const datos = { nombre: elemento('nombre').value.trim(), descripcion: elemento('descripcion').value.trim() };
        await escribir(editando === null ? '/api/equipos' : `/api/equipos/${editando}`,
            editando === null ? 'POST' : 'PUT', datos);
        cancelarEdicion();
        await cargar();
        mensaje('Equipo guardado correctamente.', 'success');
    });
});
elemento('cancelar').addEventListener('click', cancelarEdicion);
elemento('actualizar').addEventListener('click', () => accion(cargar));
elemento('disponibles').addEventListener('change', () => accion(cargar));
elemento('salir').addEventListener('click', () => accion(async () => {
    await escribir('/api/auth/logout', 'POST');
    window.location.assign('/');
}));

async function iniciar() {
    try {
        const sesion = await peticion('/api/auth/me');
        administrador = sesion.rol === 'ADMIN';
        elemento('sesion').textContent = `${sesion.username} · ${sesion.rol}`;
        elemento('administracion').hidden = !administrador;
        elemento('solicitudes').hidden = administrador;
        elemento('aprobaciones').hidden = !administrador;
        elemento('disponibles').checked = !administrador;
        elemento('actualizar').disabled = false;
        elemento('salir').disabled = false;
        await cargar();
    } catch (error) {
        elemento('equipos').replaceChildren();
        mensaje(error.message);
    }
}
iniciar();
