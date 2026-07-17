let currentRoutesData = [];
let map = null;
let currentJobPoller = null;

const windowLabelMap = {
    WINDOW_1: '08:00 - 11:59',
    WINDOW_2: '08:01 - 17:00',
    WINDOW_3: '12:00 - 16:59'
};

const depotSelect = document.getElementById('depotSelect');
const customDepotContainer = document.getElementById('customDepotContainer');
const customDepotInput = document.getElementById('customDepot');
const uploadForm = document.getElementById('uploadForm');
const submitButton = document.getElementById('submitButton');
const fileInput = document.getElementById('fileInput');
const loader = document.getElementById('loader');
const mapContainer = document.getElementById('map');
const routesContainer = document.getElementById('routes-container');
const routesList = document.getElementById('routes-list');
const routesSummary = document.getElementById('routes-summary');
const statusMessage = document.getElementById('statusMessage');
const errorPanel = document.getElementById('errorPanel');
const jobProgressPanel = document.getElementById('jobProgressPanel');
const jobProgressTitle = document.getElementById('jobProgressTitle');
const jobProgressDescription = document.getElementById('jobProgressDescription');
const jobProgressFill = document.getElementById('jobProgressFill');
const jobProgressPercent = document.getElementById('jobProgressPercent');
const jobProgressStage = document.getElementById('jobProgressStage');
const jobProgressUpdated = document.getElementById('jobProgressUpdated');
const exportPdfBtn = document.getElementById('exportPdfBtn');
const exportExcelBtn = document.getElementById('exportExcelBtn');

function escapeHtml(value) {
    return String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');
}

function safeText(value, fallback = '-') {
    const raw = value ?? '';
    const text = String(raw).trim();
    return text ? text : fallback;
}

function safeNumber(value, fallback = 0) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
}

function formatWindowLabel(value) {
    return windowLabelMap[value] || safeText(value);
}

function setStatus(message, type = 'info') {
    statusMessage.textContent = message;
    statusMessage.className = `status-message is-visible is-${type}`;
}

function clearStatus() {
    statusMessage.textContent = '';
    statusMessage.className = 'status-message';
}

function hideErrorPanel() {
    errorPanel.className = 'error-panel';
    errorPanel.innerHTML = '';
}

function stopJobPolling() {
    if (currentJobPoller) {
        clearInterval(currentJobPoller);
        currentJobPoller = null;
    }
}

function hideJobProgress() {
    stopJobPolling();
    jobProgressPanel.className = 'job-progress-panel';
    jobProgressTitle.textContent = 'Preparando procesamiento';
    jobProgressDescription.textContent = 'La app te irá mostrando cada etapa para que sepas en qué va.';
    jobProgressFill.style.width = '0%';
    jobProgressPercent.textContent = '0%';
    jobProgressStage.textContent = 'Esperando inicio';
    jobProgressUpdated.textContent = 'Sin actividad aún';
}

function setLoadingState(isLoading) {
    loader.style.display = isLoading ? 'block' : 'none';
    submitButton.disabled = isLoading;
}

function setExportButtonsEnabled(enabled) {
    exportPdfBtn.disabled = !enabled;
    exportExcelBtn.disabled = !enabled;
}

function formatJobStage(stage) {
    const labels = {
        QUEUED: 'En cola',
        STARTING: 'Iniciando',
        PREPARING: 'Preparando archivo',
        GEOCODING_DEPOT: 'Ubicando depósito',
        READING_FILE: 'Leyendo Excel',
        MAPPING_COLUMNS: 'Detectando columnas',
        PROCESSING_ROWS: 'Procesando pedidos',
        OPTIMIZING: 'Optimizando rutas',
        BUILDING_RESPONSE: 'Armando respuesta',
        COMPLETED: 'Completado',
        FAILED: 'Falló'
    };
    return labels[stage] || stage || 'Procesando';
}

