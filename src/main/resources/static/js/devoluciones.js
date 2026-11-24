'use strict';

(() => {
    const porId = id => document.getElementById(id);
    let admin = false;
    let seleccion = null;
    let guardando = false;
    let nombreEquipo = id => `#${id}`;
    let nombreUsuario = id => `#${id}`;
    let catalogo = new Map();
    let ultimosRegistros = [];

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

    function botonAccion(texto, clase, prestamo, accion) {
        const boton = document.createElement('button');
        boton.type = 'button';
        boton.className = `btn btn-sm ${clase} text-nowrap`;
        boton.textContent = texto;
        boton.setAttribute('aria-label', `${texto} del préstamo ${prestamo.id}`);
        boton.addEventListener('click', () => abrir(prestamo, accion));
        return boton;
    }

    // Dibuja solo las filas de la página actual (el paginador reparte la lista de 10 en 10).
    function dibujarFilas(pagina) {
        porId('registros').replaceChildren();
        for (const prestamo of pagina) {
            const fila = document.createElement('tr');
            celda(fila, `#${prestamo.id}`);
            celda(fila, nombreEquipo(prestamo.equipoId));
            if (admin) celda(fila, nombreUsuario(prestamo.usuarioId));
            celda(fila, null).replaceChildren(etiquetaEstadoPrestamo(prestamo));
            celda(fila, fecha(prestamo.fechaSolicitud));
            celda(fila, fecha(prestamo.fechaEntrega));
            const limiteCelda = celda(fila, textoLimite(prestamo));
            if (estaVencido(prestamo)) limiteCelda.className = 'text-danger fw-semibold';
            celda(fila, fecha(prestamo.fechaDevolucion));
            celda(fila, null).replaceChildren(observacionesPrestamo(prestamo));
            if (admin) {
                const acciones = celda(fila, '—');
                if (prestamo.estado === 'APROBADO') {
                    const grupo = document.createElement('div');
                    grupo.className = 'd-flex flex-wrap gap-2';
                    grupo.append(
                        botonAccion('Registrar entrega', 'btn-outline-primary', prestamo, 'entregar'),
                        botonAccion('Anular', 'btn-outline-danger', prestamo, 'anular'));
                    acciones.replaceChildren(grupo);
                } else if (prestamo.estado === 'ENTREGADO') {
                    acciones.replaceChildren(botonAccion('Registrar devolución', 'btn-outline-primary', prestamo, 'devolver'));
                } else if (tieneIncidenciaAbierta(prestamo)) {
                    acciones.replaceChildren(botonAccion('Resolver incidencia', 'btn-outline-danger', prestamo, 'incidencia'));
                }
            }
            porId('registros').append(fila);
        }
    }

    const paginador = crearPaginador(porId('paginador'), dibujarFilas);

    function hayFiltros() {
        return Boolean(porId('textoBusqueda').value.trim() || porId('desde').value
            || porId('hasta').value || porId('soloVencidos').checked);
    }

    // Filtra en el navegador los préstamos ya cargados: texto (equipo, código o usuario), fechas y vencidos.
    function filtrar(registros) {
        const texto = normalizarTexto(porId('textoBusqueda').value.trim());
        const desde = porId('desde').value ? new Date(`${porId('desde').value}T00:00:00`) : null;
        const hasta = porId('hasta').value ? new Date(`${porId('hasta').value}T23:59:59.999`) : null;
        const soloVencidos = porId('soloVencidos').checked;
        return registros.filter(prestamo => {
            if (texto) {
                const pajar = normalizarTexto(`#${prestamo.id} ${nombreEquipo(prestamo.equipoId)} ${admin ? nombreUsuario(prestamo.usuarioId) : ''}`);
                if (!pajar.includes(texto)) return false;
            }
            const solicitada = new Date(prestamo.fechaSolicitud);
            if (desde && solicitada < desde) return false;
            if (hasta && solicitada > hasta) return false;
            return !soloVencidos || estaVencido(prestamo);
        });
    }

    function refrescarTabla(reiniciar = true) {
        const visibles = filtrar(ultimosRegistros);
        paginador.mostrar(visibles, reiniciar);
        porId('vacio').textContent = hayFiltros()
            ? 'Ningún préstamo coincide con los filtros.'
            : 'No hay préstamos para el estado seleccionado.';
        porId('vacio').hidden = visibles.length !== 0;
    }

    // El usuario ve siempre sus incidencias abiertas, aunque filtre la tabla por otro estado.
    async function actualizarAlertaUsuario(estado, registros) {
        const contenedor = porId('alertaIncidencias');
        contenedor.replaceChildren();
        if (admin) return;
        let devueltos = registros;
        if (estado && estado !== 'DEVUELTO') {
            devueltos = await pedir('/api/historial/mio?estado=DEVUELTO');
        }
        const aviso = avisoIncidencias(devueltos, nombreEquipo);
        if (aviso) contenedor.append(aviso);
    }

    async function cargar(aviso = '', reiniciar = true) {
        const boton = porId('filtros').querySelector('button[type="submit"]');
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
            catalogo = await cargarCatalogo(admin);
            nombreEquipo = nombrarEquipos(catalogo);
            if (admin) nombreUsuario = await cargarNombresUsuarios();
            ultimosRegistros = registros;
            refrescarTabla(reiniciar);
            await actualizarAlertaUsuario(estado, registros);
        } catch (error) {
            mensaje(aviso ? `${aviso} No se pudo actualizar el historial: ${error.message}` : error.message, 'danger');
        } finally {
            boton.disabled = false;
            porId('contenido').setAttribute('aria-busy', 'false');
        }
    }

    const AYUDA_CONDICION = {
        BUEN_ESTADO: 'El equipo volverá a estar disponible.',
        CON_DETALLES: 'El equipo sigue disponible. El usuario verá un aviso para acercarse a la administración.',
        DANADO: 'El equipo queda fuera del catálogo hasta que lo marques como reparado o lo des de baja. El usuario verá un aviso.'
    };

    // Ajusta el texto de la observación según la condición elegida (obligatoria si hay detalles o daños).
    function actualizarCondicion() {
        const condicion = porId('condicion').value;
        porId('observacion-etiqueta').textContent = condicion === 'BUEN_ESTADO'
            ? 'Observación (opcional)' : 'Observación (obligatoria)';
        porId('condicion-ayuda').textContent = AYUDA_CONDICION[condicion];
    }

    // Muestra el plazo en días si es múltiplo de 24 horas y, si no, en horas.
    function ponerPlazo(horas) {
        const enDias = horas % 24 === 0;
        porId('plazoValor').value = enDias ? horas / 24 : horas;
        porId('plazoUnidad').value = enDias ? '24' : '1';
    }

    // Prepara el diálogo según la operación: entregar, devolver, anular o resolver una incidencia.
    function abrir(prestamo, accion) {
        seleccion = { id: prestamo.id, accion };
        const equipo = nombreEquipo(prestamo.equipoId);
        const titulos = {
            entregar: 'Registrar entrega', devolver: 'Registrar devolución',
            anular: 'Anular préstamo', incidencia: 'Resolver incidencia'
        };
        porId('operacion-titulo').textContent = titulos[accion];

        porId('plazo-campo').hidden = accion !== 'entregar';
        porId('condicion-campo').hidden = accion !== 'devolver';
        porId('observacion-campo').hidden = accion === 'incidencia';
        porId('resolucion-campo').hidden = accion !== 'incidencia';
        porId('observacion').value = '';
        porId('condicion').value = 'BUEN_ESTADO';
        porId('operacion-error').textContent = '';
        porId('confirmar').textContent = accion === 'anular' ? 'Anular préstamo' : 'Confirmar';
        porId('confirmar').className = `btn ${accion === 'anular' ? 'btn-danger' : 'btn-primary'}`;

        if (accion === 'entregar') {
            ponerPlazo(prestamo.plazoHoras ?? 72);
            porId('operacion-detalle').textContent = `Préstamo #${prestamo.id} · Equipo ${equipo}. Confirma que entregaste el equipo al usuario.`;
            porId('observacion-etiqueta').textContent = 'Estado del equipo al entregar (opcional)';
            porId('observacion').placeholder = 'Ej. Con un rayón en la tapa';
            porId('observacion-ayuda').textContent = 'Máximo 300 caracteres. Queda registrado para comparar al devolverlo.';
        } else if (accion === 'devolver') {
            porId('operacion-detalle').textContent = `Préstamo #${prestamo.id} · Equipo ${equipo}. Confirma que recibiste el equipo e indica en qué estado volvió.`;
            porId('observacion').placeholder = 'Ej. Pantalla rota en una esquina';
            porId('observacion-ayuda').textContent = 'Máximo 300 caracteres. No se registran cobros.';
            actualizarCondicion();
        } else if (accion === 'anular') {
            porId('operacion-detalle').textContent = `Préstamo #${prestamo.id} · Equipo ${equipo} aprobado y aún sin entregar. Si el usuario no fue por el equipo, anúlalo para liberarlo.`;
            porId('observacion-etiqueta').textContent = 'Motivo (opcional)';
            porId('observacion').placeholder = 'Ej. No fue a recogerlo';
            porId('observacion-ayuda').textContent = 'Máximo 300 caracteres. El equipo vuelve al catálogo y el usuario ve el motivo.';
        } else {
            porId('operacion-detalle').textContent = `Préstamo #${prestamo.id} · Equipo ${equipo} devuelto como «${TEXTO_CONDICION[prestamo.condicionDevolucion]}»: «${prestamo.observacion}».`
                + (prestamo.enMantenimiento === true ? ' Está en mantenimiento. ' : ' ')
                + 'Elige cómo resolver la incidencia.';
            porId('resolucion').replaceChildren(...opcionesResolucion(prestamo).map(valor => new Option(TEXTO_RESOLUCION[valor], valor)));
        }
        porId('operacion').showModal();
    }

    // Arma ruta y cuerpo de la petición según la operación elegida; devuelve un texto si hay un error de datos.
    function peticionActual() {
        const { id, accion } = seleccion;
        const observacion = porId('observacion').value;
        if (accion === 'entregar') {
            const plazoHoras = Number(porId('plazoValor').value) * Number(porId('plazoUnidad').value);
            if (!Number.isInteger(plazoHoras) || plazoHoras < 1 || plazoHoras > 720) {
                return { error: 'El plazo debe estar entre 1 hora y 30 días.' };
            }
            return { ruta: `/api/prestamos/${id}/entregar`, cuerpo: { observacion, plazoHoras } };
        }
        if (accion === 'anular') {
            return { ruta: `/api/prestamos/${id}/anular`, cuerpo: { motivo: observacion } };
        }
        if (accion === 'devolver') {
            const condicion = porId('condicion').value;
            if (condicion !== 'BUEN_ESTADO' && !observacion.trim()) {
                return { error: 'Describe el problema en la observación cuando el equipo vuelve con detalles o dañado.' };
            }
            return { ruta: `/api/prestamos/${id}/devolver`, cuerpo: { observacion, condicion } };
        }
        return { ruta: `/api/prestamos/${id}/incidencia`, cuerpo: { accion: porId('resolucion').value } };
    }

    const AVISOS = {
        entregar: 'Entrega registrada.',
        devolver: 'Devolución registrada.',
        anular: 'Préstamo anulado. El equipo volvió al catálogo.',
        incidencia: 'Incidencia resuelta.'
    };

    porId('filtros').addEventListener('submit', event => { event.preventDefault(); cargar(); });
    porId('estado').addEventListener('change', () => cargar());
    for (const id of ['textoBusqueda', 'desde', 'hasta', 'soloVencidos']) {
        porId(id).addEventListener(id === 'textoBusqueda' ? 'input' : 'change', () => refrescarTabla());
    }
    porId('limpiar').addEventListener('click', () => {
        porId('textoBusqueda').value = '';
        porId('desde').value = '';
        porId('hasta').value = '';
        porId('soloVencidos').checked = false;
        refrescarTabla();
    });
    porId('condicion').addEventListener('change', actualizarCondicion);
    for (const atajo of document.querySelectorAll('#plazo-campo [data-horas]')) {
        atajo.addEventListener('click', () => ponerPlazo(Number(atajo.dataset.horas)));
    }
    porId('cancelar').addEventListener('click', () => porId('operacion').close());
    porId('operacion').addEventListener('cancel', event => { if (guardando) event.preventDefault(); });
    porId('operacion-form').addEventListener('submit', async event => {
        event.preventDefault();
        if (guardando || !seleccion) return;
        const peticion = peticionActual();
        if (peticion.error) {
            porId('operacion-error').textContent = peticion.error;
            return;
        }
        guardando = true;
        porId('confirmar').disabled = true;
        porId('cancelar').disabled = true;
        porId('operacion-error').textContent = 'Guardando…';
        try {
            // Obtener un token vigente: Spring Security lo renueva tras iniciar sesión.
            const csrf = await pedir('/api/auth/csrf');
            const resultado = await pedir(peticion.ruta, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
                body: JSON.stringify(peticion.cuerpo)
            });
            let aviso = AVISOS[seleccion.accion];
            if (seleccion.accion === 'entregar') {
                aviso = `Entrega registrada. Plazo de devolución: ${formatearPlazo(peticion.cuerpo.plazoHoras)}.`;
            } else if (seleccion.accion === 'devolver') {
                aviso = resultado?.condicionDevolucion === 'DANADO'
                    ? 'Devolución registrada. El equipo quedó fuera del catálogo y el usuario recibirá un aviso.'
                    : resultado?.condicionDevolucion === 'CON_DETALLES'
                        ? 'Devolución registrada. El usuario recibirá un aviso para acercarse a la administración.'
                        : 'Devolución registrada. El equipo está disponible.';
            } else if (seleccion.accion === 'incidencia' && peticion.cuerpo.accion === 'MANTENIMIENTO') {
                aviso = 'Equipo enviado a mantenimiento. La incidencia sigue abierta hasta que lo marques como reparado o lo des de baja.';
            }
            porId('operacion').close();
            await cargar(aviso, false);
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
            porId('notaDevolucion').hidden = admin;
            porId('contenido').hidden = false;
            await cargar();
        } catch (error) {
            mensaje(error.message, 'danger');
        }
    }
    iniciar();
})();
