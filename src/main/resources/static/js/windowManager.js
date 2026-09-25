/**
 * ============================================================================
 * CivicGrid — Windows XP Window Manager (Pure Vanilla JavaScript)
 * Handles Draggable, Resizable Windows, Taskbar Tabs, Z-Index, and Start Menu
 * ============================================================================
 */

const WindowManager = (function () {
  let highestZIndex = 100;
  const windows = new Map(); // id -> { elem, tabElem, config, isMinimized, isMaximized, prevRect }

  // Initialize Desktop Listeners & Clock
  function init() {
    initClock();
    initStartMenu();
    initDesktopSelection();
  }

  // Live Taskbar Clock
  function initClock() {
    const clockEl = document.getElementById('tray-clock');
    if (!clockEl) return;

    function update() {
      const now = new Date();
      let hours = now.getHours();
      const minutes = String(now.getMinutes()).padStart(2, '0');
      const ampm = hours >= 12 ? 'PM' : 'AM';
      hours = hours % 12 || 12;
      clockEl.textContent = `${hours}:${minutes} ${ampm}`;
    }

    update();
    setInterval(update, 1000);
  }

  // Start Menu Toggle
  function initStartMenu() {
    const startBtn = document.getElementById('start-button');
    const startMenu = document.getElementById('start-menu');

    if (!startBtn || !startMenu) return;

    startBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      const isOpen = startMenu.classList.contains('open');
      if (isOpen) {
        closeStartMenu();
      } else {
        openStartMenu();
      }
    });

    document.addEventListener('click', (e) => {
      if (!startMenu.contains(e.target) && e.target !== startBtn) {
        closeStartMenu();
      }
    });
  }

  function openStartMenu() {
    const startBtn = document.getElementById('start-button');
    const startMenu = document.getElementById('start-menu');
    startMenu.classList.add('open');
    startBtn.classList.add('active');
  }

  function closeStartMenu() {
    const startBtn = document.getElementById('start-button');
    const startMenu = document.getElementById('start-menu');
    startMenu.classList.remove('open');
    startBtn.classList.remove('active');
  }

  // Desktop Icon Click / Selection
  function initDesktopSelection() {
    const icons = document.querySelectorAll('.desktop-icon');
    icons.forEach(icon => {
      icon.addEventListener('click', (e) => {
        e.stopPropagation();
        icons.forEach(i => i.classList.remove('selected'));
        icon.classList.add('selected');
      });

      // Double-click opens corresponding window
      icon.addEventListener('dblclick', () => {
        const targetId = icon.getAttribute('data-window');
        if (targetId && window['open_' + targetId]) {
          window['open_' + targetId]();
        }
      });
    });

    document.getElementById('desktop').addEventListener('click', () => {
      icons.forEach(i => i.classList.remove('selected'));
    });
  }

  // Bring Window to Front
  function focusWindow(id) {
    const entry = windows.get(id);
    if (!entry) return;

    highestZIndex++;
    entry.elem.style.zIndex = highestZIndex;

    // Update active visual states
    windows.forEach((win, winId) => {
      if (winId === id) {
        win.elem.classList.add('active');
        win.tabElem.classList.add('active');
      } else {
        win.elem.classList.remove('active');
        win.tabElem.classList.remove('active');
      }
    });
  }

  // Make Element Draggable via Title Bar
  function makeDraggable(winElem, handleElem, id) {
    let isDragging = false;
    let startX, startY, initialLeft, initialTop;

    handleElem.addEventListener('mousedown', (e) => {
      if (e.target.closest('.xp-window-controls')) return; // Don't drag on button click

      focusWindow(id);
      const entry = windows.get(id);
      if (entry && entry.isMaximized) return; // Cannot drag maximized window

      isDragging = true;
      startX = e.clientX;
      startY = e.clientY;
      initialLeft = winElem.offsetLeft;
      initialTop = winElem.offsetTop;

      function onMouseMove(moveEvent) {
        if (!isDragging) return;
        const dx = moveEvent.clientX - startX;
        const dy = moveEvent.clientY - startY;

        let newLeft = initialLeft + dx;
        let newTop = initialTop + dy;

        // Desktop bounds checking
        newTop = Math.max(0, newTop);
        winElem.style.left = `${newLeft}px`;
        winElem.style.top = `${newTop}px`;
      }

      function onMouseUp() {
        isDragging = false;
        document.removeEventListener('mousemove', onMouseMove);
        document.removeEventListener('mouseup', onMouseUp);
      }

      document.addEventListener('mousemove', onMouseMove);
      document.addEventListener('mouseup', onMouseUp);
    });
  }

  /**
   * Public: Open or restore an XP Window
   * @param {Object} config - { id, title, iconHtml, width, height, left, top, contentHtml, onOpen, onClose }
   */
  function openWindow(config) {
    closeStartMenu();

    // If window already exists
    if (windows.has(config.id)) {
      const entry = windows.get(config.id);
      if (entry.isMinimized) {
        entry.elem.style.display = 'flex';
        entry.isMinimized = false;
      }
      focusWindow(config.id);
      return entry.elem;
    }

    // Default positioning
    const offset = windows.size * 26;
    const defaultLeft = config.left !== undefined ? config.left : Math.max(40, 100 + offset);
    const defaultTop = config.top !== undefined ? config.top : Math.max(20, 40 + offset);
    const defaultWidth = config.width || 680;
    const defaultHeight = config.height || 450;

    // Create Window Element
    const win = document.createElement('div');
    win.className = 'xp-window active';
    win.id = `window-${config.id}`;
    win.style.left = `${defaultLeft}px`;
    win.style.top = `${defaultTop}px`;
    win.style.width = `${defaultWidth}px`;
    win.style.height = `${defaultHeight}px`;

    highestZIndex++;
    win.style.zIndex = highestZIndex;

    win.innerHTML = `
      <div class="xp-title-bar">
        <div class="xp-title-content">
          <div class="xp-title-icon">${config.iconHtml || ''}</div>
          <span>${config.title}</span>
        </div>
        <div class="xp-window-controls">
          <button class="xp-win-btn btn-min" title="Minimize">_</button>
          <button class="xp-win-btn btn-max" title="Maximize">□</button>
          <button class="xp-win-btn btn-close" title="Close">X</button>
        </div>
      </div>
      <div class="xp-menu-bar">
        <div class="xp-menu-item">File</div>
        <div class="xp-menu-item">Edit</div>
        <div class="xp-menu-item">View</div>
        <div class="xp-menu-item">Tools</div>
        <div class="xp-menu-item">Help</div>
      </div>
      <div class="xp-address-bar">
        <span style="color:#555;">Address</span>
        <div class="xp-address-input">
          <span style="color:#2f71cd; margin-right:4px;">C:\\CivicGrid\\${config.title}</span>
        </div>
      </div>
      <div class="xp-window-body" id="body-${config.id}">
        ${config.contentHtml || ''}
      </div>
      <div class="xp-status-bar">
        <span>Ready</span>
        <span>CivicGrid OS</span>
      </div>
    `;

    document.getElementById('desktop').appendChild(win);

    // Create Taskbar Tab
    const tab = document.createElement('div');
    tab.className = 'taskbar-tab active';
    tab.id = `tab-${config.id}`;
    tab.innerHTML = `
      <span>${config.title}</span>
    `;
    document.getElementById('taskbar-windows').appendChild(tab);

    const windowEntry = {
      elem: win,
      tabElem: tab,
      config: config,
      isMinimized: false,
      isMaximized: false,
      prevRect: null
    };

    windows.set(config.id, windowEntry);

    // Focus on click
    win.addEventListener('mousedown', () => focusWindow(config.id));

    // Dragging setup
    const titleBar = win.querySelector('.xp-title-bar');
    makeDraggable(win, titleBar, config.id);

    // Minimize Button
    win.querySelector('.btn-min').addEventListener('click', (e) => {
      e.stopPropagation();
      minimizeWindow(config.id);
    });

    // Maximize Button
    win.querySelector('.btn-max').addEventListener('click', (e) => {
      e.stopPropagation();
      toggleMaximize(config.id);
    });

    // Close Button
    win.querySelector('.btn-close').addEventListener('click', (e) => {
      e.stopPropagation();
      closeWindow(config.id);
    });

    // Taskbar Tab Click Toggle
    tab.addEventListener('click', () => {
      if (windowEntry.isMinimized) {
        win.style.display = 'flex';
        windowEntry.isMinimized = false;
        focusWindow(config.id);
      } else if (win.classList.contains('active')) {
        minimizeWindow(config.id);
      } else {
        focusWindow(config.id);
      }
    });

    focusWindow(config.id);

    if (typeof config.onOpen === 'function') {
      config.onOpen(win.querySelector(`#body-${config.id}`));
    }

    return win;
  }

  function minimizeWindow(id) {
    const entry = windows.get(id);
    if (!entry) return;
    entry.elem.style.display = 'none';
    entry.isMinimized = true;
    entry.elem.classList.remove('active');
    entry.tabElem.classList.remove('active');
  }

  function toggleMaximize(id) {
    const entry = windows.get(id);
    if (!entry) return;

    if (!entry.isMaximized) {
      entry.prevRect = {
        left: entry.elem.style.left,
        top: entry.elem.style.top,
        width: entry.elem.style.width,
        height: entry.elem.style.height
      };
      entry.elem.style.left = '0px';
      entry.elem.style.top = '0px';
      entry.elem.style.width = '100vw';
      entry.elem.style.height = 'calc(100vh - 30px)';
      entry.isMaximized = true;
      entry.elem.querySelector('.btn-max').textContent = '□';
    } else {
      entry.elem.style.left = entry.prevRect.left;
      entry.elem.style.top = entry.prevRect.top;
      entry.elem.style.width = entry.prevRect.width;
      entry.elem.style.height = entry.prevRect.height;
      entry.isMaximized = false;
      entry.elem.querySelector('.btn-max').textContent = '□';
    }
    focusWindow(id);
  }

  function closeWindow(id) {
    const entry = windows.get(id);
    if (!entry) return;

    if (typeof entry.config.onClose === 'function') {
      entry.config.onClose();
    }

    entry.elem.remove();
    entry.tabElem.remove();
    windows.delete(id);
  }

  return {
    init,
    openWindow,
    focusWindow,
    closeWindow
  };
})();

// Self-initialize on DOM ready
document.addEventListener('DOMContentLoaded', () => {
  WindowManager.init();
});