function renderJobProgress(job) {
    const progress = Math.max(0, Math.min(safeNumber(job?.progress, 0), 100));
    const updatedAt = job?.updatedAt ? new Date(job.updatedAt).toLocaleTimeString('es-CO') : 'Sin actualización';

    jobProgressPanel.className = 'job-progress-panel is-visible';
    jobProgressTitle.textContent = formatJobStage(job?.stage);
    jobProgressDescription.textContent = safeText(job?.message, 'Procesando archivo en segundo plano.');
    jobProgressFill.style.width = `${progress}%`;
    jobProgressPercent.textContent = `${Math.round(progress)}%`;
    jobProgressStage.textContent = `Estado: ${safeText(job?.status, 'RUNNING')}`;
    jobProgressUpdated.textContent = `Última actualización: ${updatedAt}`;
}

function buildJobError(job) {
    return {
        kind: 'job',
        status: null,
        statusText: null,
        rawText: job?.error?.technicalDetails || job?.message || 'Error durante el procesamiento.',
        parsed: job?.error || null,
        code: job?.error?.type || job?.status || null,
        message: job?.error?.message || job?.message || 'Error durante el procesamiento.',
        requestId: job?.error?.requestId || null
    };
}

async function createHttpError(response) {
    const rawText = await response.text();
    let parsed = null;

    try {
        parsed = JSON.parse(rawText);
    } catch (_) {
        parsed = null;
    }

    return {
        kind: 'http',
        status: response.status,
        statusText: response.statusText,
        rawText,
        parsed,
        code: parsed?.code || response.status,
        message: parsed?.message || rawText || response.statusText || 'Error desconocido',
        requestId: parsed?.request_id || parsed?.requestId || null
    };
}

function normalizeUnexpectedError(error) {
    if (error?.kind === 'http' || error?.kind === 'job') {
        return error;
    }

    return {
        kind: 'unexpected',
        status: null,
        statusText: null,
        rawText: error?.message || 'Error inesperado',
        parsed: null,
        code: null,
        message: error?.message || 'Error inesperado',
        requestId: null
    };
}

