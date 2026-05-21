// Exact palette extracted from server.jar:
// dk/dtu/compute/mavis/domain/gridworld/hospital/Colors.class static initializer.
// Do not adjust - these are the canonical Mavis RGB values.
const colors = {
  BLUE:      '#3050ff',  // (48, 80, 255)
  RED:       '#ff0000',
  CYAN:      '#00ffff',
  PURPLE:    '#6000b0',
  GREEN:     '#00ff00',
  ORANGE:    '#ff8000',
  PINK:      '#f060c0',
  GREY:      '#707070',
  LIGHTBLUE: '#70c0ff',
  BROWN:     '#603000',
  DEFAULT:   '#c0c0c0',  // matches LIGHT_GRAY floor as a sane fallback
};

let replay = null;
let step = 0;
let timer = null;
let highlightCoord = null;

const els = {
  app:           document.querySelector('.app'),
  boardWrap:     document.querySelector('.boardWrap'),
  meta:          document.getElementById('meta'),
  fileInput:     document.getElementById('fileInput'),
  dropZone:      document.getElementById('dropZone'),
  playBtn:       document.getElementById('playBtn'),
  prevBtn:       document.getElementById('prevBtn'),
  nextBtn:       document.getElementById('nextBtn'),
  slider:        document.getElementById('stepSlider'),
  stepInput:     document.getElementById('stepInput'),
  stepText:      document.getElementById('stepText'),
  trackInput:    document.getElementById('trackInput'),
  coordInput:    document.getElementById('coordInput'),
  coordGoBtn:    document.getElementById('coordGoBtn'),
  coordClearBtn: document.getElementById('coordClearBtn'),
  targetInput:   document.getElementById('targetInput'),
  reachInput:    document.getElementById('reachInput'),
  autoPlayInput: document.getElementById('autoPlayInput'),
  speedInput:    document.getElementById('speedInput'),
  scanBtn:       document.getElementById('scanBtn'),
  actions:       document.getElementById('actions'),
  events:        document.getElementById('events'),
  hoverInfo:     document.getElementById('hoverInfo'),
  boards:        document.getElementById('boards'),
  panViewport:   document.getElementById('panViewport'),
  panSurface:    document.getElementById('panSurface'),
  axisTopInner:  document.getElementById('axisTopInner'),
  axisLeftInner: document.getElementById('axisLeftInner'),
  hoverTip:      document.getElementById('hoverTip'),
  togglePanel:   document.getElementById('togglePanel'),
  statusInfo:    document.getElementById('statusInfo'),
  zoomInfo:      document.getElementById('zoomInfo'),
  resetViewBtn:  document.getElementById('resetView'),
};

// Fixed cell size. Zoom multiplies this; effective cell = BASE_CELL * zoom.
const BASE_CELL = 30;
const MIN_ZOOM  = 0.3;
const MAX_ZOOM  = 4.0;

const state = {
  zoom: 1,
  pan:  { x: 0, y: 0 },
};

init();

