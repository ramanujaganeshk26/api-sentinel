document.addEventListener('DOMContentLoaded', () => {
    let services = [];
    let incidents = [];
    let activeFilter = 'all';
    let searchQuery = '';
    let autoRefreshInterval = null;

    // Elements
    const servicesGrid = document.getElementById('services-grid');
    const incidentTimeline = document.getElementById('incident-timeline');
    const incidentsBadge = document.getElementById('incidents-badge');
    const resultsCount = document.getElementById('results-count');
    const searchInput = document.getElementById('search-input');
    const filterPills = document.querySelectorAll('.pill');
    const autoRefreshChk = document.getElementById('auto-refresh-chk');
    const btnRefresh = document.getElementById('btn-refresh');
    const btnExport = document.getElementById('btn-export');
    
    // Summary Metrics
    const valSystemUptime = document.getElementById('val-system-uptime');
    const valOperationalCount = document.getElementById('val-operational-count');
    const valDegradedCount = document.getElementById('val-degraded-count');
    const valDownCount = document.getElementById('val-down-count');
    const valTotalEndpoints = document.getElementById('val-total-endpoints');
    const valActiveIncidents = document.getElementById('val-active-incidents');

    // Modal elements
    const btnAddModal = document.getElementById('btn-add-modal');
    const modalBackdrop = document.getElementById('modal-backdrop');
    const modalCloseBtn = document.getElementById('modal-close-btn');
    const modalCancelBtn = document.getElementById('modal-cancel-btn');
    const addServiceForm = document.getElementById('add-service-form');

    // Toast container
    const toastContainer = document.getElementById('toast-container');

    function showToast(message, type = 'info') {
        const toast = document.createElement('div');
        toast.className = 'toast';
        if (type === 'error') toast.style.borderColor = 'var(--red-base)';
        if (type === 'success') toast.style.borderColor = 'var(--green-base)';
        toast.textContent = message;
        toastContainer.appendChild(toast);
        setTimeout(() => toast.remove(), 4000);
    }

    // Fetch Summary & Data
    async function loadData() {
        try {
            const [summaryRes, servicesRes, incidentsRes] = await Promise.all([
                fetch('/api/summary'),
                fetch('/api/services'),
                fetch('/api/incidents')
            ]);

            if (summaryRes.ok) {
                const summary = await summaryRes.json();
                updateSummaryView(summary);
            }

            if (servicesRes.ok) {
                services = await servicesRes.json();
                renderServices();
            }

            if (incidentsRes.ok) {
                incidents = await incidentsRes.json();
                renderIncidents();
            }
        } catch (err) {
            console.error('Fetch error:', err);
        }
    }

    function updateSummaryView(summary) {
        valSystemUptime.textContent = `${summary.overallUptime}%`;
        valOperationalCount.textContent = summary.operational;
        valDegradedCount.textContent = summary.degraded;
        valDownCount.textContent = summary.down;
        valTotalEndpoints.textContent = `Out of ${summary.totalEndpoints} total targets`;
        valActiveIncidents.textContent = `${summary.activeIncidents} active outage${summary.activeIncidents === 1 ? '' : 's'}`;
    }

    function renderServices() {
        const filtered = services.filter(service => {
            const matchesTag = activeFilter === 'all' || service.tag.toLowerCase() === activeFilter.toLowerCase();
            const q = searchQuery.toLowerCase();
            const matchesSearch = !q || 
                service.name.toLowerCase().includes(q) || 
                service.url.toLowerCase().includes(q) || 
                service.tag.toLowerCase().includes(q);
            return matchesTag && matchesSearch;
        });

        resultsCount.textContent = `${filtered.length} target${filtered.length === 1 ? '' : 's'}`;

        if (filtered.length === 0) {
            servicesGrid.innerHTML = `
                <div style="grid-column: 1/-1; text-align: center; padding: 48px; color: var(--text-muted);">
                    <p>No monitored endpoints match the current filter criteria.</p>
                </div>
            `;
            return;
        }

        servicesGrid.innerHTML = filtered.map(service => {
            const statusClass = (service.currentStatus || 'pending').toLowerCase();
            const statusText = service.currentStatus || 'PENDING';
            const uptime = service.uptimePercentage != null ? `${service.uptimePercentage}%` : '100%';
            const avgLatency = service.averageLatencyMs != null ? `${service.averageLatencyMs} ms` : '-';
            const lastLatency = service.lastResponseTimeMs ? `${service.lastResponseTimeMs} ms` : '-';
            const lastCheckTime = service.lastCheckedAt ? formatTime(service.lastCheckedAt) : 'Not checked yet';

            // Sparkline bars
            const latencies = service.recentLatencies || [];
            const maxLat = Math.max(...latencies, 500);
            const sparklineBars = latencies.map(l => {
                const heightPct = Math.min(100, Math.max(12, Math.round((l / maxLat) * 100)));
                let speedClass = 'fast';
                if (l > 1200) speedClass = 'slow';
                else if (l > 400) speedClass = 'medium';
                return `<div class="latency-bar ${speedClass}" style="height:${heightPct}%;" title="${l}ms"></div>`;
            }).join('');

            return `
                <article class="service-card glass-panel" data-id="${service.id}">
                    <div>
                        <div class="service-card-top">
                            <div class="service-title-area">
                                <h3 class="service-name">${escapeHtml(service.name)}</h3>
                                <span class="service-tag">${escapeHtml(service.tag)}</span>
                            </div>
                            <span class="status-badge ${statusClass}">
                                <span class="pulse-dot" style="width:6px; height:6px;"></span>
                                ${statusText}
                            </span>
                        </div>

                        <div class="service-url-box">
                            <span class="http-method">${service.method || 'GET'}</span>
                            <span class="service-url">${escapeHtml(service.url)}</span>
                        </div>

                        <div class="service-stats-grid">
                            <div class="stat-item">
                                <span class="stat-label">Uptime</span>
                                <span class="stat-val text-green">${uptime}</span>
                            </div>
                            <div class="stat-item">
                                <span class="stat-label">Avg Ping</span>
                                <span class="stat-val">${avgLatency}</span>
                            </div>
                            <div class="stat-item">
                                <span class="stat-label">Last Probe</span>
                                <span class="stat-val">${lastLatency}</span>
                            </div>
                        </div>

                        <div class="latency-sparkline" title="Recent response latencies">
                            ${sparklineBars || '<span style="font-size:0.7rem; color:var(--text-muted); margin:auto;">Awaiting probe samples...</span>'}
                        </div>
                    </div>

                    <div class="card-actions">
                        <span class="last-check-text">Checked ${lastCheckTime}</span>
                        <div class="action-buttons">
                            <button class="btn-icon btn-ping" data-id="${service.id}" title="Trigger instant probe">
                                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                    <polyline points="23 4 23 10 17 10"></polyline>
                                    <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"></path>
                                </svg>
                            </button>
                            <button class="btn-icon delete btn-delete" data-id="${service.id}" title="Remove monitor">
                                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                    <polyline points="3 6 5 6 21 6"></polyline>
                                    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                                </svg>
                            </button>
                        </div>
                    </div>
                </article>
            `;
        }).join('');

        // Wire card action listeners
        document.querySelectorAll('.btn-ping').forEach(btn => {
            btn.addEventListener('click', async (e) => {
                const id = btn.getAttribute('data-id');
                btn.classList.add('spinning');
                try {
                    const res = await fetch(`/api/services/${id}/ping`, { method: 'POST' });
                    const result = await res.json();
                    if (result.success) {
                        showToast(`Probe successful: ${result.responseTimeMs}ms (${result.message})`, 'success');
                    } else {
                        showToast(`Probe failed: ${result.message}`, 'error');
                    }
                    await loadData();
                } catch (err) {
                    showToast('Ping request failed', 'error');
                } finally {
                    btn.classList.remove('spinning');
                }
            });
        });

        document.querySelectorAll('.btn-delete').forEach(btn => {
            btn.addEventListener('click', async (e) => {
                const id = btn.getAttribute('data-id');
                if (confirm('Are you sure you want to remove this monitored service?')) {
                    try {
                        const res = await fetch(`/api/services/${id}`, { method: 'DELETE' });
                        if (res.ok) {
                            showToast('Service removed', 'info');
                            await loadData();
                        }
                    } catch (err) {
                        showToast('Error removing service', 'error');
                    }
                }
            });
        });
    }

    function renderIncidents() {
        incidentsBadge.textContent = `${incidents.length} logged`;
        if (incidents.length === 0) {
            incidentTimeline.innerHTML = `
                <div class="empty-incidents">
                    <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                        <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
                        <path d="m9 12 2 2 4-4"/>
                    </svg>
                    <p>All monitored systems are operational. No active downtime incidents detected.</p>
                </div>
            `;
            return;
        }

        incidentTimeline.innerHTML = incidents.slice(0, 8).map(inc => {
            const isResolved = inc.status === 'RESOLVED';
            const duration = inc.durationSeconds != null ? `${inc.durationSeconds}s` : 'Ongoing';
            return `
                <div class="incident-row ${isResolved ? 'resolved' : ''}">
                    <div class="incident-info">
                        <span class="incident-service-name">${escapeHtml(inc.serviceName)}</span>
                        <span class="incident-reason">${escapeHtml(inc.reason || 'Downtime detected')}</span>
                    </div>
                    <div class="incident-meta">
                        <div><strong>${isResolved ? 'RESOLVED' : 'ACTIVE OUTAGE'}</strong></div>
                        <div>Duration: ${duration} &bull; ${formatTime(inc.startedAt)}</div>
                    </div>
                </div>
            `;
        }).join('');
    }

    // Format ISO string to human time
    function formatTime(isoString) {
        if (!isoString) return '';
        try {
            const d = new Date(isoString);
            return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
        } catch {
            return isoString;
        }
    }

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/[&<>'"]/g, tag => ({
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            "'": '&#39;',
            '"': '&quot;'
        }[tag] || tag));
    }

    // Filter Buttons
    filterPills.forEach(pill => {
        pill.addEventListener('click', () => {
            filterPills.forEach(p => p.classList.remove('active'));
            pill.classList.add('active');
            activeFilter = pill.getAttribute('data-filter');
            renderServices();
        });
    });

    // Search
    searchInput.addEventListener('input', (e) => {
        searchQuery = e.target.value.trim();
        renderServices();
    });

    // Refresh button
    btnRefresh.addEventListener('click', () => {
        loadData();
        showToast('Refreshed monitor states', 'info');
    });

    // Export button
    btnExport.addEventListener('click', () => {
        window.open('/api/export', '_blank');
    });

    // Modal
    btnAddModal.addEventListener('click', () => modalBackdrop.classList.add('open'));
    modalCloseBtn.addEventListener('click', () => modalBackdrop.classList.remove('open'));
    modalCancelBtn.addEventListener('click', () => modalBackdrop.classList.remove('open'));
    modalBackdrop.addEventListener('click', (e) => {
        if (e.target === modalBackdrop) modalBackdrop.classList.remove('open');
    });

    // Add Service Form
    addServiceForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const payload = {
            name: document.getElementById('form-name').value.trim(),
            url: document.getElementById('form-url').value.trim(),
            method: document.getElementById('form-method').value,
            expectedStatusCode: parseInt(document.getElementById('form-expected').value, 10),
            intervalSeconds: parseInt(document.getElementById('form-interval').value, 10),
            timeoutMillis: parseInt(document.getElementById('form-timeout').value, 10),
            tag: document.getElementById('form-tag').value
        };

        try {
            const res = await fetch('/api/services', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            if (res.ok) {
                showToast(`Service '${payload.name}' added to monitoring rotation!`, 'success');
                modalBackdrop.classList.remove('open');
                addServiceForm.reset();
                await loadData();
            } else {
                showToast('Failed to add service', 'error');
            }
        } catch (err) {
            showToast('Network error adding service', 'error');
        }
    });

    // Auto Refresh management
    function setupAutoRefresh() {
        if (autoRefreshChk.checked) {
            if (!autoRefreshInterval) {
                autoRefreshInterval = setInterval(loadData, 5000);
            }
        } else {
            if (autoRefreshInterval) {
                clearInterval(autoRefreshInterval);
                autoRefreshInterval = null;
            }
        }
    }

    autoRefreshChk.addEventListener('change', setupAutoRefresh);

    // Initial load
    loadData();
    setupAutoRefresh();
});