function getErrorDescriptor(error) {
    const normalized = normalizeUnexpectedError(error);
    const status = normalized.status || normalized.code;
    const message = (normalized.message || '').toLowerCase();

    if (status === 502 || message.includes('application failed to respond')) {
        return {
            shortMessage: 'El servidor no alcanzó a responder a tiempo.',
            title: 'La aplicación backend no respondió a tiempo',
            description: 'La carga salió bien desde esta pantalla, pero el servidor se quedó sin responder antes de terminar el procesamiento.',
            causes: [
                'El archivo puede tener muchas filas y el proceso tardó demasiado.',
                'La geocodificación de varias direcciones pudo ralentizar o bloquear la respuesta.',
                'El backend pudo reiniciarse, quedarse sin recursos o lanzar una excepción antes de responder.'
            ],
            actions: [
                'Prueba primero con un archivo más pequeño para validar el tiempo de procesamiento.',
                'Revisa los logs del backend usando el request id si está disponible.',
                'Si ocurre siempre con el mismo archivo, revisa columnas, direcciones y pesos.'
            ]
        };
    }

    if (String(normalized.code || '').toLowerCase().includes('illegalargument')) {
        return {
            shortMessage: 'El archivo no se pudo procesar con los datos enviados.',
            title: 'La validación del archivo falló durante el procesamiento',
            description: 'El backend sí leyó la solicitud, pero encontró datos insuficientes o inválidos para construir rutas útiles.',
            causes: [
                'No se encontraron pedidos con coordenadas válidas.',
                'Faltan columnas clave como dirección, latitud, longitud o peso.',
                'El contenido del archivo no coincide con el formato esperado.'
            ],
            actions: [
                'Verifica que la primera fila tenga encabezados claros.',
                'Prueba con un archivo pequeño que ya haya funcionado antes.',
                'Abre el detalle técnico para ver el mensaje exacto.'
            ]
        };
    }

    if (status === 400) {
        return {
            shortMessage: 'Hay un problema con los datos enviados en el archivo.',
            title: 'El archivo o sus datos no pasaron la validación',
            description: 'La aplicación respondió, pero rechazó la solicitud porque encontró datos faltantes o inválidos.',
            causes: [
                'Las columnas esperadas no coinciden con los alias del backend.',
                'No se encontraron direcciones o coordenadas válidas.',
                'Algún valor numérico o franja horaria no pudo interpretarse correctamente.'
            ],
            actions: [
                'Verifica columnas como dirección, peso, latitud y longitud.',
                'Confirma que el archivo tenga encabezados en la primera fila.',
                'Prueba con 2 o 3 filas conocidas para aislar el dato conflictivo.'
            ]
        };
    }

    if (status === 500) {
        return {
            shortMessage: 'El backend falló mientras procesaba el archivo.',
            title: 'Se produjo un error interno en el servidor',
            description: 'La solicitud llegó al backend, pero algo falló durante la lectura del Excel, la geocodificación o la optimización.',
            causes: [
                'Un dato inesperado disparó una excepción dentro del procesamiento.',
                'La lectura del Excel pudo fallar por formato o contenido.',
                'La etapa de optimización pudo romperse por datos inconsistentes.'
            ],
            actions: [
                'Revisar los logs del backend te dirá exactamente en qué paso ocurrió.',
                'Si el archivo es nuevo, prueba con uno que antes sí funcionaba.',
                'Usa el detalle técnico para ver el mensaje exacto devuelto.'
            ]
        };
    }

    if (message.includes('failed to fetch') || message.includes('networkerror')) {
        return {
            shortMessage: 'No se pudo conectar con el servidor.',
            title: 'La pantalla no pudo comunicarse con el backend',
            description: 'Esto suele pasar cuando el backend no está levantado o hubo un corte de red durante la petición.',
            causes: [
                'La aplicación backend no está corriendo o se cayó.',
                'Hay un problema de red, proxy o CORS en el entorno.',
                'La petición fue interrumpida antes de recibir respuesta.'
            ],
            actions: [
                'Confirma que el backend siga arriba y respondiendo.',
                'Reintenta la operación para descartar un fallo temporal.',
                'Revisa consola y red si vuelve a ocurrir.'
            ]
        };
    }

    return {
        shortMessage: 'Ocurrió un error y necesitamos más detalle para identificarlo.',
        title: 'Se produjo un error no clasificado',
        description: 'La aplicación recibió una respuesta de error, pero no encaja en los casos más comunes.',
        causes: [
            'Puede ser un error puntual de datos, backend o infraestructura.',
            'La respuesta pudo venir desde un proxy o servicio intermedio.',
            'También puede ser un fallo nuevo no contemplado aún en la interfaz.'
        ],
        actions: [
            'Revisa el detalle técnico expandible.',
            'Si vuelve a ocurrir, comparte el request id y el mensaje exacto.',
            'Con eso será más fácil ir al punto de falla real.'
        ]
    };
}

