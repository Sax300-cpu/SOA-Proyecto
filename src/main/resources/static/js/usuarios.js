'use strict';

// Página de usuarios (solo ADMIN): lista con sus préstamos y bloqueo de cuentas.
(() => {
    const porId = id => document.getElementById(id);
    const ACTIVOS = ['SOLICITADO', 'APROBADO', 'ENTREGADO'];
    let usuarios = [];
    let resumen = new Map();
    let usuarioABloquear = null;
    let guardando = false;
    let creando = false;

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

    function insignia(color, texto) {
        const etiqueta = document.createElement('span');
        etiqueta.className = `badge rounded-pill ${color}`;
        etiqueta.textContent = texto;
        return etiqueta;
    }

    function dibujarFilas(pagina) {
        porId('registros').replaceChildren();
        for (const usuario of pagina) {
            const datos = resumen.get(usuario.id) ?? { activos: 0, vencidos: 0 };
            const fila = document.createElement('tr');
            celda(fila, usuario.nombre);
            celda(fila, usuario.username);
            celda(fila, null).replaceChildren(insignia(usuario.rol === 'ADMIN' ? 'text-bg-dark' : 'text-bg-secondary', usuario.rol));
            celda(fila, null).replaceChildren(usuario.bloqueado ? insignia('text-bg-danger', 'Bloqueado') : insignia('text-bg-success', 'Activo'));
            celda(fila, datos.activos);
            const vencidos = celda(fila, datos.vencidos);
            if (datos.vencidos > 0) vencidos.className = 'text-danger fw-semibold';
            const acciones = celda(fila, '—');
            if (usuario.rol !== 'ADMIN') {
                const boton = document.createElement('button');
                boton.type = 'button';
                boton.className = `btn btn-sm ${usuario.bloqueado ? 'btn-outline-success' : 'btn-outline-danger'}`;
                boton.textContent = usuario.bloqueado ? 'Desbloquear' : 'Bloquear';
                boton.setAttribute('aria-label', `${boton.textContent} a ${usuario.nombre}`);
                boton.addEventListener('click', () => usuario.bloqueado ? desbloquear(usuario) : abrirBloqueo(usuario));
                acciones.replaceChildren(boton);
            }
            porId('registros').append(fila);
        }
    }

    const paginador = crearPaginador(porId('paginador'), dibujarFilas);

    function refrescar(reiniciar = true) {
        const texto = normalizarTexto(porId('textoBusqueda').value.trim());
        const soloBloqueados = porId('soloBloqueados').checked;
        const visibles = usuarios.filter(u =>
            (!soloBloqueados || u.bloqueado)
            && (!texto || normalizarTexto(`${u.nombre} ${u.username}`).includes(texto)));
        paginador.mostrar(visibles, reiniciar);
        porId('vacio').hidden = visibles.length !== 0;
    }

    async function cargar(aviso = '', reiniciar = true) {
        if (aviso) mensaje(aviso, 'success'); else porId('mensaje').hidden = true;
        try {
            const [lista, prestamos] = await Promise.all([
                fetchConSesion('/api/usuarios').then(r => r.json()),
                fetchConSesion('/api/prestamos').then(r => r.json())
            ]);
            usuarios = lista;
            // Préstamos activos y vencidos de cada usuario.
            resumen = new Map();
            for (const p of prestamos) {
                const datos = resumen.get(p.usuarioId) ?? { activos: 0, vencidos: 0 };
                if (ACTIVOS.includes(p.estado)) datos.activos++;
                if (estaVencido(p)) datos.vencidos++;
                resumen.set(p.usuarioId, datos);
            }
            refrescar(reiniciar);
        } catch (error) {
            mensaje(`No se pudieron cargar los usuarios: ${error.message}`, 'danger');
        }
    }

    function abrirBloqueo(usuario) {
        usuarioABloquear = usuario;
        porId('textoBloquear').textContent = `¿Bloquear a ${usuario.nombre} (${usuario.username})?`;
        porId('errorBloquear').hidden = true;
        porId('modalBloquear').showModal();
    }

    async function desbloquear(usuario) {
        try {
            await enviarJson(`/api/usuarios/${usuario.id}/desbloquear`, 'PUT');
            await cargar(`${usuario.nombre} fue desbloqueado y ya puede solicitar equipos.`, false);
        } catch (error) {
            mensaje(error.message, 'danger');
        }
    }

    // ---- Nuevo usuario ----
    porId('abrirNuevo').addEventListener('click', () => {
        porId('formNuevo').reset();
        porId('nuevaPassword').type = 'password';
        porId('errorNuevo').hidden = true;
        porId('modalNuevo').showModal();
        porId('nuevoNombre').focus();
    });
    porId('verPassword').addEventListener('click', () => {
        const campo = porId('nuevaPassword');
        campo.type = campo.type === 'password' ? 'text' : 'password';
    });
    porId('cancelarNuevo').addEventListener('click', () => porId('modalNuevo').close());
    porId('modalNuevo').addEventListener('cancel', evento => { if (creando) evento.preventDefault(); });
    porId('formNuevo').addEventListener('submit', async evento => {
        evento.preventDefault();
        if (creando) return;
        const error = porId('errorNuevo');
        error.hidden = true;
        const nombre = porId('nuevoNombre').value.trim();
        const username = porId('nuevoUsername').value.trim();
        const password = porId('nuevaPassword').value;
        if (!nombre || !username || !password.trim()) {
            error.textContent = 'Todos los campos son obligatorios.';
            error.hidden = false;
            return;
        }
        if (username.includes(' ')) {
            error.textContent = 'El usuario no debe tener espacios.';
            error.hidden = false;
            return;
        }
        if (password.length < 8) {
            error.textContent = 'La contraseña debe tener al menos 8 caracteres.';
            error.hidden = false;
            return;
        }
        creando = true;
        porId('guardarNuevo').disabled = true;
        porId('cancelarNuevo').disabled = true;
        try {
            const creado = await enviarJson('/api/usuarios', 'POST', { nombre, username, password, rol: porId('nuevoRol').value });
            porId('modalNuevo').close();
            await cargar(`Usuario «${creado.username}» creado como ${creado.rol === 'ADMIN' ? 'administrador' : 'usuario'}.`);
        } catch (fallo) {
            error.textContent = fallo.message;
            error.hidden = false;
        } finally {
            creando = false;
            porId('guardarNuevo').disabled = false;
            porId('cancelarNuevo').disabled = false;
        }
    });

    porId('filtros').addEventListener('submit', evento => { evento.preventDefault(); cargar(); });
    porId('textoBusqueda').addEventListener('input', () => refrescar());
    porId('soloBloqueados').addEventListener('change', () => refrescar());
    porId('cancelarBloquear').addEventListener('click', () => porId('modalBloquear').close());
    porId('modalBloquear').addEventListener('cancel', evento => { if (guardando) evento.preventDefault(); });
    porId('formBloquear').addEventListener('submit', async evento => {
        evento.preventDefault();
        if (guardando || !usuarioABloquear) return;
        guardando = true;
        porId('errorBloquear').hidden = true;
        const usuario = usuarioABloquear;
        try {
            await enviarJson(`/api/usuarios/${usuario.id}/bloquear`, 'PUT');
            porId('modalBloquear').close();
            await cargar(`${usuario.nombre} fue bloqueado. Puedes desbloquearlo cuando quieras.`, false);
        } catch (error) {
            porId('errorBloquear').textContent = error.message;
            porId('errorBloquear').hidden = false;
        } finally {
            guardando = false;
        }
    });

    sesionActual.then(usuario => {
        if (usuario) cargar();
    });
})();