function init() {
  els.fileInput.addEventListener('change', e => {
    const file = e.target.files && e.target.files[0];
    if (file) loadFile(file);
  });
  ['dragenter', 'dragover'].forEach(type => els.dropZone.addEventListener(type, e => {
    e.preventDefault(); els.dropZone.classList.add('drag');
  }));
  ['dragleave', 'drop'].forEach(type => els.dropZone.addEventListener(type, e => {
    e.preventDefault(); els.dropZone.classList.remove('drag');
  }));
  els.dropZone.addEventListener('drop', e => {
    const file = e.dataTransfer.files && e.dataTransfer.files[0];
    if (file) loadFile(file);
  });
  els.playBtn.addEventListener('click', togglePlay);
  els.prevBtn.addEventListener('click', () => setStep(step - 1));
  els.nextBtn.addEventListener('click', () => setStep(step + 1));
  els.slider.addEventListener('input', e => setStep(Number(e.target.value)));
  els.stepInput.addEventListener('change', e => setStep(Number(e.target.value)));
  els.trackInput.addEventListener('input', render);
  els.coordGoBtn.addEventListener('click', () => {
    const coord = parseCoord(els.coordInput.value);
    if (coord) {
      highlightCoord = coord;
      centerOn(coord.r, coord.c);
    } else {
      highlightCoord = null;
    }
    render();
  });
  els.coordClearBtn.addEventListener('click', () => {
    highlightCoord = null;
    render();
  });
  els.coordInput.addEventListener('keydown', e => {
    if (e.key === 'Enter') els.coordGoBtn.click();
  });
  els.targetInput.addEventListener('input', render);
  els.reachInput.addEventListener('change', render);
  els.scanBtn.addEventListener('click', scanReachabilityRegression);

  els.togglePanel.addEventListener('click', () => {
    els.app.classList.toggle('collapsed');
    requestAnimationFrame(resetView);
  });

  els.resetViewBtn.addEventListener('click', resetView);
  els.boards.addEventListener('dblclick', resetView);

  // Floating coord tooltip
  els.boards.addEventListener('mousemove', e => {
    const cell = e.target.closest('.cell');
    if (!cell || !cell.dataset.r) {
      els.hoverTip.classList.remove('show');
      return;
    }
    const r = cell.dataset.r;
    const c = cell.dataset.c;
    const rect = els.boards.getBoundingClientRect();
    els.hoverTip.textContent = `(${r}, ${c})`;
    els.hoverTip.style.left = `${e.clientX - rect.left + 14}px`;
    els.hoverTip.style.top  = `${e.clientY - rect.top  + 14}px`;
    els.hoverTip.classList.add('show');
    els.hoverInfo.textContent = `Java coordinates: (${r}, ${c})`;
  });
  els.boards.addEventListener('mouseleave', () => {
    els.hoverTip.classList.remove('show');
  });

  // Wheel = zoom around cursor
  els.boards.addEventListener('wheel', e => {
    if (!replay) return;
    e.preventDefault();
    const factor = e.deltaY < 0 ? 1.1 : 1 / 1.1;
    zoomAt(e.clientX, e.clientY, factor);
  }, { passive: false });

  // Left-button drag = pan
  let dragging = false;
  let last = { x: 0, y: 0 };
  els.boards.addEventListener('mousedown', e => {
    if (e.button !== 0 || !replay) return;
    dragging = true;
    last.x = e.clientX;
    last.y = e.clientY;
    els.boards.classList.add('grabbing');
    e.preventDefault();
  });
  window.addEventListener('mousemove', e => {
    if (!dragging) return;
    state.pan.x += e.clientX - last.x;
    state.pan.y += e.clientY - last.y;
    last.x = e.clientX;
    last.y = e.clientY;
    applyPan();
  });
  window.addEventListener('mouseup', () => {
    if (!dragging) return;
    dragging = false;
    els.boards.classList.remove('grabbing');
  });

  window.addEventListener('resize', () => {
    if (state.zoom === 1) resetView();
  });

  applyZoom();
  applyPan();

  if (window.DEFAULT_REPLAY) {
    loadReplayData(window.DEFAULT_REPLAY);
  } else {
    render();
  }
}

// ---- Zoom / Pan -----------------------------------------------------------

function applyZoom() {
  const cell = Math.round(BASE_CELL * state.zoom);
  document.documentElement.style.setProperty('--cell', `${cell}px`);
  document.documentElement.style.setProperty('--axis', `${cell}px`);
  els.zoomInfo.textContent = `${Math.round(state.zoom * 100)}%`;
}

function applyPan() {
  const tx = `${state.pan.x}px`;
  const ty = `${state.pan.y}px`;
  els.panSurface.style.transform   = `translate(${tx}, ${ty})`;
  els.axisTopInner.style.transform  = `translateX(${tx})`;
  els.axisLeftInner.style.transform = `translateY(${ty})`;
}

function centerOn(r, c) {
  if (!replay) return;
  state.zoom = 1;
  applyZoom();
  requestAnimationFrame(() => {
    const cellSize = BASE_CELL;
    const cellCenterX = 1 + c * (cellSize + 1) + cellSize / 2;
    const cellCenterY = 1 + r * (cellSize + 1) + cellSize / 2;
    state.pan.x = Math.round(els.panViewport.clientWidth / 2 - cellCenterX);
    state.pan.y = Math.round(els.panViewport.clientHeight / 2 - cellCenterY);
    applyPan();
  });
}