function renderErrorPanel(error) {
    const normalized = normalizeUnexpectedError(error);
    const descriptor = getErrorDescriptor(normalized);

    const statusChip = normalized.status
        ? `<div class="error-meta-chip"><span class="material-symbols-outlined">error</span>HTTP ${escapeHtml(normalized.status)}</div>`
        : '';
    const requestChip = normalized.requestId
        ? `<div class="error-meta-chip"><span class="material-symbols-outlined">tag</span>Request ID: ${escapeHtml(normalized.requestId)}</div>`
        : '';
    const codeChip = normalized.code && normalized.code !== normalized.status
        ? `<div class="error-meta-chip"><span class="material-symbols-outlined">code</span>Código: ${escapeHtml(normalized.code)}</div>`
        : '';
    const rawDetail = normalized.rawText || normalized.message || 'Sin detalle técnico.';

    errorPanel.innerHTML = `
        <div class="error-panel-header">
            <div class="error-panel-icon">
                <span class="material-symbols-outlined">warning</span>
            </div>
            <div>
                <h3 class="error-panel-title">${escapeHtml(descriptor.title)}</h3>
                <p class="error-panel-description">${escapeHtml(descriptor.description)}</p>
            </div>
        </div>
        <div class="error-meta">
            ${statusChip}
            ${codeChip}
            ${requestChip}
        </div>
        <div class="error-grid">
            <div class="error-card">
                <h4>Posibles causas</h4>
                <ul class="error-list">
                    ${descriptor.causes.map(item => `<li>${escapeHtml(item)}</li>`).join('')}
                </ul>
            </div>
            <div class="error-card">
                <h4>Qué revisar ahora</h4>
                <ul class="error-list">
                    ${descriptor.actions.map(item => `<li>${escapeHtml(item)}</li>`).join('')}
                </ul>
            </div>
        </div>
        <details class="error-details">
            <summary>Ver detalle técnico</summary>
            <pre class="error-raw">${escapeHtml(rawDetail)}</pre>
        </details>
    `;

    errorPanel.className = 'error-panel is-visible';
    setStatus(descriptor.shortMessage, 'error');
}

function applyRouteDisplayNames(routes) {
    let nhrCount = 0;
    let nprCount = 0;
    let carryCount = 0;
    let hypotheticalCount = 0;

    routes.forEach((route, index) => {
        const type = safeText(route.vehicleType, '').toUpperCase();
        const id = safeText(route.vehicleId, '');

        if (type.includes('HIPOTÉTICO') || type.includes('HYPOTHETICAL')) {
            hypotheticalCount++;
            route.displayName = `Hipotético ${hypotheticalCount}`;
        } else if (type.includes('NPR')) {
            const match = id.match(/NPR-(\d+)/i);
            const number = match ? match[1] : ++nprCount;
            route.displayName = `NPR ${number}`;
        } else if (type.includes('NHR')) {
            const match = id.match(/NHR-(\d+)/i);
            const number = match ? match[1] : ++nhrCount;
            route.displayName = `NHR ${number}`;
        } else if (type.includes('CARRY')) {
            const match = id.match(/Carry-(\d+)/i);
            const number = match ? match[1] : ++carryCount;
            route.displayName = id.includes('Trip2') ? `Carry ${number} (2da vuelta)` : `Carry ${number}`;
        } else {
            route.displayName = safeText(route.vehicleId, `Ruta ${index + 1}`);
        }
    });
}

function setDepotMode() {
    const isCustom = depotSelect.value === 'other';
    customDepotContainer.classList.toggle('hidden-panel', !isCustom);
    customDepotInput.required = isCustom;

    if (!isCustom) {
        customDepotInput.value = '';
    }
}

function getSelectedDepotAddress() {
    return depotSelect.value === 'other' ? customDepotInput.value.trim() : depotSelect.value;
}

function resetResultsView() {
    currentRoutesData = [];
    mapContainer.style.display = 'none';
    routesContainer.classList.add('hidden-section');
    routesSummary.innerHTML = '';
    routesList.innerHTML = '';
    setExportButtonsEnabled(false);
}

function createEmptyState(message) {
    return `
        <div class="empty-state">
            <span class="material-symbols-outlined">inbox</span>
            <p>${escapeHtml(message)}</p>
        </div>
    `;
}

