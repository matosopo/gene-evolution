(function () {
  'use strict';

  const DEFAULTS = {
    finalStepCount: 2000,
    crowdingFactor: 1000,
    randomSeed: 42,
    spawnProbability: 0.01,
    deathProbability: 0.05,
    replicationProbability: 0.20,
    mutationProbability: 0.02,
    variation: 0.05
  };

  const REDRAW_EVERY = 10;
  const MAX_VISIBLE_SERIES = 25;

  const form = document.getElementById('config-form');
  const runBtn = document.getElementById('run-btn');
  const resetBtn = document.getElementById('reset-btn');
  const statusEl = document.getElementById('status');
  const summaryEl = document.getElementById('summary');
  const summaryList = document.getElementById('summary-list');
  const canvas = document.getElementById('chart');

  let chart = null;
  let series = new Map(); // id -> {data:[{x,y}], peak, colorIdx}
  let totalSteps = 0;
  let stepsSinceRedraw = 0;
  let currentEventSource = null;

  function buildConfigPayload(formData) {
    return {
      finalStepCount: Number(formData.get('finalStepCount')),
      crowdingFactor: Number(formData.get('crowdingFactor')),
      randomSeed: Number(formData.get('randomSeed')),
      spontaneous: {
        spawnProbability: Number(formData.get('spawnProbability')),
        deathProbability: Number(formData.get('deathProbability')),
        replicationProbability: Number(formData.get('replicationProbability')),
        mutationProbability: Number(formData.get('mutationProbability')),
        variation: Number(formData.get('variation'))
      }
    };
  }

  // Golden-ratio hue, mirrors PngGraphRecorder.colorFor(id).
  // HSB(hue, 0.75, 0.85) ≈ HSL(hue, ~68%, ~53%).
  function colorFor(id) {
    const hue = ((Number(id) * 0.6180339887) % 1) * 360;
    return `hsl(${hue}, 68%, 53%)`;
  }

  function setStatus(kind, text) {
    statusEl.className = 'status ' + kind;
    statusEl.textContent = text;
  }

  function ensureChart() {
    if (chart) {
      chart.destroy();
      chart = null;
    }
    chart = new Chart(canvas.getContext('2d'), {
      type: 'line',
      data: { datasets: [] },
      options: {
        animation: false,
        responsive: true,
        maintainAspectRatio: false,
        parsing: false,
        normalized: true,
        elements: {
          point: { radius: 0 },
          line: { borderWidth: 1.5, tension: 0 }
        },
        scales: {
          x: { type: 'linear', title: { display: true, text: 'step' } },
          y: { beginAtZero: true, title: { display: true, text: 'count' } }
        },
        plugins: {
          legend: { position: 'right', labels: { boxWidth: 14, font: { size: 11 } } },
          tooltip: { enabled: false }
        }
      }
    });
  }

  function resetSeries() {
    series = new Map();
    stepsSinceRedraw = 0;
  }

  function recordStep(payload) {
    const step = payload.step;
    for (const s of payload.species) {
      let entry = series.get(s.id);
      if (!entry) {
        entry = { id: s.id, data: [], peak: 0, color: colorFor(s.id) };
        series.set(s.id, entry);
      }
      entry.data.push({ x: step, y: s.count });
      if (s.count > entry.peak) entry.peak = s.count;
    }
  }

  function rebuildDatasets() {
    let globalPeak = 0;
    for (const e of series.values()) if (e.peak > globalPeak) globalPeak = e.peak;
    const threshold = Math.max(5, Math.floor(globalPeak / 100));
    const visible = [];
    for (const e of series.values()) if (e.peak >= threshold) visible.push(e);
    visible.sort((a, b) => b.peak - a.peak);
    const shown = visible.slice(0, MAX_VISIBLE_SERIES);

    chart.data.datasets = shown.map(e => ({
      label: '#' + e.id + ' (' + e.peak + ')',
      data: e.data,
      borderColor: e.color,
      backgroundColor: e.color
    }));
  }

  function maybeRedraw(force) {
    stepsSinceRedraw++;
    if (force || stepsSinceRedraw >= REDRAW_EVERY) {
      rebuildDatasets();
      chart.update('none');
      stepsSinceRedraw = 0;
    }
  }

  function showSummary(payload) {
    summaryList.innerHTML = '';
    const rows = [
      ['steps', payload.steps],
      ['totalN', payload.totalN],
      ['activeSpecies', payload.activeSpecies],
      ['species tracked', series.size],
      ['elapsed', payload.elapsedMs + ' ms']
    ];
    for (const [k, v] of rows) {
      const dt = document.createElement('dt'); dt.textContent = k;
      const dd = document.createElement('dd'); dd.textContent = v;
      summaryList.appendChild(dt); summaryList.appendChild(dd);
    }
    summaryEl.hidden = false;
  }

  function closeStream() {
    if (currentEventSource) {
      currentEventSource.close();
      currentEventSource = null;
    }
  }

  function startStream(runId) {
    closeStream();
    const es = new EventSource('/api/runs/' + encodeURIComponent(runId) + '/events');
    currentEventSource = es;

    es.addEventListener('start', ev => {
      const payload = JSON.parse(ev.data);
      totalSteps = payload.finalStepCount;
      setStatus('running', 'Running — step 0 / ' + totalSteps);
    });

    es.addEventListener('step', ev => {
      const payload = JSON.parse(ev.data);
      recordStep(payload);
      setStatus('running', 'Running — step ' + (payload.step + 1) + ' / ' + totalSteps);
      maybeRedraw(false);
    });

    es.addEventListener('end', ev => {
      const payload = JSON.parse(ev.data);
      maybeRedraw(true);
      setStatus('done', 'Done (' + payload.elapsedMs + ' ms)');
      showSummary(payload);
      runBtn.disabled = false;
      closeStream();
    });

    es.addEventListener('error', ev => {
      try {
        const payload = JSON.parse(ev.data);
        setStatus('error', 'Error: ' + payload.message);
      } catch (_) {
        setStatus('error', 'Connection lost');
      }
      runBtn.disabled = false;
      closeStream();
    });
  }

  async function submitForm(e) {
    e.preventDefault();
    const payload = buildConfigPayload(new FormData(form));

    ensureChart();
    resetSeries();
    summaryEl.hidden = true;
    runBtn.disabled = true;
    setStatus('running', 'Submitting…');

    let resp;
    try {
      resp = await fetch('/api/runs', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
    } catch (err) {
      setStatus('error', 'Network error: ' + err.message);
      runBtn.disabled = false;
      return;
    }

    if (!resp.ok) {
      let msg = 'HTTP ' + resp.status;
      try { msg += ': ' + (await resp.json()).error; } catch (_) {}
      setStatus('error', msg);
      runBtn.disabled = false;
      return;
    }

    const { runId } = await resp.json();
    startStream(runId);
  }

  function resetDefaults() {
    for (const [k, v] of Object.entries(DEFAULTS)) {
      const el = form.elements.namedItem(k);
      if (el) el.value = v;
    }
  }

  form.addEventListener('submit', submitForm);
  resetBtn.addEventListener('click', resetDefaults);

  ensureChart();
  setStatus('idle', 'Idle');
})();
