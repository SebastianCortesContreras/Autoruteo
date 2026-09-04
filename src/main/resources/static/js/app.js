let currentRoutesData = [];
let currentUnassignedCount = 0;
let currentUnassignedOrders = [];
let map = null;
let currentJobPoller = null;
let manualEditMode = false;
let selectedOrderId = null;
let pendingMovePreview = null;
let editHistory = [];
let originalEditSnapshot = null;
let draggedTableOrderId = null;
let pendingChangesExpanded = false;
const routePalette = ['#0057d9', '#e53935', '#00a86b', '#8e24aa', '#fb8c00', '#00838f', '#c2185b', '#7cb342', '#5e35b1', '#f4511e', '#00897b', '#6d4c41'];
const unassignedRouteColor = '#9e9e9e';

const windowLabelMap = {
    WINDOW_1: '08:00 - 10:59',
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
const mapEditorPanel = document.getElementById('mapEditorPanel');
const mapEditorHint = document.getElementById('mapEditorHint');
const mapContainer = document.getElementById('map');
const toggleEditModeBtn = document.getElementById('toggleEditModeBtn');
const selectedOrderInfo = document.getElementById('selectedOrderInfo');
const targetRouteSelect = document.getElementById('targetRouteSelect');
const moveToRouteBtn = document.getElementById('moveToRouteBtn');
const unassignOrderBtn = document.getElementById('unassignOrderBtn');
const moveOrderUpBtn = document.getElementById('moveOrderUpBtn');
const moveOrderDownBtn = document.getElementById('moveOrderDownBtn');
const clearSelectionBtn = document.getElementById('clearSelectionBtn');
const suggestedMovesList = document.getElementById('suggestedMovesList');
const movePreviewInfo = document.getElementById('movePreviewInfo');
const confirmMoveBtn = document.getElementById('confirmMoveBtn');
const cancelPreviewBtn = document.getElementById('cancelPreviewBtn');
const undoEditBtn = document.getElementById('undoEditBtn');
const resetEditsBtn = document.getElementById('resetEditsBtn');
const pendingChangesPanel = document.getElementById('pendingChangesPanel');
const pendingChangesDescription = document.getElementById('pendingChangesDescription');
const pendingChangesMetrics = document.getElementById('pendingChangesMetrics');
const pendingChangesSummary = document.getElementById('pendingChangesSummary');
const saveManualChangesBtn = document.getElementById('saveManualChangesBtn');
const discardChangesBtn = document.getElementById('discardChangesBtn');
const viewChangesSummaryBtn = document.getElementById('viewChangesSummaryBtn');
const routesContainer = document.getElementById('routes-container');
const routesList = document.getElementById('routes-list');
const routesSummary = document.getElementById('routes-summary');
const unassignedSidebar = document.getElementById('unassignedSidebar');
const unassignedCountChip = document.getElementById('unassignedCountChip');
const unassignedSearchInput = document.getElementById('unassignedSearchInput');
const unassignedWindowFilter = document.getElementById('unassignedWindowFilter');
const unassignedList = document.getElementById('unassignedList');
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

function getOrderLocation(order) {
    const latitude = safeNumber(order?.location?.latitude, NaN);
    const longitude = safeNumber(order?.location?.longitude, NaN);

    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
        return null;
    }

    return { latitude, longitude };
}

function getRouteDepotLocation(route) {
    const latitude = safeNumber(route?.depotLocation?.latitude, NaN);
    const longitude = safeNumber(route?.depotLocation?.longitude, NaN);

    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
        return null;
    }

    return { latitude, longitude };
}

function calculateDistanceMeters(first, second) {
    if (!first || !second) {
        return 0;
    }

    const earthRadius = 6371000;
    const dLat = ((second.latitude - first.latitude) * Math.PI) / 180;
    const dLon = ((second.longitude - first.longitude) * Math.PI) / 180;
    const a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
        + Math.cos((first.latitude * Math.PI) / 180) * Math.cos((second.latitude * Math.PI) / 180)
        * Math.sin(dLon / 2) * Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return Math.round(earthRadius * c);
}

function getReadableTextColor(backgroundColor) {
    const color = safeText(backgroundColor, '').replace('#', '');
    if (!/^[0-9a-fA-F]{6}$/.test(color)) {
        return '#1b1f24';
    }

    const red = Number.parseInt(color.slice(0, 2), 16);
    const green = Number.parseInt(color.slice(2, 4), 16);
    const blue = Number.parseInt(color.slice(4, 6), 16);
    const luminance = (0.299 * red) + (0.587 * green) + (0.114 * blue);
    return luminance > 160 ? '#1b1f24' : '#ffffff';
}

function clampColorChannel(value) {
    return Math.max(0, Math.min(255, Math.round(value)));
}

function hslToHex(hue, saturation, lightness) {
    const normalizedHue = ((hue % 360) + 360) % 360;
    const s = Math.max(0, Math.min(100, saturation)) / 100;
    const l = Math.max(0, Math.min(100, lightness)) / 100;

    const chroma = (1 - Math.abs((2 * l) - 1)) * s;
    const segment = normalizedHue / 60;
    const x = chroma * (1 - Math.abs((segment % 2) - 1));

    let red = 0;
    let green = 0;
    let blue = 0;

    if (segment >= 0 && segment < 1) {
        red = chroma;
        green = x;
    } else if (segment >= 1 && segment < 2) {
        red = x;
        green = chroma;
    } else if (segment >= 2 && segment < 3) {
        green = chroma;
        blue = x;
    } else if (segment >= 3 && segment < 4) {
        green = x;
        blue = chroma;
    } else if (segment >= 4 && segment < 5) {
        red = x;
        blue = chroma;
    } else {
        red = chroma;
        blue = x;
    }

    const match = l - (chroma / 2);
    const toHex = channel => clampColorChannel((channel + match) * 255).toString(16).padStart(2, '0');

    return `#${toHex(red)}${toHex(green)}${toHex(blue)}`;
}

function generateDistinctRouteColor(index, totalRoutes = 1) {
    if (index < routePalette.length) {
        return routePalette[index];
    }

    const safeTotalRoutes = Math.max(totalRoutes, routePalette.length + 1, 1);
    const hueStep = 360 / safeTotalRoutes;
    const hueOffset = 12;
    const lightnessBands = [46, 56, 40, 62];
    const hue = (index * hueStep + hueOffset) % 360;
    const saturation = 84;
    const lightness = lightnessBands[index % lightnessBands.length];

    return hslToHex(hue, saturation, lightness);
}

function resolveRouteColor(route, index = 0, totalRoutes = 1) {
    const existingColor = safeText(route?.routeColor, '');
    return existingColor || generateDistinctRouteColor(index, totalRoutes);
}

function cloneSerializable(value) {
    return JSON.parse(JSON.stringify(value ?? null));
}

function snapshotEditableState() {
    return {
        routes: cloneSerializable(currentRoutesData) || [],
        unassignedCount: currentUnassignedCount,
        unassignedOrders: cloneSerializable(currentUnassignedOrders) || [],
        selectedOrderId: safeText(selectedOrderId, ''),
        pendingMovePreview: pendingMovePreview ? { ...pendingMovePreview } : null
    };
}

function rememberOriginalEditState() {
    originalEditSnapshot = snapshotEditableState();
    editHistory = [];
    pendingChangesExpanded = false;
}

function pushEditHistory(label, details = {}) {
    editHistory.push({
        label,
        details,
        snapshot: snapshotEditableState()
    });

    if (editHistory.length > 40) {
        editHistory.shift();
    }
}

