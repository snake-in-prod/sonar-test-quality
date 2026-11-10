/* Project page "Test Quality": headline tiles with trends, then charts of the gate, mutants, test health, test reach,
   issues by rule, hot spots, slowest tests and history. Plain DOM, charts are inline SVG. */
window.registerExtension('testquality/testquality', function (options) {
  var base = options.baseUrl || window.baseUrl || '';
  var project = options.component.key;
  var branchLike = options.branchLike;
  // same rule as the SonarQube UI: a non-main branch by name, a pull request by key, the main branch with nothing
  var scope = {};
  if (branchLike && branchLike.isMain !== undefined && !branchLike.isMain) {
    scope.branch = branchLike.name;
  } else if (branchLike && branchLike.key !== undefined) {
    scope.pullRequest = branchLike.key;
  }
  var SVG = 'http://www.w3.org/2000/svg';
  var REPOSITORIES = ['testquality-python', 'testquality-js', 'testquality-ts', 'testquality-dart'];
  var MUTATION_HINT = 'Set sonar.testquality.mutation.reportPaths';
  var RUN_HINT = 'Set sonar.testquality.junit.reportPaths or sonar.testquality.dart.reportPaths';
  var NAMES = {
    tq_mutation_score: 'Mutation score', tq_tests_without_assertion: 'Tests without assertions',
    tq_correctness_issues: 'Correctness issues', tq_flaky_tests: 'Flaky tests', tq_tests: 'Tests',
    tq_skipped_tests: 'Skipped tests', tq_assertions_per_test: 'Assertions per test', tq_mocks: 'Mocks',
    tq_mock_heavy_tests: 'Mock-heavy tests', tq_test_duration_p95: 'Test duration p95', tq_test_reach: 'Test reach',
    tq_source_files: 'Source files', tq_untested_source_files: 'Untested source files',
    tq_mutants_survived: 'Survived mutants', tq_mutants: 'Mutants'
  };
  var TILES = [
    {key: 'tq_mutation_score', unit: '%', higherIsBetter: true, word: 'no report', hint: MUTATION_HINT},
    {key: 'tq_tests_without_assertion', word: 'no tests', hint: 'No test files found; check sonar.tests or the test patterns'},
    {key: 'tq_correctness_issues', word: 'no tests', hint: 'No test files found; check sonar.tests or the test patterns'},
    {key: 'tq_flaky_tests', word: 'no report', hint: RUN_HINT + ' with the reports of several runs'}
  ];
  var NEW = ['new_tq_tests', 'new_tq_tests_without_assertion', 'new_tq_correctness_issues', 'new_tq_untested_source_files'];
  var MEASURES = ['tq_tests', 'tq_tests_without_assertion', 'tq_skipped_tests', 'tq_assertions_per_test', 'tq_mocks',
    'tq_correctness_issues', 'tq_flaky_tests', 'tq_mutation_score', 'tq_mutants', 'tq_mutants_killed', 'tq_mutants_survived',
    'tq_mutants_no_coverage', 'tq_mutants_timeout', 'tq_test_duration_p95', 'tq_slowest_tests', 'tq_source_files',
    'tq_untested_source_files', 'tq_test_reach'].concat(NEW);

  function name(key) {
    return key.indexOf('new_') === 0 ? 'New ' + (NAMES[key.slice(4)] || key.slice(4)).toLowerCase() : NAMES[key] || key;
  }

  function el(ns, tag, attrs, children) {
    var e = ns ? document.createElementNS(ns, tag) : document.createElement(tag);
    Object.keys(attrs || {}).forEach(function (k) {
      if (attrs[k] !== undefined && attrs[k] !== null) {
        e.setAttribute(k, attrs[k]);
      }
    });
    (children || []).forEach(function (c) {
      if (c !== null && c !== undefined) {
        e.appendChild(typeof c === 'object' ? c : document.createTextNode(String(c)));
      }
    });
    return e;
  }
  function h(tag, attrs, children) { return el(null, tag, attrs, children); }
  function s(tag, attrs, children) { return el(SVG, tag, attrs, children); }

  function fmt(v) {
    return Number(v).toLocaleString(undefined, {maximumFractionDigits: 1});
  }
  function sum(parts) {
    return parts.reduce(function (a, p) { return a + p.value; }, 0);
  }

  function api(path, params, withScope) {
    var q = new URLSearchParams(Object.assign({}, withScope === false ? {} : scope, params));
    return fetch(base + '/api/' + path + '?' + q.toString(), {credentials: 'same-origin'}).then(function (r) {
      if (!r.ok) {
        throw new Error(path + ' answered HTTP ' + r.status);
      }
      return r.json();
    });
  }
  function page(path, params) {
    return base + '/' + path + '?' + new URLSearchParams(Object.assign({id: project}, scope, params)).toString();
  }

  // the page follows SonarQube's light or dark theme: read the background the page sits on
  function dark(node) {
    for (; node && node.nodeType === 1; node = node.parentElement) {
      var c = (getComputedStyle(node).backgroundColor.match(/[\d.]+/g) || []).map(Number);
      if (c.length >= 3 && (c.length < 4 || c[3] > 0)) {
        return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2] < 128;
      }
    }
    return false;
  }

  function info(text) {
    return h('span', {class: 'tq-info', title: text, 'aria-label': text, role: 'img', tabindex: '0'}, ['i']);
  }
  function empty(word, hint) {
    return h('div', {class: 'tq-empty'}, [h('span', {class: 'tq-dash', 'aria-hidden': 'true'}, ['–']), word,
      hint ? info(hint) : null]);
  }
  function legend(parts, total) {
    return h('ul', {class: 'tq-legend'}, parts.map(function (p) {
      return h('li', {}, [h('span', {class: 'tq-sw', style: 'background:' + p.color}), p.label + ' ', h('b', {}, [fmt(p.value)]),
        total ? h('span', {class: 'tq-muted'}, [' ' + Math.round(p.value / total * 100) + '%']) : null]);
    }));
  }

  // ---- charts

  function sparkline(points, label) {
    if (points.length < 2) {
      return null;
    }
    var vals = points.map(function (p) { return p.v; });
    var min = Math.min.apply(null, vals);
    var span = Math.max.apply(null, vals) - min || 1;
    var pts = vals.map(function (v, i) {
      return (i / (vals.length - 1) * 100).toFixed(1) + ',' + (26 - (v - min) / span * 24).toFixed(1);
    }).join(' ');
    return s('svg', {class: 'tq-spark', viewBox: '0 0 100 28', preserveAspectRatio: 'none', role: 'img',
      'aria-label': label + ' over the last ' + vals.length + ' analyses: ' + vals.map(fmt).join(', ')}, [
      s('polyline', {points: pts, fill: 'none', style: 'stroke:var(--blue)', 'stroke-width': '2',
        'vector-effect': 'non-scaling-stroke', 'stroke-linejoin': 'round', 'stroke-linecap': 'round'})]);
  }

  // a ring of parts; a part that is the "rest" of a ratio takes the track color
  function donut(parts, center, sub, label) {
    var total = sum(parts) || 1;
    var shown = parts.filter(function (p) { return p.value > 0; });
    var gap = shown.length > 1 ? 1 : 0;
    var at = 0;
    var arcs = shown.map(function (p) {
      var len = p.value / total * 100;
      var dash = Math.max(len - gap, 0.3);
      var arc = s('circle', {cx: 60, cy: 60, r: 50, fill: 'none', 'stroke-width': 16, pathLength: 100,
        style: 'stroke:' + p.color, 'stroke-dasharray': dash + ' ' + (100 - dash), 'stroke-dashoffset': -at,
        transform: 'rotate(-90 60 60)'}, [s('title', {}, [p.label + ': ' + fmt(p.value)])]);
      at += len;
      return arc;
    });
    return s('svg', {class: 'tq-donut', viewBox: '0 0 120 120', role: 'img', 'aria-label': label}, arcs.concat([
      s('text', {x: 60, y: sub ? 58 : 61, 'text-anchor': 'middle', 'dominant-baseline': 'middle', class: 'tq-big'}, [center]),
      sub ? s('text', {x: 60, y: 77, 'text-anchor': 'middle', class: 'tq-small'}, [sub]) : null]));
  }

  // horizontal bars, label above each bar, value at the bar end; items [{label, value, href}]
  function hbars(items, w, o) {
    var row = 34;
    var top = o.ref ? 18 : 0;
    var unit = o.unit || '';
    var plot = w - 64;
    var max = Math.max.apply(null, items.map(function (i) { return i.value; }).concat([o.ref ? o.ref.value : 0])) || 1;
    var chars = Math.max(Math.floor(w / 6.6), 12);
    var links = items.some(function (i) { return i.href; });
    var marks = items.map(function (it, i) {
      var y = top + i * row;
      var bw = Math.max(it.value / max * plot, 3);
      var tip = it.label + ': ' + fmt(it.value) + unit;
      var label = it.label.length > chars ? '…' + it.label.slice(-(chars - 1)) : it.label;
      var kids = [s('title', {}, [tip]), s('rect', {class: 'tq-hit', x: 0, y: y, width: w, height: row, fill: 'transparent'}),
        s('text', {x: 0, y: y + 12, class: 'tq-label'}, [label]),
        s('rect', {x: 0, y: y + 17, width: bw, height: 10, rx: 2, style: 'fill:' + o.color}),
        s('text', {x: bw + 6, y: y + 26, class: 'tq-val'}, [fmt(it.value) + unit])];
      return it.href ? s('a', {href: it.href, 'aria-label': tip}, kids) : s('g', {}, kids);
    });
    if (o.ref) {
      var x = o.ref.value / max * plot;
      marks.unshift(s('line', {x1: x, x2: x, y1: top - 4, y2: top + items.length * row, class: 'tq-ref'}));
      marks.push(s('text', {x: x, y: 10, 'text-anchor': x > w - 60 ? 'end' : 'middle', class: 'tq-small'}, [o.ref.label]));
    }
    return s('svg', {width: w, height: top + items.length * row, role: links ? 'group' : 'img',
      'aria-label': o.label + ': ' + items.map(function (i) { return i.label + ' ' + fmt(i.value) + unit; }).join(', ') +
        (o.ref ? '; ' + o.ref.label : '')}, marks);
  }

  function stack(parts, w, label) {
    var total = sum(parts) || 1;
    var x = 0;
    return s('svg', {width: w, height: 24, role: 'img', 'aria-label': label}, parts.filter(function (p) { return p.value > 0; })
      .map(function (p) {
        var pw = p.value / total * w;
        var r = s('rect', {x: x, y: 0, width: Math.max(pw - 2, 2), height: 24, rx: 3, style: 'fill:' + p.color},
          [s('title', {}, [p.label + ': ' + fmt(p.value)])]);
        x += pw;
        return r;
      }));
  }

  function bullet(label, value, max, w, color) {
    return h('div', {class: 'tq-bullet'}, [h('div', {class: 'tq-bullet-head'}, [label, h('b', {}, [fmt(value)])]),
      s('svg', {width: w, height: 10, role: 'img', 'aria-label': label + ': ' + fmt(value)}, [
        s('rect', {width: w, height: 10, rx: 5, style: 'fill:var(--track)'}),
        s('rect', {width: Math.max(Math.min(value / max, 1) * w, 6), height: 10, rx: 5, style: 'fill:' + color})])]);
  }

  // small multiples over the same analyses: one panel per series, never two scales on one axis
  function lines(series, dates, w) {
    var ph = 64;
    var gap = 40;
    var left = 36;
    var right = 44;
    var pw = w - left - right;
    var x = function (date) {
      return left + (dates.length < 2 ? pw / 2 : dates.indexOf(date) / (dates.length - 1) * pw);
    };
    var marks = [];
    series.forEach(function (se, k) {
      var y0 = 24 + k * (ph + gap);
      var vals = se.points.map(function (p) { return p.v; });
      var min = Math.min.apply(null, vals);
      var max = Math.max.apply(null, vals);
      if (max === min) {
        min -= 1;
        max += 1;
      }
      var y = function (v) { return y0 + ph - (v - min) / (max - min) * ph; };
      marks.push(s('text', {x: 0, y: y0 - 12, class: 'tq-label'}, [se.label]),
        s('line', {x1: left, x2: left + pw, y1: y0, y2: y0, class: 'tq-grid-line'}),
        s('line', {x1: left, x2: left + pw, y1: y0 + ph, y2: y0 + ph, class: 'tq-axis'}),
        s('text', {x: left - 6, y: y0 + 4, 'text-anchor': 'end', class: 'tq-small'}, [fmt(max)]),
        s('text', {x: left - 6, y: y0 + ph + 4, 'text-anchor': 'end', class: 'tq-small'}, [fmt(min)]),
        s('polyline', {fill: 'none', style: 'stroke:' + se.color, 'stroke-width': 2, 'stroke-linejoin': 'round',
          points: se.points.map(function (p) { return x(p.date) + ',' + y(p.v); }).join(' ')}));
      se.points.forEach(function (p) {
        marks.push(s('circle', {cx: x(p.date), cy: y(p.v), r: 4, class: 'tq-dot', style: 'fill:' + se.color},
          [s('title', {}, [p.date.slice(0, 10) + ': ' + fmt(p.v) + (se.unit || '')])]));
      });
      var last = se.points[se.points.length - 1];
      marks.push(s('text', {x: x(last.date) + 8, y: y(last.v) + 4, class: 'tq-val'}, [fmt(last.v) + (se.unit || '')]));
    });
    var bottom = 24 + series.length * (ph + gap) - gap + 18;
    marks.push(s('text', {x: left, y: bottom, class: 'tq-small'}, [dates[0].slice(0, 10)]),
      s('text', {x: left + pw, y: bottom, 'text-anchor': 'end', class: 'tq-small'}, [dates[dates.length - 1].slice(0, 10)]));
    return s('svg', {width: w, height: bottom + 4, role: 'img', 'aria-label': series.map(function (se) {
      return se.label + ' over ' + se.points.length + ' analyses: ' + se.points.map(function (p) { return fmt(p.v); }).join(', ');
    }).join('; ')}, marks);
  }

  // ---- layout

  var style = h('style', {}, [
    '.tq{container-type:inline-size;padding:0 20px 24px 20px;font-size:13px;color:inherit;' +
    '--blue:#2a78d6;--blue2:#86b6ef;--orange:#eb6834;--gray:#a3a29c;--track:rgba(128,128,128,.22);--good:#006300;--bad:#d03b3b;' +
    '--ok:#0ca30c;--err:#d03b3b;--card:rgba(128,128,128,.06);--rule:rgba(128,128,128,.28)}' +
    '.tq.tq-dark{--blue:#3987e5;--orange:#d95926;--gray:#6e6d68;--good:#0ca30c;--bad:#e66767;--err:#e66767}' +
    '.tq h1{font-size:20px;margin:16px 0 12px 0}.tq h2{font-size:14px;font-weight:600;margin:0 0 12px 0}' +
    '.tq h3{font-size:12px;font-weight:600;margin:0 0 8px 0;opacity:.75}' +
    '.tq-tiles{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px;list-style:none;padding:0;margin:0 0 12px 0}' +
    '.tq-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px;grid-auto-flow:dense}.tq-wide{grid-column:1/-1}' +
    '@container (max-width:760px){.tq-grid{grid-template-columns:minmax(0,1fr)}.tq-tiles{grid-template-columns:repeat(2,minmax(0,1fr))}}' +
    '.tq-panel,.tq-tile{background:var(--card);border:1px solid var(--rule);border-radius:8px;padding:14px 16px;min-width:0}' +
    '.tq-tile .tq-name{font-size:12px;opacity:.8}.tq-tile .tq-num{display:block;font-size:34px;font-weight:600;line-height:1.15;margin:2px 0}' +
    '.tq-tile .tq-foot{display:flex;align-items:flex-end;gap:10px;min-height:28px}.tq-spark{flex:1;min-width:0;height:28px}' +
    '.tq-delta{font-size:12px;font-weight:600;white-space:nowrap}.tq-good{color:var(--good)}.tq-bad{color:var(--bad)}' +
    '.tq-muted{opacity:.65}' +
    '.tq-empty{display:flex;align-items:center;gap:8px;opacity:.65;min-height:42px}.tq-dash{font-size:30px;font-weight:600}' +
    '.tq-info{display:inline-flex;align-items:center;justify-content:center;width:16px;height:16px;border-radius:50%;' +
    'border:1px solid currentColor;font:italic 600 11px Georgia,serif;cursor:help}' +
    '.tq svg{display:block;overflow:visible}.tq svg text{fill:currentColor}.tq-label{font-size:12px}' +
    '.tq-val{font-size:12px;font-weight:600}.tq-small{font-size:11px;opacity:.7}.tq-big{font-size:19px;font-weight:600}' +
    '.tq-ref{stroke:currentColor;stroke-dasharray:3 3;opacity:.45}.tq-grid-line{stroke:var(--rule)}.tq-axis{stroke:currentColor;opacity:.35}' +
    '.tq-dot{stroke:var(--surface,#fff);stroke-width:2}.tq.tq-dark .tq-dot{stroke:#1a1d24}' +
    '.tq svg a{cursor:pointer}.tq svg a:hover .tq-label{text-decoration:underline}' +
    '.tq svg a:focus{outline:none}.tq svg a:focus-visible .tq-hit{stroke:var(--blue);stroke-width:2}' +
    '.tq-legend{list-style:none;padding:0;margin:10px 0 0 0;display:flex;flex-wrap:wrap;gap:4px 16px;font-size:12px}' +
    '.tq-sw{display:inline-block;width:10px;height:10px;border-radius:2px;margin-right:6px}' +
    '.tq-row{display:flex;flex-wrap:wrap;gap:12px 20px;align-items:center}.tq-row>.tq-legend{flex-direction:column;margin:0}' +
    '.tq-donut{width:140px;height:140px;flex:none}' +
    '.tq-cols{display:grid;gap:12px 24px}.tq-bullets{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px;margin-top:16px}' +
    '.tq-bullet-head{display:flex;justify-content:space-between;font-size:12px;margin-bottom:4px}.tq-bullet-head b{font-size:16px}' +
    '.tq-chips{display:flex;flex-wrap:wrap;gap:6px;list-style:none;padding:0;margin:12px 0 0 0}' +
    '.tq-chip{display:inline-flex;gap:6px;align-items:center;border:1px solid var(--rule);border-radius:999px;padding:3px 10px;font-size:12px}' +
    '.tq-chip b{font-weight:700}.tq-pass b{color:var(--ok)}.tq-fail{border-color:var(--err)}.tq-fail b{color:var(--err)}' +
    '.tq-status{font-size:18px;padding:4px 14px}.tq-status b{font-size:18px}' +
    '.tq-stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(110px,1fr));gap:12px;list-style:none;padding:0;margin:0}' +
    '.tq-stats b{display:block;font-size:26px;font-weight:600}.tq-stats span{font-size:12px;opacity:.8}' +
    '.tq-files{list-style:none;padding:0;margin:0;font-size:12px}.tq-files li{margin:0 0 6px 0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}' +
    '.tq a{color:inherit}'
  ]);
  var root = h('div', {class: 'tq'}, [style, h('h1', {}, ['Test Quality'])]);
  var tiles = h('ul', {class: 'tq-tiles', 'aria-label': 'Headline measures'});
  var grid = h('div', {class: 'tq-grid'});
  root.appendChild(tiles);
  root.appendChild(grid);
  options.el.appendChild(root);
  if (dark(options.el)) {
    root.classList.add('tq-dark');
  }

  // each panel draws for its current width and draws again when the page width changes
  var draws = [];
  function panel(title, wide) {
    var body = h('div', {}, [h('span', {class: 'tq-muted'}, ['…'])]);
    var node = h('section', {class: 'tq-panel' + (wide ? ' tq-wide' : ''), 'aria-label': title}, [h('h2', {}, [title]), body]);
    grid.appendChild(node);
    return {
      node: node,
      draw: function (fn) {
        var redraw = function () { body.replaceChildren.apply(body, [].concat(fn(Math.max(body.clientWidth, 200)))); };
        draws.push(redraw);
        redraw();
      },
      empty: function (word, hint) { body.replaceChildren(empty(word, hint)); },
      fail: function (e) { body.replaceChildren(h('p', {role: 'alert', class: 'tq-muted'}, ['Cannot load: ' + e.message])); }
    };
  }
  var lastWidth = root.clientWidth;
  var observer = window.ResizeObserver ? new ResizeObserver(function () {
    if (Math.abs(root.clientWidth - lastWidth) > 16) {
      lastWidth = root.clientWidth;
      draws.forEach(function (d) { d(); });
    }
  }) : null;
  if (observer) {
    observer.observe(root);
  }

  var gatePanel = panel('Quality gate');
  var newPanel = panel('New code');
  var mutantPanel = panel('Mutants');
  var healthPanel = panel('Test health');
  var rulePanel = panel('Issues by rule');
  var reachPanel = panel('Test reach');
  var hotPanel = panel('Hot spots', true);
  var slowPanel = panel('Slowest tests');
  var historyPanel = panel('History');

  // ---- data

  var measures = api('measures/component', {component: project, metricKeys: MEASURES.join(',')}).then(function (data) {
    var values = {};
    (data.component.measures || []).forEach(function (m) {
      var v = m.value !== undefined ? m.value : (m.period || (m.periods || [])[0] || {}).value;
      if (v !== undefined && v !== '') {
        values[m.metric] = m.metric === 'tq_slowest_tests' ? v : Number(v);
      }
    });
    return values;
  });

  // the last (up to) 20 analyses: from the date of the 20th latest one
  var history = api('project_analyses/search', {project: project, ps: 20}).then(function (data) {
    var analyses = data.analyses || [];
    if (analyses.length === 0) {
      return {};
    }
    return api('measures/search_history', {component: project, ps: 1000, from: analyses[analyses.length - 1].date,
      metrics: TILES.map(function (t) { return t.key; }).join(',')}).then(function (data) {
      var out = {};
      (data.measures || []).forEach(function (m) {
        out[m.metric] = m.history.filter(function (p) { return p.value !== undefined; })
          .map(function (p) { return {date: p.date, v: Number(p.value)}; });
      });
      return out;
    });
  }).catch(function () { return {}; });

  Promise.all([measures, history]).then(function (r) {
    var values = r[0];
    var hist = r[1];
    TILES.forEach(function (t) {
      var v = values[t.key];
      var points = hist[t.key] || [];
      var foot = [];
      if (v !== undefined) {
        foot.push(sparkline(points.slice(-20), name(t.key)));
        if (points.length >= 2) {
          var d = Math.round((points[points.length - 1].v - points[points.length - 2].v) * 10) / 10;
          var good = t.higherIsBetter ? d > 0 : d < 0;
          foot.push(h('span', {class: 'tq-delta ' + (d === 0 ? 'tq-muted' : good ? 'tq-good' : 'tq-bad'),
            title: 'Change since the previous analysis'},
          [d === 0 ? '±0' : (d > 0 ? '▲ +' : '▼ ') + fmt(d)]));
        }
      }
      tiles.appendChild(h('li', {class: 'tq-tile'}, [h('span', {class: 'tq-name'}, [name(t.key)]),
        v === undefined ? empty(t.word, t.hint) : h('span', {class: 'tq-num'}, [fmt(v) + (t.unit || '')]),
        h('div', {class: 'tq-foot'}, foot)]));
    });

    // new code
    var fresh = NEW.filter(function (k) { return values[k] !== undefined; });
    if (fresh.length === 0) {
      newPanel.node.remove();
    } else {
      newPanel.draw(function () {
        return h('ul', {class: 'tq-stats'}, fresh.map(function (k) {
          var bad = k !== 'new_tq_tests' && values[k] > 0;
          return h('li', {}, [h('b', {class: bad ? 'tq-bad' : ''}, [fmt(values[k])]), h('span', {}, [name(k).replace('New ', '')])]);
        }));
      });
    }

    // mutants: killed includes timeouts
    if (values.tq_mutants === undefined) {
      mutantPanel.empty('no report', MUTATION_HINT);
    } else {
      var timeout = values.tq_mutants_timeout || 0;
      var parts = [
        {label: 'Killed', value: Math.max((values.tq_mutants_killed || 0) - timeout, 0), color: 'var(--blue)'},
        {label: 'Timeout', value: timeout, color: 'var(--blue2)'},
        {label: 'Survived', value: values.tq_mutants_survived || 0, color: 'var(--orange)'},
        {label: 'No coverage', value: values.tq_mutants_no_coverage || 0, color: 'var(--gray)'}
      ];
      var score = values.tq_mutation_score === undefined ? '–' : fmt(values.tq_mutation_score) + '%';
      mutantPanel.draw(function () {
        return h('div', {class: 'tq-row'}, [donut(parts, score, 'score', 'Mutation score ' + score + ': ' +
          parts.map(function (p) { return p.label + ' ' + p.value; }).join(', ')), legend(parts, sum(parts))]);
      });
    }

    // test health
    if (!values.tq_tests) {
      healthPanel.empty('no tests', 'No test files found; check sonar.tests or the test patterns');
    } else {
      var without = values.tq_tests_without_assertion || 0;
      var skipped = values.tq_skipped_tests || 0;
      var health = [
        {label: 'With assertions', value: Math.max(values.tq_tests - without - skipped, 0), color: 'var(--blue)'},
        {label: 'Without', value: without, color: 'var(--orange)'},
        {label: 'Skipped', value: skipped, color: 'var(--gray)'}
      ];
      var apt = values.tq_assertions_per_test || 0;
      var mpt = Math.round((values.tq_mocks || 0) / values.tq_tests * 10) / 10;
      var scale = Math.max(3, Math.ceil(Math.max(apt, mpt)));
      healthPanel.draw(function (w) {
        var half = (w - 16) / 2;
        return [h('div', {class: 'tq-bullet-head'}, ['Tests', h('b', {}, [fmt(values.tq_tests)])]),
          stack(health, w, values.tq_tests + ' tests: ' + health.map(function (p) { return p.label + ' ' + p.value; }).join(', ')),
          legend(health, values.tq_tests),
          h('div', {class: 'tq-bullets'}, [bullet('Assertions / test', apt, scale, half, 'var(--blue)'),
            bullet('Mocks / test', mpt, scale, half, 'var(--blue2)')])];
      });
    }

    // test reach
    if (values.tq_test_reach === undefined) {
      reachPanel.empty('no data', 'Analyse sources and tests together (sonar.sources and sonar.tests)');
    } else {
      var untested = values.tq_untested_source_files || 0;
      var total = values.tq_source_files || 0;
      var reach = [{label: 'Reached', value: total - untested, color: 'var(--blue)'},
        {label: 'Untested', value: untested, color: 'var(--orange)'}];
      var ring = function () {
        return donut(total ? reach : [{label: 'Reached', value: values.tq_test_reach, color: 'var(--blue)'},
          {label: 'Untested', value: 100 - values.tq_test_reach, color: 'var(--track)'}],
        fmt(values.tq_test_reach) + '%', total ? (total - untested) + ' / ' + total : null,
        'Test reach ' + fmt(values.tq_test_reach) + '%: ' + untested + ' of ' + total + ' source files no test imports');
      };
      reachPanel.draw(function () { return h('div', {class: 'tq-row'}, [ring(), legend(reach)]); });
      tree('tq_untested_source_files', 'FIL').then(function (files) {
        files = files.filter(function (f) { return f.value > 0; });
        if (files.length > 0) {
          // one file is one untested file: a list of links, not bars of equal length
          reachPanel.draw(function () {
            return h('div', {class: 'tq-row'}, [ring(), h('div', {style: 'flex:1;min-width:0'}, [h('h3', {}, ['Untested files']),
              h('ul', {class: 'tq-files'}, files.map(function (f) {
                return h('li', {}, [h('span', {class: 'tq-sw', style: 'background:var(--orange)'}), h('a', {href: f.href, title: f.label}, [f.label])]);
              }))])]);
          });
        }
      }).catch(function () { /* the ring stays */ });
    }

    // slowest tests with the p95 line
    var slowest = [];
    try {
      slowest = JSON.parse(values.tq_slowest_tests || '[]');
    } catch (e) {
      slowest = [];
    }
    if (slowest.length === 0) {
      slowPanel.empty('no report', RUN_HINT);
    } else {
      var p95 = values.tq_test_duration_p95;
      slowPanel.draw(function (w) {
        return hbars(slowest.map(function (t) { return {label: t.name, value: Number(t.ms)}; }), w, {color: 'var(--blue)',
          unit: ' ms', label: 'Slowest tests', ref: p95 === undefined ? null : {value: p95, label: 'p95 ' + fmt(p95) + ' ms'}});
      });
    }

    // history: one small panel per measure, same analyses on the x axis
    var series = [
      {key: 'tq_mutation_score', label: 'Mutation score', unit: '%', color: 'var(--blue)'},
      {key: 'tq_tests_without_assertion', label: 'Tests without assertions', color: 'var(--orange)'}
    ].map(function (se) { se.points = (hist[se.key] || []).slice(-20); return se; })
      .filter(function (se) { return se.points.length > 0; });
    var dates = [];
    series.forEach(function (se) {
      se.points.forEach(function (p) {
        if (dates.indexOf(p.date) < 0) {
          dates.push(p.date);
        }
      });
    });
    dates.sort();
    if (dates.length < 2) {
      historyPanel.empty('one analysis', 'The trend shows from the second analysis on');
    } else {
      historyPanel.draw(function (w) { return lines(series, dates, w); });
    }
  }).catch(function (e) {
    tiles.replaceChildren(h('li', {role: 'alert'}, ['Cannot load the measures: ' + e.message]));
    [newPanel, mutantPanel, healthPanel, reachPanel, slowPanel, historyPanel].forEach(function (p) { p.fail(e); });
  });

  // quality gate: overall status and the conditions on this plugin's metrics
  api('qualitygates/project_status', {projectKey: project}).then(function (data) {
    var status = data.projectStatus || {};
    var conditions = (status.conditions || []).filter(function (c) { return /^(new_)?tq_/.test(c.metricKey); });
    var chip = function (ok, text, cls) {
      return h('li', {class: 'tq-chip ' + (ok ? 'tq-pass' : 'tq-fail') + (cls || '')}, [
        h('b', {'aria-hidden': 'true'}, [ok ? '✓' : '✗']), text]);
    };
    var overall = status.status === 'OK' ? 'Passed' : status.status === 'ERROR' ? 'Failed' : null;
    gatePanel.draw(function () {
      if (!overall) {
        return empty('no gate', 'Assign a quality gate to the project');
      }
      return [h('ul', {class: 'tq-chips', style: 'margin:0', 'aria-label': 'Quality gate ' + overall}, [chip(overall === 'Passed', overall, ' tq-status')]),
        conditions.length === 0 ? empty('no tq_ conditions', 'Add conditions on tq_ metrics to the quality gate')
          : h('ul', {class: 'tq-chips', 'aria-label': 'Conditions'}, conditions.map(function (c) {
            var ok = c.status !== 'ERROR';
            var bound = (c.comparator === 'LT' ? '≥ ' : '≤ ') + fmt(c.errorThreshold);
            return chip(ok, name(c.metricKey) + ' ' + (c.actualValue === undefined ? '–' : fmt(c.actualValue)) +
              ' (' + bound + ')' + (ok ? '' : ', failed'));
          }))];
    });
  }).catch(gatePanel.fail);

  // correctness issues by rule
  api('rules/search', {repositories: REPOSITORIES.join(','), f: 'name', ps: 500}, false).then(function (data) {
    var names = {};
    (data.rules || []).forEach(function (r) { names[r.key] = r.name; });
    var keys = Object.keys(names);
    if (keys.length === 0) {
      return [names, []];
    }
    return api('issues/search', {componentKeys: project, resolved: 'false', rules: keys.join(','), facets: 'rules', ps: 1})
      .then(function (issues) {
        var facet = (issues.facets || []).filter(function (f) { return f.property === 'rules'; })[0] || {values: []};
        return [names, facet.values.filter(function (v) { return v.count > 0; })];
      });
  }).then(function (r) {
    var counts = r[1].sort(function (a, b) { return b.count - a.count; }).map(function (v) {
      return {label: r[0][v.val] || v.val, value: v.count, href: page('project/issues', {resolved: 'false', rules: v.val})};
    });
    if (counts.length === 0) {
      rulePanel.empty('none', 'No open issues of the Test Quality rules');
    } else {
      rulePanel.draw(function (w) { return hbars(counts, w, {color: 'var(--orange)', label: 'Open correctness issues by rule'}); });
    }
  }).catch(rulePanel.fail);

  // hot spots: test files by correctness issues, source files by survived mutants
  function tree(metric, qualifiers) {
    return api('measures/component_tree', {component: project, qualifiers: qualifiers, metricKeys: metric, metricSort: metric,
      s: 'metric', asc: 'false', metricSortFilter: 'withMeasuresOnly', ps: 10}).then(function (data) {
      return (data.components || []).map(function (c) {
        var m = (c.measures || []).filter(function (x) { return x.metric === metric; })[0] || {};
        return {label: c.path || c.name, value: Number(m.value || 0), href: page('code', {selected: c.key})};
      }).filter(function (f) { return f.value > 0; });
    });
  }
  Promise.all([tree('tq_correctness_issues', 'FIL,UTS'), tree('tq_mutants_survived', 'FIL,UTS')]).then(function (r) {
    hotPanel.draw(function (w) {
      var two = w >= 560;
      var half = two ? (w - 24) / 2 : w;
      var chart = function (title, files, label, word, hint) {
        return h('div', {style: 'min-width:0'}, [h('h3', {}, [title]), files.length === 0 ? empty(word, hint)
          : hbars(files, half, {color: 'var(--orange)', label: label})]);
      };
      return h('div', {class: 'tq-cols', style: 'grid-template-columns:' + (two ? '1fr 1fr' : '1fr')}, [
        chart('Test files · issues', r[0], 'Test files by correctness issues', 'none'),
        chart('Source files · survived mutants', r[1], 'Source files by survived mutants', 'no report', MUTATION_HINT)]);
    });
  }).catch(hotPanel.fail);

  return function () {
    if (observer) {
      observer.disconnect();
    }
    root.remove();
  };
});
