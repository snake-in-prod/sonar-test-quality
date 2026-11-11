# Design

One plugin, key `testquality`, for SonarQube Community Build 26.7 and later. It finds tests that pass while checking
nothing (rules) and measures the test suite (metrics), for Python, JavaScript/TypeScript and Flutter/Dart.

## Flow

```
file -> LanguageSupport.parse -> ParsedFile (tokens, comments, token tree, language extras)
     -> LanguageSupport.extract -> TestFile (TestCase list)
     -> active TestRules (RuleContext) -> issues
TestFile -> code measures (tq_*, new_tq_*) on the file -> TqAggregator sums to directories and the project
test file -> LanguageSupport.outline -> imports resolved to project paths
source file (second pass) -> outline -> reached? -> SourceRules -> issues, tq_source_files, tq_untested_source_files
reports (mutation JSON, JUnit XML, Dart JSON) -> ReportSensor -> report measures
```

`sensor/Analyzer` runs this for one file. The SonarQube sensor, the command line scanner (`cli/Scan`) and the tests
(`RuleVerifier`) all go through it: `analyze` for every test file first, then `analyzeSource` for every source file. Project
files that rules read (`RuleContext.project`) stay parsed in a cache of the 2000 most recently used.

New code is per file: `new_tq_*` take the file's value when `InputFile.status()` is ADDED or CHANGED, else 0. The
plugin API gives no new code lines, and the per file value is what a gate condition on new code can use. They are
saved only in pull request (`sonar.pullrequest.key`) and branch (`sonar.branch.name`, not `main` or `master`)
analyses, where the status is against the target branch; on the main branch it is against the previous analysis
(everything on the first), so nothing is saved and the gate's new code conditions are not evaluated
(`TestQualitySensor.newCodeAnalysis`). Scanner plugins get no main branch name, hence the name check.

## Test reach

`LanguageSupport.outline` gives a file's imports, resolved to project paths, and its functions and classes (top level
and class members, no nested functions). The analyzer collects the imports of every test file; on the first source
file it adds the imports of those imported source files (one level), and that set is "reached". Source files that a
test reaches through two imports or more count as untested: cheap, predictable, and a test of the file in between
rarely exercises them much. Files without a function or class, generated, configuration, migration, declaration and
entry point files are not source files (`Analyzer.NOT_SOURCE` and a header comment check).

Package imports resolve to project files where the language has a manifest: Dart `package:` imports through
`pubspec.yaml`, JS/TS workspace package names through `package.json` (read with the project read function, they are
not source files): the entry, or a deep path under the package directory or its `src/`.

## Parsing

No grammar and no external parser. Each language has a lexer that turns source into `Token`s
(`parse/TokenKind`: IDENT, NUMBER, STRING, OP, plus NEWLINE/INDENT/DEDENT for Python, OTHER for text a rule never
reads such as JSX text). Comments are returned separately (`Lexed`). `TreeBuilder` (shared) groups tokens by
brackets into a `Node` tree. `Chains` (shared) finds member/call chains (`expect(x).resolves.toEqual(1)`,
`self.assertEqual(a, b)`, `tester.tap(...)`) in that tree; most rules are patterns over chains.

Lexer rules every language must get right, because a wrong token breaks the bracket tree:

- strings with all their forms (Python prefixes and triple quotes, JS template literals with `${}` nesting, Dart raw
  and triple-quoted strings with `$x` / `${}`): one STRING token each, interpolations included in the text;
- comments: Python `#`, JS and Dart `//` and `/* */` (Dart nests `/* */`), JS `/** */`;
- JS regex literals (`/ab+c/g` after `(`, `,`, `=`, `:`, `[`, `!`, `&`, `|`, `?`, `{`, `}`, `;`, `return`, `typeof`...):
  one STRING token;
- JSX / TSX in expression position: tags are OP / IDENT tokens, `{...}` inside JSX are normal groups, the text between
  tags is OTHER (so an apostrophe in JSX text does not open a string);
- multi-character operators as one OP token (`=>`, `===`, `?.`, `..`, `?..`, `**`, `//`, `:=`, `->`, `...`);
- Python: NEWLINE at the end of each logical line, INDENT/DEDENT on indentation changes, none inside brackets or after
  a line continuation backslash.

A lexer never throws on bad input: an unknown character becomes an OTHER token.

## Test model

`TestCase(name, suite, nameToken, start, end, body, skipped, async, assertions, mocks)`. The extractor of each
language finds:

- Python: `def test_*` functions at module level and in classes (pytest: classes named `Test*` without `__init__`;
  unittest: subclasses of `TestCase`, `unittest.TestCase`, `IsolatedAsyncioTestCase`), async ones too. `body` is the
  `PyStatement` of the def.
- JS/TS: `it`, `test`, `it.each(...)(...)`, `test.each`, `it.only`, `it.skip`, `xit`, `fit`, `it.concurrent`, Playwright
  `test(...)` and `test.describe`, inside `describe` / `suite` / `context` blocks for `suite`. `body` is the callback's
  `{}` group (or the arrow expression group).
- Dart: `test(...)`, `testWidgets(...)`, `testGoldens`, inside `group(...)`; `skip:` named argument. `body` is the
  closure's `{}` group. In files importing package:bloc_test, `blocTest<B, S>('description', build: ..., act: ...,
  expect: ..., verify: ...)` too: `body` is the call's `()` argument group, its `skip:` (a count of states) is not a
  skip, and `expect:` and `errors:` count one assertion each.
- JS/TS and Dart: a function of the same file that calls a test function with its own parameters as name and body (the
  body parameter passed as is, or called in the closure it passes) is a wrapper: `function itWithUser(name, fn) {
  it(name, async () => fn(await login())); }`, `void slideshowTest(String name, body) { testWidgets(name, (t) async {
  await body(t); }); }`. Each call of it with a string literal name and a function literal body is a test (start: the
  wrapper's name, body: that function); its own call of the test function is not. Dart rules ask
  `DartSupport.testFunctions` which framework function a test runs (`test` or `testWidgets`) and which closure parameter
  is the tester. Wrappers declared in other project files count too when the test file imports them: Dart through an
  `import` of the file (relative or `package:`) or of a file that `export`s it; JS/TS through `import { a, b as c }` or
  a default import (the file's `export default function`) of a relative or workspace package path, or through one
  `export { a } from` / `export * from` of the imported file. Namespace imports, `require` and tsconfig paths are not
  followed. `LanguageSupport.extract(file, paths, read)` resolves them; each imported file is read and indexed once per
  analysis (files without a test call or an export are not parsed). Dart rules get them from
  `DartSupport.testFunctions(ParsedFile)`.

`assertions` counts: Python `assert` statements, `self.assert*` / `self.fail`, `pytest.raises` / `pytest.warns` blocks,
mock `assert_*` calls; JS `expect(...).<matcher>(...)` chains (a matcher call, `.not`, `.resolves` etc. included),
`assert.*`, `expect.assertions`, Playwright `expect`; Dart `expect`, `expectLater`, `verify*`. `mocks` counts:
Python `@patch` / `@mock.patch*` decorators, `patch(...)` / `mock.patch.object(...)` calls, `Mock()` / `MagicMock()` /
`AsyncMock()`, `mocker.patch*`, `monkeypatch.setattr`; JS `jest.fn` / `vi.fn` / `jest.spyOn` / `vi.spyOn` /
`jest.mock` / `vi.mock`; Dart `Mock*()` constructors of classes extending `Mock`, `when(...)`.

## Rules

`rules/RuleCatalog` holds every rule's metadata (family, key, severity, default activation, name, description,
example) and its factory. Each rule is one class `rules/<family>/<Key>.java` implementing `TestRule`. A rule may have
a longer HTML description in `src/main/resources/rules/<family>/<Key>.html`, which replaces the generated one.

Each rule has a fixture, `src/test/resources/fixtures/<family>/<Key>.<py|ts|js|dart>` (or a directory of that name
for rules that read other project files), with `Noncompliant` comments on the lines that must get an issue
(`Noncompliant 2` for two, `Noncompliant {{part of the message}}` to check the message), and compliant code that must
stay silent. A test class per family calls `RuleVerifier.verify(family, key)` once per rule.

A rule on source files implements `SourceRule` and runs only in the second pass, with `RuleContext.outline()` and
`reached()`; `SourceWithoutTest` (one class, `rules/SourceWithoutTest.java`, registered in every family) is the only
one. Its fixtures are directories with tests and sources; `RuleVerifier` checks the source files' marks too and
returns which ones are reached.

Rule quality bar: an issue must be a real defect in the test in at least 9 of 10 cases on real projects. When a
pattern is ambiguous, stay silent. Messages say what is wrong and what to do, in one sentence.

## Conventions

- Java 21, no new dependencies (gson is the only shaded library), no reflection, no network.
- One-line commit messages, no co-author lines, author `snake-in-prod`.
- Public repository: generic names only.
