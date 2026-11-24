'use strict';

// Envuelve fetch() para incluir la cookie de sesión y redirigir al login si devuelve 401.
async function fetchConSesion(ruta, opciones = {}) {
    const respuesta = await fetch(ruta, { credentials: 'same-origin', ...opciones });
    if (respuesta.status === 401) {
        window.location.replace('/login.html');
        throw new Error('La sesión ha terminado.');
    }
    return respuesta;
}

// Devuelve un <span> tipo badge con color según el estado del préstamo.
function etiquetaEstado(estado) {
    const colores = {
        SOLICITADO: 'text-bg-warning',
        APROBADO: 'text-bg-info',
        ENTREGADO: 'text-bg-primary',
        DEVUELTO: 'text-bg-success',
        RECHAZADO: 'text-bg-danger'
    };
    const etiqueta = document.createElement('span');
    etiqueta.className = `badge rounded-pill ${colores[estado] || 'text-bg-secondary'}`;
    etiqueta.textContent = estado ?? '—';
    return etiqueta;
}

// Indica si un préstamo entregado ya superó su fecha límite de devolución.
function estaVencido(prestamo) {
    return prestamo?.estado === 'ENTREGADO'
        && Boolean(prestamo.fechaLimite)
        && new Date(prestamo.fechaLimite) < new Date();
}

const TEXTO_CONDICION = { BUEN_ESTADO: 'Buen estado', CON_DETALLES: 'Con detalles', DANADO: 'Dañado' };

// Una devolución con detalles o daños deja una incidencia abierta hasta que el administrador la resuelve.
function tieneIncidenciaAbierta(prestamo) {
    return prestamo?.estado === 'DEVUELTO'
        && ['CON_DETALLES', 'DANADO'].includes(prestamo.condicionDevolucion)
        && prestamo.incidenciaAtendida !== true;
}

// Devuelve la insignia de estado del préstamo y agrega "Vencido" en rojo y la condición del equipo cuando corresponde.
function etiquetaEstadoPrestamo(prestamo) {
    const base = etiquetaEstado(prestamo?.estado);
    const extras = [];
    if (estaVencido(prestamo)) {
        extras.push(['text-bg-danger', 'Vencido']);
    }
    if (prestamo?.estado === 'DEVUELTO' && ['CON_DETALLES', 'DANADO'].includes(prestamo.condicionDevolucion)) {
        const color = prestamo.condicionDevolucion === 'DANADO' ? 'text-bg-danger' : 'text-bg-warning';
        extras.push([color, TEXTO_CONDICION[prestamo.condicionDevolucion]]);
        if (prestamo.incidenciaAtendida === true) extras.push(['text-bg-secondary', 'Atendida']);
        else if (prestamo.enMantenimiento === true) extras.push(['text-bg-info', 'En mantenimiento']);
    }
    if (extras.length === 0) return base;
    const grupo = document.createElement('span');
    grupo.className = 'd-inline-flex flex-wrap gap-1 align-items-center';
    grupo.append(base);
    for (const [color, texto] of extras) {
        const insignia = document.createElement('span');
        insignia.className = `badge rounded-pill ${color}`;
        insignia.textContent = texto;
        grupo.append(insignia);
    }
    return grupo;
}

// Celda de observaciones: muestra lo anotado al entregar y al devolver el equipo.
function observacionesPrestamo(prestamo) {
    const contenedor = document.createElement('span');
    const rotuloFinal = prestamo?.estado === 'RECHAZADO' ? 'Motivo' : 'Devolución';
    const lineas = [['Entrega', prestamo?.observacionEntrega], [rotuloFinal, prestamo?.observacion]];
    for (const [titulo, texto] of lineas) {
        if (!texto) continue;
        const linea = document.createElement('div');
        const rotulo = document.createElement('span');
        rotulo.className = 'text-body-secondary';
        rotulo.textContent = `${titulo}: `;
        linea.append(rotulo, document.createTextNode(texto));
        contenedor.append(linea);
    }
    if (!contenedor.hasChildNodes()) contenedor.textContent = '—';
    return contenedor;
}