function restoreEditableState(snapshot, statusMessage = '') {
    currentRoutesData = cloneSerializable(snapshot?.routes) || [];
    currentUnassignedCount = safeNumber(snapshot?.unassignedCount, 0);
    currentUnassignedOrders = cloneSerializable(snapshot?.unassignedOrders) || [];
    selectedOrderId = safeText(snapshot?.selectedOrderId, '');
    pendingMovePreview = snapshot?.pendingMovePreview ? { ...snapshot.pendingMovePreview } : null;
    normalizeEditedRoutes();
    renderRoutesView(true);
    if (statusMessage) {
        setStatus(statusMessage, 'info');
    }
}

function undoLastEdit() {
    const previous = editHistory.pop();
    if (!previous) {
        return;
    }

    restoreEditableState(previous.snapshot, `Se deshizo: ${previous.label}.`);
}

function resetAllEdits() {
    if (!originalEditSnapshot) {
        return;
    }

    restoreEditableState(originalEditSnapshot, 'Se restauró el resultado original de la optimización.');
    editHistory = [];
    pendingChangesExpanded = false;
}

function getVehicleCapacityKg(route) {
    const type = safeText(route?.vehicleType, '').toUpperCase();
    if (type.includes('NPR')) {
        return 5000;
    }
    if (type.includes('NHR')) {
        return 3000;
    }
    if (type.includes('CARRY')) {
        return 750;
    }
    if (type.includes('HIPOTÉTICO') || type.includes('HYPOTHETICAL')) {
        return 10000;
    }
    return 0;
}

function formatDistanceKm(meters) {
    return `${(safeNumber(meters, 0) / 1000).toFixed(2)} km`;
}

function formatDeltaKm(meters) {
    const distanceKm = (safeNumber(meters, 0) / 1000).toFixed(2);
    return `${meters >= 0 ? '+' : ''}${distanceKm} km`;
}

function calculateInsertionPreview(order, route, insertIndex, excludeOrderId = '') {
    const orderLocation = getOrderLocation(order);
    const filteredOrders = (route?.orders || []).filter(item => safeText(item?.id, '') !== safeText(excludeOrderId, ''));
    const depotLocation = getRouteDepotLocation(route);

    if (!orderLocation) {
        return null;
    }

    const previousLocation = insertIndex === 0 ? depotLocation : getOrderLocation(filteredOrders[insertIndex - 1]);
    const nextLocation = insertIndex === filteredOrders.length ? depotLocation : getOrderLocation(filteredOrders[insertIndex]);
    const baseDistance = calculateDistanceMeters(previousLocation, nextLocation);
    const addedDistance = calculateDistanceMeters(previousLocation, orderLocation) + calculateDistanceMeters(orderLocation, nextLocation);
    const deltaMeters = addedDistance - baseDistance;
    const alreadyInRoute = (route?.orders || []).some(item => safeText(item?.id, '') === safeText(excludeOrderId, ''));
    const projectedLoad = Math.round(safeNumber(route?.totalLoad, 0) + (alreadyInRoute ? 0 : safeNumber(order?.demand, 0)));
    const capacityKg = getVehicleCapacityKg(route);

    return {
        routeId: safeText(route?.vehicleId, ''),
        displayName: safeText(route?.displayName, safeText(route?.vehicleId, 'Ruta')),
        insertIndex,
        deltaMeters,
        projectedLoad,
        projectedOrders: filteredOrders.length + 1,
        capacityKg,
        overCapacity: capacityKg > 0 && projectedLoad > capacityKg,
        routeColor: safeText(route?.routeColor, '')
    };
}

function getInsertionOptionsForRoute(order, route, excludeOrderId = '') {
    const filteredOrders = (route?.orders || []).filter(item => safeText(item?.id, '') !== safeText(excludeOrderId, ''));
    const options = [];

    for (let insertIndex = 0; insertIndex <= filteredOrders.length; insertIndex++) {
        const option = calculateInsertionPreview(order, route, insertIndex, excludeOrderId);
        if (option) {
            options.push(option);
        }
    }

    return options.sort((left, right) => {
        if (left.overCapacity !== right.overCapacity) {
            return left.overCapacity ? 1 : -1;
        }
        return left.deltaMeters - right.deltaMeters;
    });
}

function buildMovePreview(orderId, routeId, preferredInsertIndex = null) {
    const position = findOrderPosition(orderId);
    const route = currentRoutesData.find(item => safeText(item?.vehicleId, '') === safeText(routeId, ''));
    if (!position || !route) {
        return null;
    }

    const options = getInsertionOptionsForRoute(position.order, route, orderId);
    const preview = preferredInsertIndex === null
        ? options[0]
        : options.find(option => option.insertIndex === Number(preferredInsertIndex)) || options[0];

    if (!preview) {
        return null;
    }

    return {
        orderId: safeText(position.order?.id, ''),
        sourceType: position.sourceType,
        sourceRouteId: safeText(position.route?.vehicleId, ''),
        sourceOrderIndex: position.orderIndex,
        targetRouteId: preview.routeId,
        targetRouteName: preview.displayName,
        targetInsertIndex: preview.insertIndex,
        deltaMeters: preview.deltaMeters,
        projectedLoad: preview.projectedLoad,
        projectedOrders: preview.projectedOrders,
        capacityKg: preview.capacityKg,
        overCapacity: preview.overCapacity,
        routeColor: preview.routeColor
    };
}

function getRouteSuggestionsForOrder(orderId, limit = 3) {
    const position = findOrderPosition(orderId);
    if (!position) {
        return [];
    }

    return currentRoutesData
        .filter(route => safeText(route?.vehicleId, '') !== safeText(position.route?.vehicleId, ''))
        .map(route => getInsertionOptionsForRoute(position.order, route, orderId)[0])
        .filter(Boolean)
        .sort((left, right) => {
            if (left.overCapacity !== right.overCapacity) {
                return left.overCapacity ? 1 : -1;
            }
            return left.deltaMeters - right.deltaMeters;
        })
        .slice(0, limit);
}

function getOverCapacityRoutes() {
    return currentRoutesData.filter(route => {
        const capacityKg = getVehicleCapacityKg(route);
        return capacityKg > 0 && safeNumber(route?.totalLoad, 0) > capacityKg;
    });
}

function buildChangeTitle(change) {
    const details = change?.details || {};
    if (details.type === 'move') {
        return `${safeText(details.orderId, 'Pedido')} → ${safeText(details.targetRouteName, 'Ruta destino')}`;
    }
    if (details.type === 'unassign') {
        return `${safeText(details.orderId, 'Pedido')} → Sin asignar`;
    }
    if (details.type === 'reorder') {
        return `${safeText(details.orderId, 'Pedido')} reordenado en ${safeText(details.routeName, 'ruta')}`;
    }
    return safeText(change?.label, 'Cambio manual');
}

function buildChangeMeta(change) {
    const details = change?.details || {};
    if (details.type === 'move') {
        return `Desde ${safeText(details.sourceLabel, 'origen')} a posición ${safeNumber(details.targetPosition, 1)}.`;
    }
    if (details.type === 'unassign') {
        return `Se retiró de ${safeText(details.sourceLabel, 'su ruta original')} y quedó en la bandeja de no asignados.`;
    }
    if (details.type === 'reorder') {
        return `Pasó de la posición ${safeNumber(details.fromPosition, 1)} a ${safeNumber(details.toPosition, 1)}.`;
    }
    return 'Cambio aplicado manualmente en la planeación.';
}

function describeOrderSource(positionLike) {
    if (!positionLike) {
        return 'origen desconocido';
    }

    return positionLike.sourceType === 'route'
        ? safeText(positionLike.route?.displayName, safeText(positionLike.route?.vehicleId, 'Ruta'))
        : 'Sin asignar';
}

