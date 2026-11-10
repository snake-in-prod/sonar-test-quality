/* Project page "Test Quality": headline test measures, issues by rule, files to look at first, slowest tests. */
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
  var REPOSITORIES = ['testquality-python', 'testquality-js', 'testquality-ts', 'testquality-dart'];
  var TILES = [
    {key: 'tq_mutation_score', name: 'Mutation score', label: 'of the mutants that ran were killed', percent: true, higherIsBetter: true},
    {key: 'tq_tests_without_assertion', name: 'Tests without assertions', label: 'tests that check nothing'},
    {key: 'tq_correctness_issues', name: 'Correctness issues', label: 'defects found in the tests'},
    {key: 'tq_flaky_tests', name: 'Flaky tests', label: 'passed and failed in the same analysis'}
  ];
  var SECONDARY = [
    ['tq_tests', 'Tests'], ['tq_assertions_per_test', 'Assertions per test'], ['tq_mocks', 'Mocks'],
    ['tq_mock_heavy_tests', 'Mock-heavy tests'], ['tq_skipped_tests', 'Skipped tests'], ['tq_test_duration_p95', 'Test duration p95', ' ms']
  ];
  var MEASURES = TILES.map(function (t) { return t.key; }).concat(SECONDARY.map(function (s) { return s[0]; }),
    ['tq_slowest_tests']);

  function h(tag, attrs, children) {
    var e = document.createElement(tag);
    Object.keys(attrs || {}).forEach(function (k) { e.setAttribute(k, attrs[k]); });
    (children || []).forEach(function (c) {
      if (c !== null && c !== undefined) {
        e.appendChild(typeof c === 'object' ? c : document.createTextNode(String(c)));
      }
    });
    return e;
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

  function num(value) {
    var n = Number(value);
    return Math.round(n * 10) / 10;
  }

  function table(caption, headers, rows) {
    return h('table', {class: 'tq-table'}, [
      h('caption', {class: 'tq-hidden'}, [caption]),
      h('thead', {}, [h('tr', {}, headers.map(function (x, i) {
        return h('th', {scope: 'col', class: i > 0 ? 'tq-num' : ''}, [x]);
      }))]),
      h('tbody', {}, rows.map(function (r) {
        return h('tr', {}, r.map(function (x, i) { return h('td', {class: i > 0 ? 'tq-num' : ''}, [x]); }));
      }))
    ]);
  }

  function section(title, id) {
    var s = h('section', {'aria-labelledby': id}, [h('h2', {id: id}, [title])]);
    root.appendChild(s);
    var body = s.appendChild(h('div', {}, [h('p', {class: 'tq-muted'}, ['Loading...'])]));
    return {
      show: function (node) { body.replaceChildren(node); },
      fail: function (e) { body.replaceChildren(h('p', {role: 'alert'}, ['Cannot load this part: ' + e.message])); }
    };
  }

  var style = h('style', {}, [
    '.tq{padding:0 20px 20px 20px;color:inherit;font-size:14px}' +
    '.tq h1{font-size:20px;margin:16px 0}.tq h2{font-size:16px;margin:24px 0 8px 0}.tq h3{font-size:14px;margin:0 0 6px 0}' +
    '.tq-muted{opacity:.7}.tq-hidden{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0 0 0 0)}' +
    '.tq-tiles{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:12px;list-style:none;padding:0;margin:0}' +
    '.tq-tile{border:1px solid rgba(128,128,128,.4);border-radius:6px;padding:12px}' +
    '.tq-tile .tq-name{font-weight:600}.tq-tile .tq-value{display:block;font-size:28px;margin:6px 0 2px 0}' +
    '.tq-tile .tq-value.tq-none{font-size:16px;opacity:.7;margin:14px 0 8px 0}.tq-tile .tq-label,.tq-tile .tq-delta{display:block;font-size:12px;opacity:.75}' +
    '.tq-secondary{display:flex;flex-wrap:wrap;gap:4px 20px;list-style:none;padding:0;margin:14px 0 0 0}' +
    '.tq-notes{margin:12px 0 0 0;padding:0;list-style:none}.tq-notes li{margin:4px 0}' +
    '.tq-notes code{font-family:monospace;padding:0 3px;border:1px solid rgba(128,128,128,.4);border-radius:3px}' +
    '.tq-cols{display:flex;flex-wrap:wrap;gap:24px}.tq-cols>div{flex:1 1 320px;min-width:0}' +
    '.tq-table{border-collapse:collapse;font-size:13px;width:100%;max-width:900px}' +
    '.tq-table th,.tq-table td{text-align:left;padding:4px 12px 4px 0;border-bottom:1px solid rgba(128,128,128,.25);vertical-align:top;word-break:break-word}' +
    '.tq-table th{border-bottom-color:rgba(128,128,128,.6)}.tq-table .tq-num{text-align:right;white-space:nowrap}' +
    '.tq a{color:inherit;text-decoration:underline}'
  ]);
  var root = h('div', {class: 'tq'}, [style, h('h1', {}, ['Test Quality'])]);
  options.el.appendChild(root);

  // 1, 2 and 6: tiles, secondary line, notes about missing reports
  var tiles = h('ul', {class: 'tq-tiles', 'aria-label': 'Headline measures'});
  var secondary = h('ul', {class: 'tq-secondary', 'aria-label': 'Other measures'});
  var notes = h('ul', {class: 'tq-notes'});
  var status = h('p', {class: 'tq-muted', role: 'status'}, ['Loading...']);
  [status, tiles, secondary, notes].forEach(function (e) { root.appendChild(e); });

  var measures = api('measures/component', {component: project, metricKeys: MEASURES.join(',')}).then(function (data) {
    var values = {};
    (data.component.measures || []).forEach(function (m) { values[m.metric] = m.value; });
    return values;
  });

  // change since the previous analysis: the history from the date of the second latest analysis holds the last two values
  var previous = api('project_analyses/search', {project: project, ps: 2}).then(function (data) {
    var analyses = data.analyses || [];
    if (analyses.length < 2) {
      return {};
    }
    return api('measures/search_history', {component: project, metrics: TILES.map(function (t) { return t.key; }).join(','),
      from: analyses[1].date, ps: 1000}).then(function (history) {
      var deltas = {};
      (history.measures || []).forEach(function (m) {
        var last = m.history.slice(-2);
        if (last.length === 2 && last[0].value !== undefined && last[1].value !== undefined) {
          deltas[m.metric] = Number(last[1].value) - Number(last[0].value);
        }
      });
      return deltas;
    });
  }).catch(function () { return {}; });

  Promise.all([measures, previous]).then(function (r) {
    var values = r[0];
    var deltas = r[1];
    status.remove();
    TILES.forEach(function (t) {
      var v = values[t.key];
      var has = v !== undefined;
      var d = deltas[t.key];
      var change = null;
      if (has && d !== undefined) {
        d = num(d);
        change = d === 0 ? 'no change since the previous analysis'
          : (d > 0 ? '+' : '') + d + (t.percent ? ' points' : '') + ' since the previous analysis';
      }
      tiles.appendChild(h('li', {class: 'tq-tile'}, [
        h('span', {class: 'tq-name'}, [t.name]),
        h('span', {class: 'tq-value' + (has ? '' : ' tq-none')}, [has ? num(v) + (t.percent ? '%' : '') : 'no report']),
        has ? h('span', {class: 'tq-label'}, [t.label]) : null,
        change ? h('span', {class: 'tq-delta'}, [change]) : null
      ]));
    });
    SECONDARY.forEach(function (s) {
      var v = values[s[0]];
      secondary.appendChild(h('li', {}, [s[1] + ': ', h('strong', {}, [v === undefined ? '-' : num(v) + (s[2] || '')])]));
    });

    function note(what, settings, rest) {
      var parts = [what + ': set '];
      settings.forEach(function (s, i) {
        parts.push(h('code', {}, [s]));
        if (i < settings.length - 1) {
          parts.push(' or ');
        }
      });
      notes.appendChild(h('li', {}, parts.concat([' ' + rest])));
    }
    if (values.tq_mutation_score === undefined) {
      note('No mutation report', ['sonar.testquality.mutation.reportPaths'], 'to get the mutation score and survived mutants.');
    }
    if (values.tq_flaky_tests === undefined && values.tq_test_duration_p95 === undefined && values.tq_slowest_tests === undefined) {
      note('No test run report', ['sonar.testquality.junit.reportPaths', 'sonar.testquality.dart.reportPaths'],
        '(Flutter) to get flaky and slow tests.');
    }

    // 5: slowest tests
    var slow = section('Slowest tests', 'tq-slowest');
    var rows = [];
    try {
      rows = JSON.parse(values.tq_slowest_tests || '[]');
    } catch (e) {
      rows = [];
    }
    slow.show(rows.length === 0 ? h('p', {class: 'tq-muted'}, ['No test durations reported.'])
      : table('Slowest tests', ['Test', 'Time (ms)'], rows.map(function (t) { return [t.name, num(t.ms)]; })));
  }).catch(function (e) {
    status.textContent = 'Cannot load the measures: ' + e.message;
  });

  // 3: correctness issues by rule
  var byRule = section('Correctness issues by rule', 'tq-rules');
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
    var names = r[0];
    var counts = r[1].sort(function (a, b) { return b.count - a.count; });
    byRule.show(counts.length === 0 ? h('p', {class: 'tq-muted'}, ['No open correctness issues.'])
      : table('Open correctness issues by rule', ['Rule', 'Open issues'], counts.map(function (v) {
        return [h('a', {href: page('project/issues', {resolved: 'false', rules: v.val})}, [names[v.val] || v.val]), v.count];
      })));
  }).catch(byRule.fail);

  // 4: files to look at first
  var files = section('Files to look at first', 'tq-files');
  function tree(metric, keys) {
    return api('measures/component_tree', {component: project, qualifiers: 'FIL,UTS', metricKeys: keys.join(','),
      metricSort: metric, s: 'metric', asc: 'false', metricSortFilter: 'withMeasuresOnly', ps: 10}).then(function (data) {
      return (data.components || []).map(function (c) {
        var v = {};
        (c.measures || []).forEach(function (m) { v[m.metric] = Number(m.value || 0); });
        return {key: c.key, path: c.path || c.name, v: v};
      });
    });
  }
  function fileLink(f) {
    return h('a', {href: page('code', {selected: f.key})}, [f.path]);
  }
  var testKeys = ['tq_correctness_issues', 'tq_tests_without_assertion'];
  Promise.all([tree(testKeys[0], testKeys), tree(testKeys[1], testKeys), tree('tq_mutants_survived', ['tq_mutants_survived'])])
    .then(function (r) {
      var merged = {};
      r[0].concat(r[1]).forEach(function (f) { merged[f.key] = f; });
      var score = function (f) { return (f.v.tq_correctness_issues || 0) + (f.v.tq_tests_without_assertion || 0); };
      var tests = Object.keys(merged).map(function (k) { return merged[k]; }).filter(function (f) { return score(f) > 0; })
        .sort(function (a, b) { return score(b) - score(a); }).slice(0, 10);
      var sources = r[2].filter(function (f) { return f.v.tq_mutants_survived > 0; });
      files.show(h('div', {class: 'tq-cols'}, [
        h('div', {}, [h('h3', {}, ['Test files']), tests.length === 0 ? h('p', {class: 'tq-muted'}, ['Nothing to report.'])
          : table('Test files with most problems', ['File', 'Correctness issues', 'Tests without assertions'], tests.map(function (f) {
            return [fileLink(f), f.v.tq_correctness_issues || 0, f.v.tq_tests_without_assertion || 0];
          }))]),
        h('div', {}, [h('h3', {}, ['Source files']), sources.length === 0 ? h('p', {class: 'tq-muted'}, ['No survived mutants reported.'])
          : table('Source files with most survived mutants', ['File', 'Survived mutants'], sources.map(function (f) {
            return [fileLink(f), f.v.tq_mutants_survived];
          }))])
      ]));
    }).catch(files.fail);

  return function () {
    root.remove();
  };
});