function resetView() {
  state.zoom = 1;
  applyZoom();
  if (!replay) { state.pan.x = state.pan.y = 0; applyPan(); return; }
  requestAnimationFrame(() => {
    const card = els.panSurface.firstElementChild;
    if (!card) { state.pan.x = state.pan.y = 0; applyPan(); return; }
    const boardW = card.offsetWidth;
    const boardH = card.offsetHeight;
    state.pan.x = Math.round((els.panViewport.clientWidth  - boardW) / 2);
    state.pan.y = Math.round((els.panViewport.clientHeight - boardH) / 2);
    applyPan();
  });
}

function zoomAt(clientX, clientY, factor) {
  const newZoom = clamp(state.zoom * factor, MIN_ZOOM, MAX_ZOOM);
  const realFactor = newZoom / state.zoom;
  if (realFactor === 1) return;

  const rect = els.panViewport.getBoundingClientRect();
  const mx = clientX - rect.left;
  const my = clientY - rect.top;
  state.pan.x = mx - (mx - state.pan.x) * realFactor;
  state.pan.y = my - (my - state.pan.y) * realFactor;
  state.zoom = newZoom;

  applyZoom();
  applyPan();
}

function rebuildAxes(rows, cols) {
  els.axisTopInner.innerHTML = '';
  els.axisLeftInner.innerHTML = '';
  els.axisTopInner.style.gridTemplateColumns = `repeat(${cols}, var(--axis))`;
  els.axisTopInner.style.gridTemplateRows    = `var(--axis)`;
  els.axisLeftInner.style.gridTemplateColumns = `var(--axis)`;
  els.axisLeftInner.style.gridTemplateRows    = `repeat(${rows}, var(--axis))`;
  for (let c = 0; c < cols; c++) els.axisTopInner.appendChild(div('axisCell', String(c)));
  for (let r = 0; r < rows; r++) els.axisLeftInner.appendChild(div('axisCell', String(r)));
}

// ---- Data loading ----------------------------------------------------------

async function loadFile(file) {
  const text = await file.text();
  loadReplayData(JSON.parse(text));
}

function loadReplayData(data) {
  replay = data;
  step = 0;
  els.slider.max = replay.frames.length - 1;
  els.stepInput.max = replay.frames.length - 1;
  const s = replay.summary || {};
  els.meta.innerHTML = `<b>${escapeHtml(replay.level.name)}</b><br>${replay.level.rows}x${replay.level.cols}, ${replay.frames.length} frames<br>${escapeHtml(s.outcome || 'unknown')} | ${s.executedSteps ?? 0} steps | ${s.satisfiedBoxGoals ?? 0}/${s.totalBoxGoals ?? 0} box goals<br>${escapeHtml(replay.generatedAt || '')}`;
  buildBoard();
  render();
  if (els.autoPlayInput.checked) startPlay();
}

// ---- Board construction (axes + panSurface) --------------------------------

function buildBoard() {
  const { rows, cols } = replay.level;
  rebuildAxes(rows, cols);

  els.boards.classList.remove('empty');
  for (const e of els.boards.querySelectorAll(':scope > .empty')) e.remove();
}

// ---- Render current frame --------------------------------------------------

