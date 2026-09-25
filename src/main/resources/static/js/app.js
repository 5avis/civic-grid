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
  let autoRefreshTimer = null;
  let isAutoRefreshEnabled = true;

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

  // Initialize Application
  function init() {
    initClock();
    initEventListeners();
    initCurrency();
    loadAllMasterData();
    refreshAllData();

    // Start 5-second live telemetry refresh to sync with @Scheduled backend simulator
    autoRefreshTimer = setInterval(() => {
      if (isAutoRefreshEnabled) {
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
            <button class="xp-btn xp-btn-sm" onclick="CivicGridApp.openDimmingModal(${light.id}, '${escapeHtml(light.poleCode)}', ${light.dimmingPercentage})">
              Dim
            </button>
          </td>
        </tr>
      `;
    }).join('');
  }

  // Quick Dimming helper
  async function quickSetDimming(lightId, percent) {
    try {
      setStatus(`Setting light #${lightId} brightness to ${percent}%...`);
      const res = await fetch(`/api/street-lights/${lightId}/dimming`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ dimmingPercentage: percent })
      });
      if (!res.ok) throw new Error('HTTP ' + res.status);
      await fetchStreetLights();
      await fetchPowerSummary();
      setStatus(`Light #${lightId} dimming set to ${percent}%.`);
    } catch (err) {
      alert('Failed to set dimming: ' + err.message);
    }
  }

  // Modal: Register Street Light
  function openRegisterLightModal() {
    document.getElementById('new-light-form')?.reset();
    document.getElementById('new-light-dim-val').textContent = '80%';
    document.getElementById('modal-register-light').classList.add('open');
  }

  async function handleRegisterLightSubmit(e) {
    e.preventDefault();
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
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ poleCode, zoneId, dimmingPercentage })
      });

      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.message || 'HTTP ' + res.status);
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
    currentDimmingLightId = id;
    document.getElementById('dimming-pole-label').textContent = `${poleCode} (ID: #${id})`;
    document.getElementById('dimming-slider').value = currentDim;
    document.getElementById('dimming-slider-val').textContent = `${currentDim}%`;
    document.getElementById('modal-dimming').classList.add('open');
  }

  async function handleDimmingSubmit(e) {
    e.preventDefault();
    if (!currentDimmingLightId) return;

    const dimmingPercentage = parseInt(document.getElementById('dimming-slider').value, 10);
    try {
      setStatus(`Updating light #${currentDimmingLightId} to ${dimmingPercentage}%...`);
      const res = await fetch(`/api/street-lights/${currentDimmingLightId}/dimming`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ dimmingPercentage })
      });

      if (!res.ok) throw new Error('HTTP ' + res.status);

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
      renderFaultTickets(tickets);
      setStatus(`Loaded ${tickets.length} fault tickets.`);
    } catch (err) {
      setStatus('Error loading fault tickets: ' + err.message);
    }
  }

  function renderFaultTickets(tickets) {
    const tbody = document.getElementById('faults-tbody');
    if (!tbody) return;

    if (!tickets || tickets.length === 0) {
      tbody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: #155724; padding: 24px; background: #f4faf4;">All street lights operational. No automated fault tickets detected.</td></tr>`;
      return;
    }

    tbody.innerHTML = tickets.map((t) => {
      const isResolved = t.status === 'RESOLVED';
      const statusBadge = isResolved
        ? `<span class="xp-badge xp-badge-resolved">RESOLVED</span>`
        : `<span class="xp-badge xp-badge-fault">OPEN</span>`;

      const resolveBtn = isResolved
        ? `<span style="color: #666; font-size: 10px;">Resolved</span>`
        : `<button class="xp-btn xp-btn-sm xp-btn-default" onclick="CivicGridApp.resolveFault(${t.id})">Resolve Fault</button>`;

      const poleCode = t.streetLight ? t.streetLight.poleCode : `#${t.streetLightId || '?'}`;
      const createdAt = t.createdAt ? new Date(t.createdAt).toLocaleString() : 'N/A';

      return `
        <tr style="${isResolved ? 'opacity: 0.75;' : 'font-weight: 500;'}">
          <td><strong>${escapeHtml(t.ticketNumber || '#' + t.id)}</strong></td>
          <td><strong style="color: #003c74;">${escapeHtml(poleCode)}</strong></td>
          <td style="max-width: 320px;">${escapeHtml(t.description)}</td>
          <td>${createdAt}</td>
          <td>${statusBadge}</td>
          <td>${resolveBtn}</td>
        </tr>
      `;
    }).join('');
  }

  async function resolveFault(ticketId) {
    try {
      setStatus(`Resolving fault ticket #${ticketId}...`);
      const res = await fetch(`/api/fault-tickets/${ticketId}/resolve`, {
        method: 'PUT'
      });
      if (!res.ok) throw new Error('HTTP ' + res.status);

      await fetchFaultTickets();
      await fetchStreetLights();
      await fetchPowerSummary();
      setStatus(`Fault ticket #${ticketId} resolved. Street light restored to ACTIVE.`);
    } catch (err) {
      alert('Error resolving fault ticket: ' + err.message);
    }
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
      : `UNBALANCED (D:${formatCurrency(totalDebit)} ≠ C:${formatCurrency(totalCredit)})`;

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
