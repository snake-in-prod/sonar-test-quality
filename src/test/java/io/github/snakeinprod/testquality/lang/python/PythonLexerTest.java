package io.github.snakeinprod.testquality.lang.python;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.github.snakeinprod.testquality.parse.Lexed;
import io.github.snakeinprod.testquality.parse.Token;
import io.github.snakeinprod.testquality.parse.TokenKind;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PythonLexerTest {

  /** Tokens as "text" for code and STRING / NUMBER / OTHER, and "NL", ">", "<" for NEWLINE, INDENT, DEDENT. */
  private static List<String> lex(String src) {
    return PythonLexer.lex(src).tokens().stream().map(t -> switch (t.kind()) {
      case NEWLINE -> "NL";
      case INDENT -> ">";
      case DEDENT -> "<";
      default -> t.text();
    }).toList();
  }

  private static List<TokenKind> kinds(String src) {
    return PythonLexer.lex(src).tokens().stream().map(Token::kind).toList();
  }

  @Test
  void nested_fstrings_are_one_string() {
    String s1 = "f\"{f'{name!r}'} and {', '.join([f'{n}' for n in 'ab'])}\"";
    String s2 = "f\"{items[\"a\"]} {x:{w}.{p}} {{literal}} {y:>10}\"";
    String s3 = "rf'''{a} \"{b}\" # no comment\n{c!s:{'>'}{10}}'''";
    assertThat(lex("x = " + s1 + "\n")).containsExactly("x", "=", s1, "NL");
    assertThat(lex("x = " + s2 + "\n")).containsExactly("x", "=", s2, "NL");
    assertThat(lex("x = " + s3 + "\n")).containsExactly("x", "=", s3, "NL");
    assertThat(PythonLexer.lex("x = " + s3).comments()).isEmpty();
  }

  @Test
  void fstring_expression_spanning_lines() {
    String s = "f\"list: {\", \".join([\n    'a',  # first\n    'b',\n])}\"";
    assertThat(lex("x = " + s + "\ny = 1\n")).containsExactly("x", "=", s, "NL", "y", "=", "1", "NL");
  }

  @Test
  void triple_quoted_strings_hold_quotes_hashes_and_newlines() {
    String s = "'''a \"b\" 'c' # d\nnext '' line'''";
    Lexed lexed = PythonLexer.lex("s = " + s + "\n");
    assertThat(lexed.tokens()).extracting(Token::text).containsExactly("s", "=", s, "\n");
    assertThat(lexed.comments()).isEmpty();
    Token str = lexed.tokens().get(2);
    assertThat(str.line()).isEqualTo(1);
    assertThat(str.endLine()).isEqualTo(2);
    assertThat(str.endColumn()).isEqualTo("next '' line'''".length());
    assertThat(lex("d = \"\"\"x\"\"\" + \"\"\n")).containsExactly("d", "=", "\"\"\"x\"\"\"", "+", "\"\"", "NL");
  }

  @Test
  void every_string_prefix() {
    String src = "[u'a', U\"b\", r'\\d', R\"\\d\", b'x', B\"x\", br'\\x', rB\"\\x\", Rb'y', f'{1}', F\"{2}\", fr'{3}', Rf'{4}', fR'5']";
    List<Token> tokens = PythonLexer.lex(src).tokens();
    assertThat(tokens).filteredOn(t -> t.kind() == TokenKind.STRING).hasSize(14);
    assertThat(tokens).filteredOn(t -> t.kind() == TokenKind.IDENT).isEmpty();
    assertThat(lex("r'\\'' + 'it\\'s'")).containsExactly("r'\\''", "+", "'it\\'s'", "NL");
    assertThat(lex("rb = bf'x'")).containsExactly("rb", "=", "bf", "'x'", "NL");
  }

  @Test
  void comments_are_separate() {
    Lexed lexed = PythonLexer.lex("x = 1  # set x\n# alone\ny = '#'\n");
    assertThat(lexed.comments()).extracting(Token::text).containsExactly("# set x", "# alone");
    assertThat(lexed.comments()).extracting(Token::line).containsExactly(1, 2);
    assertThat(lex("x = 1  # set x\n# alone\ny = '#'\n")).containsExactly("x", "=", "1", "NL", "y", "=", "'#'", "NL");
  }

  @Test
  void continuation_lines_join() {
    assertThat(lex("x = 1 + \\\n    2\ny = 3\n")).containsExactly("x", "=", "1", "+", "2", "NL", "y", "=", "3", "NL");
    assertThat(lex("x = 1 + \\\r\n    2\r\n")).containsExactly("x", "=", "1", "+", "2", "NL");
  }

  @Test
  void brackets_span_lines_without_layout_tokens() {
    String src = "def f():\n    call(1,\n  2,\n        [3,\n4])\n    return {\n'a': 1}\n";
    assertThat(lex(src)).containsExactly("def", "f", "(", ")", ":", "NL", ">", "call", "(", "1", ",", "2", ",", "[", "3", ",", "4",
      "]", ")", "NL", "return", "{", "'a'", ":", "1", "}", "NL", "<");
  }

  @Test
  void decorators_async_and_class_with_methods() {
    String src = """
      @pytest.mark.skip(reason="x")
      class TestA(Base):

          def test_one(self):
              assert True

          # comment between methods
          async def test_two(self, *args, **kw):
              await x()
      y = 1
      """;
    assertThat(lex(src)).containsExactly(
      "@", "pytest", ".", "mark", ".", "skip", "(", "reason", "=", "\"x\"", ")", "NL",
      "class", "TestA", "(", "Base", ")", ":", "NL",
      ">", "def", "test_one", "(", "self", ")", ":", "NL",
      ">", "assert", "True", "NL",
      "<", "async", "def", "test_two", "(", "self", ",", "*", "args", ",", "**", "kw", ")", ":", "NL",
      ">", "await", "x", "(", ")", "NL",
      "<", "<", "y", "=", "1", "NL");
  }

  @Test
  void tabs_count_to_the_next_multiple_of_eight() {
    assertThat(lex("if a:\n\tb\n        c\n  \td\n")).containsExactly("if", "a", ":", "NL", ">", "b", "NL", "c", "NL", "d", "NL", "<");
  }

  @Test
  void blank_and_comment_only_lines_produce_nothing() {
    String src = "def f():\n    a\n\n        # deeper comment\n# column zero comment\n   \n    b\n";
    assertThat(lex(src)).containsExactly("def", "f", "(", ")", ":", "NL", ">", "a", "NL", "b", "NL", "<");
  }

  @Test
  void end_of_file_closes_line_and_blocks() {
    List<Token> tokens = PythonLexer.lex("if a:\n    if b:\n        c").tokens();
    assertThat(tokens.subList(tokens.size() - 3, tokens.size())).extracting(Token::kind)
      .containsExactly(TokenKind.NEWLINE, TokenKind.DEDENT, TokenKind.DEDENT);
    assertThat(lex("")).isEmpty();
    assertThat(lex("\n\n# only a comment\n")).isEmpty();
  }

  @Test
  void numbers() {
    assertThat(lex("1_000 0x1F 1e-3 3j .5 1.5E+10 0o17 0b1010 10.")).containsExactly("1_000", "0x1F", "1e-3", "3j", ".5", "1.5E+10",
      "0o17", "0b1010", "10.", "NL");
    assertThat(kinds("1_000 0x1F 1e-3 3j").subList(0, 4)).containsOnly(TokenKind.NUMBER);
    assertThat(lex("0x1e+1")).containsExactly("0x1e", "+", "1", "NL");
  }

  @Test
  void multi_char_operators() {
    assertThat(lex("a ** b // c := d -> ... == != <= >= += -= *= /= **= //= >>= <<= @ @= ~ ^ | &"))
      .containsExactly("a", "**", "b", "//", "c", ":=", "d", "->", "...", "==", "!=", "<=", ">=", "+=", "-=", "*=", "/=", "**=", "//=",
        ">>=", "<<=", "@", "@=", "~", "^", "|", "&", "NL");
    assertThat(kinds("@ ...").subList(0, 2)).containsOnly(TokenKind.OP);
  }

  @Test
  void unknown_characters_become_other() {
    List<Token> tokens = PythonLexer.lex("a $ b ? `c`").tokens();
    assertThat(tokens).filteredOn(t -> t.kind() == TokenKind.OTHER).extracting(Token::text).containsExactly("$", "?", "`", "`");
  }

  @Test
  void unicode_identifiers_and_bom() {
    assertThat(lex("\uFEFFcafé = 1")).containsExactly("café", "=", "1", "NL");
    assertThat(PythonLexer.lex("\uFEFFx").tokens().get(0).column()).isZero();
  }

  @Test
  void unterminated_string_stops_at_line_end() {
    assertThat(lex("x = 'abc\ny = 2\n")).containsExactly("x", "=", "'abc", "NL", "y", "=", "2", "NL");
  }

  @Test
  void bad_dedent_keeps_layout_balanced() {
    List<TokenKind> k = kinds("if a:\n    b\n  c\nd\n");
    assertThat(k.stream().filter(x -> x == TokenKind.INDENT).count()).isEqualTo(k.stream().filter(x -> x == TokenKind.DEDENT).count());
  }

  @Test
  void never_throws_on_garbage() {
    String alphabet = "fFrRbB'\"{}()[]:!=\\\n\r\t #@.0x9_a$é";
    Random random = new Random(42);
    for (int i = 0; i < 3000; i++) {
      StringBuilder sb = new StringBuilder();
      int len = random.nextInt(60);
      for (int j = 0; j < len; j++) {
        sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
      }
      String src = sb.toString();
      assertThatCode(() -> {
        Lexed lexed = PythonLexer.lex(src);
        long indents = lexed.tokens().stream().filter(t -> t.kind() == TokenKind.INDENT).count();
        long dedents = lexed.tokens().stream().filter(t -> t.kind() == TokenKind.DEDENT).count();
        assertThat(indents).isEqualTo(dedents);
        new PythonSupport().extract(new PythonSupport().parse("x.py", "py", src));
      }).as(src).doesNotThrowAnyException();
    }
    String deep = "f'{" + "f'{".repeat(5000);
    assertThatCode(() -> PythonLexer.lex(deep)).doesNotThrowAnyException();
  }
}
