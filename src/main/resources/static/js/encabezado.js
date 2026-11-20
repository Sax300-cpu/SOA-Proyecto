'use strict';

async function fetchConSesion(ruta, opciones = {}) {
    const respuesta = await fetch(ruta, { credentials: 'same-origin', ...opciones });
    if (respuesta.status === 401) {
        window.location.replace('/login.html');
        throw new Error('La sesión ha terminado.');
    }
    return respuesta;
}

async function iniciarEncabezado() {
    const encabezado = document.getElementById('encabezado');
    encabezado.innerHTML = `
        <div class="container">
            <nav class="d-flex flex-wrap gap-3 align-items-center justify-content-between" aria-label="Navegación principal">
                <a class="text-white text-decoration-none fw-bold" href="/">Préstamo de equipos</a>
                <div id="enlacesEncabezado" class="flex-wrap align-items-center gap-2" hidden>
                    <a class="btn btn-sm btn-outline-light" href="/">Inicio</a>
                    <a class="btn btn-sm btn-outline-light" href="/equipos.html">Catálogo</a>
                    <a class="btn btn-sm btn-outline-light" href="/admin-aprobaciones.html" data-rol="ADMIN">Pendientes</a>
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

const sesionActual = iniciarEncabezado();
window.addEventListener('pageshow', evento => {
    if (evento.persisted) window.location.reload();
});