function render() {
  if (!replay) {
    els.panSurface.innerHTML = '';
    els.boards.classList.add('empty');
    for (const e of els.boards.querySelectorAll(':scope > .empty')) e.remove();
    const empty = document.createElement('div');
    empty.className = 'empty';
    empty.textContent = 'Drop a replay JSON file to begin.';
    els.boards.appendChild(empty);
    els.statusInfo.textContent = '';
    return;
  }
  els.boards.classList.remove('empty');
  for (const e of els.boards.querySelectorAll(':scope > .empty')) e.remove();
  step = Math.max(0, Math.min(step, replay.frames.length - 1));
  const frame = replay.frames[step];
  const prev = replay.frames[Math.max(0, step - 1)];
  els.slider.value = step;
  els.stepInput.value = step;
  els.stepText.textContent = `/ ${replay.frames.length - 1}`;

  const { rows, cols } = replay.level;
  const goals = goalMap();
  const agents = new Map(frame.agents.map(a => [`${a.r},${a.c}`, a]));
  const boxes = new Map(frame.boxes.map(b => [`${b.r},${b.c}`, b]));
  const changed = changedCells(prev, frame);
  const track = trackedCells();
  const trail = trailCells();
  const coord = highlightCoord;
  const target = parseCoord(els.targetInput.value);
  const reachable = els.reachInput.checked ? reachableCells(frame, selectedAgentId()) : new Set();

  // Rebuild the board DOM on panSurface
  const card = document.createElement('div');
  card.className = 'boardCard';

  const board = document.createElement('div');
  board.className = 'board';
  board.style.gridTemplateColumns = `repeat(${cols}, var(--cell))`;

  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < cols; c++) {
      const key = `${r},${c}`;
      const cell = document.createElement('div');
      cell.className = 'cell';
      cell.dataset.r = r;
      cell.dataset.c = c;

      if (replay.level.walls[r][c] === '+') cell.classList.add('wall');

      // Goal background - match level-viewer's goal-fill / goal-solved
      if (goals.has(key)) {
        const goalType = goals.get(key);
        const satisfied = isGoalSatisfiedInFrame(frame, r, c, goalType);
        cell.classList.add(satisfied ? 'goal-solved' : 'goal-fill');
        const gc = document.createElement('span');
        gc.className = 'goalChar';
        gc.textContent = goalType;
        cell.appendChild(gc);
      }

      // Highlights (order matters - later ones paint on top)
      if (trail.has(key)) cell.classList.add('trail');
      if (reachable.has(key)) cell.classList.add('reachable');
      if (changed.has(key)) cell.classList.add('changed');
      if (track.has(key)) cell.classList.add('track');
      if (coord && coord.r === r && coord.c === c) cell.classList.add('coord');
      if (target && target.r === r && target.c === c && !reachable.has(key)) cell.classList.add('unreachableTarget');

      // Tokens
      if (boxes.has(key)) cell.appendChild(token(boxes.get(key).type, boxColor(boxes.get(key).type)));
      if (agents.has(key)) cell.appendChild(token(String(agents.get(key).id), agentColor(agents.get(key).id), 'agentToken'));

      board.appendChild(cell);
    }
  }

  card.appendChild(board);
  els.panSurface.innerHTML = '';
  els.panSurface.appendChild(card);

  els.statusInfo.textContent = `${rows}x${cols} | ${replay.frames.length} frames | step ${step}/${replay.frames.length - 1}`;

  renderActions(frame);
}

function isGoalSatisfiedInFrame(frame, r, c, goalType) {
  // Box goal: a box of the correct type is at (r,c) in this frame
  for (const b of frame.boxes) {
    if (b.r === r && b.c === c && b.type === goalType) return true;
  }
  // Agent goal: an agent with matching id is at (r,c) in this frame
  for (const a of frame.agents) {
    if (a.r === r && a.c === c && String(a.id) === goalType) return true;
  }
  return false;
}

// ---- Actions panel ---------------------------------------------------------

function renderActions(frame) {
  els.actions.innerHTML = '';
  const actions = frame.actions || [];
  const accepted = frame.accepted || [];
  for (let i = 0; i < actions.length; i++) {
    const row = document.createElement('div');
    row.className = `actionRow ${accepted[i] ? '' : 'rejected'}`;
    const left = document.createElement('span');
    left.textContent = i;
    const right = document.createElement('span');
    right.className = 'act';
    right.textContent = actions[i];
    row.appendChild(left);
    row.appendChild(right);
    els.actions.appendChild(row);
  }
}

// ---- Playback --------------------------------------------------------------

function setStep(next) {
  step = Math.max(0, Math.min(next, replay ? replay.frames.length - 1 : 0));
  render();
}

function togglePlay() {
  if (!replay) return;
  if (timer) {
    clearInterval(timer); timer = null;
    els.playBtn.textContent = 'Play';
    els.playBtn.classList.remove('active');
    return;
  }
  startPlay();
}

function startPlay() {
  if (!replay || timer) return;
  els.playBtn.textContent = 'Pause';
  els.playBtn.classList.add('active');
  timer = setInterval(() => {
    if (step >= replay.frames.length - 1) { togglePlay(); return; }
    setStep(step + 1);
  }, Math.max(20, Number(els.speedInput.value) || 120));
}