function summarizePendingChanges() {
    const affectedRoutes = new Set();
    const changedOrders = new Set();
    let totalDeltaMeters = 0;

    editHistory.forEach(change => {
        const details = change?.details || {};
        if (details.sourceRouteId) {
            affectedRoutes.add(details.sourceRouteId);
        }
        if (details.targetRouteId) {
            affectedRoutes.add(details.targetRouteId);
        }
        if (details.routeId) {
            affectedRoutes.add(details.routeId);
        }
        if (details.orderId) {
            changedOrders.add(details.orderId);
        }
        if (Number.isFinite(Number(details.deltaMeters))) {
            totalDeltaMeters += Number(details.deltaMeters);
        }
    });

    return {
        changeCount: editHistory.length,
        affectedRoutes: affectedRoutes.size,
        changedOrders: changedOrders.size,
        totalDeltaMeters,
        overCapacityRoutes: getOverCapacityRoutes()
    };
}

function renderPendingChangesPanel() {
    if (!pendingChangesPanel || !pendingChangesDescription || !pendingChangesMetrics || !pendingChangesSummary) {
        return;
    }

    const summary = summarizePendingChanges();
    const hasChanges = summary.changeCount > 0;
    pendingChangesPanel.className = `pending-changes-panel${hasChanges ? ' is-dirty' : ''}`;
    pendingChangesDescription.textContent = hasChanges
        ? `${summary.changeCount} cambios listos para revisar. ${summary.changedOrders} pedidos y ${summary.affectedRoutes} rutas quedaron impactados.`
        : 'Todavía no hay cambios manuales acumulados.';

    pendingChangesMetrics.innerHTML = `
        <div class="summary-chip">
            <span class="material-symbols-outlined">edit_note</span>
            <span><span class="summary-label">Cambios</span> ${summary.changeCount}</span>
        </div>
        <div class="summary-chip">
            <span class="material-symbols-outlined">alt_route</span>
            <span><span class="summary-label">Rutas afectadas</span> ${summary.affectedRoutes}</span>
        </div>
        <div class="summary-chip">
            <span class="material-symbols-outlined">route</span>
            <span><span class="summary-label">Δ km estimado</span> ${formatDistanceKm(Math.abs(summary.totalDeltaMeters))}</span>
        </div>
        <div class="summary-chip">
            <span class="material-symbols-outlined">warning</span>
            <span><span class="summary-label">Riesgos</span> ${summary.overCapacityRoutes.length}</span>
        </div>
    `;

    if (!hasChanges) {
        pendingChangesSummary.innerHTML = '<div class="editor-empty-state">Confirma movimientos o reordenamientos para ver el resumen acumulado.</div>';
        viewChangesSummaryBtn.innerHTML = '<span class="material-symbols-outlined">notes</span>Ver resumen';
        return;
    }

    const items = pendingChangesExpanded ? editHistory : editHistory.slice(-3);
    const hiddenCount = Math.max(0, editHistory.length - items.length);
    const overCapacityNote = summary.overCapacityRoutes.length > 0
        ? `<div class="pending-change-item">
                <div class="pending-change-item-head">
                    <p class="pending-change-title">Rutas con riesgo de capacidad</p>
                </div>
                <p class="pending-change-meta">${summary.overCapacityRoutes.map(route => safeText(route.displayName, route.vehicleId)).join(', ')}</p>
           </div>`
        : '';

    pendingChangesSummary.innerHTML = `
        ${items.map(change => {
            const details = change?.details || {};
            const chips = [];
            if (Number.isFinite(Number(details.deltaMeters))) {
                chips.push(`<span class="pending-change-tag"><span class="material-symbols-outlined">route</span>${escapeHtml(formatDeltaKm(Number(details.deltaMeters)))}</span>`);
            }
            if (details.targetRouteName) {
                chips.push(`<span class="pending-change-tag"><span class="material-symbols-outlined">local_shipping</span>${escapeHtml(safeText(details.targetRouteName, 'Ruta'))}</span>`);
            } else if (details.routeName) {
                chips.push(`<span class="pending-change-tag"><span class="material-symbols-outlined">local_shipping</span>${escapeHtml(safeText(details.routeName, 'Ruta'))}</span>`);
            }
            if (details.overCapacity) {
                chips.push('<span class="pending-change-tag is-warning"><span class="material-symbols-outlined">warning</span>Supera capacidad</span>');
            }

            return `
                <div class="pending-change-item">
                    <div class="pending-change-item-head">
                        <p class="pending-change-title">${escapeHtml(buildChangeTitle(change))}</p>
                        <span class="pending-change-tag">${escapeHtml(safeText(change.label, 'Cambio'))}</span>
                    </div>
                    <p class="pending-change-meta">${escapeHtml(buildChangeMeta(change))}</p>
                    ${chips.length > 0 ? `<div class="pending-change-tags">${chips.join('')}</div>` : ''}
                </div>
            `;
        }).join('')}
        ${hiddenCount > 0 ? `<div class="editor-empty-state">Hay ${hiddenCount} cambios anteriores ocultos. Usa "Ver resumen" para mostrarlos.</div>` : ''}
        ${overCapacityNote}
    `;

    viewChangesSummaryBtn.innerHTML = pendingChangesExpanded
        ? '<span class="material-symbols-outlined">expand_less</span>Ocultar detalle'
        : '<span class="material-symbols-outlined">notes</span>Ver resumen';
}

