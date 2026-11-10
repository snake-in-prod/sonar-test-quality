package io.github.snakeinprod.testquality.reports;

import java.io.IOException;
import java.io.InputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** Reads JUnit XML (pytest --junitxml, jest-junit, Vitest, Playwright) into {@link Results}. */
public final class JUnitReport {

  private JUnitReport() {
  }

  /** Adds every {@code testcase} of the report to {@code into}. */
  public static void parse(InputStream xml, Results into) throws IOException {
    Element root;
    try {
      root = builder().parse(xml).getDocumentElement();
    } catch (SAXException e) {
      throw new IOException("not readable XML: " + e.getMessage(), e);
    }
    NodeList cases = root.getElementsByTagName("testcase");
    for (int i = 0; i < cases.getLength(); i++) {
      Element tc = (Element) cases.item(i);
      String prefix = tc.getAttribute("classname");
      if (prefix.isEmpty() && tc.getParentNode() instanceof Element suite) {
        prefix = suite.getAttribute("name");
      }
      into.add(Results.id(prefix, tc.getAttribute("name")), millis(tc.getAttribute("time")), outcome(tc));
    }
  }

  private static Results.Outcome outcome(Element tc) {
    Results.Outcome out = Results.Outcome.PASSED;
    for (Node c = tc.getFirstChild(); c != null; c = c.getNextSibling()) {
      switch (c.getNodeName()) {
        case "failure", "error" -> {
          return Results.Outcome.FAILED;
        }
        case "skipped" -> out = Results.Outcome.SKIPPED;
        default -> {
          // system-out, properties...
        }
      }
    }
    return out;
  }

  private static long millis(String seconds) {
    try {
      return Math.round(Double.parseDouble(seconds.replace(",", "")) * 1000);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static DocumentBuilder builder() throws IOException {
    try {
      DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
      f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      f.setFeature("http://xml.org/sax/features/external-general-entities", false);
      f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      f.setXIncludeAware(false);
      f.setExpandEntityReferences(false);
      DocumentBuilder b = f.newDocumentBuilder();
      b.setErrorHandler(null);
      return b;
    } catch (ParserConfigurationException e) {
      throw new IOException(e);
    }
  }
}
