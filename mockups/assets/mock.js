// Shared icon sprite (Lucide-derived paths, 24px grid, stroked) and screen switcher.
const ICONS = {
  settings: '<path d="M21 4h-7M10 4H3M21 12h-9M8 12H3M21 20h-5M12 20H3M14 2v4M8 10v4M16 18v4"/>',
  plus: '<path d="M5 12h14M12 5v14"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
  help: '<circle cx="12" cy="12" r="10"/><path d="M9.1 9a3 3 0 0 1 5.8 1c0 2-3 3-3 3M12 17h.01"/>',
  monitor: '<rect x="2" y="3" width="20" height="14" rx="2"/><path d="M8 21h8M12 17v4"/>',
  play: '<path d="M7 4.5v15l12.5-7.5z" fill="currentColor" stroke="none"/>',
  chart: '<path d="M3 3v18h18M8 17v-6M13 17V7M18 17v-4"/>',
  mouse: '<rect x="6" y="3" width="12" height="18" rx="6"/><path d="M12 7v4"/>',
  gamepad: '<path d="M6 11h4M8 9v4M15 12h.01M18 10h.01"/><path d="M17.3 5H6.7a4 4 0 0 0-3.96 3.44L2 14.6a3 3 0 0 0 5.2 2.2L9 15h6l1.8 1.8a3 3 0 0 0 5.2-2.2l-.74-6.16A4 4 0 0 0 17.3 5z"/>',
  keyboard: '<rect x="2" y="5" width="20" height="14" rx="2"/><path d="M6 9h.01M10 9h.01M14 9h.01M18 9h.01M6 13h.01M18 13h.01M10 13h4M7 16h10"/>',
  command: '<path d="M4 17l6-6-6-6M12 19h8"/>',
  unlink: '<path d="M9 17H7A5 5 0 0 1 7 7h2M15 7h2a5 5 0 0 1 4 8M8 12h3M2 2l20 20"/>',
  power: '<path d="M12 2v10M18.4 6.6a9 9 0 1 1-12.8 0"/>',
  left: '<path d="M15 18l-6-6 6-6"/>',
  right: '<path d="M9 18l6-6-6-6"/>',
  down: '<path d="M6 9l6 6 6-6"/>',
  check: '<path d="M20 6L9 17l-5-5"/>',
  menu: '<path d="M4 6h16M4 12h16M4 18h16"/>',
  view: '<rect x="3" y="5" width="12" height="10" rx="1.5"/><rect x="9" y="9" width="12" height="10" rx="1.5"/>',
  moon: '<path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9z"/>',
  lock: '<rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/>',
  info: '<circle cx="12" cy="12" r="10"/><path d="M12 16v-4M12 8h.01"/>',
  eyeoff: '<path d="M9.9 4.2A10 10 0 0 1 22 12a13 13 0 0 1-2.2 3M6.6 6.6A13 13 0 0 0 2 12s3.5 7 10 7a9.7 9.7 0 0 0 5.4-1.6M14.1 14.1a3 3 0 1 1-4.2-4.2M2 2l20 20"/>',
  home: '<path d="M3 10l9-7 9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1z"/>',
  grid: '<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>',
  speaker: '<path d="M11 5L6 9H2v6h4l5 4z"/><path d="M15.5 8.5a5 5 0 0 1 0 7M19 5a10 10 0 0 1 0 14"/>',
  film: '<rect x="2" y="3" width="20" height="18" rx="2"/><path d="M7 3v18M17 3v18M2 8h5M2 16h5M17 8h5M17 16h5"/>',
  layers: '<path d="M12 2l10 5-10 5L2 7z"/><path d="M2 12l10 5 10-5M2 17l10 5 10-5"/>',
  globe: '<circle cx="12" cy="12" r="10"/><path d="M2 12h20M12 2a15 15 0 0 1 0 20M12 2a15 15 0 0 0 0 20"/>',
  wifi: '<path d="M5 12.55a11 11 0 0 1 14.08 0M1.42 9a16 16 0 0 1 21.16 0M8.53 16.11a6 6 0 0 1 6.95 0M12 20h.01"/>',
  pin: '<path d="M12 17v5M9 10.8V4h6v6.8l3 3.2v2H6v-2z"/>',
  more: '<circle cx="5" cy="12" r="1.6" fill="currentColor"/><circle cx="12" cy="12" r="1.6" fill="currentColor"/><circle cx="19" cy="12" r="1.6" fill="currentColor"/>',
  backspace: '<path d="M21 4H8l-7 8 7 8h13a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2zM18 9l-6 6M12 9l6 6"/>',
  space: '<path d="M4 10v4h16v-4"/>',
  enter: '<path d="M9 10l-5 5 5 5"/><path d="M20 4v7a4 4 0 0 1-4 4H4"/>',
  shift: '<path d="M12 3l9 9h-5v8H8v-8H3z"/>',
};

document.addEventListener('DOMContentLoaded', () => {
  const sprite = Object.entries(ICONS).map(([k, v]) =>
    `<symbol id="i-${k}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${v}</symbol>`).join('');
  document.body.insertAdjacentHTML('afterbegin', `<svg width="0" height="0" style="position:absolute" aria-hidden="true">${sprite}</svg>`);
  document.querySelectorAll('i[data-i]').forEach(el => {
    el.outerHTML = `<svg class="ic ${el.className}" aria-hidden="true"><use href="#i-${el.dataset.i}"/></svg>`;
  });

  const solo = new URLSearchParams(location.search).get('s');
  if (solo) {
    document.body.classList.add('solo');
    document.querySelectorAll('.screen').forEach(s => { if (s.id !== solo) s.remove(); });
  } else {
    const fit = () => {
      const z = Math.min(1, (window.innerWidth - 64) / 1920);
      document.querySelectorAll('.frame').forEach(f => f.style.zoom = z);
    };
    fit();
    window.addEventListener('resize', fit);
  }
});