function saveManualChanges() {
    if (editHistory.length === 0) {
        return;
    }

    originalEditSnapshot = snapshotEditableState();
    editHistory = [];
    pendingChangesExpanded = false;
    renderRoutesView(true);
    setStatus('Los cambios manuales quedaron guardados como nueva base de trabajo.', 'success');
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

function applyRouteColors(routes) {
    const totalRoutes = routes.length;

    routes.forEach((route, index) => {
        if (!safeText(route.routeColor, '')) {
            route.routeColor = generateDistinctRouteColor(index, totalRoutes);
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
    currentUnassignedCount = 0;
    currentUnassignedOrders = [];
    manualEditMode = false;
    selectedOrderId = null;
    pendingMovePreview = null;
    editHistory = [];
    originalEditSnapshot = null;
    pendingChangesExpanded = false;
    if (unassignedSearchInput) {
        unassignedSearchInput.value = '';
    }
    if (unassignedWindowFilter) {
        unassignedWindowFilter.value = 'ALL';
    }
    mapContainer.style.display = 'none';
    mapEditorPanel.className = 'card section-card map-editor-panel hidden-section';
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

function getFilteredUnassignedOrders() {
    const query = safeText(unassignedSearchInput?.value, '').toLowerCase();
    const windowValue = safeText(unassignedWindowFilter?.value, 'ALL');

    return currentUnassignedOrders.filter(order => {
        const matchesWindow = windowValue === 'ALL' || safeText(order?.deliveryWindow, '') === windowValue;
        const haystack = [
            safeText(order?.id, ''),
            safeText(order?.name, ''),
            safeText(order?.address, '')
        ].join(' ').toLowerCase();
        const matchesQuery = !query || haystack.includes(query);
        return matchesWindow && matchesQuery;
    });
}

function focusOrderOnMap(orderId) {
    const position = findOrderPosition(orderId);
    const location = getOrderLocation(position?.order);
    if (!map || !location) {
        return;
    }

    map.setView([location.latitude, location.longitude], Math.max(map.getZoom(), 14));
}

function renderUnassignedSidebar() {
    if (!unassignedSidebar || !unassignedList || !unassignedCountChip) {
        return;
    }

    if (currentUnassignedOrders.length === 0) {
        unassignedSidebar.classList.add('hidden-section');
        unassignedCountChip.textContent = '0';
        unassignedList.innerHTML = '';
        return;
    }

    unassignedSidebar.classList.remove('hidden-section');
    unassignedCountChip.textContent = String(currentUnassignedOrders.length);

    const filteredOrders = getFilteredUnassignedOrders();
    if (filteredOrders.length === 0) {
        unassignedList.innerHTML = '<div class="unassigned-list-empty">No hay pedidos que coincidan con el filtro.</div>';
        return;
    }

    unassignedList.innerHTML = filteredOrders.map(order => {
        const orderId = safeText(order?.id, '');
        const isSelected = selectedOrderId === orderId ? ' is-selected' : '';
        return `
            <button type="button" class="unassigned-item${isSelected}" data-order-id="${escapeHtml(orderId)}">
                <div class="unassigned-item-head">
                    <span class="unassigned-item-id">${escapeHtml(orderId)}</span>
                    <span class="order-sequence-badge" style="background-color: ${escapeHtml(unassignedRouteColor)};">${escapeHtml(formatWindowLabel(order?.deliveryWindow))}</span>
                </div>
                <p class="unassigned-item-meta">${escapeHtml(safeText(order?.name, 'Sin cliente'))} · ${Math.round(safeNumber(order?.demand, 0))} kg</p>
                <p class="unassigned-item-address">${escapeHtml(safeText(order?.address, 'Sin dirección'))}</p>
            </button>
        `;
    }).join('');
}

function findOrderPosition(orderId) {
    if (!orderId) {
        return null;
    }

    for (let routeIndex = 0; routeIndex < currentRoutesData.length; routeIndex++) {
        const orders = currentRoutesData[routeIndex].orders || [];
        const orderIndex = orders.findIndex(order => safeText(order?.id, '') === orderId);
        if (orderIndex !== -1) {
            return {
                sourceType: 'route',
                routeIndex,
                orderIndex,
                route: currentRoutesData[routeIndex],
                order: orders[orderIndex]
            };
        }
    }

    const unassignedOrderIndex = currentUnassignedOrders.findIndex(order => safeText(order?.id, '') === orderId);
    if (unassignedOrderIndex !== -1) {
        return {
            sourceType: 'unassigned',
            routeIndex: -1,
            orderIndex: unassignedOrderIndex,
            route: null,
            order: currentUnassignedOrders[unassignedOrderIndex]
        };
    }

    return null;
}

function renderSuggestedMoves() {
    if (!suggestedMovesList) {
        return;
    }

    if (!manualEditMode) {
        suggestedMovesList.innerHTML = '<div class="editor-empty-state">Activa el modo edición para ver sugerencias de movimiento.</div>';
        return;
    }

    const selectedPosition = findOrderPosition(selectedOrderId);
    if (!selectedPosition) {
        suggestedMovesList.innerHTML = '<div class="editor-empty-state">Selecciona un pedido en el mapa, la tabla o la lista lateral.</div>';
        return;
    }

    const suggestions = getRouteSuggestionsForOrder(selectedOrderId);
    if (suggestions.length === 0) {
        suggestedMovesList.innerHTML = '<div class="editor-empty-state">No hay otras rutas disponibles para sugerir este movimiento.</div>';
        return;
    }

    suggestedMovesList.innerHTML = suggestions.map((suggestion, index) => {
        const isSelected = pendingMovePreview
            && safeText(pendingMovePreview.targetRouteId, '') === safeText(suggestion.routeId, '')
            && Number(pendingMovePreview.targetInsertIndex) === Number(suggestion.insertIndex);

        return `
            <button type="button" class="suggested-move-btn${isSelected ? ' is-selected' : ''}"
                data-suggest-route-id="${escapeHtml(suggestion.routeId)}"
                data-suggest-insert-index="${escapeHtml(String(suggestion.insertIndex))}">
                <div class="suggested-move-route">
                    <span>${index + 1}. ${escapeHtml(suggestion.displayName)}</span>
                    <span class="route-color-dot" style="background-color: ${escapeHtml(safeText(suggestion.routeColor, '#1565c0'))};"></span>
                </div>
                <p class="suggested-move-meta">Posición sugerida: ${suggestion.insertIndex + 1} · Impacto: ${escapeHtml(formatDeltaKm(suggestion.deltaMeters))}</p>
                <p class="suggested-move-meta">Carga proyectada: ${suggestion.projectedLoad} kg${suggestion.capacityKg ? ` / ${suggestion.capacityKg} kg` : ''}${suggestion.overCapacity ? ' · supera capacidad' : ''}</p>
            </button>
        `;
    }).join('');
}

function renderMovePreviewPanel() {
    if (!movePreviewInfo) {
        return;
    }

    if (!pendingMovePreview) {
        movePreviewInfo.className = 'move-preview-panel is-empty';
        movePreviewInfo.innerHTML = '<div class="editor-empty-state">Aún no hay un movimiento en vista previa.</div>';
        return;
    }

    const selectedPosition = findOrderPosition(pendingMovePreview.orderId);
    const sourceLabel = selectedPosition?.sourceType === 'route'
        ? safeText(selectedPosition?.route?.displayName, 'Ruta actual')
        : 'Sin asignar';

    movePreviewInfo.className = 'move-preview-panel is-ready';
    movePreviewInfo.innerHTML = `
        <h4 class="preview-title">${escapeHtml(safeText(selectedPosition?.order?.id, 'Pedido'))} → ${escapeHtml(safeText(pendingMovePreview.targetRouteName, 'Ruta destino'))}</h4>
        <p class="preview-description">Desde ${escapeHtml(sourceLabel)} hacia la posición ${pendingMovePreview.targetInsertIndex + 1}. Revisa el impacto antes de confirmar.</p>
        <div class="preview-chip-row">
            <span class="preview-chip"><span class="material-symbols-outlined">route</span>${escapeHtml(formatDeltaKm(pendingMovePreview.deltaMeters))}</span>
            <span class="preview-chip"><span class="material-symbols-outlined">inventory_2</span>${pendingMovePreview.projectedLoad} kg${pendingMovePreview.capacityKg ? ` / ${pendingMovePreview.capacityKg} kg` : ''}</span>
            <span class="preview-chip"><span class="material-symbols-outlined">format_list_numbered</span>${pendingMovePreview.projectedOrders} pedidos</span>
        </div>
        <p class="preview-description">${pendingMovePreview.overCapacity ? 'La ruta superaría la capacidad estimada del vehículo.' : 'La ruta sigue dentro de la capacidad estimada del vehículo.'}</p>
    `;
}

function createRouteMarkerIcon(color, label, isSelected, isUnassigned = false) {
    return L.divIcon({
        className: '',
        html: `<div class="route-map-marker${isSelected ? ' is-selected' : ''}${isUnassigned ? ' is-unassigned' : ''}" style="background-color: ${escapeHtml(color)};">${escapeHtml(label)}</div>`,
        iconSize: [28, 28],
        iconAnchor: [14, 14],
        popupAnchor: [0, -14]
    });
}

function recalculateRouteMetrics(route) {
    const depotLocation = getRouteDepotLocation(route);
    let previousLocation = depotLocation;
    let accumulatedDistance = 0;
    let totalDistance = 0;
    let totalLoad = 0;

    (route.orders || []).forEach(order => {
        const orderLocation = getOrderLocation(order);
        if (previousLocation && orderLocation) {
            const legDistance = calculateDistanceMeters(previousLocation, orderLocation);
            accumulatedDistance += legDistance;
            totalDistance += legDistance;
            previousLocation = orderLocation;
        } else if (orderLocation) {
            previousLocation = orderLocation;
        }

        totalLoad += safeNumber(order?.demand, 0);
        order.accumulatedDistanceKm = `${(accumulatedDistance / 1000).toFixed(2)} km`;
    });

    if ((route.orders || []).length > 0 && previousLocation && depotLocation) {
        totalDistance += calculateDistanceMeters(previousLocation, depotLocation);
    }

    route.totalLoad = Math.round(totalLoad);
    route.totalDistance = totalDistance;
    route.totalDistanceKm = `${(totalDistance / 1000).toFixed(2)} km`;
}

function normalizeEditedRoutes() {
    currentRoutesData = currentRoutesData.filter(route => (route.orders || []).length > 0);
    currentRoutesData.forEach(recalculateRouteMetrics);
    applyRouteDisplayNames(currentRoutesData);
    applyRouteColors(currentRoutesData);
    currentUnassignedCount = Math.max(currentUnassignedCount, currentUnassignedOrders.length);

    if (selectedOrderId && !findOrderPosition(selectedOrderId)) {
        selectedOrderId = null;
    }

    if (pendingMovePreview) {
        const previewOrder = findOrderPosition(pendingMovePreview.orderId);
        const previewRoute = currentRoutesData.find(route => safeText(route.vehicleId, '') === safeText(pendingMovePreview.targetRouteId, ''));
        if (!previewOrder || !previewRoute) {
            pendingMovePreview = null;
        }
    }
}

function buildTargetRouteOptions(selectedPosition, preferredRouteId = '') {
    if (!targetRouteSelect) {
        return;
    }

    const currentRouteId = selectedPosition?.sourceType === 'route' ? selectedPosition?.route?.vehicleId || '' : '';
    const retainedRouteId = safeText(preferredRouteId, '');
    const availableRoutes = currentRoutesData
        .filter(route => safeText(route.vehicleId, '') !== currentRouteId);
    const options = availableRoutes
        .map((route, index) => {
            const routeId = safeText(route.vehicleId, '');
            const routeColor = resolveRouteColor(route, index, availableRoutes.length || 1);
            const routeTextColor = getReadableTextColor(routeColor);
            const label = `${safeText(route.displayName, routeId)} (${(route.orders || []).length} pedidos)`;
            const isSelected = routeId === retainedRouteId ? ' selected' : '';
            return `<option value="${escapeHtml(routeId)}" style="background-color: ${escapeHtml(routeColor)}; color: ${escapeHtml(routeTextColor)};"${isSelected}>${escapeHtml(label)}</option>`;
        });

    targetRouteSelect.innerHTML = `
        <option value="">Selecciona una ruta destino</option>
        ${options.join('')}
    `;
}

function updateTargetRouteSelectAppearance() {
    if (!targetRouteSelect) {
        return;
    }

    const selectedRoute = currentRoutesData.find(route => safeText(route.vehicleId, '') === safeText(targetRouteSelect.value, ''));
    const routeColor = safeText(selectedRoute?.routeColor, '');
    const textColor = getReadableTextColor(routeColor);

    if (!routeColor || targetRouteSelect.disabled) {
        targetRouteSelect.style.backgroundColor = '#ffffff';
        targetRouteSelect.style.color = '';
        targetRouteSelect.style.borderColor = '';
        targetRouteSelect.style.fontWeight = '500';
        return;
    }

    targetRouteSelect.style.backgroundColor = routeColor;
    targetRouteSelect.style.color = textColor;
    targetRouteSelect.style.borderColor = routeColor;
    targetRouteSelect.style.fontWeight = '700';
}

function refreshMapEditor() {
    if (!mapEditorPanel) {
        return;
    }

    const hasOrders = currentRoutesData.length > 0 || currentUnassignedOrders.length > 0;
    mapEditorPanel.className = hasOrders
        ? `card section-card map-editor-panel${manualEditMode ? ' is-editing' : ''}`
        : 'card section-card map-editor-panel hidden-section';

    if (!hasOrders) {
        return;
    }

    mapEditorHint.textContent = manualEditMode
        ? 'Haz click en un pedido para ver sugerencias, previsualizar el cambio o arrástralo para proponer una nueva posición.'
        : 'Activa el modo edición para mover pedidos entre rutas, probar alternativas y reordenar entregas.';
    toggleEditModeBtn.innerHTML = manualEditMode
        ? '<span class="material-symbols-outlined">edit_off</span>Desactivar edición manual'
        : '<span class="material-symbols-outlined">edit_location_alt</span>Activar edición manual';

    const selectedPosition = findOrderPosition(selectedOrderId);
    if (pendingMovePreview && safeText(pendingMovePreview.orderId, '') !== safeText(selectedPosition?.order?.id, '')) {
        pendingMovePreview = null;
    }

    buildTargetRouteOptions(selectedPosition, pendingMovePreview?.targetRouteId || targetRouteSelect.value);
    updateTargetRouteSelectAppearance();

    if (!manualEditMode) {
        selectedOrderInfo.textContent = 'Activa el modo edición y selecciona un pedido en el mapa, la tabla o la lista lateral para empezar.';
    } else if (!selectedPosition) {
        selectedOrderInfo.textContent = 'Selecciona un pedido para ver sugerencias, previsualizarlo, desasignarlo o reordenarlo con arrastre.';
    } else if (selectedPosition.sourceType === 'unassigned') {
        selectedOrderInfo.textContent = `${safeText(selectedPosition.order?.id)} · ${safeText(selectedPosition.order?.name)} · Sin asignar`;
    } else {
        selectedOrderInfo.textContent = `${safeText(selectedPosition.order?.id)} · ${safeText(selectedPosition.order?.name)} · ${safeText(selectedPosition.route?.displayName)} · posición ${selectedPosition.orderIndex + 1} de ${(selectedPosition.route?.orders || []).length}`;
    }

    const canEditSelection = manualEditMode && Boolean(selectedPosition);
    const availableTargetRoutes = currentRoutesData.filter(route =>
        safeText(route.vehicleId, '') !== safeText(selectedPosition?.route?.vehicleId, '')).length;
    const routeOrders = selectedPosition?.route?.orders || [];
    targetRouteSelect.disabled = !canEditSelection || availableTargetRoutes === 0;
    moveToRouteBtn.disabled = !canEditSelection || availableTargetRoutes === 0 || !safeText(targetRouteSelect.value, '');
    unassignOrderBtn.disabled = !canEditSelection || selectedPosition?.sourceType === 'unassigned';
    moveOrderUpBtn.disabled = !canEditSelection || selectedPosition?.sourceType !== 'route' || selectedPosition.orderIndex === 0;
    moveOrderDownBtn.disabled = !canEditSelection || selectedPosition?.sourceType !== 'route' || selectedPosition.orderIndex === routeOrders.length - 1;
    clearSelectionBtn.disabled = !manualEditMode || !selectedPosition;
    confirmMoveBtn.disabled = !manualEditMode || !pendingMovePreview;
    cancelPreviewBtn.disabled = !manualEditMode || !pendingMovePreview;
    undoEditBtn.disabled = editHistory.length === 0;
    resetEditsBtn.disabled = !originalEditSnapshot || editHistory.length === 0;
    saveManualChangesBtn.disabled = editHistory.length === 0;
    discardChangesBtn.disabled = editHistory.length === 0;
    viewChangesSummaryBtn.disabled = editHistory.length === 0;
    renderSuggestedMoves();
    renderMovePreviewPanel();
    renderPendingChangesPanel();
}

function renderRoutesView(preserveMapViewport = false) {
    refreshMapEditor();
    renderUnassignedSidebar();

    if (currentRoutesData.length > 0 || currentUnassignedOrders.length > 0) {
        mapContainer.style.display = 'block';
        initMap(currentRoutesData, preserveMapViewport);
        displayRoutesTable(currentRoutesData, currentRoutesData.length, currentUnassignedCount);
        setExportButtonsEnabled(currentRoutesData.length > 0);
        return;
    }

    mapContainer.style.display = 'none';
    routesContainer.classList.remove('hidden-section');
    routesSummary.innerHTML = '';
    routesList.innerHTML = createEmptyState('El proceso terminó, pero no se generaron rutas para mostrar.');
    setExportButtonsEnabled(false);
}

function extractOrder(orderId) {
    const sourcePosition = findOrderPosition(orderId);
    if (!sourcePosition) {
        return null;
    }

    const extractedOrder = sourcePosition.sourceType === 'route'
        ? sourcePosition.route.orders.splice(sourcePosition.orderIndex, 1)[0]
        : currentUnassignedOrders.splice(sourcePosition.orderIndex, 1)[0];
    return {
        order: extractedOrder,
        sourceType: sourcePosition.sourceType,
        sourceRouteId: safeText(sourcePosition.route?.vehicleId, ''),
        sourceOrderIndex: sourcePosition.orderIndex
    };
}

function insertOrderIntoUnassigned(order, targetIndex = currentUnassignedOrders.length) {
    if (!order) {
        return false;
    }

    order.accumulatedDistanceKm = '-';
    const normalizedIndex = Math.max(0, Math.min(targetIndex, currentUnassignedOrders.length));
    currentUnassignedOrders.splice(normalizedIndex, 0, order);
    return true;
}

function insertOrderIntoRoute(order, routeId, targetIndex) {
    const route = currentRoutesData.find(item => safeText(item.vehicleId, '') === routeId);
    if (!route) {
        return false;
    }

    const orders = route.orders || [];
    const normalizedIndex = Math.max(0, Math.min(targetIndex, orders.length));
    orders.splice(normalizedIndex, 0, order);
    route.orders = orders;
    return true;
}

function moveSelectedOrderToRoute(routeId) {
    if (!selectedOrderId || !safeText(routeId, '')) {
        return;
    }

    const preview = buildMovePreview(selectedOrderId, routeId);
    if (!preview) {
        return;
    }

    pendingMovePreview = preview;
    renderRoutesView(true);
    setStatus('Vista previa lista. Revisa el impacto y confirma el cambio.', 'info');
}

function confirmPendingMove() {
    if (!pendingMovePreview) {
        return;
    }

    const snapshotBeforeEdit = snapshotEditableState();
    const extracted = extractOrder(pendingMovePreview.orderId);
    if (!extracted) {
        return;
    }

    let targetIndex = pendingMovePreview.targetInsertIndex;
    if (extracted.sourceType === 'route'
        && safeText(extracted.sourceRouteId, '') === safeText(pendingMovePreview.targetRouteId, '')
        && extracted.sourceOrderIndex < targetIndex) {
        targetIndex -= 1;
    }

    pushEditHistory('mover pedido', {
        type: 'move',
        orderId: safeText(extracted.order?.id, ''),
        sourceRouteId: safeText(extracted.sourceRouteId, ''),
        sourceLabel: extracted.sourceType === 'route'
            ? safeText(currentRoutesData.find(route => safeText(route.vehicleId, '') === safeText(extracted.sourceRouteId, ''))?.displayName, extracted.sourceRouteId)
            : 'Sin asignar',
        targetRouteId: safeText(pendingMovePreview.targetRouteId, ''),
        targetRouteName: safeText(pendingMovePreview.targetRouteName, ''),
        targetPosition: safeNumber(pendingMovePreview.targetInsertIndex, 0) + 1,
        deltaMeters: safeNumber(pendingMovePreview.deltaMeters, 0),
        overCapacity: Boolean(pendingMovePreview.overCapacity)
    });

    if (!insertOrderIntoRoute(extracted.order, pendingMovePreview.targetRouteId, targetIndex)) {
        restoreEditableState(snapshotBeforeEdit);
        editHistory.pop();
        return;
    }

    if (extracted.sourceType === 'unassigned') {
        currentUnassignedCount = Math.max(0, currentUnassignedCount - 1);
    }

    selectedOrderId = safeText(extracted.order?.id, '');
    pendingMovePreview = null;
    normalizeEditedRoutes();
    renderRoutesView(true);
    setStatus('Cambio confirmado. La ruta se actualizó con la vista previa elegida.', 'success');
}

function moveSelectedOrderToUnassigned() {
    if (!selectedOrderId) {
        return;
    }

    const extracted = extractOrder(selectedOrderId);
    if (!extracted || extracted.sourceType === 'unassigned') {
        return;
    }

    pushEditHistory('desasignar pedido', {
        type: 'unassign',
        orderId: safeText(extracted.order?.id, ''),
        sourceRouteId: safeText(extracted.sourceRouteId, ''),
        sourceLabel: safeText(currentRoutesData.find(route => safeText(route.vehicleId, '') === safeText(extracted.sourceRouteId, ''))?.displayName, extracted.sourceRouteId)
    });
    insertOrderIntoUnassigned(extracted.order);
    currentUnassignedCount += 1;
    pendingMovePreview = null;
    normalizeEditedRoutes();
    renderRoutesView(true);
    setStatus('Pedido desasignado manualmente y marcado en gris en el mapa.', 'success');
}

function moveSelectedOrderWithinRoute(step) {
    const position = findOrderPosition(selectedOrderId);
    if (!position || position.sourceType !== 'route') {
        return;
    }

    const targetIndex = position.orderIndex + step;
    if (targetIndex < 0 || targetIndex >= position.route.orders.length) {
        return;
    }

    pushEditHistory('ajustar orden de entrega', {
        type: 'reorder',
        orderId: safeText(position.order?.id, ''),
        routeId: safeText(position.route?.vehicleId, ''),
        routeName: safeText(position.route?.displayName, position.route?.vehicleId),
        fromPosition: position.orderIndex + 1,
        toPosition: targetIndex + 1
    });
    const routeOrders = position.route.orders;
    const [order] = routeOrders.splice(position.orderIndex, 1);
    routeOrders.splice(targetIndex, 0, order);
    pendingMovePreview = null;
    normalizeEditedRoutes();
    renderRoutesView(true);
    setStatus('Orden de entrega actualizado manualmente.', 'success');
}

function moveOrderWithinRouteToIndex(orderId, rawTargetIndex) {
    const position = findOrderPosition(orderId);
    if (!position || position.sourceType !== 'route') {
        return;
    }

    const routeOrders = position.route.orders || [];
    const normalizedTarget = Math.max(0, Math.min(Number(rawTargetIndex), routeOrders.length));
    const adjustedTarget = position.orderIndex < normalizedTarget ? normalizedTarget - 1 : normalizedTarget;

    if (adjustedTarget === position.orderIndex) {
        return;
    }

    pushEditHistory('reordenar desde la tabla', {
        type: 'reorder',
        orderId: safeText(position.order?.id, ''),
        routeId: safeText(position.route?.vehicleId, ''),
        routeName: safeText(position.route?.displayName, position.route?.vehicleId),
        fromPosition: position.orderIndex + 1,
        toPosition: Math.max(0, Math.min(adjustedTarget, routeOrders.length)) + 1
    });
    const [order] = routeOrders.splice(position.orderIndex, 1);
    routeOrders.splice(Math.max(0, Math.min(adjustedTarget, routeOrders.length)), 0, order);
    pendingMovePreview = null;
    normalizeEditedRoutes();
    renderRoutesView(true);
    setStatus('La secuencia de la ruta se actualizó desde la tabla.', 'success');
}

function findBestInsertionPoint(dropLocation, orderId = '') {
    let bestMatch = null;

    currentRoutesData.forEach(route => {
        const routeId = safeText(route.vehicleId, '');
        const depotLocation = getRouteDepotLocation(route);
        const orders = (route.orders || []).filter(order => safeText(order?.id, '') !== safeText(orderId, ''));

        for (let insertIndex = 0; insertIndex <= orders.length; insertIndex++) {
            const previousLocation = insertIndex === 0 ? depotLocation : getOrderLocation(orders[insertIndex - 1]);
            const nextLocation = insertIndex === orders.length ? depotLocation : getOrderLocation(orders[insertIndex]);
            const score = calculateDistanceMeters(dropLocation, previousLocation) + calculateDistanceMeters(dropLocation, nextLocation);

            if (!bestMatch || score < bestMatch.score) {
                bestMatch = { routeId, insertIndex, score };
            }
        }
    });

    return bestMatch;
}

function moveOrderByDrag(orderId, latLng) {
    const position = findOrderPosition(orderId);
    if (!position) {
        return;
    }

    const dropLocation = {
        latitude: safeNumber(latLng?.lat, NaN),
        longitude: safeNumber(latLng?.lng, NaN)
    };

    if (!Number.isFinite(dropLocation.latitude) || !Number.isFinite(dropLocation.longitude)) {
        renderRoutesView(true);
        return;
    }

    const bestMatch = findBestInsertionPoint(dropLocation, orderId);
    const preview = bestMatch ? buildMovePreview(orderId, bestMatch.routeId, bestMatch.insertIndex) : null;
    if (!preview) {
        renderRoutesView(true);
        return;
    }

    pendingMovePreview = preview;
    selectedOrderId = safeText(position.order?.id, '');
    renderRoutesView(true);
    setStatus('Vista previa creada desde el mapa. Confirma el cambio si te convence.', 'info');
}

function applyOptimizationResult(data) {
    currentRoutesData = Array.isArray(data?.routes) ? data.routes : [];
    currentUnassignedCount = safeNumber(data?.unassigned, 0);
    currentUnassignedOrders = [];
    manualEditMode = false;
    selectedOrderId = null;
    pendingMovePreview = null;
    applyRouteDisplayNames(currentRoutesData);
    applyRouteColors(currentRoutesData);
    rememberOriginalEditState();
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
        `Optimización completada. Vehículos usados: ${safeNumber(data?.totalVehiclesUsed, currentRoutesData.length)}. Pedidos no asignados: ${currentUnassignedCount}.`,
        'success'
    );

    if (currentRoutesData.length > 0) {
        renderRoutesView();
        mapContainer.scrollIntoView({ behavior: 'smooth' });
        return;
    }

    refreshMapEditor();
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

function initMap(routes, preserveMapViewport = false) {
    const previousCenter = preserveMapViewport && map ? map.getCenter() : null;
    const previousZoom = preserveMapViewport && map ? map.getZoom() : null;

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

    const bounds = [];

    routes.forEach((route, routeIndex) => {
        const color = resolveRouteColor(route, routeIndex, routes.length);
        const pathCoords = [];
        const isPreviewTarget = pendingMovePreview && safeText(pendingMovePreview.targetRouteId, '') === safeText(route.vehicleId, '');

        (route.orders || []).forEach((order, orderIndex) => {
            const location = getOrderLocation(order);
            if (!location) {
                return;
            }

            const latLng = [location.latitude, location.longitude];
            const orderId = safeText(order?.id, '');
            pathCoords.push(latLng);
            bounds.push(latLng);

            const marker = L.marker(latLng, {
                draggable: manualEditMode,
                icon: createRouteMarkerIcon(color, String(orderIndex + 1), selectedOrderId === orderId)
            }).addTo(map);

            marker.bindPopup(`
                <b>Pedido:</b> ${escapeHtml(safeText(order?.id))}<br>
                <b>Cliente:</b> ${escapeHtml(safeText(order?.name))}<br>
                <b>Dirección:</b> ${escapeHtml(safeText(order?.address))}<br>
                <b>Peso:</b> ${Math.round(safeNumber(order?.demand, 0))} kg<br>
                <b>Franja:</b> ${escapeHtml(formatWindowLabel(order?.deliveryWindow))}<br>
                <b>Vehículo:</b> ${escapeHtml(safeText(route?.vehicleType, 'Vehículo'))} (${escapeHtml(safeText(route?.vehicleId, 'Sin identificador'))})<br>
                <b>Orden en ruta:</b> ${orderIndex + 1}<br>
                <b>Edición:</b> ${manualEditMode ? 'Activa. Puedes hacer click o arrastrar este punto.' : 'Activa el modo edición para modificar esta ruta.'}
            `);

            marker.on('click', () => {
                if (!manualEditMode) {
                    return;
                }

                selectedOrderId = orderId;
                renderRoutesView(true);
            });

            if (manualEditMode) {
                marker.on('dragstart', () => {
                    selectedOrderId = orderId;
                    refreshMapEditor();
                });
                marker.on('dragend', event => moveOrderByDrag(orderId, event.target.getLatLng()));
            }
        });

        if (pathCoords.length > 1) {
            L.polyline(pathCoords, {
                color,
                weight: isPreviewTarget ? 7 : 4,
                opacity: isPreviewTarget ? 0.96 : 0.75
            }).addTo(map);
        }
    });

    currentUnassignedOrders.forEach((order, orderIndex) => {
        const location = getOrderLocation(order);
        if (!location) {
            return;
        }

        const latLng = [location.latitude, location.longitude];
        const orderId = safeText(order?.id, '');
        bounds.push(latLng);

        const marker = L.marker(latLng, {
            draggable: manualEditMode,
            icon: createRouteMarkerIcon(unassignedRouteColor, String(orderIndex + 1), selectedOrderId === orderId, true)
        }).addTo(map);

        marker.bindPopup(`
            <b>Pedido:</b> ${escapeHtml(safeText(order?.id))}<br>
            <b>Cliente:</b> ${escapeHtml(safeText(order?.name))}<br>
            <b>Dirección:</b> ${escapeHtml(safeText(order?.address))}<br>
            <b>Peso:</b> ${Math.round(safeNumber(order?.demand, 0))} kg<br>
            <b>Estado:</b> Sin asignar<br>
            <b>Edición:</b> ${manualEditMode ? 'Activa. Puedes hacer click o arrastrar este punto a una ruta.' : 'Activa el modo edición para asignar este pedido.'}
        `);

        marker.on('click', () => {
            if (!manualEditMode) {
                return;
            }

            selectedOrderId = orderId;
            renderRoutesView(true);
        });

        if (manualEditMode) {
            marker.on('dragstart', () => {
                selectedOrderId = orderId;
                refreshMapEditor();
            });
            marker.on('dragend', event => moveOrderByDrag(orderId, event.target.getLatLng()));
        }
    });

    if (previousCenter && Number.isFinite(previousZoom)) {
        map.setView(previousCenter, previousZoom);
    } else if (bounds.length > 0) {
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
    const totalOrders = routes.reduce((acc, route) => acc + (route.orders?.length || 0), 0) + safeNumber(unassigned, 0);

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

function buildOrderRows(orders, routeColor, routeId) {
    const color = safeText(routeColor, generateDistinctRouteColor(0, 1));
    return (orders || []).map((order, index) => `
        <tr class="${selectedOrderId === safeText(order?.id, '') ? 'is-selected ' : ''}${manualEditMode ? 'is-draggable' : ''}" draggable="${manualEditMode ? 'true' : 'false'}" data-route-id="${escapeHtml(safeText(routeId, ''))}" data-order-id="${escapeHtml(safeText(order?.id, ''))}" data-order-index="${index}">
            <td class="align-center order-drag-cell">
                <span class="material-symbols-outlined order-drag-handle">drag_indicator</span>
            </td>
            <td class="align-center">
                <span class="order-sequence-badge" style="background-color: ${escapeHtml(color)};">${index + 1}</span>
            </td>
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

    if ((!routes || routes.length === 0) && currentUnassignedOrders.length === 0) {
        routesContainer.classList.remove('hidden-section');
        routesList.innerHTML = createEmptyState('No hay rutas para mostrar todavía.');
        return;
    }

    routesContainer.classList.remove('hidden-section');
    routesSummary.innerHTML = buildRouteSummary(routes, totalVehiclesUsed, unassigned);

    routes.forEach((route, routeIndex) => {
        const card = document.createElement('div');
        card.className = `route-card${pendingMovePreview && safeText(pendingMovePreview.targetRouteId, '') === safeText(route.vehicleId, '') ? ' is-preview-target' : ''}`;
        const routeColor = resolveRouteColor(route, routeIndex, routes.length || 1);

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
                    <div class="route-title-row">
                        <span class="route-color-dot" style="background-color: ${escapeHtml(routeColor)};"></span>
                        <h3 class="route-card-title" style="color: ${escapeHtml(routeColor)};">${escapeHtml(safeText(route.displayName, 'Ruta'))}</h3>
                    </div>
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
                            <th class="align-center">Mover</th>
                            <th class="align-center">Orden</th>
                            <th>ID</th>
                            <th>Cliente</th>
                            <th>Dirección</th>
                            <th class="align-right">Km acum.</th>
                            <th class="align-right">Peso</th>
                            <th class="align-center">Franja</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${buildOrderRows(route.orders, route.routeColor, route.vehicleId)}
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
toggleEditModeBtn.addEventListener('click', () => {
    if (currentRoutesData.length === 0 && currentUnassignedOrders.length === 0) {
        return;
    }

    manualEditMode = !manualEditMode;
    if (!manualEditMode) {
        selectedOrderId = null;
        pendingMovePreview = null;
    }
    renderRoutesView(true);
});
targetRouteSelect.addEventListener('change', refreshMapEditor);
unassignedSearchInput.addEventListener('input', renderUnassignedSidebar);
unassignedWindowFilter.addEventListener('change', renderUnassignedSidebar);
suggestedMovesList.addEventListener('click', event => {
    const button = event.target.closest('[data-suggest-route-id]');
    if (!button) {
        return;
    }

    const preview = buildMovePreview(selectedOrderId, button.dataset.suggestRouteId, button.dataset.suggestInsertIndex);
    if (!preview) {
        return;
    }

    pendingMovePreview = preview;
    targetRouteSelect.value = preview.targetRouteId;
    renderRoutesView(true);
    setStatus('Se cargó la sugerencia elegida como vista previa.', 'info');
});
unassignedList.addEventListener('click', event => {
    const button = event.target.closest('[data-order-id]');
    if (!button) {
        return;
    }

    selectedOrderId = button.dataset.orderId;
    focusOrderOnMap(selectedOrderId);
    renderRoutesView(true);
});
moveToRouteBtn.addEventListener('click', () => moveSelectedOrderToRoute(targetRouteSelect.value));
confirmMoveBtn.addEventListener('click', confirmPendingMove);
cancelPreviewBtn.addEventListener('click', () => {
    pendingMovePreview = null;
    renderRoutesView(true);
    setStatus('Se canceló la vista previa del movimiento.', 'info');
});
unassignOrderBtn.addEventListener('click', moveSelectedOrderToUnassigned);
moveOrderUpBtn.addEventListener('click', () => moveSelectedOrderWithinRoute(-1));
moveOrderDownBtn.addEventListener('click', () => moveSelectedOrderWithinRoute(1));
undoEditBtn.addEventListener('click', undoLastEdit);
resetEditsBtn.addEventListener('click', resetAllEdits);
saveManualChangesBtn.addEventListener('click', saveManualChanges);
discardChangesBtn.addEventListener('click', resetAllEdits);
viewChangesSummaryBtn.addEventListener('click', () => {
    if (editHistory.length === 0) {
        return;
    }

    pendingChangesExpanded = !pendingChangesExpanded;
    renderPendingChangesPanel();
    pendingChangesPanel?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
});
routesList.addEventListener('click', event => {
    const row = event.target.closest('tr[data-order-id]');
    if (!row) {
        return;
    }

    selectedOrderId = row.dataset.orderId;
    focusOrderOnMap(selectedOrderId);
    renderRoutesView(true);
});
routesList.addEventListener('dragstart', event => {
    const row = event.target.closest('tr[data-order-id]');
    if (!manualEditMode || !row) {
        return;
    }

    draggedTableOrderId = row.dataset.orderId;
    selectedOrderId = draggedTableOrderId;
    row.classList.add('is-dragging');

    if (event.dataTransfer) {
        event.dataTransfer.effectAllowed = 'move';
        event.dataTransfer.setData('text/plain', draggedTableOrderId);
    }
});
routesList.addEventListener('dragover', event => {
    const row = event.target.closest('tr[data-order-id]');
    if (!manualEditMode || !draggedTableOrderId || !row) {
        return;
    }

    const sourcePosition = findOrderPosition(draggedTableOrderId);
    if (!sourcePosition || sourcePosition.sourceType !== 'route' || safeText(sourcePosition.route?.vehicleId, '') !== safeText(row.dataset.routeId, '')) {
        return;
    }

    event.preventDefault();
    routesList.querySelectorAll('.is-drop-target').forEach(item => item.classList.remove('is-drop-target'));
    row.classList.add('is-drop-target');
});
routesList.addEventListener('drop', event => {
    const row = event.target.closest('tr[data-order-id]');
    if (!manualEditMode || !draggedTableOrderId || !row) {
        return;
    }

    const sourcePosition = findOrderPosition(draggedTableOrderId);
    if (!sourcePosition || sourcePosition.sourceType !== 'route' || safeText(sourcePosition.route?.vehicleId, '') !== safeText(row.dataset.routeId, '')) {
        return;
    }

    event.preventDefault();
    const rowRect = row.getBoundingClientRect();
    const targetIndex = Number(row.dataset.orderIndex) + (event.clientY > (rowRect.top + rowRect.height / 2) ? 1 : 0);
    routesList.querySelectorAll('.is-drop-target').forEach(item => item.classList.remove('is-drop-target'));
    moveOrderWithinRouteToIndex(draggedTableOrderId, targetIndex);
    draggedTableOrderId = null;
});
routesList.addEventListener('dragend', () => {
    draggedTableOrderId = null;
    routesList.querySelectorAll('.is-drop-target').forEach(item => item.classList.remove('is-drop-target'));
    routesList.querySelectorAll('.is-dragging').forEach(item => item.classList.remove('is-dragging'));
});
clearSelectionBtn.addEventListener('click', () => {
    selectedOrderId = null;
    pendingMovePreview = null;
    refreshMapEditor();
    renderRoutesView(true);
});

setDepotMode();
setExportButtonsEnabled(false);
hideJobProgress();
refreshMapEditor();
