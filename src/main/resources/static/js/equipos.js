'use strict';

const elemento = id => document.getElementById(id);
let administrador = false;
let editando = null;
let ocupado = false;
let equipoElegido = null;
let equipos = [];
let busqueda = '';
let equiposSolicitados = new Set();
const ESTADOS_ACTIVOS = new Set(['SOLICITADO', 'APROBADO', 'ENTREGADO']);

function mensaje(texto, tipo = 'danger') {
    elemento('mensaje').textContent = texto;
    elemento('mensaje').className = `alert alert-${tipo}`;
    elemento('mensaje').hidden = false;
}

async function peticion(ruta, opciones = {}) {
    const respuesta = await fetchConSesion(ruta, opciones);
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

// Abre el modal para registrar (equipo = null) o para editar un equipo existente.
function abrirModalEquipo(equipo = null) {
    editando = equipo ? equipo.id : null;
    elemento('formEquipo').reset();
    elemento('nombre').value = equipo?.nombre ?? '';
    elemento('descripcion').value = equipo?.descripcion ?? '';
    elemento('tituloFormulario').textContent = equipo ? `Editar equipo ${equipo.codigo ?? '#' + equipo.id}` : 'Registrar equipo';
    elemento('guardar').textContent = equipo ? 'Guardar cambios' : 'Registrar';
    elemento('errorEquipo').hidden = true;
    elemento('modalEquipo').showModal();
    elemento('nombre').focus();
}

function cerrarModalEquipo() {
    editando = null;
    elemento('modalEquipo').close();
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
    document.querySelectorAll('main button').forEach(boton => { boton.disabled = true; });
    try {
        await tarea();
    } catch (error) {
        mensaje(error.message);
    } finally {
        ocupado = false;
        document.querySelectorAll('main button').forEach(boton => {
            if (!boton.dataset.fijoDeshabilitado) boton.disabled = false;
        });
    }
}

async function cargar() {
    if (administrador) {
        equipos = await peticion(`/api/equipos?disponibles=${elemento('disponibles').checked}`);
        equiposSolicitados = new Set();
    } else {
        const [listaEquipos, misSolicitudes] = await Promise.all([
            peticion(`/api/equipos?disponibles=${elemento('disponibles').checked}`),
            peticion('/api/prestamos/mios').catch(() => [])
        ]);
        equipos = listaEquipos;
        equiposSolicitados = new Set(
            misSolicitudes.filter(p => ESTADOS_ACTIVOS.has(p.estado)).map(p => p.equipoId)
        );
    }
    mostrarEquipos();
}

// Convierte a minúsculas y quita tildes para comparar texto fácilmente en el buscador.
function normalizar(texto) {
    return texto.toLocaleLowerCase('es').normalize('NFD').replace(/[\u0300-\u036f]/g, '');
}

function mostrarEquipos() {
    const resultados = equipos.filter(equipo =>
        normalizar(`${equipo.codigo ?? ''} ${equipo.nombre} ${equipo.descripcion ?? ''}`).includes(busqueda));
    const tabla = elemento('equipos');
    tabla.replaceChildren();
    elemento('contador').textContent = `${resultados.length} ${resultados.length === 1 ? 'equipo' : 'equipos'}`;
    if (resultados.length === 0) {
        const fila = document.createElement('tr');
        celda(fila, 'No hay equipos que coincidan con el filtro.').colSpan = 5;
        tabla.append(fila);
    }
    resultados.forEach(equipo => {
        const fila = document.createElement('tr');
        celda(fila, equipo.codigo);
        celda(fila, equipo.nombre);
        celda(fila, equipo.descripcion);
        const estadoEquipo = document.createElement('span');
        estadoEquipo.className = `badge rounded-pill ${equipo.disponible ? 'text-bg-success' : 'text-bg-secondary'}`;
        estadoEquipo.textContent = equipo.disponible ? 'Disponible' : 'No disponible';
        celda(fila, '').replaceChildren(estadoEquipo);
        const acciones = celda(fila, '');
        if (administrador) {
            const editar = document.createElement('button');
            editar.className = 'btn btn-sm btn-outline-primary me-2';
            editar.textContent = 'Editar';
            editar.addEventListener('click', () => abrirModalEquipo(equipo));
            const desactivar = document.createElement('button');
            desactivar.className = 'btn btn-sm btn-outline-danger';
            desactivar.textContent = 'Desactivar';
            desactivar.addEventListener('click', () => {
                if (!confirm(`¿Desactivar «${equipo.nombre}»? Se conservará su historial.`)) return;
                accion(async () => {
                    await escribir(`/api/equipos/${equipo.id}/desactivar`, 'PUT');
                    await cargar();
                    mensaje('Equipo desactivado. Se conservó su historial.', 'success');
                });
            });
            acciones.append(editar, desactivar);
        } else if (equipo.disponible) {
            if (equiposSolicitados.has(equipo.id)) {
                const enCurso = document.createElement('button');
                enCurso.type = 'button';
                enCurso.className = 'btn btn-sm btn-outline-secondary';
                enCurso.textContent = 'Solicitud en curso';
                enCurso.disabled = true;
                enCurso.dataset.fijoDeshabilitado = 'true';
                acciones.append(enCurso);
            } else {
                const solicitar = document.createElement('button');
                solicitar.type = 'button';
                solicitar.className = 'btn btn-sm btn-outline-primary';
                solicitar.textContent = 'Solicitar equipo';
                solicitar.setAttribute('aria-label', `Solicitar equipo ${equipo.nombre}`);
                solicitar.addEventListener('click', () => {
                    equipoElegido = equipo;
                    elemento('equipoSolicitud').textContent = equipo.codigo ? `${equipo.codigo} · ${equipo.nombre}` : equipo.nombre;
                    elemento('descripcionSolicitud').textContent = equipo.descripcion ?? '';
                    elemento('errorSolicitud').hidden = true;
                    elemento('solicitar').showModal();
                });
                acciones.append(solicitar);
            }
        } else {
            acciones.textContent = 'No disponible para solicitar';
        }
        tabla.append(fila);
    });
}

elemento('busqueda').addEventListener('submit', evento => {
    evento.preventDefault();
    busqueda = normalizar(elemento('textoBusqueda').value.trim());
    mostrarEquipos();
});

elemento('cancelarSolicitud').addEventListener('click', () => elemento('solicitar').close());
elemento('solicitar').addEventListener('cancel', evento => {
    if (ocupado) evento.preventDefault();
});
elemento('formSolicitar').addEventListener('submit', evento => {
    evento.preventDefault();
    if (!equipoElegido) return;
    accion(async () => {
        const errorSolicitud = elemento('errorSolicitud');
        errorSolicitud.hidden = true;
        try {
            await escribir('/api/prestamos', 'POST', { equipoId: equipoElegido.id });
        } catch (error) {
            errorSolicitud.textContent = error.message;
            errorSolicitud.hidden = false;
            return;
        }
        elemento('solicitar').close();
        await cargar();
        mensaje(`Solicitud de ${equipoElegido.nombre} registrada. Puedes verla en Mis solicitudes.`, 'success');
    });
});

elemento('formEquipo').addEventListener('submit', evento => {
    evento.preventDefault();
    const errorEquipo = elemento('errorEquipo');
    errorEquipo.hidden = true;
    const nombre = elemento('nombre').value.trim();
    const descripcion = elemento('descripcion').value.trim();
    if (!nombre) {
        errorEquipo.textContent = 'El nombre del equipo es obligatorio.';
        errorEquipo.hidden = false;
        return;
    }
    accion(async () => {
        try {
            await escribir(editando === null ? '/api/equipos' : `/api/equipos/${editando}`,
                editando === null ? 'POST' : 'PUT', { nombre, descripcion });
        } catch (error) {
            errorEquipo.textContent = error.message;
            errorEquipo.hidden = false;
            return;
        }
        cerrarModalEquipo();
        await cargar();
        mensaje('Equipo guardado correctamente.', 'success');
    });
});
elemento('cancelar').addEventListener('click', cerrarModalEquipo);
elemento('abrirRegistro').addEventListener('click', () => abrirModalEquipo());
elemento('modalEquipo').addEventListener('cancel', evento => {
    if (ocupado) evento.preventDefault();
});
elemento('actualizar').addEventListener('click', () => accion(cargar));
elemento('disponibles').addEventListener('change', () => accion(cargar));
async function iniciar() {
    try {
        const sesion = await sesionActual;
        if (!sesion) return;
        administrador = sesion.rol === 'ADMIN';
        elemento('abrirRegistro').hidden = !administrador;
        elemento('disponibles').checked = !administrador;
        elemento('actualizar').disabled = false;
        await cargar();
    } catch (error) {
        elemento('equipos').replaceChildren();
        mensaje(error.message);
    }
}
iniciar();
