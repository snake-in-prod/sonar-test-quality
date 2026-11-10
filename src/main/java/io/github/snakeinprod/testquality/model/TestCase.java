package io.github.snakeinprod.testquality.model;

import io.github.snakeinprod.testquality.parse.Node;
import io.github.snakeinprod.testquality.parse.Token;

/**
 * One test: a pytest or unittest test function or method, an {@code it} / {@code test}, a {@code test} / {@code testWidgets}.
 *
 * @param name       the name or description the runner reports ({@code test_login}, {@code "saves the user"})
 * @param suite      the enclosing class or {@code describe} / {@code group} names joined by " > ", "" at top level
 * @param nameToken  where to report about the test as a whole
 * @param body       the test body: for brace languages the "{...}" group (or the arrow body), for Python the
 *                   {@link io.github.snakeinprod.testquality.lang.python.PyStatement} of the def (as Object here)
 * @param skipped    marked skip, xfail, {@code it.skip}, {@code xit}, {@code skip: true}
 * @param async      an async function or a test taking an async callback
 * @param assertions assertions the body can reach (assert statements, assert methods, expect + matcher, raises blocks,
 *                   mock assertions, verify)
 * @param mocks      mocks created or patched for this test (decorators and fixtures included)
 */
public record TestCase(String name, String suite, Token nameToken, Token start, Token end, Object body, boolean skipped,
  boolean async, int assertions, int mocks) {

  public Node.Group bodyGroup() {
    return body instanceof Node.Group g ? g : null;
  }
}