// ---- Helpers ---------------------------------------------------------------

function goalMap() {
  const out = new Map();
  for (const g of replay.level.boxGoals) out.set(`${g.r},${g.c}`, g.type);
  for (const g of replay.level.agentGoals) out.set(`${g.r},${g.c}`, String(g.agent));
  return out;
}

function changedCells(prev, cur) {
  const out = new Set();
  if (!prev || prev === cur) return out;
  const addAgentMoves = () => {
    const before = new Map(prev.agents.map(x => [x.id, x]));
    for (const x of cur.agents) {
      const y = before.get(x.id);
      if (!y || y.r !== x.r || y.c !== x.c) {
        out.add(`${x.r},${x.c}`);
        if (y) out.add(`${y.r},${y.c}`);
      }
    }
  };
  const addBoxMoves = () => {
    const before = new Set(prev.boxes.map(x => `${x.type}@${x.r},${x.c}`));
    const after = new Set(cur.boxes.map(x => `${x.type}@${x.r},${x.c}`));
    for (const x of cur.boxes) if (!before.has(`${x.type}@${x.r},${x.c}`)) out.add(`${x.r},${x.c}`);
    for (const x of prev.boxes) if (!after.has(`${x.type}@${x.r},${x.c}`)) out.add(`${x.r},${x.c}`);
  };
  addAgentMoves();
  addBoxMoves();
  return out;
}

function trackedCells() {
  const q = els.trackInput.value.trim().toLowerCase();
  const out = new Set();
  if (!q || !replay) return out;
  const frame = replay.frames[step];
  if (q.startsWith('agent')) {
    const id = Number(q.replace('agent', ''));
    for (const a of frame.agents) if (a.id === id) out.add(`${a.r},${a.c}`);
  } else if (q.startsWith('box')) {
    const type = q.replace('box', '').toUpperCase();
    for (const b of frame.boxes) if (b.type === type) out.add(`${b.r},${b.c}`);
  }
  return out;
}

function trailCells() {
  const q = els.trackInput.value.trim().toLowerCase();
  const out = new Set();
  if (!q || !replay) return out;
  for (let i = 0; i <= step; i++) {
    const frame = replay.frames[i];
    if (q.startsWith('agent')) {
      const id = Number(q.replace('agent', ''));
      for (const a of frame.agents) if (a.id === id) out.add(`${a.r},${a.c}`);
    } else if (q.startsWith('box')) {
      const type = q.replace('box', '').toUpperCase();
      for (const b of frame.boxes) if (b.type === type) out.add(`${b.r},${b.c}`);
    }
  }
  return out;
}

function selectedAgentId() {
  const q = els.trackInput.value.trim().toLowerCase();
  if (!q.startsWith('agent')) return null;
  const id = Number(q.replace('agent', ''));
  return Number.isFinite(id) ? id : null;
}

function parseCoord(text) {
  const m = String(text || '').trim().match(/^\(?\s*(\d+)\s*[, ]\s*(\d+)\s*\)?$/);
  if (!m) return null;
  return { r: Number(m[1]), c: Number(m[2]) };
}

function reachableCells(frame, agentId) {
  const out = new Set();
  if (agentId == null || !frame) return out;
  const agent = frame.agents.find(a => a.id === agentId);
  if (!agent) return out;
  const blocked = new Set(frame.boxes.map(b => `${b.r},${b.c}`));
  for (const a of frame.agents) {
    if (a.id !== agentId) blocked.add(`${a.r},${a.c}`);
  }
  const q = [{ r: agent.r, c: agent.c }];
  out.add(`${agent.r},${agent.c}`);
  for (let head = 0; head < q.length; head++) {
    const p = q[head];
    for (const d of [[1,0],[-1,0],[0,1],[0,-1]]) {
      const r = p.r + d[0], c = p.c + d[1];
      const key = `${r},${c}`;
      if (out.has(key) || isWall(r, c) || blocked.has(key)) continue;
      out.add(key);
      q.push({ r, c });
    }
  }
  return out;
}

