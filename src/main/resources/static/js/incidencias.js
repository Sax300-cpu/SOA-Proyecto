'use strict';

// Página de incidencias (solo ADMIN): equipos devueltos con detalles o daños y su resolución.
(() => {
    const porId = id => document.getElementById(id);
    let nombreEquipo = id => `#${id}`;
    let nombreUsuario = id => `#${id}`;
    let incidencias = [];
    let seleccion = null;
    let guardando = false;

    function mensaje(texto, tipo = 'info') {
        porId('mensaje').textContent = texto;
        porId('mensaje').className = `alert alert-${tipo}`;
        porId('mensaje').hidden = false;
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

    function insignia(color, texto) {
        const etiqueta = document.createElement('span');
        etiqueta.className = `badge rounded-pill ${color}`;
        etiqueta.textContent = texto;
        return etiqueta;
    }

    // Abierta, en mantenimiento o atendida, según los datos del préstamo.
    function estadoIncidencia(p) {
        if (p.incidenciaAtendida === true) return 'ATENDIDAS';
        return p.enMantenimiento === true ? 'MANTENIMIENTO' : 'ABIERTAS';
    }

    function dibujarFilas(pagina) {
        porId('registros').replaceChildren();
        for (const p of pagina) {
            const fila = document.createElement('tr');
            celda(fila, `#${p.id}`);
            celda(fila, nombreEquipo(p.equipoId));
            celda(fila, nombreUsuario(p.usuarioId));
            celda(fila, null).replaceChildren(insignia(
                p.condicionDevolucion === 'DANADO' ? 'text-bg-danger' : 'text-bg-warning',
                TEXTO_CONDICION[p.condicionDevolucion]));
            celda(fila, null).replaceChildren(observacionesPrestamo(p));
            celda(fila, fecha(p.fechaDevolucion));
            const estado = estadoIncidencia(p);
            celda(fila, null).replaceChildren(estado === 'ATENDIDAS' ? insignia('text-bg-secondary', 'Atendida')
                : estado === 'MANTENIMIENTO' ? insignia('text-bg-info', 'En mantenimiento')
                : insignia('text-bg-danger', 'Abierta'));
            const acciones = celda(fila, '—');
            if (estado !== 'ATENDIDAS') {
                const boton = document.createElement('button');
                boton.type = 'button';
                boton.className = 'btn btn-sm btn-outline-danger text-nowrap';
                boton.textContent = 'Resolver incidencia';
                boton.setAttribute('aria-label', `Resolver la incidencia del préstamo ${p.id}`);
                boton.addEventListener('click', () => abrir(p));
                acciones.replaceChildren(boton);
            }
            porId('registros').append(fila);
        }
    }

    const paginador = crearPaginador(porId('paginador'), dibujarFilas);

    function refrescar(reiniciar = true) {
        const filtro = porId('estadoIncidencia').value;
        const texto = normalizarTexto(porId('textoBusqueda').value.trim());
        const visibles = incidencias.filter(p => {
            if (filtro !== 'TODAS' && estadoIncidencia(p) !== filtro) return false;
            if (!texto) return true;
            return normalizarTexto(`#${p.id} ${nombreEquipo(p.equipoId)} ${nombreUsuario(p.usuarioId)} ${p.observacion ?? ''}`).includes(texto);
        });
        paginador.mostrar(visibles, reiniciar);
        porId('vacio').hidden = visibles.length !== 0;
    }

    async function cargar(aviso = '', reiniciar = true) {
        if (aviso) mensaje(aviso, 'success'); else porId('mensaje').hidden = true;
        try {
            const devueltos = await (await fetchConSesion('/api/prestamos?estado=DEVUELTO')).json();
            incidencias = devueltos
                .filter(p => ['CON_DETALLES', 'DANADO'].includes(p.condicionDevolucion))
                .sort((a, b) => b.id - a.id);
            nombreEquipo = nombrarEquipos(await cargarCatalogo(true));
            nombreUsuario = await cargarNombresUsuarios();
            refrescar(reiniciar);
        } catch (error) {
            mensaje(`No se pudieron cargar las incidencias: ${error.message}`, 'danger');
        }
    }

    function abrir(p) {
        seleccion = p;
        porId('detalleResolver').textContent = `Préstamo #${p.id} · Equipo ${nombreEquipo(p.equipoId)} devuelto como «${TEXTO_CONDICION[p.condicionDevolucion]}»: «${p.observacion}».`
            + (p.enMantenimiento === true ? ' Está en mantenimiento.' : '');
        porId('resolucion').replaceChildren(...opcionesResolucion(p).map(valor => new Option(TEXTO_RESOLUCION[valor], valor)));
        porId('errorResolver').hidden = true;
        porId('modalResolver').showModal();
    }

    porId('filtros').addEventListener('submit', evento => { evento.preventDefault(); cargar(); });
    porId('estadoIncidencia').addEventListener('change', () => refrescar());
    porId('textoBusqueda').addEventListener('input', () => refrescar());
    porId('cancelarResolver').addEventListener('click', () => porId('modalResolver').close());
    porId('modalResolver').addEventListener('cancel', evento => { if (guardando) evento.preventDefault(); });
    porId('formResolver').addEventListener('submit', async evento => {
        evento.preventDefault();
        if (guardando || !seleccion) return;
        guardando = true;
        porId('confirmarResolver').disabled = true;
        porId('cancelarResolver').disabled = true;
        porId('errorResolver').hidden = true;
        const accion = porId('resolucion').value;
        try {
            await enviarJson(`/api/prestamos/${seleccion.id}/incidencia`, 'PUT', { accion });
            porId('modalResolver').close();
            await cargar(accion === 'MANTENIMIENTO'
                ? 'Equipo enviado a mantenimiento. La incidencia sigue abierta hasta que lo marques como reparado o lo des de baja.'
                : 'Incidencia resuelta.', false);
        } catch (error) {
            porId('errorResolver').textContent = error.message;
            porId('errorResolver').hidden = false;
        } finally {
            guardando = false;
            porId('confirmarResolver').disabled = false;
            porId('cancelarResolver').disabled = false;
        }
    });

    sesionActual.then(usuario => {
        if (usuario) cargar();
    });
})();
