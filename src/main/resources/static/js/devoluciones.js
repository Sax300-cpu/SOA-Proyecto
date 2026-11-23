'use strict';

(() => {
    const porId = id => document.getElementById(id);
    let admin = false;
    let seleccion = null;
    let guardando = false;
    let nombreEquipo = id => `#${id}`;

    // Aviso destacado: solo para confirmaciones de acciones y errores.
    function mensaje(texto, tipo = 'info') {
        porId('mensaje').textContent = texto;
        porId('mensaje').className = `alert alert-${tipo}`;
        porId('mensaje').hidden = false;
    }


    async function pedir(ruta, opciones = {}) {
        const respuesta = await fetchConSesion(ruta, opciones);
        const datos = await respuesta.json().catch(() => null);
        if (!respuesta.ok) {
            const error = respuesta.status === 403 ? 'No tienes permiso o la sesión cambió. Vuelve a iniciar sesión y reintenta.'
                : datos?.error || 'No se pudo completar la operación. Actualiza e inténtalo de nuevo.';
            throw new Error(error);
        }
        return datos;
    }

    function celda(fila, texto) {
        const td = document.createElement('td');
        td.textContent = texto ?? '—';
        fila.append(td);
        return td;
    }

    function fecha(valor) {
        return valor ? new Date(valor).toLocaleString('es-EC', { dateStyle: 'short', timeStyle: 'short' }) : '—';
    }

    function mostrar(registros) {
        porId('registros').replaceChildren();
        for (const prestamo of registros) {
            const fila = document.createElement('tr');
            celda(fila, `#${prestamo.id}`);
            celda(fila, nombreEquipo(prestamo.equipoId));
            if (admin) celda(fila, `#${prestamo.usuarioId}`);
            celda(fila, null).replaceChildren(etiquetaEstado(prestamo.estado));
            celda(fila, fecha(prestamo.fechaSolicitud));
            celda(fila, fecha(prestamo.fechaEntrega));
            celda(fila, fecha(prestamo.fechaDevolucion));
            celda(fila, prestamo.observacion);
            if (admin) {
                const acciones = celda(fila, '—');
                if (['APROBADO', 'ENTREGADO'].includes(prestamo.estado)) {
                    const boton = document.createElement('button');
                    boton.type = 'button';
                    boton.className = 'btn btn-sm btn-outline-primary text-nowrap';
                    boton.textContent = prestamo.estado === 'APROBADO' ? 'Registrar entrega' : 'Registrar devolución';
                    boton.setAttribute('aria-label', `${boton.textContent} del préstamo ${prestamo.id}`);
                    boton.addEventListener('click', () => abrir(prestamo));
                    acciones.replaceChildren(boton);
                }
            }
            porId('registros').append(fila);
        }
        porId('vacio').hidden = registros.length !== 0;
    }

    async function cargar(aviso = '') {
        const boton = porId('filtros').querySelector('button');
        boton.disabled = true;
        porId('contenido').setAttribute('aria-busy', 'true');
        // Retirar acciones antiguas mientras se actualizan los registros.
        porId('registros').replaceChildren();
        porId('vacio').hidden = true;
        if (aviso) mensaje(aviso, 'success'); else porId('mensaje').hidden = true;
        try {
            const base = admin ? '/api/prestamos' : '/api/historial/mio';
            const estado = porId('estado').value;
            const registros = await pedir(base + (estado ? `?estado=${encodeURIComponent(estado)}` : ''));
            nombreEquipo = await cargarNombresEquipos();
            mostrar(registros);
        } catch (error) {
            mensaje(aviso ? `${aviso} No se pudo actualizar el historial: ${error.message}` : error.message, 'danger');
        } finally {
            boton.disabled = false;
            porId('contenido').setAttribute('aria-busy', 'false');
        }
    }

    function abrir(prestamo) {
        seleccion = { id: prestamo.id, accion: prestamo.estado === 'APROBADO' ? 'entregar' : 'devolver' };
        const entrega = seleccion.accion === 'entregar';
        porId('operacion-titulo').textContent = entrega ? 'Registrar entrega' : 'Registrar devolución';
        porId('operacion-detalle').textContent = `Préstamo #${prestamo.id} · Equipo ${nombreEquipo(prestamo.equipoId)}. `
            + (entrega ? 'Confirma que entregaste el equipo al usuario.' : 'Confirma que recibiste el equipo. Volverá a estar disponible.');
        porId('observacion-campo').hidden = entrega;
        porId('observacion').value = '';
        porId('operacion-error').textContent = '';
        porId('operacion').showModal();
    }

    porId('filtros').addEventListener('submit', event => { event.preventDefault(); cargar(); });
    porId('estado').addEventListener('change', () => cargar());
    porId('cancelar').addEventListener('click', () => porId('operacion').close());
    porId('operacion').addEventListener('cancel', event => { if (guardando) event.preventDefault(); });
    porId('operacion-form').addEventListener('submit', async event => {
        event.preventDefault();
        if (guardando || !seleccion) return;
        guardando = true;
        porId('confirmar').disabled = true;
        porId('cancelar').disabled = true;
        porId('operacion-error').textContent = 'Guardando…';
        try {
            // Obtener un token vigente: Spring Security lo renueva tras iniciar sesión.
            const csrf = await pedir('/api/auth/csrf');
            await pedir(`/api/prestamos/${seleccion.id}/${seleccion.accion}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
                ...(seleccion.accion === 'devolver' ? { body: JSON.stringify({ observacion: porId('observacion').value }) } : {})
            });
            const aviso = seleccion.accion === 'entregar' ? 'Entrega registrada.' : 'Devolución registrada. El equipo está disponible.';
            porId('operacion').close();
            await cargar(aviso);
        } catch (error) {
            porId('operacion-error').textContent = error.message;
        } finally {
            guardando = false;
            porId('confirmar').disabled = false;
            porId('cancelar').disabled = false;
        }
    });

    async function iniciar() {
        try {
            const sesion = await sesionActual;
            if (!sesion) return;
            admin = sesion.rol === 'ADMIN';
            if (!admin && sesion.rol !== 'USUARIO') throw new Error('La cuenta no tiene un rol válido.');
            porId('titulo').textContent = admin ? 'Entregas, devoluciones e historial' : 'Mi historial de préstamos';
            porId('ayuda').textContent = admin
                ? 'Entrega los préstamos aprobados y registra la devolución de los equipos recibidos.'
                : 'Consulta únicamente tus préstamos y las fechas de entrega y devolución.';
            porId('usuario-col').hidden = !admin;
            porId('acciones-col').hidden = !admin;
            porId('contenido').hidden = false;
            await cargar();
        } catch (error) {
            mensaje(error.message, 'danger');
        }
    }
    iniciar();
})();