function isWall(r, c) {
  return r < 0 || c < 0 || r >= replay.level.rows || c >= replay.level.cols
      || replay.level.walls[r][c] === '+';
}

// ---- Reachability regression scan ------------------------------------------

function scanReachabilityRegression() {
  if (!replay) return;
  const agentId = selectedAgentId();
  const target = parseCoord(els.targetInput.value) || parseCoord(els.coordInput.value);
  els.events.innerHTML = '';
  if (agentId == null || !target) {
    els.events.textContent = 'Set Track object to agentN and target to r,c.';
    return;
  }
  const targetKey = `${target.r},${target.c}`;
  const rows = [];
  let prevCan = reachableCells(replay.frames[0], agentId).has(targetKey);
  for (let i = 1; i < replay.frames.length; i++) {
    const can = reachableCells(replay.frames[i], agentId).has(targetKey);
    if (prevCan && !can) {
      rows.push(describeRegression(i, agentId, targetKey));
    }
    prevCan = can;
  }
  if (rows.length === 0) {
    els.events.textContent = `No reachable -> unreachable transition found for agent${agentId} to (${target.r},${target.c}).`;
    return;
  }
  for (const row of rows.slice(0, 20)) {
    const el = div('eventRow', '');
    el.innerHTML = `<b>step ${row.step}</b> agent${agentId} lost target ${escapeHtml(targetKey)}<br>${escapeHtml(row.reason)}<br>${escapeHtml(row.actions)}`;
    el.addEventListener('click', () => setStep(row.step));
    els.events.appendChild(el);
  }
}

function describeRegression(stepIdx, agentId, targetKey) {
  const prev = replay.frames[stepIdx - 1];
  const cur = replay.frames[stepIdx];
  const moved = movedObjects(prev, cur);
  const goalInfo = goalMap();
  const interesting = moved
    .filter(x => x.kind === 'box' || x.kind === 'agent')
    .map(x => `${x.kind}${x.id} ${x.from}->${x.to}${goalInfo.has(x.to) ? ' on-goal ' + goalInfo.get(x.to) : ''}`);
  const actions = (cur.actions || []).map((a, i) => `${i}:${a}${cur.accepted && cur.accepted[i] ? '' : ' rejected'}`).join(' | ');
  return {
    step: stepIdx,
    reason: interesting.length ? interesting.join('; ') : 'No moved object identified; inspect changed cells.',
    actions
  };
}

function movedObjects(prev, cur) {
  const out = [];
  const prevAgents = new Map(prev.agents.map(a => [a.id, a]));
  for (const a of cur.agents) {
    const p = prevAgents.get(a.id);
    if (!p || p.r !== a.r || p.c !== a.c) {
      out.push({ kind: 'agent', id: a.id, from: p ? `${p.r},${p.c}` : '?', to: `${a.r},${a.c}` });
    }
  }
  const prevBoxes = new Set(prev.boxes.map(b => `${b.type}@${b.r},${b.c}`));
  const curBoxes = new Set(cur.boxes.map(b => `${b.type}@${b.r},${b.c}`));
  for (const b of cur.boxes) {
    const here = `${b.type}@${b.r},${b.c}`;
    if (prevBoxes.has(here)) continue;
    const candidates = prev.boxes.filter(x => x.type === b.type && !curBoxes.has(`${x.type}@${x.r},${x.c}`));
    const from = candidates.length ? `${candidates[0].r},${candidates[0].c}` : '?';
    out.push({ kind: 'box', id: b.type, from, to: `${b.r},${b.c}` });
  }
  return out;
}

// ---- DOM utilities ---------------------------------------------------------

function token(text, color, extraClass = '') {
  const el = document.createElement('span');
  el.className = extraClass ? `token ${extraClass}` : 'token';
  el.textContent = text;
  el.style.background = color;
  return el;
}
function boxColor(type) { return colors[replay.level.boxColors[type]] || colors.DEFAULT; }
function agentColor(id) { return colors[replay.level.agentColors[String(id)]] || colors.DEFAULT; }
function div(cls, text) { const el = document.createElement('div'); el.className = cls; el.textContent = text; return el; }
function escapeHtml(s) { return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }
function clamp(v, lo, hi) { return Math.max(lo, Math.min(hi, v)); }