// Alerta para el usuario con los equipos devueltos con detalles o daños que el administrador aún no resuelve.
// Devuelve null si no hay incidencias abiertas.
function avisoIncidencias(prestamos, nombreEquipo) {
    const abiertas = prestamos.filter(tieneIncidenciaAbierta);
    if (abiertas.length === 0) return null;
    const hayDanados = abiertas.some(p => p.condicionDevolucion === 'DANADO');
    const aviso = document.createElement('div');
    aviso.className = `alert ${hayDanados ? 'alert-danger' : 'alert-warning'}`;
    aviso.setAttribute('role', 'alert');
    const titulo = document.createElement('strong');
    titulo.textContent = abiertas.length === 1
        ? 'El administrador dejó una observación sobre un equipo que devolviste. '
        : `El administrador dejó observaciones sobre ${abiertas.length} equipos que devolviste. `;
    aviso.append(titulo, document.createTextNode('Acércate a la administración para revisarlo.'));
    const lista = document.createElement('ul');
    lista.className = 'mb-0 mt-2';
    for (const p of abiertas) {
        const item = document.createElement('li');
        item.textContent = `${nombreEquipo(p.equipoId)} · ${TEXTO_CONDICION[p.condicionDevolucion]}: «${p.observacion}»`
            + (p.enMantenimiento === true ? ' (en mantenimiento)' : '');
        lista.append(item);
    }
    aviso.append(lista);
    return aviso;
}

// Pagina una lista en memoria: dibuja `porPagina` filas por página y una botonera debajo de la tabla.
// `dibujarPagina(filas)` recibe solo las filas de la página actual. `mostrar(items, reiniciar)` vuelve a la página 1
// salvo que reiniciar sea false (útil al recargar tras una acción para quedarse donde estaba).
function crearPaginador(contenedor, dibujarPagina, porPagina = 10) {
    let items = [];
    let pagina = 1;

    function totalPaginas() {
        return Math.max(1, Math.ceil(items.length / porPagina));
    }

    function boton(texto, destino, { activo = false, deshabilitado = false, etiqueta } = {}) {
        const li = document.createElement('li');
        li.className = `page-item${activo ? ' active' : ''}${deshabilitado ? ' disabled' : ''}`;
        const enlace = document.createElement('button');
        enlace.type = 'button';
        enlace.className = 'page-link';
        enlace.textContent = texto;
        if (etiqueta) enlace.setAttribute('aria-label', etiqueta);
        if (activo) enlace.setAttribute('aria-current', 'page');
        enlace.disabled = deshabilitado;
        if (deshabilitado) enlace.dataset.fijoDeshabilitado = 'true';
        enlace.addEventListener('click', () => { pagina = destino; dibujar(); });
        li.append(enlace);
        return li;
    }

    function numerosVisibles(total) {
        // Primera, última y las vecinas de la actual; los huecos se marcan con «…».
        const mostrar = new Set([1, total, pagina - 1, pagina, pagina + 1]);
        const numeros = [...mostrar].filter(n => n >= 1 && n <= total).sort((a, b) => a - b);
        const salida = [];
        numeros.forEach((n, i) => {
            if (i > 0 && n - numeros[i - 1] > 1) salida.push('…');
            salida.push(n);
        });
        return salida;
    }

    function dibujar() {
        const total = totalPaginas();
        pagina = Math.min(Math.max(pagina, 1), total);
        const inicio = (pagina - 1) * porPagina;
        dibujarPagina(items.slice(inicio, inicio + porPagina));
        contenedor.replaceChildren();
        if (items.length <= porPagina) return;

        const resumen = document.createElement('span');
        resumen.className = 'text-body-secondary small';
        resumen.textContent = `Mostrando ${inicio + 1}–${Math.min(inicio + porPagina, items.length)} de ${items.length}`;

        const nav = document.createElement('nav');
        nav.setAttribute('aria-label', 'Paginación de la tabla');
        const lista = document.createElement('ul');
        lista.className = 'pagination pagination-sm mb-0';
        lista.append(boton('Anterior', pagina - 1, { deshabilitado: pagina === 1, etiqueta: 'Página anterior' }));
        for (const n of numerosVisibles(total)) {
            if (n === '…') {
                const hueco = document.createElement('li');
                hueco.className = 'page-item disabled';
                hueco.innerHTML = '<span class="page-link">…</span>';
                lista.append(hueco);
            } else {
                lista.append(boton(String(n), n, { activo: n === pagina, etiqueta: `Página ${n}` }));
            }
        }
        lista.append(boton('Siguiente', pagina + 1, { deshabilitado: pagina === total, etiqueta: 'Página siguiente' }));
        nav.append(lista);
        contenedor.append(resumen, nav);
    }

    return {
        mostrar(nuevos, reiniciar = true) {
            items = nuevos;
            if (reiniciar) pagina = 1;
            dibujar();
        }
    };
}

