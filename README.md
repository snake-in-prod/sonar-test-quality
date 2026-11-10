# Test Quality for SonarQube

Finds tests that pass while they check nothing, and measures how good a test suite is, for **Python**,
**JavaScript / TypeScript** and **Flutter / Dart**.

- **38 rules** on test files: an assert that is always true, a mock assertion with a typo, a test that never runs, an
  async assertion nobody awaits, a widget expectation before the frame was pumped.
- **Production code checks**: which source files no test imports (test reach), as metrics and as an optional rule.
- **Test quality metrics**: tests, assertions per test, tests without assertions, mocks, mock-heavy and skipped tests,
  and from reports the mutation score, the slowest tests (p95) and flaky tests; new code variants for quality gates.
- **A Test Quality page** per project, and a ready-made "Test Quality" quality gate (installed by a script).

The built-in Python and JavaScript analyzers' own test rules (an assert on a tuple, exclusive tests, tests without
assertions) are not repeated. SonarQube has no Dart analyzer; this plugin brings a `dart` language for test files.

## Install

Requires SonarQube Community Build 26.7 or later (tested on 26.10) and Java 21.

1. Download `sonar-test-quality-<version>.jar` from the [releases](https://github.com/snake-in-prod/sonar-test-quality/releases).
2. Copy it to `extensions/plugins/` of the SonarQube server and restart.
3. Profiles. Dart gets "Sonar way" as its default profile with the active rules. For Python, JavaScript and TypeScript the
   bundled analyzers own "Sonar way", and SonarQube lets only one plugin define a built-in profile of a given name, so
   this plugin adds a built-in "Test Quality" profile with its active rules. To keep the analyzer's rules too, extend
   "Sonar way" (*Quality Profiles > Sonar way > Extend*), then on the *Rules* page filter on the repository
   (`testquality-python`, `testquality-js` or `testquality-ts`) and use *Bulk Change > Activate in* your profile.
4. Analyse as usual; test files are found through `sonar.tests` or by name (see Settings).

## Rules

Every rule has a description with an example in SonarQube. Rules not active by default are useful but noisier on
some code bases; activate them in your own profile.

### Python (pytest, unittest)

| Rule | Name | Severity | Active by default |
| --- | --- | --- | --- |
| `MockAssertionTypo` | Mock assertion methods should be spelled correctly | Critical | yes |
| `MockAttributeAsAssertion` | Mock attributes should not be used as assertions | Major | yes |
| `ShadowedTest` | Test names should be unique in a module or class | Critical | yes |
| `UndiscoveredTest` | Tests should be named so the runner collects them | Major | yes |
| `ReturnInsteadOfAssert` | Tests should assert their comparisons, not return them | Major | yes |
| `AssertionInsideRaises` | Assertions should not follow the raising call inside pytest.raises | Major | yes |
| `RaisesTooBroad` | pytest.raises should name the exception and wrap one call | Major | yes |
| `PatchWhereDefined` | mock.patch should target the module that uses the name | Major | yes |
| `MockNeverUsed` | Patched mocks should be used by the test | Minor | no |
| `SleepInTest` | Tests should not sleep | Minor | no |
| `UnseededRandom` | Random values in tests should be seeded | Minor | no |
| `ComparisonWithoutAssert` | Comparisons in tests should be asserted | Major | yes |
| `ExceptSwallowsAssertion` | Assertions should not be caught | Major | yes |
| `ParametrizeMismatch` | pytest.mark.parametrize should match the test parameters | Major | yes |

### JavaScript / TypeScript (Jest, Vitest, Testing Library, Playwright)

| Rule | Name | Severity | Active by default |
| --- | --- | --- | --- |
| `ExpectWithoutMatcher` | expect() should be finished with a matcher | Critical | yes |
| `UnawaitedAsyncAssertion` | Async assertions should be awaited or returned | Critical | yes |
| `AssertionInUnawaitedCallback` | Assertions should not sit in callbacks the test does not wait for | Major | yes |
| `AlwaysTruthyMatcher` | Matchers should be able to fail | Major | yes |
| `ShadowedTest` | Test names should be unique in a describe block | Critical | yes |
| `SnapshotOnlyTest` | Tests should not rely on a snapshot alone | Minor | no |
| `ExpectAssertionsMismatch` | expect.assertions(n) should match the assertions the test can reach | Major | yes |
| `SyncQueryForAsyncUi` | Use findBy queries for UI that renders after an action | Major | yes |
| `WaitForTimeout` | Tests should not wait for a fixed time | Minor | no |
| `MockNeverAsserted` | Mock functions passed in should be asserted on | Minor | no |
| `DoneAndPromise` | Tests should not take done and return a promise | Major | yes |
| `AssertionInLoopNeverRun` | Assertions should not run only inside a loop that can be empty | Minor | no |

### Flutter / Dart (flutter_test, package:test, mockito, mocktail)

| Rule | Name | Severity | Active by default |
| --- | --- | --- | --- |
| `UnawaitedTesterAction` | WidgetTester actions should be awaited | Critical | yes |
| `ExpectWithoutPump` | Widget expectations should follow a pump after an action | Critical | yes |
| `UnawaitedExpectLater` | expectLater should be awaited or returned | Critical | yes |
| `AsyncMatcherNotAwaited` | Async matchers should be awaited before the test ends | Major | yes |
| `VerifyWithoutCall` | verify and when should take a call on a mock | Critical | yes |
| `StubNeverUsed` | Stubs should be used by the test | Minor | no |
| `MockNeverVerified` | Mocks passed in should be verified or stubbed | Minor | no |
| `TrivialMatcher` | Matchers should be able to fail | Major | yes |
| `GoldenOnlyTest` | Widget tests should not rely on a golden file alone | Minor | no |
| `DuplicateTestName` | Test descriptions should be unique in a group | Major | yes |
| `RealDelayInTest` | Widget tests should not wait for real time | Minor | no |
| `SkipWithoutReason` | Skipped tests should say why | Minor | no |

### Source files (all three languages)

| Rule | Name | Severity | Active by default |
| --- | --- | --- | --- |
| `SourceWithoutTest` | Source files should be exercised by at least one test | Minor | no |

A policy rule in each repository (`testquality-python`, `testquality-js`, `testquality-ts`, `testquality-dart`): an
issue on the first function of a source file that defines at least 3 functions or methods and that no test reaches
(see Test reach below). Activate it in your profile when every module should have tests.

Every rule was run on public projects before release (psf/requests, pallets/flask, encode/httpx, pallets/click,
pytest-dev/pytest-mock, vercel/swr, pmndrs/zustand, testing-library/react-testing-library, felangel/bloc,
rrousselGit/riverpod, flutter/samples): 41 issues, all of them real test defects after the fixes made during the review.

## Metrics

Raw values, prefix `tq_` (`new_tq_` on new code), saved on test files (code metrics), on source files (test reach) or
on the source files that were mutated (mutation metrics), and summed up to directories and the project.

| Metric | Source | Meaning |
| --- | --- | --- |
| `tq_tests` | code | test functions and test cases found |
| `tq_assertions` | code | assertions in tests |
| `tq_tests_without_assertion` | code | tests with no assertion they can reach (skipped tests not counted) |
| `tq_assertions_per_test` | code | assertions / tests |
| `tq_mocks` | code | mocks created or patched in tests |
| `tq_mock_heavy_tests` | code | tests with more mocks than assertions |
| `tq_skipped_tests` | code | tests marked skip or xfail |
| `tq_correctness_issues` | rules | issues of the Test Quality rules |
| `tq_mutants`, `tq_mutants_killed`, `tq_mutants_survived`, `tq_mutants_no_coverage`, `tq_mutants_timeout` | mutation report | mutants per source file (killed includes timeouts) |
| `tq_mutation_score` | mutation report | killed / mutants that were run, in % |
| `tq_test_duration_p95` | JUnit XML, Dart JSON | 95th percentile of test time, ms |
| `tq_slowest_tests` | JUnit XML, Dart JSON | JSON list of the 10 slowest tests |
| `tq_flaky_tests` | several reports | tests that both passed and failed across the reports of one analysis |
| `tq_source_files` | imports | source files with at least one function or class (see Test reach) |
| `tq_untested_source_files` | imports | source files no test reaches |
| `tq_test_reach` | imports | (source files - untested) / source files, in %, on directories and the project |
| `new_tq_tests`, `new_tq_tests_without_assertion`, `new_tq_correctness_issues` | code, rules | the same on new code |
| `new_tq_untested_source_files` | imports | untested source files on new code |

### New code

SonarQube does not show plugins the new code period line by line, so new code is counted **per file**: a file is new
code when SonarQube marks it added or changed (against the previous analysis, or against the reference branch in a
branch or pull request analysis). A new or changed file counts whole: a test file that gets one new test brings all
its tests, tests without assertion and correctness issues into the `new_tq_*` measures. Unchanged files count 0. The
first analysis of a project marks every file as added.

### Test reach

Python, JavaScript / TypeScript and Dart tests reach the code under test through imports, so the plugin reads the
imports of every test file and resolves them to project files. A source file is **reached** when a test imports it,
or imports a source file that imports it (one level, to stay cheap and predictable). This is not coverage: an
imported function may still be untested, and code reached only through a framework (routes, plugins, dependency
injection) shows as untested.

- Python: `import a.b`, `from a.b import c`, relative imports, and packages (`a/b/__init__.py`); absolute imports are
  looked up from each directory above the test file and its `src/`, so src layouts and nested projects resolve.
- JavaScript / TypeScript: `import ... from`, `export ... from`, `require()` and `import()` of paths starting with
  `.` or `/`, with or without extension (`.js`, `.jsx`, `.ts`, `.tsx`, `.mjs`, `.cjs`, `.mts`, `.cts`), `index`
  files, and `./x.js` written for `x.ts`. Aliases (tsconfig paths, webpack aliases) are not resolved.
- Dart: relative imports, `export` and `part`, and `package:<name>/x.dart` as `lib/x.dart` of the project package
  whose `pubspec.yaml` has that name.

Not counted as source files: files under the test patterns, files without a function or class (an empty
`__init__.py`, an `index` file that only re-exports), generated files (`*_pb2.py`, `*.g.dart`, `*.freezed.dart`, a
"generated" / "do not edit" header comment), declarations (`*.d.ts`), configuration (`*.config.js`, dot files,
`setup.py`, `settings.py`, `conftest.py`), migrations, entry points (`main.dart`, `__main__.py`, `manage.py`), and
`build`, `dist`, `coverage`, `node_modules` directories.

## Reports

| Setting | Report |
| --- | --- |
| `sonar.testquality.mutation.reportPaths` | mutation-testing-elements JSON: Stryker (`reports/mutation/mutation.json`), or mutmut / cosmic-ray results converted to that format |
| `sonar.testquality.junit.reportPaths` | JUnit XML: `pytest --junitxml=report.xml`, jest-junit, Vitest and Playwright JUnit reporters |
| `sonar.testquality.dart.reportPaths` | `flutter test --machine > test.json` or `dart test --reporter json > test.json` |

Paths are relative to the project base directory or absolute; globs (`**/junit*.xml`) are allowed. Give the reports of
several runs of the same tests (for example reruns in CI) to find flaky tests. A report that cannot be read is logged
and skipped.

## Settings

| Setting | Default | |
| --- | --- | --- |
| `sonar.testquality.python.testPatterns` | `**/test_*.py,**/*_test.py,**/tests/**/*.py,**/conftest.py` | test files under `sonar.sources` |
| `sonar.testquality.js.testPatterns` | `**/*.test.*,**/*.spec.*,**/__tests__/**` | |
| `sonar.testquality.dart.testPatterns` | `**/*_test.dart,**/test/**/*.dart,**/integration_test/**/*.dart` | |
| `sonar.dart.file.suffixes` | `.dart` | set it empty when another plugin already claims `.dart`; two languages cannot share a suffix |
| `sonar.testquality.slowTestMs` | `1000` | slow test threshold on the page |

## Quality gate

The plugin API cannot create quality gates, so a script installs the "Test Quality" gate:

```bash
SONAR_HOST_URL=https://sonar.example.org SONAR_TOKEN=<token> scripts/create-test-quality-gate.sh [--with-sonar-way]
```

The token needs the *Administer Quality Gates* permission. The script needs bash and curl, creates the gate or updates
its conditions, and can run again at any time; conditions on other metrics that you added are kept. With
`--with-sonar-way` it also copies the conditions of "Sonar way" (new issues, coverage, duplications, hotspots);
recent SonarQube versions already put those into every new gate. Then select the gate for a project in *Project
Settings > Quality Gate*, or make it the default on the *Quality Gates* page.

| Condition | Fails when |
| --- | --- |
| Test correctness issues on new code (`new_tq_correctness_issues`) | greater than 0 |
| Tests without assertion on new code (`new_tq_tests_without_assertion`) | greater than 0 |
| Untested source files on new code (`new_tq_untested_source_files`) | greater than 0 |
| Mutation score, overall (`tq_mutation_score`) | less than 60 % |
| Flaky tests (`tq_flaky_tests`) | greater than 0 |

Projects without a mutation or test report have no `tq_mutation_score` or `tq_flaky_tests`, and a condition without
a value does not fail.

## Without SonarQube

`java -cp target/classes:<gson jar> io.github.snakeinprod.testquality.cli.Scan <dir>` runs every rule on a directory and
prints the issues, a count per rule, the number of tests found and the source files no test reaches.

## Build

`./mvnw verify` (Java 21). The design is in [docs/DESIGN.md](docs/DESIGN.md).

## License

Apache-2.0
