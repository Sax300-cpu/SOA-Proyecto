'use strict';

const elemento = id => document.getElementById(id);
let administrador = false;
let editando = null;
let ocupado = false;
let equipoElegido = null;
let equipos = [];
let busqueda = '';
let prefijoManual = false;
let equipoADesactivar = null;
let equiposEnMantenimiento = new Set();
let equiposDanados = new Set();
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
    // El prefijo solo se elige al registrar; al editar el código ya no cambia.
    prefijoManual = false;
    elemento('prefijo').value = '';
    elemento('grupoPrefijo').hidden = Boolean(equipo);
    elemento('tituloFormulario').textContent = equipo ? `Editar equipo ${equipo.codigo ?? '#' + equipo.id}` : 'Registrar equipo';
    elemento('guardar').textContent = equipo ? 'Guardar cambios' : 'Registrar';
    elemento('errorEquipo').hidden = true;
    elemento('modalEquipo').showModal();
    elemento('nombre').focus();
}

// Mismas reglas que el servidor: sin tildes ni símbolos, 3 primeras letras en mayúsculas.
function sugerirPrefijo(nombre) {
    const letras = nombre.normalize('NFD').replace(/[^A-Za-z]/g, '').toUpperCase();
    return letras.length >= 3 ? letras.slice(0, 3) : '';
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

// Solo el ADMIN puede ver la lista de equipos desactivados para volver a activarlos.
function viendoDesactivados() {
    return administrador && elemento('verDesactivados').checked;
}

async function cargar(reiniciar = true) {
    const desactivados = viendoDesactivados();
    elemento('disponibles').disabled = desactivados;
    elemento('tipoLista').textContent = desactivados ? 'Equipos desactivados' : 'Equipos activos';
    if (desactivados) {
        equipos = await peticion('/api/equipos/desactivados');
        equiposSolicitados = new Set();
    } else if (administrador) {
        equipos = await peticion(`/api/equipos?disponibles=${elemento('disponibles').checked}`);
        equiposSolicitados = new Set();
        // Para distinguir "En mantenimiento" y "Dañado" de un equipo simplemente prestado.
        const devueltos = await peticion('/api/prestamos?estado=DEVUELTO').catch(() => []);
        const abiertas = devueltos.filter(p => p.incidenciaAtendida === false);
        equiposEnMantenimiento = new Set(abiertas.filter(p => p.enMantenimiento === true).map(p => p.equipoId));
        equiposDanados = new Set(abiertas.filter(p => p.condicionDevolucion === 'DANADO' && p.enMantenimiento !== true).map(p => p.equipoId));
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
    mostrarEquipos(reiniciar);
}

// Convierte a minúsculas y quita tildes para comparar texto fácilmente en el buscador.
function normalizar(texto) {
    return texto.toLocaleLowerCase('es').normalize('NFD').replace(/[\u0300-\u036f]/g, '');
}

function mostrarEquipos(reiniciar = true) {
    const resultados = equipos.filter(equipo =>
        normalizar(`${equipo.codigo ?? ''} ${equipo.nombre} ${equipo.descripcion ?? ''}`).includes(busqueda));
    elemento('contador').textContent = `${resultados.length} ${resultados.length === 1 ? 'equipo' : 'equipos'}`;
    paginadorEquipos.mostrar(resultados, reiniciar);
}

// Dibuja solo las filas de la página actual (10 por página).
function dibujarEquipos(pagina) {
    const tabla = elemento('equipos');
    tabla.replaceChildren();
    if (pagina.length === 0) {
        const fila = document.createElement('tr');
        celda(fila, viendoDesactivados() ? 'No hay equipos desactivados.' : 'No hay equipos que coincidan con el filtro.').colSpan = 5;
        tabla.append(fila);
    }
    pagina.forEach(equipo => {
        const fila = document.createElement('tr');
        celda(fila, equipo.codigo);
        celda(fila, equipo.nombre);
        celda(fila, equipo.descripcion);
        const estadoEquipo = document.createElement('span');
        const inactivo = equipo.activo === false;
        const enMantenimiento = !inactivo && !equipo.disponible && equiposEnMantenimiento.has(equipo.id);
        const danado = !inactivo && !equipo.disponible && !enMantenimiento && equiposDanados.has(equipo.id);
        estadoEquipo.className = `badge rounded-pill ${inactivo ? 'text-bg-dark' : enMantenimiento ? 'text-bg-info' : danado ? 'text-bg-danger' : equipo.disponible ? 'text-bg-success' : 'text-bg-secondary'}`;
        estadoEquipo.textContent = inactivo ? 'Desactivado' : enMantenimiento ? 'En mantenimiento' : danado ? 'Dañado' : equipo.disponible ? 'Disponible' : 'No disponible';
        celda(fila, '').replaceChildren(estadoEquipo);
        const acciones = celda(fila, '');
        if (viendoDesactivados()) {
            const activar = document.createElement('button');
            activar.type = 'button';
            activar.className = 'btn btn-sm btn-outline-success';
            activar.textContent = 'Activar';
            activar.setAttribute('aria-label', `Activar equipo ${equipo.nombre}`);
            activar.addEventListener('click', () => {
                accion(async () => {
                    await escribir(`/api/equipos/${equipo.id}/activar`, 'PUT');
                    await cargar(false);
                    mensaje(`«${equipo.nombre}» volvió al catálogo.`, 'success');
                });
            });
            acciones.append(activar);
        } else if (administrador) {
            const editar = document.createElement('button');
            editar.className = 'btn btn-sm btn-outline-primary me-2';
            editar.textContent = 'Editar';
            editar.addEventListener('click', () => abrirModalEquipo(equipo));
            const desactivar = document.createElement('button');
            desactivar.className = 'btn btn-sm btn-outline-danger';
            desactivar.textContent = 'Desactivar';
            desactivar.addEventListener('click', () => {
                equipoADesactivar = equipo;
                elemento('textoDesactivar').textContent = `¿Desactivar «${equipo.nombre}»${equipo.codigo ? ` (${equipo.codigo})` : ''}?`;
                elemento('errorDesactivar').hidden = true;
                elemento('modalDesactivar').showModal();
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

const paginadorEquipos = crearPaginador(elemento('paginador'), dibujarEquipos);

elemento('busqueda').addEventListener('submit', evento => {
    evento.preventDefault();
    busqueda = normalizar(elemento('textoBusqueda').value.trim());
    mostrarEquipos();
});

elemento('cancelarDesactivar').addEventListener('click', () => elemento('modalDesactivar').close());
elemento('modalDesactivar').addEventListener('cancel', evento => {
    if (ocupado) evento.preventDefault();
});
elemento('formDesactivar').addEventListener('submit', evento => {
    evento.preventDefault();
    if (!equipoADesactivar) return;
    const equipo = equipoADesactivar;
    accion(async () => {
        const error = elemento('errorDesactivar');
        error.hidden = true;
        try {
            await escribir(`/api/equipos/${equipo.id}/desactivar`, 'PUT');
        } catch (fallo) {
            error.textContent = fallo.message;
            error.hidden = false;
            return;
        }
        elemento('modalDesactivar').close();
        await cargar(false);
        mensaje('Equipo desactivado. Se conservó su historial; puedes activarlo con «Ver desactivados».', 'success');
    });
});
elemento('verDesactivados').addEventListener('change', () => accion(cargar));

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
        await cargar(false);
        mensaje(`Solicitud de ${equipoElegido.nombre} registrada. Puedes verla en Mis solicitudes.`, 'success');
    });
});

elemento('formEquipo').addEventListener('submit', evento => {
    evento.preventDefault();
    const errorEquipo = elemento('errorEquipo');
    errorEquipo.hidden = true;
    const nombre = elemento('nombre').value.trim();
    const descripcion = elemento('descripcion').value.trim();
    const prefijo = elemento('prefijo').value.trim().toUpperCase();
    if (!nombre) {
        errorEquipo.textContent = 'El nombre del equipo es obligatorio.';
        errorEquipo.hidden = false;
        return;
    }
    if (editando === null && prefijo && !/^[A-Z]{3}$/.test(prefijo)) {
        errorEquipo.textContent = 'El prefijo debe tener exactamente 3 letras.';
        errorEquipo.hidden = false;
        return;
    }
    accion(async () => {
        try {
            await escribir(editando === null ? '/api/equipos' : `/api/equipos/${editando}`,
                editando === null ? 'POST' : 'PUT',
                editando === null ? { nombre, descripcion, prefijo } : { nombre, descripcion });
        } catch (error) {
            errorEquipo.textContent = error.message;
            errorEquipo.hidden = false;
            return;
        }
        cerrarModalEquipo();
        await cargar(false);
        mensaje('Equipo guardado correctamente.', 'success');
    });
});
elemento('nombre').addEventListener('input', () => {
    if (editando === null && !prefijoManual) elemento('prefijo').value = sugerirPrefijo(elemento('nombre').value);
});
elemento('prefijo').addEventListener('input', () => {
    const campo = elemento('prefijo');
    campo.value = campo.value.replace(/[^A-Za-z]/g, '').slice(0, 3).toUpperCase();
    // Si lo vacía, vuelve a sugerirse desde el nombre.
    prefijoManual = campo.value !== '';
    if (!prefijoManual) campo.value = sugerirPrefijo(elemento('nombre').value);
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
        elemento('grupoDesactivados').hidden = !administrador;
        elemento('disponibles').checked = !administrador;
        elemento('actualizar').disabled = false;
        await cargar();
    } catch (error) {
        elemento('equipos').replaceChildren();
        mensaje(error.message);
    }
}
iniciar();