// Carga los equipos y devuelve un Map id -> equipo. Con incluirDesactivados (solo ADMIN) también los inactivos,
// para que el historial siga mostrando su nombre.
async function cargarCatalogo(incluirDesactivados = false) {
    const catalogo = new Map();
    const rutas = ['/api/equipos?disponibles=false'];
    if (incluirDesactivados) rutas.push('/api/equipos/desactivados');
    for (const ruta of rutas) {
        try {
            const respuesta = await fetch(ruta, { credentials: 'same-origin' });
            if (respuesta.ok) {
                for (const equipo of await respuesta.json()) catalogo.set(equipo.id, equipo);
            }
        } catch {
            // Si falla, se muestra el id como respaldo.
        }
    }
    return catalogo;
}

// Dado un catálogo (Map id -> equipo), devuelve una función id -> "CÓDIGO · Nombre" (o "#id" si no está).
function nombrarEquipos(catalogo) {
    return id => {
        const equipo = catalogo.get(id);
        if (!equipo) return `#${id}`;
        return equipo.codigo ? `${equipo.codigo} · ${equipo.nombre}` : equipo.nombre;
    };
}

async function cargarNombresEquipos(incluirDesactivados = false) {
    return nombrarEquipos(await cargarCatalogo(incluirDesactivados));
}

// Solo para el ADMIN: devuelve una función id -> "Nombre (usuario)" (o "#id" si no se pudo cargar).
async function cargarNombresUsuarios() {
    const nombres = new Map();
    try {
        const respuesta = await fetch('/api/usuarios', { credentials: 'same-origin' });
        if (respuesta.ok) {
            for (const usuario of await respuesta.json()) {
                nombres.set(usuario.id, `${usuario.nombre} (${usuario.username})`);
            }
        }
    } catch {
        // Si falla, se muestra el id como respaldo.
    }
    return id => nombres.get(id) ?? `#${id}`;
}

// Texto del plazo de préstamo: 2 -> "2 horas", 24 -> "1 día", 72 -> "3 días".
function formatearPlazo(horas) {
    if (!horas) return '—';
    if (horas % 24 === 0) {
        const dias = horas / 24;
        return `${dias} ${dias === 1 ? 'día' : 'días'}`;
    }
    return `${horas} ${horas === 1 ? 'hora' : 'horas'}`;
}

// Minúsculas y sin tildes, para comparar texto en los buscadores.
function normalizarTexto(texto) {
    return (texto ?? '').toLocaleLowerCase('es').normalize('NFD').replace(/[\u0300-\u036f]/g, '');
}

// Envía JSON con el token CSRF y devuelve la respuesta ya convertida; lanza Error con el mensaje del servidor.
async function enviarJson(ruta, metodo, cuerpo) {
    const csrf = await (await fetchConSesion('/api/auth/csrf')).json();
    const respuesta = await fetchConSesion(ruta, {
        method: metodo,
        headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
        ...(cuerpo === undefined ? {} : { body: JSON.stringify(cuerpo) })
    });
    const datos = await respuesta.json().catch(() => null);
    if (!respuesta.ok) {
        throw new Error(respuesta.status === 403
            ? 'No tienes permiso o la sesión cambió. Vuelve a iniciar sesión y reintenta.'
            : datos?.error || 'No se pudo completar la operación. Inténtalo de nuevo.');
    }
    return datos;
}

// Texto de la columna «Fecha límite»: la fecha una vez entregado, o el plazo aprobado antes de la entrega.
function textoLimite(prestamo) {
    if (prestamo?.fechaLimite) {
        return new Date(prestamo.fechaLimite).toLocaleString('es-EC', { dateStyle: 'short', timeStyle: 'short' });
    }
    if (prestamo?.estado === 'APROBADO' && prestamo.plazoHoras) {
        return `${formatearPlazo(prestamo.plazoHoras)} desde la entrega`;
    }
    return '—';
}

let ultimoPlazoAprobado = 72;