function applyOptimizationResult(data) {
    currentRoutesData = Array.isArray(data?.routes) ? data.routes : [];
    applyRouteDisplayNames(currentRoutesData);
    hideErrorPanel();
    setLoadingState(false);
    setExportButtonsEnabled(currentRoutesData.length > 0);

    renderJobProgress({
        status: 'COMPLETED',
        stage: 'COMPLETED',
        progress: 100,
        message: `Proceso completado. Vehículos usados: ${safeNumber(data?.totalVehiclesUsed, currentRoutesData.length)}. Pedidos no asignados: ${safeNumber(data?.unassigned, 0)}.`,
        updatedAt: new Date().toISOString()
    });

    setStatus(
        `Optimización completada. Vehículos usados: ${safeNumber(data?.totalVehiclesUsed, currentRoutesData.length)}. Pedidos no asignados: ${safeNumber(data?.unassigned, 0)}.`,
        'success'
    );

    if (currentRoutesData.length > 0) {
        mapContainer.style.display = 'block';
        initMap(currentRoutesData);
        displayRoutesTable(currentRoutesData, data?.totalVehiclesUsed, data?.unassigned);
        mapContainer.scrollIntoView({ behavior: 'smooth' });
        return;
    }

    routesContainer.classList.remove('hidden-section');
    routesSummary.innerHTML = '';
    routesList.innerHTML = createEmptyState('El proceso terminó, pero no se generaron rutas para mostrar.');
}

async function fetchJobStatus(jobId) {
    const response = await fetch(`/api/routes/upload/${encodeURIComponent(jobId)}`);
    if (!response.ok) {
        throw await createHttpError(response);
    }
    return response.json();
}

async function startPollingJob(jobId) {
    stopJobPolling();

    const tick = async () => {
        const job = await fetchJobStatus(jobId);
        renderJobProgress(job);

        if (job.status === 'COMPLETED') {
            stopJobPolling();
            if (job.result) {
                applyOptimizationResult(job.result);
            } else {
                throw new Error('El proceso terminó sin devolver resultado.');
            }
            return;
        }

        if (job.status === 'FAILED') {
            stopJobPolling();
            setLoadingState(false);
            setExportButtonsEnabled(false);
            renderErrorPanel(buildJobError(job));
        }
    };

    await tick();

    currentJobPoller = setInterval(() => {
        tick().catch(error => {
            stopJobPolling();
            setLoadingState(false);
            setExportButtonsEnabled(false);
            renderErrorPanel(error);
        });
    }, 1500);
}

function initMap(routes) {
    if (map) {
        map.remove();
    }

    let centerLat = 4.6097;
    let centerLon = -74.0817;

    if (routes.length > 0 && Array.isArray(routes[0].orders) && routes[0].orders.length > 0) {
        centerLat = safeNumber(routes[0].orders[0]?.location?.latitude, centerLat);
        centerLon = safeNumber(routes[0].orders[0]?.location?.longitude, centerLon);
    }

    map = L.map('map').setView([centerLat, centerLon], 13);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
    }).addTo(map);

    const colors = ['#1565c0', '#ef5350', '#26a69a', '#7e57c2', '#fb8c00', '#3949ab', '#8e24aa', '#00897b'];
    const bounds = [];

    routes.forEach((route, routeIndex) => {
        const color = colors[routeIndex % colors.length];
        const pathCoords = [];

        (route.orders || []).forEach((order, orderIndex) => {
            const lat = safeNumber(order?.location?.latitude, NaN);
            const lon = safeNumber(order?.location?.longitude, NaN);

            if (!Number.isFinite(lat) || !Number.isFinite(lon)) {
                return;
            }

            const latLng = [lat, lon];
            pathCoords.push(latLng);
            bounds.push(latLng);

            const marker = L.circleMarker(latLng, {
                color,
                fillColor: color,
                fillOpacity: 0.85,
                radius: 8,
                weight: 2
            }).addTo(map);

            marker.bindPopup(`
                <b>Pedido:</b> ${escapeHtml(safeText(order?.id))}<br>
                <b>Cliente:</b> ${escapeHtml(safeText(order?.name))}<br>
                <b>Dirección:</b> ${escapeHtml(safeText(order?.address))}<br>
                <b>Peso:</b> ${Math.round(safeNumber(order?.demand, 0))} kg<br>
                <b>Franja:</b> ${escapeHtml(formatWindowLabel(order?.deliveryWindow))}<br>
                <b>Vehículo:</b> ${escapeHtml(safeText(route?.vehicleType, 'Vehículo'))} (${escapeHtml(safeText(route?.vehicleId, 'Sin identificador'))})<br>
                <b>Orden en ruta:</b> ${orderIndex + 1}
            `);
        });

        if (pathCoords.length > 1) {
            L.polyline(pathCoords, {
                color,
                weight: 4,
                opacity: 0.75
            }).addTo(map);
        }
    });

    if (bounds.length > 0) {
        map.fitBounds(bounds, { padding: [50, 50] });
    }
}

