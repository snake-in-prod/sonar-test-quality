package io.github.snakeinprod.testquality.lang.dart;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.snakeinprod.testquality.parse.Lexed;
import io.github.snakeinprod.testquality.parse.Token;
import io.github.snakeinprod.testquality.parse.TokenKind;
import java.util.List;
import org.junit.jupiter.api.Test;

class DartLexerTest {

  private static List<String> texts(String src) {
    return DartLexer.lex(src).tokens().stream().map(Token::text).toList();
  }

  private static List<TokenKind> kinds(String src) {
    return DartLexer.lex(src).tokens().stream().map(Token::kind).toList();
  }

  @Test
  void nested_interpolation_is_one_string() {
    String s = "'a ${b({'k': \"x ${c('}')} y\"})} z'";
    assertThat(texts("f(" + s + ");")).containsExactly("f", "(", s, ")", ";");
    assertThat(texts("'$name and $_x$y'")).containsExactly("'$name and $_x$y'");
  }

  @Test
  void raw_strings_ignore_escapes_and_interpolation() {
    assertThat(texts("r'a\\' + r\"${x\" + r'''x''' + r\"\"\"y\"\"\"")).containsExactly("r'a\\'", "+", "r\"${x\"", "+", "r'''x'''", "+",
      "r\"\"\"y\"\"\"");
  }

  @Test
  void triple_quotes_span_lines_and_hold_single_quotes() {
    Lexed l = DartLexer.lex("x = '''one\n'two' \"\"\n${a}''';\ny");
    assertThat(l.tokens()).extracting(Token::text).containsExactly("x", "=", "'''one\n'two' \"\"\n${a}'''", ";", "y");
    Token str = l.tokens().get(2);
    assertThat(str.line()).isEqualTo(1);
    assertThat(str.endLine()).isEqualTo(3);
    assertThat(l.tokens().get(4).line()).isEqualTo(4);
  }

  @Test
  void adjacent_literals_stay_separate() {
    assertThat(kinds("'a' \"b\" '''c'''")).containsExactly(TokenKind.STRING, TokenKind.STRING, TokenKind.STRING);
  }

  @Test
  void nested_block_comments_and_line_comments() {
    Lexed l = DartLexer.lex("a /* x /* y */ z */ b // c\n/// doc\nd");
    assertThat(l.tokens()).extracting(Token::text).containsExactly("a", "b", "d");
    assertThat(l.comments()).extracting(Token::text).containsExactly("/* x /* y */ z */", "// c", "/// doc");
    assertThat(l.comments()).allMatch(c -> c.kind() == TokenKind.OTHER);
  }

  @Test
  void numbers_identifiers_and_operators() {
    assertThat(texts("1_000 0xFF_FF 1e3 1.5e-2 .5 x.y 1.toString()")).containsExactly("1_000", "0xFF_FF", "1e3", "1.5e-2", ".5", "x",
      ".", "y", "1", ".", "toString", "(", ")");
    assertThat(texts("\\$a _b a?..b ...? x ??= y ~/ 2 => a?.b @override #sym != >>>=")).containsExactly("\\", "$a", "_b", "a", "?..",
      "b", "...?", "x", "??=", "y", "~/", "2", "=>", "a", "?.", "b", "@", "override", "#", "sym", "!=", ">>>=");
    assertThat(kinds("\\ é")).containsExactly(TokenKind.OTHER, TokenKind.OTHER);
  }

  @Test
  void unterminated_input_does_not_throw() {
    assertThat(texts("'abc\nx")).containsExactly("'abc", "x");
    assertThat(texts("\"${ '")).hasSize(1);
    assertThat(texts("/* /* */")).isEmpty();
    assertThat(texts("'''x")).containsExactly("'''x");
  }

  @Test
  void positions_are_one_based_lines_and_zero_based_columns() {
    Token t = DartLexer.lex("a\r\n  bb").tokens().get(1);
    assertThat(t).isEqualTo(new Token(TokenKind.IDENT, "bb", 2, 2, 2, 4));
  }
}
