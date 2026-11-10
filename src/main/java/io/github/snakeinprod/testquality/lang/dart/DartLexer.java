package io.github.snakeinprod.testquality.lang.dart;

import io.github.snakeinprod.testquality.parse.Lexed;
import io.github.snakeinprod.testquality.parse.Token;
import io.github.snakeinprod.testquality.parse.TokenKind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Dart lexer. Comments ({@code //}, {@code ///}, nested {@code /* *}{@code /}) go to {@link Lexed#comments()}. A string
 * literal (quotes, triple quotes, {@code r} prefix, {@code $x} and {@code ${...}} with nested strings) is one STRING
 * token; adjacent literals stay separate. Never throws: an unknown character is an OTHER token.
 */
public final class DartLexer {

  /** Longest first, so that the first match wins. */
  private static final String[] OPS = {">>>=", "...?", "??=", "?..", "...", "~/=", ">>>", ">>=", "<<=", "=>", "==", "!=", "<=",
    ">=", "&&", "||", "??", "?.", "..", "~/", "++", "--", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<", ">>"};
  private static final String SINGLE = "(){}[];,.:?=!<>+-*/%&|^~@#";

  private final String src;
  private final int n;
  private final int[] lineStarts;
  private final List<Token> tokens = new ArrayList<>();
  private final List<Token> comments = new ArrayList<>();

  private DartLexer(String src) {
    this.src = src;
    this.n = src.length();
    List<Integer> starts = new ArrayList<>();
    starts.add(0);
    for (int i = 0; i < n; i++) {
      char c = src.charAt(i);
      if (c == '\n' || c == '\r' && (i + 1 >= n || src.charAt(i + 1) != '\n')) {
        starts.add(i + 1);
      }
    }
    this.lineStarts = starts.stream().mapToInt(Integer::intValue).toArray();
  }

  public static Lexed lex(String source) {
    DartLexer l = new DartLexer(source);
    l.run();
    return new Lexed(List.copyOf(l.tokens), List.copyOf(l.comments));
  }

  private void run() {
    int i = 0;
    while (i < n) {
      char c = src.charAt(i);
      int end;
      if (Character.isWhitespace(c) || c == '﻿') {
        i++;
        continue;
      }
      if (c == '/' && at(i + 1) == '/') {
        end = lineEnd(i);
        add(comments, TokenKind.OTHER, i, end);
      } else if (c == '/' && at(i + 1) == '*') {
        end = blockComment(i);
        add(comments, TokenKind.OTHER, i, end);
      } else if (c == 'r' && isQuote(at(i + 1))) {
        end = string(i + 1, true);
        add(tokens, TokenKind.STRING, i, end);
      } else if (isQuote(c)) {
        end = string(i, false);
        add(tokens, TokenKind.STRING, i, end);
      } else if (isDigit(c) || c == '.' && isDigit(at(i + 1))) {
        end = number(i);
        add(tokens, TokenKind.NUMBER, i, end);
      } else if (isIdentStart(c)) {
        end = i + 1;
        while (end < n && isIdentPart(src.charAt(end))) {
          end++;
        }
        add(tokens, TokenKind.IDENT, i, end);
      } else {
        end = op(i);
        if (end > i) {
          add(tokens, TokenKind.OP, i, end);
        } else {
          end = i + Character.charCount(src.codePointAt(i));
          add(tokens, TokenKind.OTHER, i, end);
        }
      }
      i = end;
    }
  }

  private char at(int i) {
    return i < n ? src.charAt(i) : '\0';
  }

  private int lineEnd(int i) {
    while (i < n && src.charAt(i) != '\n' && src.charAt(i) != '\r') {
      i++;
    }
    return i;
  }

  /** End of a nested block comment starting at {@code i}; the end of the source when unterminated. */
  private int blockComment(int i) {
    int depth = 0;
    while (i < n) {
      if (src.startsWith("/*", i)) {
        depth++;
        i += 2;
      } else if (src.startsWith("*/", i)) {
        i += 2;
        if (--depth == 0) {
          return i;
        }
      } else {
        i++;
      }
    }
    return n;
  }

  /**
   * End of a string literal whose opening quote is at {@code q}. A single-line literal stops at a line end when
   * unterminated, so that one bad quote does not swallow the file.
   */
  private int string(int q, boolean raw) {
    char quote = src.charAt(q);
    String triple = String.valueOf(quote).repeat(3);
    boolean multi = src.startsWith(triple, q);
    int i = q + (multi ? 3 : 1);
    while (i < n) {
      char c = src.charAt(i);
      if (multi ? src.startsWith(triple, i) : c == quote) {
        return i + (multi ? 3 : 1);
      }
      if (!multi && (c == '\n' || c == '\r')) {
        return i;
      }
      if (!raw && c == '\\') {
        i += at(i + 1) == '\n' || at(i + 1) == '\r' ? 1 : 2;
      } else if (!raw && c == '$' && at(i + 1) == '{') {
        i = interpolation(i + 2);
      } else {
        i++;
      }
    }
    return n;
  }

  /** End (past the closing brace) of a {@code ${...}} body starting at {@code i}, after the opening brace. */
  private int interpolation(int i) {
    int depth = 1;
    while (i < n) {
      char c = src.charAt(i);
      if (c == '{') {
        depth++;
        i++;
      } else if (c == '}') {
        i++;
        if (--depth == 0) {
          return i;
        }
      } else if (c == 'r' && isQuote(at(i + 1)) && (i == 0 || !isIdentPart(src.charAt(i - 1)))) {
        i = string(i + 1, true);
      } else if (isQuote(c)) {
        i = string(i, false);
      } else if (c == '/' && at(i + 1) == '/') {
        i = lineEnd(i);
      } else if (c == '/' && at(i + 1) == '*') {
        i = blockComment(i);
      } else {
        i++;
      }
    }
    return n;
  }

  private int number(int i) {
    if (src.charAt(i) == '0' && (at(i + 1) == 'x' || at(i + 1) == 'X')) {
      i += 2;
      while (i < n && (Character.digit(src.charAt(i), 16) >= 0 || src.charAt(i) == '_')) {
        i++;
      }
      return i;
    }
    i = digits(i);
    if (at(i) == '.' && isDigit(at(i + 1))) {
      i = digits(i + 1);
    }
    if ((at(i) == 'e' || at(i) == 'E') && (isDigit(at(i + 1)) || (at(i + 1) == '+' || at(i + 1) == '-') && isDigit(at(i + 2)))) {
      i = digits(i + 2);
    }
    return i;
  }

  private int digits(int i) {
    while (i < n && (isDigit(src.charAt(i)) || src.charAt(i) == '_')) {
      i++;
    }
    return i;
  }

  private int op(int i) {
    for (String o : OPS) {
      if (src.startsWith(o, i)) {
        return i + o.length();
      }
    }
    return SINGLE.indexOf(src.charAt(i)) >= 0 ? i + 1 : i;
  }

  private void add(List<Token> out, TokenKind kind, int from, int to) {
    int l1 = line(from);
    int l2 = line(to);
    out.add(new Token(kind, src.substring(from, to), l1 + 1, from - lineStarts[l1], l2 + 1, to - lineStarts[l2]));
  }

  /** 0-based line of an offset. */
  private int line(int offset) {
    int k = Arrays.binarySearch(lineStarts, offset);
    return k >= 0 ? k : -k - 2;
  }

  private static boolean isQuote(char c) {
    return c == '\'' || c == '"';
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  private static boolean isIdentStart(char c) {
    return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c == '_' || c == '$';
  }

  private static boolean isIdentPart(char c) {
    return isIdentStart(c) || isDigit(c);
  }
}
