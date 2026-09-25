/**
 * ============================================================================
 * CivicGrid — Municipal Smart Street Light Controller & ERP Accounting
 * Authentic Windows XP Luna/Olive Application Controller (Pure Vanilla JS)
 * ============================================================================
 */

const CivicGridApp = (function () {
  let activeTab = 'lights';
  let activeSubTab = 'balance-sheet';
  let zonesCache = [];
  let contactsCache = [];
  let accountsCache = [];
  let lightsCache = [];
  let faultsCache = [];
  let autoRefreshTimer = null;
  let isAutoRefreshEnabled = true;

  // User Role Configuration: ADMIN vs ACCOUNTANT
  let currentUserRole = 'ADMIN';
  try {
    const savedRole = localStorage.getItem('civicgrid_user_role');
    if (savedRole === 'ADMIN' || savedRole === 'ACCOUNTANT') {
      currentUserRole = savedRole;
    }
  } catch (e) {}
  let pendingLoginRole = currentUserRole;

  // Financial caches for instant currency re-rendering
  let lastJournalEntries = [];
  let lastBalanceSheetData = null;
  let lastPnlData = null;
  let lastBudgetData = null;

  // Currency Configuration
  const CURRENCIES = {
    INR: { code: 'INR', symbol: '₹', rate: 83.0, locale: 'en-IN', label: '₹ Rupee (INR)' },
    USD: { code: 'USD', symbol: '$', rate: 1.0, locale: 'en-US', label: '$ Dollar (USD)' },
    EUR: { code: 'EUR', symbol: '€', rate: 0.92, locale: 'en-IE', label: '€ Euro (EUR)' },
    GBP: { code: 'GBP', symbol: '£', rate: 0.79, locale: 'en-GB', label: '£ Pound (GBP)' }
  };

  let currentCurrency = 'INR';
  try {
    const saved = localStorage.getItem('civicgrid_currency');
    if (saved && CURRENCIES[saved]) {
      currentCurrency = saved;
    }
  } catch (e) {}

  function formatCurrency(amount, withSignPrefix = false) {
    const rawVal = typeof amount === 'number' ? amount : parseFloat(amount) || 0;
    const cfg = CURRENCIES[currentCurrency] || CURRENCIES.INR;

    // Multiply base USD value by the exchange rate before formatting and displaying
    const convertedVal = rawVal * (cfg.rate != null ? cfg.rate : 1.0);
    const absVal = Math.abs(convertedVal);

    const numStr = absVal.toLocaleString(cfg.locale, {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    });

    if (withSignPrefix) {
      const sign = convertedVal >= 0 ? '+' : '-';
      return `${sign}${cfg.symbol}${numStr}`;
    }

    const sign = convertedVal < 0 ? '-' : '';
    return `${sign}${cfg.symbol}${numStr}`;
  }

  function setCurrency(code) {
    if (!CURRENCIES[code]) return;
    currentCurrency = code;
    try {
      localStorage.setItem('civicgrid_currency', code);
    } catch (e) {}

    const sel = document.getElementById('currency-selector');
    if (sel && sel.value !== code) {
      sel.value = code;
    }

    const sym = CURRENCIES[code].symbol;
    document.querySelectorAll('.currency-sym').forEach((el) => {
      el.textContent = sym;
    });

    if (lastJournalEntries && lastJournalEntries.length > 0) {
      renderJournalEntries(lastJournalEntries);
    }
    if (lastBalanceSheetData) {
      renderBalanceSheet(lastBalanceSheetData);
    }
    if (lastPnlData) {
      renderProfitAndLoss(lastPnlData);
    }
    if (lastBudgetData) {
      renderBudgetReport(lastBudgetData);
    }

    setStatus(`Currency display updated to ${CURRENCIES[code].label}`);
  }

  function initCurrency() {
    const sel = document.getElementById('currency-selector');
    if (sel) {
      sel.value = currentCurrency;
    }
    const sym = (CURRENCIES[currentCurrency] || CURRENCIES.INR).symbol;
    document.querySelectorAll('.currency-sym').forEach((el) => {
      el.textContent = sym;
    });
  }

  // =========================================================================
  // User Role Management: ADMIN vs ACCOUNTANT
  // =========================================================================

  let isLoggedIn = sessionStorage.getItem('civicgrid_logged_in') === 'true';

  function initUserRole() {
    applyUserRole(currentUserRole);
  }

  function applyUserRole(role) {
    currentUserRole = role;
    try {
      localStorage.setItem('civicgrid_user_role', role);
    } catch (e) {}

    // Update Toolbar Select
    const userSelect = document.getElementById('user-role-select');
    if (userSelect) userSelect.value = role;

    // Update Status Bar User Panel (No emoji!)
    const statusBadge = document.getElementById('status-user-badge');
    if (statusBadge) {
      statusBadge.textContent = role === 'ADMIN'
        ? 'User: Admin (Full Access)'
        : 'User: Accountant (Finance Only)';
    }

    // Toolbar buttons: "+ New Light" enabled for Admin only
    const tbNewLight = document.getElementById('tb-btn-new-light');
    if (tbNewLight) {
      tbNewLight.disabled = role !== 'ADMIN';
      tbNewLight.title = role === 'ADMIN'
        ? 'Register New Street Light'
        : 'Admin privileges required to register new street lights';
    }

    // Street Lights pane: "Register New Light..." button
    const btnRegPane = document.getElementById('btn-register-light-pane');
    if (btnRegPane) {
      btnRegPane.disabled = role !== 'ADMIN';
      btnRegPane.title = role === 'ADMIN'
        ? 'Register New Light...'
        : 'Admin privileges required to register street lights';
    }

    // Re-render table views if caches exist
    if (lightsCache && lightsCache.length) renderStreetLights(lightsCache);
    if (faultsCache && faultsCache.length) renderFaultTickets(faultsCache);
  }

  function switchUserRole(role) {
    if (role !== 'ADMIN' && role !== 'ACCOUNTANT') role = 'ADMIN';
    applyUserRole(role);
    setStatus(`Switched active user to ${role === 'ADMIN' ? 'Administrator' : 'Accountant'}.`);
  }

  function openLoginModal(isMandatory = false) {
    pendingLoginRole = currentUserRole;
    selectLoginCard(pendingLoginRole);
    const pwdInput = document.getElementById('login-password');
    if (pwdInput) pwdInput.value = '';
    const errorDiv = document.getElementById('login-error-msg');
    if (errorDiv) errorDiv.style.display = 'none';

    const cancelBtn = document.getElementById('btn-cancel-login');
    if (cancelBtn) {
      cancelBtn.style.display = (isMandatory || !isLoggedIn) ? 'none' : 'inline-block';
    }

    document.getElementById('modal-login')?.classList.add('open');
    setTimeout(() => pwdInput?.focus(), 100);
  }

  function cancelLogin() {
    if (!isLoggedIn) {
      alert('Please enter the password to log on to CivicGrid.');
      return;
    }
    closeModal('modal-login');
  }

  function selectLoginCard(role) {
    pendingLoginRole = role;
    const adminRadio = document.getElementById('radio-user-admin');
    const acctRadio = document.getElementById('radio-user-accountant');
    if (adminRadio) adminRadio.checked = role === 'ADMIN';
    if (acctRadio) acctRadio.checked = role === 'ACCOUNTANT';
    updateLoginModalCards();
  }

  function updateLoginModalCards() {
    const adminCard = document.getElementById('card-user-admin');
    const accountantCard = document.getElementById('card-user-accountant');
    if (adminCard) adminCard.classList.toggle('selected', pendingLoginRole === 'ADMIN');
    if (accountantCard) accountantCard.classList.toggle('selected', pendingLoginRole === 'ACCOUNTANT');
  }

  function handleLoginSubmit(e) {
    if (e) e.preventDefault();
    const pwdInput = document.getElementById('login-password');
    const errorDiv = document.getElementById('login-error-msg');
    const entered = (pwdInput?.value || '').trim();

    // Shared password for both accounts: '1234' (also accept 'admin' or 'civicgrid')
    if (entered === '1234' || entered.toLowerCase() === 'admin' || entered.toLowerCase() === 'civicgrid') {
      isLoggedIn = true;
      try {
        sessionStorage.setItem('civicgrid_logged_in', 'true');
      } catch (e) {}
      if (errorDiv) errorDiv.style.display = 'none';

      switchUserRole(pendingLoginRole);
      closeModal('modal-login');
      setStatus(`Logged on as ${pendingLoginRole === 'ADMIN' ? 'Administrator' : 'Accountant'}.`);
    } else {
      if (errorDiv) {
        errorDiv.textContent = 'Invalid password. Shared password for both users is: 1234';
        errorDiv.style.display = 'block';
      }
      if (pwdInput) {
        pwdInput.select();
        pwdInput.focus();
      }
    }
  }

  function confirmUserLogin() {
    handleLoginSubmit(null);
  }

  // Initialize Application
  function init() {
    initClock();
    initEventListeners();
    initCurrency();
    initUserRole();
    loadAllMasterData();
    refreshAllData();

    // Open login box above the page with background not visible if not logged in
    if (!isLoggedIn) {
      openLoginModal(true);
    }

    // Start 5-second live telemetry refresh to sync with @Scheduled backend simulator
    autoRefreshTimer = setInterval(() => {
      if (isAutoRefreshEnabled && isLoggedIn) {
        fetchPowerSummary();
        if (activeTab === 'lights') fetchStreetLights();
        if (activeTab === 'faults') fetchFaultTickets();
      }
    }, 5000);
  }

  // Live Clock in Status Bar
  function initClock() {
    const clockEl = document.getElementById('status-clock');
    if (!clockEl) return;

    function update() {
      const now = new Date();
      let hours = now.getHours();
      const minutes = String(now.getMinutes()).padStart(2, '0');
      const seconds = String(now.getSeconds()).padStart(2, '0');
      const ampm = hours >= 12 ? 'PM' : 'AM';
      hours = hours % 12 || 12;
      clockEl.textContent = `${hours}:${minutes}:${seconds} ${ampm}`;
    }

    update();
    setInterval(update, 1000);
  }

  // Setup Event Listeners
  function initEventListeners() {
    // Top Tabs
    document.querySelectorAll('.xp-tab').forEach((tab) => {
      tab.addEventListener('click', () => {
        switchTab(tab.getAttribute('data-tab'));
      });
    });

    // Toolbar Buttons
    document.querySelectorAll('.xp-tb-nav').forEach((btn) => {
      btn.addEventListener('click', () => {
        switchTab(btn.getAttribute('data-tab'));
      });
    });

    // Menu dropdown toggles
    document.querySelectorAll('.xp-menu-item').forEach((item) => {
      item.addEventListener('click', (e) => {
        e.stopPropagation();
        const isOpen = item.classList.contains('open');
        document.querySelectorAll('.xp-menu-item').forEach(m => m.classList.remove('open'));
        if (!isOpen) item.classList.add('open');
      });
    });

    document.addEventListener('click', () => {
      document.querySelectorAll('.xp-menu-item').forEach(m => m.classList.remove('open'));
    });

    // Zone filter in Lights tab
    const zoneFilter = document.getElementById('zone-filter');
    if (zoneFilter) {
      zoneFilter.addEventListener('change', () => renderStreetLights(lightsCache));
    }

    // Search pole in Lights tab
    const searchPole = document.getElementById('search-pole');
    if (searchPole) {
      searchPole.addEventListener('input', () => renderStreetLights(lightsCache));
    }

    // Reports sub-tabs
    document.querySelectorAll('.xp-subtab-btn').forEach((btn) => {
      btn.addEventListener('click', () => {
        switchSubTab(btn.getAttribute('data-subtab'));
      });
    });

    // Dimming slider live label
    const dimSlider = document.getElementById('dimming-slider');
    const dimValue = document.getElementById('dimming-slider-val');
    if (dimSlider && dimValue) {
      dimSlider.addEventListener('input', (e) => {
        dimValue.textContent = `${e.target.value}%`;
      });
    }

    // New light slider live label
    const newDimSlider = document.getElementById('new-light-dimming');
    const newDimValue = document.getElementById('new-light-dim-val');
    if (newDimSlider && newDimValue) {
      newDimSlider.addEventListener('input', (e) => {
        newDimValue.textContent = `${e.target.value}%`;
      });
    }
  }

  // Tab Navigation
  function switchTab(tabId) {
    if (!tabId) return;
    activeTab = tabId;

    document.querySelectorAll('.xp-tab').forEach((tab) => {
      tab.classList.toggle('active', tab.getAttribute('data-tab') === tabId);
    });

    document.querySelectorAll('.xp-tb-nav').forEach((btn) => {
      btn.classList.toggle('active', btn.getAttribute('data-tab') === tabId);
    });

    document.querySelectorAll('.tab-pane').forEach((pane) => {
      pane.classList.toggle('active', pane.id === `tab-${tabId}`);
    });

    // Fetch tab-specific data on switch
    if (tabId === 'lights') fetchStreetLights();
    if (tabId === 'faults') fetchFaultTickets();
    if (tabId === 'accounting') fetchJournalEntries();
    if (tabId === 'reports') fetchReportData();
    if (tabId === 'power') fetchPowerAnalytics();

    setStatus(`Viewing ${tabId.replace('-', ' ').toUpperCase()} module.`);
  }

  // Reports Sub-Tabs
  function switchSubTab(subTabId) {
    activeSubTab = subTabId;
    document.querySelectorAll('.xp-subtab-btn').forEach((btn) => {
      btn.classList.toggle('active', btn.getAttribute('data-subtab') === subTabId);
    });
    document.querySelectorAll('.subtab-pane').forEach((pane) => {
      pane.style.display = pane.id === `subtab-${subTabId}` ? 'block' : 'none';
    });
    fetchReportData();
  }

  // Status text updater
  function setStatus(text) {
    const el = document.getElementById('status-text');
    if (el) el.textContent = text;
  }

  // =========================================================================
  // Master Data Loading
  // =========================================================================

  async function loadAllMasterData() {
    try {
      // 1. Zones
      const zRes = await fetch('/api/zones');
      if (zRes.ok) {
        zonesCache = await zRes.json();
        populateZoneDropdowns();
      }

      // 2. Contacts
      const cRes = await fetch('/api/contacts');
      if (cRes.ok) {
        contactsCache = await cRes.json();
        populateContactDropdowns();
      }

      // 3. Accounts
      const aRes = await fetch('/api/chart-of-accounts');
      if (aRes.ok) {
        accountsCache = await aRes.json();
        populateAccountDropdowns();
      }
    } catch (err) {
      console.warn('Error loading master data:', err);
    }
  }

  function populateZoneDropdowns() {
    const filterSelect = document.getElementById('zone-filter');
    const newSelect = document.getElementById('new-light-zone');
    const txSelect = document.getElementById('tx-zone');

    if (filterSelect) {
      filterSelect.innerHTML = '<option value="ALL">All Grid Zones</option>';
      zonesCache.forEach((z) => {
        filterSelect.innerHTML += `<option value="${z.id}">${escapeHtml(z.name)}</option>`;
      });
    }

    if (newSelect) {
      newSelect.innerHTML = '';
      zonesCache.forEach((z) => {
        newSelect.innerHTML += `<option value="${z.id}">${escapeHtml(z.name)}</option>`;
      });
    }

    if (txSelect) {
      txSelect.innerHTML = '<option value="">None / General Utility</option>';
      zonesCache.forEach((z) => {
        txSelect.innerHTML += `<option value="${z.id}">${escapeHtml(z.name)}</option>`;
      });
    }
  }

  function populateContactDropdowns() {
    const txParty = document.getElementById('tx-party');
    if (!txParty) return;
    txParty.innerHTML = '<option value="">-- Select Contact / Entity --</option>';
    contactsCache.forEach((c) => {
      txParty.innerHTML += `<option value="${c.id}">${escapeHtml(c.name)} (${c.type})</option>`;
    });
  }

  function populateAccountDropdowns() {
    const txAccount = document.getElementById('tx-account');
    if (!txAccount) return;
    txAccount.innerHTML = '<option value="">-- Select Chart of Account --</option>';
    accountsCache.forEach((a) => {
      txAccount.innerHTML += `<option value="${a.code}">${a.code} - ${escapeHtml(a.name)} [${a.type}]</option>`;
    });
  }

  // =========================================================================
  // Telemetry & KPI Power Summary
  // =========================================================================

  async function fetchPowerSummary() {
    try {
      const res = await fetch('/api/street-lights/power-summary');
      if (!res.ok) return;
      const data = await res.json();

      const totalLights = data.totalLights ?? 0;
      const faultyLights = data.faultyLights ?? 0;
      const totalWatts = data.totalPowerDrawWatts ?? data.totalWatts ?? 0;
      const avgWatts = data.averagePowerDrawWatts ?? data.averageWatts ?? 0;

      document.getElementById('kpi-total-lights').textContent = totalLights;
      document.getElementById('kpi-faulty-lights').textContent = faultyLights;
      document.getElementById('kpi-total-power').textContent = `${totalWatts.toFixed(1)} W (${(totalWatts / 1000).toFixed(2)} kW)`;
      document.getElementById('kpi-avg-power').textContent = `${avgWatts.toFixed(1)} W/pole`;

      const faultyEl = document.getElementById('kpi-faulty-lights');
      if (faultyLights > 0) {
        faultyEl.style.color = '#cc3719';
      } else {
        faultyEl.style.color = '#155724';
      }

      // Update badge count
      const badge = document.getElementById('fault-badge-count');
      const tbBadge = document.getElementById('tb-fault-badge');
      if (badge) {
        badge.textContent = faultyLights > 0 ? faultyLights : '0';
        badge.style.display = faultyLights > 0 ? 'inline-block' : 'none';
      }
      if (tbBadge) {
        tbBadge.textContent = faultyLights > 0 ? `(${faultyLights})` : '';
      }
    } catch (err) {
      console.warn('Power summary fetch failed:', err);
    }
  }

  // =========================================================================
  // Module 1: Street Lights Management
  // =========================================================================

  async function fetchStreetLights() {
    try {
      setStatus('Fetching street light grid data...');
      const res = await fetch('/api/street-lights');
      if (!res.ok) throw new Error('HTTP ' + res.status);
      lightsCache = await res.json();
      renderStreetLights(lightsCache);
      setStatus(`Loaded ${lightsCache.length} street lights.`);
    } catch (err) {
      setStatus('Error loading street lights: ' + err.message);
    }
  }

  function renderStreetLights(lights) {
    const tbody = document.getElementById('lights-tbody');
    if (!tbody) return;

    const filterVal = document.getElementById('zone-filter')?.value || 'ALL';
    const searchVal = (document.getElementById('search-pole')?.value || '').trim().toLowerCase();

    const filtered = lights.filter((l) => {
      const matchZone = filterVal === 'ALL' || String(l.zone?.id || l.zoneId) === filterVal;
      const matchSearch = !searchVal || (l.poleCode && l.poleCode.toLowerCase().includes(searchVal));
      return matchZone && matchSearch;
    });

    if (filtered.length === 0) {
      tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: #777; padding: 20px;">No street lights found matching current filter.</td></tr>`;
      return;
    }

    tbody.innerHTML = filtered.map((light) => {
      const zoneName = light.zone ? light.zone.name : `Zone #${light.zoneId || '?'}`;
      const isFaulty = light.status === 'FAULTY';
      const statusBadge = isFaulty
        ? `<span class="xp-badge xp-badge-fault">FAULTY</span>`
        : `<span class="xp-badge xp-badge-active">ACTIVE</span>`;

      return `
        <tr>
          <td><strong>#${light.id}</strong></td>
          <td><strong style="color: #003c74;">${escapeHtml(light.poleCode)}</strong></td>
          <td>${escapeHtml(zoneName)}</td>
          <td>
            <div style="display: flex; align-items: center; gap: 8px;">
              <div class="xp-progress-outer" style="width: 70px; height: 10px;">
                <div class="xp-progress-inner" style="width: ${light.dimmingPercentage}%;"></div>
              </div>
              <span>${light.dimmingPercentage}%</span>
            </div>
          </td>
          <td><strong style="color: ${light.simulatedPowerDraw > 0 ? '#1b5e20' : '#777'};">${light.simulatedPowerDraw != null ? light.simulatedPowerDraw.toFixed(1) : '0.0'} W</strong></td>
          <td>${statusBadge}</td>
          <td>
            <button class="xp-btn xp-btn-sm"
                    ${currentUserRole === 'ADMIN' ? `onclick="CivicGridApp.openDimmingModal(${light.id}, '${escapeHtml(light.poleCode)}', ${light.dimmingPercentage})"` : 'disabled'}
                    title="${currentUserRole === 'ADMIN' ? 'Adjust light brightness' : 'Admin access required to adjust brightness'}">
              Dim
            </button>
          </td>
        </tr>
      `;
    }).join('');
  }

  // Quick Dimming helper
  async function quickSetDimming(lightId, percent) {
    if (currentUserRole !== 'ADMIN') {
      alert('Access Denied: Only Administrator accounts can adjust street light brightness.');
      return;
    }
    try {
      setStatus(`Setting light #${lightId} brightness to ${percent}%...`);
      const res = await fetch(`/api/street-lights/${lightId}/dimming`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'X-User-Role': currentUserRole
        },
        body: JSON.stringify({ dimmingPercentage: percent })
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.error || 'HTTP ' + res.status);
      }
      await fetchStreetLights();
      await fetchPowerSummary();
      setStatus(`Light #${lightId} dimming set to ${percent}%.`);
    } catch (err) {
      alert('Failed to set dimming: ' + err.message);
    }
  }

  // Modal: Register Street Light
  function openRegisterLightModal() {
    if (currentUserRole !== 'ADMIN') {
      alert('Access Denied: Only Administrator accounts can register new street lights.');
      return;
    }
    document.getElementById('new-light-form')?.reset();
    document.getElementById('new-light-dim-val').textContent = '80%';
    document.getElementById('modal-register-light').classList.add('open');
  }

  async function handleRegisterLightSubmit(e) {
    e.preventDefault();
    if (currentUserRole !== 'ADMIN') {
      alert('Access Denied: Only Administrator accounts can register new street lights.');
      return;
    }
    const poleCode = document.getElementById('new-light-pole').value.trim();
    const zoneId = parseInt(document.getElementById('new-light-zone').value, 10);
    const dimmingPercentage = parseInt(document.getElementById('new-light-dimming').value, 10);

    if (!poleCode) {
      alert('Please enter a pole code.');
      return;
    }

    try {
      setStatus(`Registering light pole ${poleCode}...`);
      const res = await fetch('/api/street-lights', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-User-Role': currentUserRole
        },
        body: JSON.stringify({ poleCode, zoneId, dimmingPercentage })
      });

      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.error || err.message || 'HTTP ' + res.status);
      }

      closeModal('modal-register-light');
      await fetchStreetLights();
      await fetchPowerSummary();
      setStatus(`Light pole ${poleCode} registered successfully.`);
    } catch (err) {
      alert('Error registering light: ' + err.message);
    }
  }

  // Modal: Adjust Dimming
  let currentDimmingLightId = null;

  function openDimmingModal(id, poleCode, currentDim) {
    if (currentUserRole !== 'ADMIN') {
      alert('Access Denied: Only Administrator accounts can adjust street light brightness.');
      return;
    }
    currentDimmingLightId = id;
    document.getElementById('dimming-pole-label').textContent = `${poleCode} (ID: #${id})`;
    document.getElementById('dimming-slider').value = currentDim;
    document.getElementById('dimming-slider-val').textContent = `${currentDim}%`;
    document.getElementById('modal-dimming').classList.add('open');
  }

  async function handleDimmingSubmit(e) {
    e.preventDefault();
    if (!currentDimmingLightId) return;
    if (currentUserRole !== 'ADMIN') {
      alert('Access Denied: Only Administrator accounts can adjust street light brightness.');
      return;
    }

    const dimmingPercentage = parseInt(document.getElementById('dimming-slider').value, 10);
    try {
      setStatus(`Updating light #${currentDimmingLightId} to ${dimmingPercentage}%...`);
      const res = await fetch(`/api/street-lights/${currentDimmingLightId}/dimming`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'X-User-Role': currentUserRole
        },
        body: JSON.stringify({ dimmingPercentage })
      });

      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.error || 'HTTP ' + res.status);
      }

      closeModal('modal-dimming');
      await fetchStreetLights();
      await fetchPowerSummary();
      setStatus(`Light #${currentDimmingLightId} dimming updated.`);
    } catch (err) {
      alert('Error updating dimming: ' + err.message);
    }
  }

  // =========================================================================
  // Module 2: Automated Fault Tickets
  // =========================================================================

  async function fetchFaultTickets() {
    try {
      setStatus('Fetching automated fault alerts...');
      const res = await fetch('/api/fault-tickets');
      if (!res.ok) throw new Error('HTTP ' + res.status);
      const tickets = await res.json();
      faultsCache = tickets || [];
      renderFaultTickets(faultsCache);
      setStatus(`Loaded ${tickets.length} fault tickets.`);
    } catch (err) {
      setStatus('Error loading fault tickets: ' + err.message);
    }
  }

  function renderFaultTickets(tickets) {
    faultsCache = tickets || [];
    const tbody = document.getElementById('faults-tbody');
    if (!tbody) return;

    if (!tickets || tickets.length === 0) {
      tbody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: #155724; padding: 24px; background: #f4faf4;">All street lights operational. No automated fault tickets detected.</td></tr>`;
      return;
    }

    const isAdmin = currentUserRole === 'ADMIN';

    tbody.innerHTML = tickets.map((t) => {
      const isResolved = t.status === 'RESOLVED';
      const statusBadge = isResolved
        ? `<span class="xp-badge xp-badge-resolved">RESOLVED</span>`
        : `<span class="xp-badge xp-badge-fault">NOT RESOLVED</span>`;

      // Two options: "Resolved" & "Not Resolved"
      // If clicked on resolved -> changes to not resolved
      // If clicked on not resolved -> changes to resolved
      // Only enabled to Admin
      let toggleBtn;
      if (isResolved) {
        toggleBtn = `
          <button class="xp-btn xp-btn-sm xp-btn-resolved"
                  ${isAdmin ? `onclick="CivicGridApp.toggleFaultTicket(${t.id})"` : 'disabled'}
                  title="${isAdmin ? 'Click to change status to Not Resolved (Reopen ticket)' : 'Admin access required to change ticket resolution status'}">
            Resolved
          </button>
        `;
      } else {
        toggleBtn = `
          <button class="xp-btn xp-btn-sm xp-btn-not-resolved"
                  ${isAdmin ? `onclick="CivicGridApp.toggleFaultTicket(${t.id})"` : 'disabled'}
                  title="${isAdmin ? 'Click to change status to Resolved' : 'Admin access required to change ticket resolution status'}">
            Not Resolved
          </button>
        `;
      }

      const poleCode = t.streetLight ? t.streetLight.poleCode : `#${t.streetLightId || '?'}`;
      const createdAt = t.createdAt ? new Date(t.createdAt).toLocaleString() : 'N/A';

      return `
        <tr style="${isResolved ? 'opacity: 0.85;' : 'font-weight: 500;'}">
          <td><strong>${escapeHtml(t.ticketNumber || '#' + t.id)}</strong></td>
          <td><strong style="color: #003c74;">${escapeHtml(poleCode)}</strong></td>
          <td style="max-width: 320px;">${escapeHtml(t.description)}</td>
          <td>${createdAt}</td>
          <td>${statusBadge}</td>
          <td>${toggleBtn}</td>
        </tr>
      `;
    }).join('');
  }

  async function toggleFaultTicket(ticketId) {
    if (currentUserRole !== 'ADMIN') {
      alert('Access Denied: Only Admin can resolve or reopen fault tickets.');
      return;
    }
    try {
      setStatus(`Updating fault ticket #${ticketId}...`);
      const res = await fetch(`/api/fault-tickets/${ticketId}/toggle`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'X-User-Role': currentUserRole
        }
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.error || `HTTP ${res.status}`);
      }
      const updated = await res.json();
      const statusText = updated.status === 'RESOLVED' ? 'RESOLVED' : 'NOT RESOLVED (OPEN)';
      setStatus(`Fault ticket #${ticketId} changed to ${statusText}.`);
      await fetchFaultTickets();
      await fetchStreetLights();
      await fetchPowerSummary();
    } catch (err) {
      alert('Error updating fault ticket: ' + err.message);
      setStatus('Failed to update fault ticket.');
    }
  }

  function resolveFault(ticketId) {
    return toggleFaultTicket(ticketId);
  }

  // =========================================================================
  // Module 3: ERP Accounting & Journal Entries
  // =========================================================================

  async function fetchJournalEntries() {
    try {
      setStatus('Loading double-entry general ledger...');
      const res = await fetch('/api/journal-entries');
      if (!res.ok) throw new Error('HTTP ' + res.status);
      const entries = await res.json();
      lastJournalEntries = entries;
      renderJournalEntries(entries);
      setStatus(`Loaded ${entries.length} journal ledger entries.`);
    } catch (err) {
      setStatus('Error loading journal entries: ' + err.message);
    }
  }

  function renderJournalEntries(entries) {
    const tbody = document.getElementById('journal-tbody');
    const tfoot = document.getElementById('journal-tfoot');
    if (!tbody) return;

    if (!entries || entries.length === 0) {
      tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: #777; padding: 20px;">No journal entries posted yet. Click "Record New Transaction" to create one.</td></tr>`;
      if (tfoot) tfoot.innerHTML = '';
      return;
    }

    let totalDebit = 0;
    let totalCredit = 0;

    tbody.innerHTML = entries.map((e) => {
      const debitVal = parseFloat(e.debit || 0);
      const creditVal = parseFloat(e.credit || 0);
      totalDebit += debitVal;
      totalCredit += creditVal;

      const accountName = e.account ? e.account.name : (e.accountName || '');
      const accountCode = e.account ? e.account.code : (e.accountCode || '');
      const dateVal = e.entryDate || e.date || '';

      return `
        <tr>
          <td><strong>${escapeHtml(e.entryNumber || '#' + e.id)}</strong></td>
          <td>${dateVal}</td>
          <td><span style="font-family: monospace; font-weight: bold; background: #eee; padding: 1px 4px; border: 1px solid #ccc;">${escapeHtml(accountCode)}</span></td>
          <td>${escapeHtml(accountName)}</td>
          <td style="text-align: right; color: ${debitVal > 0 ? '#003c74' : '#999'}; font-weight: ${debitVal > 0 ? 'bold' : 'normal'};">
            ${debitVal > 0 ? formatCurrency(debitVal) : '-'}
          </td>
          <td style="text-align: right; color: ${creditVal > 0 ? '#1b5e20' : '#999'}; font-weight: ${creditVal > 0 ? 'bold' : 'normal'};">
            ${creditVal > 0 ? formatCurrency(creditVal) : '-'}
          </td>
          <td>${escapeHtml(e.description || e.reference || '')}</td>
        </tr>
      `;
    }).join('');

    const isBalanced = Math.abs(totalDebit - totalCredit) < 0.01;
    document.getElementById('kpi-ledger-status').textContent = isBalanced
      ? `Balanced (${formatCurrency(totalDebit)})`
      : `UNBALANCED (D:${formatCurrency(totalDebit)} != C:${formatCurrency(totalCredit)})`;

    if (tfoot) {
      tfoot.innerHTML = `
        <tr>
          <td colspan="4" style="text-align: right; font-weight: bold;">TOTALS (${isBalanced ? 'BALANCED' : 'UNBALANCED'}):</td>
          <td style="text-align: right; font-weight: bold; color: #003c74;">${formatCurrency(totalDebit)}</td>
          <td style="text-align: right; font-weight: bold; color: #1b5e20;">${formatCurrency(totalCredit)}</td>
          <td></td>
        </tr>
      `;
    }
  }

  // Modal: New Transaction
  function openNewTransactionModal() {
    document.getElementById('tx-form')?.reset();
    document.getElementById('tx-date').value = new Date().toISOString().split('T')[0];
    document.getElementById('modal-transaction').classList.add('open');
  }

  async function handleTransactionSubmit(e) {
    e.preventDefault();
    const type = document.getElementById('tx-type').value;
    const amount = parseFloat(document.getElementById('tx-amount').value);
    const date = document.getElementById('tx-date').value;
    const partyId = document.getElementById('tx-party').value ? parseInt(document.getElementById('tx-party').value, 10) : null;
    const accountCode = document.getElementById('tx-account').value;
    const zoneId = document.getElementById('tx-zone').value ? parseInt(document.getElementById('tx-zone').value, 10) : null;
    const description = document.getElementById('tx-desc').value.trim();

    if (!type || isNaN(amount) || amount <= 0 || !accountCode) {
      alert('Please fill in transaction type, valid amount > 0, and target account.');
      return;
    }

    const cfg = CURRENCIES[currentCurrency] || CURRENCIES.INR;
    // Convert user-entered currency amount to base USD for persistence
    const baseUsdAmount = parseFloat((amount / (cfg.rate || 1.0)).toFixed(2));

    try {
      setStatus(`Submitting ${type} transaction of ${formatCurrency(baseUsdAmount)}...`);
      const res = await fetch('/api/transactions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ type, amount: baseUsdAmount, date, partyId, accountCode, zoneId, description })
      });

      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.message || 'HTTP ' + res.status);
      }

      const result = await res.json();
      closeModal('modal-transaction');
      await fetchJournalEntries();
      await fetchReportData();
      alert(`Success! Transaction posted.\nDocument: ${result.documentNumber || ''}\nJournal Entry: ${result.journalEntryNumber || 'Created'}\nAmount: ${formatCurrency(parseFloat(result.amount != null ? result.amount : baseUsdAmount))}`);
      setStatus(`Transaction posted to ledger.`);
    } catch (err) {
      alert('Failed to post transaction: ' + err.message);
    }
  }

  // =========================================================================
  // Module 4: Financial Reports
  // =========================================================================

  async function fetchReportData() {
    if (activeSubTab === 'balance-sheet') await fetchBalanceSheet();
    else if (activeSubTab === 'pnl') await fetchProfitAndLoss();
    else if (activeSubTab === 'budget') await fetchBudgetReport();
  }

  async function fetchBalanceSheet() {
    try {
      setStatus('Generating Balance Sheet statement...');
      const res = await fetch('/api/reports/balance-sheet');
      if (!res.ok) throw new Error('HTTP ' + res.status);
      const data = await res.json();
      lastBalanceSheetData = data;
      renderBalanceSheet(data);
      setStatus('Balance Sheet generated.');
    } catch (err) {
      setStatus('Error loading Balance Sheet: ' + err.message);
    }
  }

  function renderBalanceSheet(data) {
    if (!data) return;
    const tbodyAssets = document.getElementById('bs-assets-tbody');
    const tbodyLiab = document.getElementById('bs-liab-tbody');

    if (tbodyAssets) {
      tbodyAssets.innerHTML = (data.assets || []).map(a => `
        <tr>
          <td><span style="font-family: monospace;">${a.accountCode}</span></td>
          <td><strong>${escapeHtml(a.accountName)}</strong></td>
          <td style="text-align: right; color: #003c74;">${formatCurrency(a.balance)}</td>
        </tr>
      `).join('') + `
        <tr style="background: #eef3eb; font-weight: bold;">
          <td colspan="2">TOTAL ASSETS</td>
          <td style="text-align: right; color: #003c74;">${formatCurrency(data.totalAssets)}</td>
        </tr>
      `;
    }

    if (tbodyLiab) {
      tbodyLiab.innerHTML = (data.liabilities || []).map(l => `
        <tr>
          <td><span style="font-family: monospace;">${l.accountCode}</span></td>
          <td><strong>${escapeHtml(l.accountName)}</strong></td>
          <td style="text-align: right; color: #cc3719;">${formatCurrency(l.balance)}</td>
        </tr>
      `).join('') + `
        <tr style="background: #fdf5f5; font-weight: bold;">
          <td colspan="2">TOTAL LIABILITIES</td>
          <td style="text-align: right; color: #cc3719;">${formatCurrency(data.totalLiabilities)}</td>
        </tr>
        <tr>
          <td><span style="font-family: monospace;">3999</span></td>
          <td><strong>Retained Earnings / Operational Equity</strong></td>
          <td style="text-align: right; color: #1b5e20;">${formatCurrency(data.retainedEarnings)}</td>
        </tr>
        <tr style="background: #eef3eb; font-weight: bold; border-top: 2px solid #526f45;">
          <td colspan="2">TOTAL LIABILITIES & EQUITY</td>
          <td style="text-align: right; color: #1b5e20;">${formatCurrency(data.totalLiabilitiesAndEquity)}</td>
        </tr>
      `;
    }

    const balCheck = document.getElementById('bs-balance-check');
    if (balCheck) {
      balCheck.innerHTML = data.balanced
        ? `<span style="color: #155724; font-weight: bold;">Statement in Balance (Assets = Liabilities + Equity)</span>`
        : `<span style="color: #cc3719; font-weight: bold;">Variance Detected in Double-Entry Equation</span>`;
    }
  }

  async function fetchProfitAndLoss() {
    try {
      setStatus('Generating Profit & Loss statement...');
      const res = await fetch('/api/reports/pnl');
      if (!res.ok) throw new Error('HTTP ' + res.status);
      const data = await res.json();
      lastPnlData = data;
      renderProfitAndLoss(data);
      setStatus('Profit & Loss statement generated.');
    } catch (err) {
      setStatus('Error loading P&L: ' + err.message);
    }
  }

  function renderProfitAndLoss(data) {
    if (!data) return;
    const tbodyIncome = document.getElementById('pnl-income-tbody');
    const tbodyExpense = document.getElementById('pnl-expense-tbody');

    if (tbodyIncome) {
      tbodyIncome.innerHTML = (data.income || []).map(i => `
        <tr>
          <td><span style="font-family: monospace;">${i.accountCode}</span></td>
          <td><strong>${escapeHtml(i.accountName)}</strong></td>
          <td style="text-align: right; color: #1b5e20;">${formatCurrency(i.balance)}</td>
        </tr>
      `).join('') + `
        <tr style="background: #eef3eb; font-weight: bold;">
          <td colspan="2">TOTAL REVENUE / FEES</td>
          <td style="text-align: right; color: #1b5e20;">${formatCurrency(data.totalIncome)}</td>
        </tr>
      `;
    }

    if (tbodyExpense) {
      tbodyExpense.innerHTML = (data.expenses || []).map(e => `
        <tr>
          <td><span style="font-family: monospace;">${e.accountCode}</span></td>
          <td><strong>${escapeHtml(e.accountName)}</strong></td>
          <td style="text-align: right; color: #cc3719;">${formatCurrency(e.balance)}</td>
        </tr>
      `).join('') + `
        <tr style="background: #fdf5f5; font-weight: bold;">
          <td colspan="2">TOTAL OPERATING EXPENSES</td>
          <td style="text-align: right; color: #cc3719;">${formatCurrency(data.totalExpenses)}</td>
        </tr>
      `;
    }

    const netMargin = parseFloat(data.netProfitOrLoss || 0);
    const netEl = document.getElementById('pnl-net-result');
    if (netEl) {
      netEl.innerHTML = `
        <strong>NET OPERATING MARGIN:</strong>
        <span style="font-size: 16px; font-weight: bold; color: ${netMargin >= 0 ? '#1b5e20' : '#cc3719'};">
          ${formatCurrency(netMargin, true)}
        </span>
      `;
    }
  }

  async function fetchBudgetReport() {
    try {
      setStatus('Generating Zone Budget Variance report...');
      const res = await fetch('/api/reports/budget?year=2026');
      if (!res.ok) throw new Error('HTTP ' + res.status);
      const data = await res.json();
      lastBudgetData = data;
      renderBudgetReport(data);
      setStatus('Budget Variance report generated.');
    } catch (err) {
      setStatus('Error loading budget report: ' + err.message);
    }
  }

  function renderBudgetReport(data) {
    if (!data) return;
    const tbody = document.getElementById('budget-tbody');
    if (!tbody) return;

    tbody.innerHTML = (data.zoneReports || []).map(b => {
      const planned = parseFloat(b.plannedAmount ?? b.allocatedBudget ?? 0);
      const actual = parseFloat(b.actualAmount ?? b.actualSpend ?? 0);
      const variance = parseFloat(b.varianceAmount ?? b.variance ?? 0);
      const utilPercent = b.utilizationPercentage != null ? Math.round(b.utilizationPercentage) : (planned > 0 ? Math.round((actual / planned) * 100) : 0);
      const isOver = b.status === 'OVER_BUDGET' || variance < 0;

      return `
        <tr>
          <td><strong>${escapeHtml(b.zoneName)}</strong></td>
          <td>FY${data.fiscalYear || 2026}</td>
          <td style="text-align: right;">${formatCurrency(planned)}</td>
          <td style="text-align: right; font-weight: bold; color: ${isOver ? '#cc3719' : '#003c74'};">${formatCurrency(actual)}</td>
          <td style="text-align: right; font-weight: bold; color: ${isOver ? '#cc3719' : '#1b5e20'};">
            ${formatCurrency(variance, true)}
          </td>
          <td style="width: 140px;">
            <div style="display: flex; align-items: center; gap: 6px;">
              <div class="xp-progress-outer" style="height: 12px; flex: 1;">
                <div class="xp-progress-inner ${isOver ? 'overbudget' : ''}" style="width: ${Math.min(utilPercent, 100)}%;"></div>
              </div>
              <span style="font-size: 10px; width: 34px;">${utilPercent}%</span>
            </div>
          </td>
        </tr>
      `;
    }).join('') + `
      <tr style="background: #f0ede0; font-weight: bold; border-top: 2px solid #526f45;">
        <td colspan="2">TOTAL CONSOLIDATED GRID BUDGET</td>
        <td style="text-align: right;">${formatCurrency(data.totalPlanned)}</td>
        <td style="text-align: right; color: #003c74;">${formatCurrency(data.totalActual)}</td>
        <td style="text-align: right; color: ${data.totalVariance < 0 ? '#cc3719' : '#1b5e20'};">
          ${formatCurrency(data.totalVariance, true)}
        </td>
        <td></td>
      </tr>
    `;
  }

  // =========================================================================
  // Module 5: Power Analytics & System Details
  // =========================================================================

  async function fetchPowerAnalytics() {
    setStatus('Loading grid power analytics...');
    await fetchPowerSummary();
    setStatus('Power analytics updated.');
  }

  // =========================================================================
  // Global Refresh & Modal Helpers
  // =========================================================================

  function refreshAllData() {
    setStatus('Refreshing all grid telemetry and ledger data...');
    fetchPowerSummary();
    if (activeTab === 'lights') fetchStreetLights();
    else if (activeTab === 'faults') fetchFaultTickets();
    else if (activeTab === 'accounting') fetchJournalEntries();
    else if (activeTab === 'reports') fetchReportData();
    else if (activeTab === 'power') fetchPowerAnalytics();
  }

  function closeModal(modalId) {
    document.getElementById(modalId)?.classList.remove('open');
  }

  function escapeHtml(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function openAboutDialog() {
    alert(
      "CivicGrid Municipal Controller & ERP Accounting\n" +
      "Version: 1.0 (Service Pack 1)\n" +
      "UI Style: Windows XP Luna / Olive Skeuomorphic Theme\n" +
      "Backend: Spring Boot 4.1.1 + MariaDB 13.0\n" +
      "Power Simulator: @Scheduled (5000ms loop with night fault detection)"
    );
  }

  // Public Interface
  return {
    init,
    switchTab,
    switchSubTab,
    refreshAllData,
    openRegisterLightModal,
    handleRegisterLightSubmit,
    openDimmingModal,
    handleDimmingSubmit,
    quickSetDimming,
    openNewTransactionModal,
    handleTransactionSubmit,
    resolveFault,
    toggleFaultTicket,
    switchUserRole,
    openLoginModal,
    selectLoginCard,
    confirmUserLogin,
    handleLoginSubmit,
    cancelLogin,
    closeModal,
    openAboutDialog,
    setCurrency,
    formatCurrency
  };
})();

// Bootstrap on DOM load
window.addEventListener('DOMContentLoaded', () => {
  CivicGridApp.init();
});