function getRouteBadgeColor(comment) {
    const text = safeText(comment, '');
    if (text.includes('Dedicada')) {
        return '#7b1fa2';
    }
    if (text.includes('Abierta')) {
        return '#2e7d32';
    }
    return '#1565c0';
}

function buildRouteSummary(routes, totalVehiclesUsed, unassigned) {
    const totalOrders = routes.reduce((acc, route) => acc + (route.orders?.length || 0), 0);

    return `
        <div class="summary-chip">
            <span class="material-symbols-outlined">route</span>
            <span><span class="summary-label">Rutas</span> ${safeNumber(totalVehiclesUsed, routes.length)}</span>
        </div>
        <div class="summary-chip">
            <span class="material-symbols-outlined">assignment_late</span>
            <span><span class="summary-label">No asignados</span> ${safeNumber(unassigned, 0)}</span>
        </div>
        <div class="summary-chip">
            <span class="material-symbols-outlined">inventory_2</span>
            <span><span class="summary-label">Pedidos</span> ${totalOrders}</span>
        </div>
    `;
}

function buildOrderRows(orders) {
    return (orders || []).map(order => `
        <tr>
            <td>${escapeHtml(safeText(order?.id))}</td>
            <td>${escapeHtml(safeText(order?.name))}</td>
            <td>${escapeHtml(safeText(order?.address))}</td>
            <td class="align-right">${escapeHtml(safeText(order?.accumulatedDistanceKm))}</td>
            <td class="align-right">${Math.round(safeNumber(order?.demand, 0))}</td>
            <td class="align-center">${escapeHtml(formatWindowLabel(order?.deliveryWindow))}</td>
        </tr>
    `).join('');
}

function displayRoutesTable(routes, totalVehiclesUsed, unassigned) {
    routesList.innerHTML = '';
    routesSummary.innerHTML = '';

    if (!routes || routes.length === 0) {
        routesContainer.classList.remove('hidden-section');
        routesList.innerHTML = createEmptyState('No hay rutas para mostrar todavía.');
        return;
    }

    routesContainer.classList.remove('hidden-section');
    routesSummary.innerHTML = buildRouteSummary(routes, totalVehiclesUsed, unassigned);

    routes.forEach(route => {
        const card = document.createElement('div');
        card.className = 'route-card';

        const comment = safeText(route.comments, '');
        const commentHtml = comment
            ? `<span class="route-badge" style="background-color: ${getRouteBadgeColor(comment)};">${escapeHtml(comment)}</span>`
            : '';

        const totalDistanceKm = route.totalDistanceKm
            ? safeText(route.totalDistanceKm)
            : `${(safeNumber(route.totalDistance, 0) / 1000).toFixed(2)} km`;

        card.innerHTML = `
            <div class="route-card-header">
                <div>
                    <h3 class="route-card-title">${escapeHtml(safeText(route.displayName, 'Ruta'))}</h3>
                    <p class="route-meta">${escapeHtml(safeText(route.vehicleType, 'Vehículo'))} · ${escapeHtml(safeText(route.vehicleId, 'Sin identificador'))}</p>
                </div>
                <div>${commentHtml}</div>
            </div>
            <div class="route-card-header compact-top">
                <div class="route-metrics">
                    <div class="metric">
                        <span class="metric-label">Peso total</span>
                        <span class="metric-value">${Math.round(safeNumber(route.totalLoad, 0))} kg</span>
                    </div>
                    <div class="metric">
                        <span class="metric-label">Distancia total</span>
                        <span class="metric-value">${escapeHtml(totalDistanceKm)}</span>
                    </div>
                    <div class="metric">
                        <span class="metric-label">Pedidos</span>
                        <span class="metric-value">${route.orders?.length || 0}</span>
                    </div>
                </div>
            </div>
            <div class="table-wrap">
                <table class="orders-table">
                    <thead>
                        <tr>
                            <th>ID</th>
                            <th>Cliente</th>
                            <th>Dirección</th>
                            <th class="align-right">Km acum.</th>
                            <th class="align-right">Peso</th>
                            <th class="align-center">Franja</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${buildOrderRows(route.orders)}
                    </tbody>
                </table>
            </div>
        `;

        routesList.appendChild(card);
    });
}