// Diálogo para que el administrador fije el plazo de devolución al aprobar un préstamo.
// Devuelve una promesa con las horas elegidas (1 a 720) o null si cancela. El plazo empieza a contar al entregar.
function pedirPlazoAprobacion(detalle) {
    return new Promise(resolver => {
        let dialogo = document.getElementById('dialogoPlazo');
        if (!dialogo) {
            dialogo = document.createElement('dialog');
            dialogo.id = 'dialogoPlazo';
            dialogo.className = 'modal-dialogo';
            dialogo.setAttribute('aria-labelledby', 'tituloPlazo');
            dialogo.innerHTML = `
                <form novalidate>
                    <h2 id="tituloPlazo" class="h4 mb-2">Aprobar préstamo</h2>
                    <p class="detalle-plazo mb-3"></p>
                    <label for="plazoAprobValor" class="form-label">Plazo de devolución</label>
                    <div class="input-group">
                        <input id="plazoAprobValor" type="number" min="1" max="720" step="1" class="form-control" required>
                        <select id="plazoAprobUnidad" class="form-select flex-grow-0 w-auto" aria-label="Unidad del plazo">
                            <option value="1">horas</option>
                            <option value="24">días</option>
                        </select>
                    </div>
                    <div class="d-flex flex-wrap gap-2 mt-2">
                        <button type="button" class="btn btn-sm btn-outline-secondary" data-horas="2">2 h</button>
                        <button type="button" class="btn btn-sm btn-outline-secondary" data-horas="3">3 h</button>
                        <button type="button" class="btn btn-sm btn-outline-secondary" data-horas="24">1 día</button>
                        <button type="button" class="btn btn-sm btn-outline-secondary" data-horas="72">3 días</button>
                    </div>
                    <small class="text-body-secondary">El plazo empieza a contar cuando registres la entrega.</small>
                    <p class="error-plazo alert alert-danger py-2 mt-3 mb-0" role="alert" hidden></p>
                    <div class="d-flex flex-wrap justify-content-end gap-2 mt-3">
                        <button type="button" class="cancelar-plazo btn btn-outline-secondary">Cancelar</button>
                        <button type="submit" class="btn btn-primary">Aprobar</button>
                    </div>
                </form>`;
            document.body.append(dialogo);
        }
        const formulario = dialogo.querySelector('form');
        const valor = dialogo.querySelector('#plazoAprobValor');
        const unidad = dialogo.querySelector('#plazoAprobUnidad');
        const error = dialogo.querySelector('.error-plazo');
        const ponerPlazo = horas => {
            const enDias = horas % 24 === 0;
            valor.value = enDias ? horas / 24 : horas;
            unidad.value = enDias ? '24' : '1';
        };
        dialogo.querySelector('.detalle-plazo').textContent = detalle;
        error.hidden = true;
        ponerPlazo(ultimoPlazoAprobado);

        const control = new AbortController();
        const { signal } = control;
        let elegido = null;
        formulario.addEventListener('submit', evento => {
            evento.preventDefault();
            const horas = Number(valor.value) * Number(unidad.value);
            if (!Number.isInteger(horas) || horas < 1 || horas > 720) {
                error.textContent = 'El plazo debe estar entre 1 hora y 30 días.';
                error.hidden = false;
                return;
            }
            elegido = horas;
            ultimoPlazoAprobado = horas;
            dialogo.close();
        }, { signal });
        dialogo.querySelector('.cancelar-plazo').addEventListener('click', () => dialogo.close(), { signal });
        for (const atajo of dialogo.querySelectorAll('[data-horas]')) {
            atajo.addEventListener('click', () => ponerPlazo(Number(atajo.dataset.horas)), { signal });
        }
        dialogo.addEventListener('close', () => {
            control.abort();
            resolver(elegido);
        }, { signal });
        dialogo.showModal();
        valor.focus();
    });
}

// Cómo se puede resolver una incidencia (mismas reglas que el servidor).
const TEXTO_RESOLUCION = {
    ATENDIDA: 'Marcar como atendida: el equipo sigue disponible',
    MANTENIMIENTO: 'Enviar a mantenimiento: sale del catálogo y la incidencia sigue abierta',
    REPARADO: 'Equipo reparado: vuelve al catálogo',
    BAJA: 'Dar de baja: se desactiva el equipo'
};

function opcionesResolucion(prestamo) {
    if (prestamo.enMantenimiento === true) return ['REPARADO', 'BAJA'];
    if (prestamo.condicionDevolucion === 'DANADO') return ['REPARADO', 'MANTENIMIENTO', 'BAJA'];
    return ['ATENDIDA', 'MANTENIMIENTO', 'BAJA'];
}

