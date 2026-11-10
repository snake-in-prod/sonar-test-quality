# Design

One plugin, key `testquality`, for SonarQube Community Build 26.7 and later. It finds tests that pass while checking
nothing (rules) and measures the test suite (metrics), for Python, JavaScript/TypeScript and Flutter/Dart.

## Flow

```
file -> LanguageSupport.parse -> ParsedFile (tokens, comments, token tree, language extras)
     -> LanguageSupport.extract -> TestFile (TestCase list)
     -> active TestRules (RuleContext) -> issues
TestFile -> code measures (tq_*) on the file -> TqAggregator sums to directories and the project
reports (mutation JSON, JUnit XML, Dart JSON) -> ReportSensor -> report measures
```

`sensor/Analyzer` runs this for one file. The SonarQube sensor, the command line scanner (`cli/Scan`) and the tests
(`RuleVerifier`) all go through it.

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
  closure's `{}` group.

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

Rule quality bar: an issue must be a real defect in the test in at least 9 of 10 cases on real projects. When a
pattern is ambiguous, stay silent. Messages say what is wrong and what to do, in one sentence.

## Conventions

- Java 21, no new dependencies (gson is the only shaded library), no reflection, no network.
- One-line commit messages, no co-author lines, author `snake-in-prod`.
- Public repository: generic names only.