async function exportRoutes(format) {
    if (!currentRoutesData || currentRoutesData.length === 0) {
        setStatus('No hay rutas para exportar todavía.', 'error');
        return;
    }

    const isPdf = format === 'pdf';
    const button = isPdf ? exportPdfBtn : exportExcelBtn;
    const label = isPdf ? 'PDF' : 'Excel';
    const url = isPdf ? '/api/export/pdf' : '/api/export/excel';

    button.disabled = true;
    setStatus(`Generando archivo ${label}...`, 'info');

    try {
        const response = await fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(currentRoutesData)
        });

        if (!response.ok) {
            throw await createHttpError(response);
        }

        const blob = await response.blob();
        const downloadUrl = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = downloadUrl;
        link.download = isPdf ? 'rutas.pdf' : 'rutas.xlsx';
        document.body.appendChild(link);
        link.click();
        link.remove();
        window.URL.revokeObjectURL(downloadUrl);

        setStatus(`El archivo ${label} se descargó correctamente.`, 'success');
    } catch (error) {
        renderErrorPanel(error);
    } finally {
        setExportButtonsEnabled(currentRoutesData.length > 0);
    }
}

depotSelect.addEventListener('change', setDepotMode);

uploadForm.addEventListener('submit', async event => {
    event.preventDefault();

    if (!fileInput.files[0]) {
        setStatus('Por favor selecciona un archivo.', 'error');
        return;
    }

    const depotAddress = getSelectedDepotAddress();
    if (!depotAddress) {
        setStatus('Debes indicar un depósito o una dirección personalizada.', 'error');
        return;
    }

    const formData = new FormData();
    formData.append('file', fileInput.files[0]);
    formData.append('depotAddress', depotAddress);
    formData.append('carryCount', document.getElementById('carryCount')?.value || '10');
    formData.append('nhrCount', document.getElementById('nhrCount')?.value || '10');
    formData.append('nprCount', document.getElementById('nprCount')?.value || '5');

    hideErrorPanel();
    hideJobProgress();
    clearStatus();
    resetResultsView();
    setLoadingState(true);
    setStatus('Archivo recibido. Vamos a procesarlo en segundo plano y te mostraremos el avance.', 'info');

    try {
        const response = await fetch('/api/routes/upload', {
            method: 'POST',
            body: formData
        });

        if (!response.ok) {
            throw await createHttpError(response);
        }

        const job = await response.json();
        setLoadingState(false);
        renderJobProgress(job);
        setStatus('Procesamiento iniciado. Te iremos mostrando cada etapa.', 'info');
        await startPollingJob(job.jobId);
    } catch (error) {
        stopJobPolling();
        setLoadingState(false);
        renderErrorPanel(error);
    }
});

exportPdfBtn.addEventListener('click', () => exportRoutes('pdf'));
exportExcelBtn.addEventListener('click', () => exportRoutes('excel'));

setDepotMode();
setExportButtonsEnabled(false);
hideJobProgress();