// Dibuja la barra compartida, verifica /api/auth/me, filtra enlaces por rol y conecta el logout.
async function iniciarEncabezado() {
    const encabezado = document.getElementById('encabezado');
    encabezado.innerHTML = `
        <div class="container">
            <nav class="d-flex flex-wrap gap-3 align-items-center justify-content-between" aria-label="Navegación principal">
                <a class="text-white text-decoration-none fw-bold" href="/"><img src="/img/logo-uta-circulo.png" alt="" class="logo-encabezado">Préstamo de equipos</a>
                <div id="enlacesEncabezado" class="flex-wrap align-items-center gap-2" hidden>
                    <a class="btn btn-sm btn-outline-light" href="/">Inicio</a>
                    <a class="btn btn-sm btn-outline-light" href="/equipos.html">Catálogo</a>
                    <a class="btn btn-sm btn-outline-light" href="/admin-aprobaciones.html" data-rol="ADMIN">Pendientes</a>
                    <a class="btn btn-sm btn-outline-light" href="/incidencias.html" data-rol="ADMIN">Incidencias</a>
                    <a class="btn btn-sm btn-outline-light" href="/usuarios.html" data-rol="ADMIN">Usuarios</a>
                    <a class="btn btn-sm btn-outline-light" href="/mis-solicitudes.html" data-rol="USUARIO">Mis solicitudes</a>
                    <a class="btn btn-sm btn-outline-light" href="/devoluciones.html">Historial</a>
                </div>
                <div class="d-flex flex-wrap align-items-center gap-3">
                    <span id="usuarioEncabezado">Comprobando sesión…</span>
                    <button id="botonSalir" type="button" class="btn btn-sm btn-outline-light" hidden>Cerrar sesión</button>
                </div>
            </nav>
            <p id="alertaEncabezado" class="alert alert-danger mt-3 mb-0" role="alert" hidden></p>
        </div>`;

    const opciones = document.getElementById('enlacesEncabezado');
    const nombre = document.getElementById('usuarioEncabezado');
    const salir = document.getElementById('botonSalir');
    const aviso = document.getElementById('alertaEncabezado');

    salir.addEventListener('click', async () => {
        salir.disabled = true;
        aviso.hidden = true;
        try {
            const respuesta = await fetchConSesion('/api/auth/csrf');
            if (!respuesta.ok) throw new Error();
            const csrf = await respuesta.json();
            const cierre = await fetchConSesion('/api/auth/logout', {
                method: 'POST',
                headers: { [csrf.headerName]: csrf.token }
            });
            if (!cierre.ok) throw new Error();
            window.location.replace('/login.html');
        } catch {
            aviso.textContent = 'No se pudo cerrar la sesión. Inténtalo de nuevo.';
            aviso.hidden = false;
            salir.disabled = false;
        }
    });

    try {
        const respuesta = await fetch('/api/auth/me', { credentials: 'same-origin' });
        if (respuesta.status === 401) {
            window.location.replace('/login.html');
            return null;
        }
        if (!respuesta.ok) throw new Error();

        const usuario = await respuesta.json();
        if (encabezado.dataset.rol && encabezado.dataset.rol !== usuario.rol) {
            window.location.replace('/');
            return null;
        }

        const rutaActual = window.location.pathname === '/index.html' ? '/' : window.location.pathname;
        nombre.textContent = `${usuario.nombre} · ${usuario.rol}`;
        for (const enlace of opciones.querySelectorAll('a')) {
            enlace.hidden = Boolean(enlace.dataset.rol && enlace.dataset.rol !== usuario.rol);
            if (enlace.getAttribute('href') === rutaActual) {
                enlace.setAttribute('aria-current', 'page');
                enlace.classList.add('active');
            }
        }
        opciones.hidden = false;
        opciones.classList.add('d-flex');
        salir.hidden = false;
        return usuario;
    } catch {
        nombre.textContent = 'Sesión no disponible';
        aviso.textContent = 'No se pudo verificar la sesión.';
        aviso.hidden = false;
        return null;
    }
}

// Recarga si el navegador vuelve con el botón Atrás usando caché, para revalidar la sesión.
const sesionActual = iniciarEncabezado();
window.addEventListener('pageshow', evento => {
    if (evento.persisted) window.location.reload();
});
