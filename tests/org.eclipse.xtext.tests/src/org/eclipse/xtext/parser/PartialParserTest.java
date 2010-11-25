/*******************************************************************************
 * Copyright (c) 2008 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
package org.eclipse.xtext.parser;

import java.io.StringReader;
import java.util.Iterator;

import org.eclipse.xtext.XtextStandaloneSetup;
import org.eclipse.xtext.nodemodel.BidiTreeIterator;
import org.eclipse.xtext.nodemodel.ICompositeNode;
import org.eclipse.xtext.nodemodel.ILeafNode;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.testlanguages.LookaheadTestLanguageStandaloneSetup;
import org.eclipse.xtext.testlanguages.PartialParserTestLanguageStandaloneSetup;
import org.eclipse.xtext.testlanguages.ReferenceGrammarTestLanguageStandaloneSetup;
import org.eclipse.xtext.testlanguages.SimpleExpressionsTestLanguageStandaloneSetup;

import com.google.common.collect.Iterables;

/**
 * @author Jan K�hnlein - Initial contribution and API
 * @author Sebastian Zarnekow
 */
public class PartialParserTest extends AbstractPartialParserTest {

	public void testExpression() throws Exception {
		with(SimpleExpressionsTestLanguageStandaloneSetup.class);
		String model = "(a+b+c)*(c/d)";
		parseAndCompareAllSubstrings(model);
	}
	
	public void testExpression_9_1() throws Exception {
		with(SimpleExpressionsTestLanguageStandaloneSetup.class);
		String model = "(a+b+c)*(c/d)";
		partiallyParseAndCompare(model, 9, 1);
	}

	public void testLookahead() throws Exception {
		with(LookaheadTestLanguageStandaloneSetup.class);
		String model = "bar a foo bar c b d foo bar b c";
		parseAndCompareAllSubstrings(model);
	}

	private void parseAndCompareAllSubstrings(String model) throws Exception {
		for (int i = 0; i < model.length() - 1; ++i) {
			for (int j = 1; j + i < model.length(); ++j) {
				partiallyParseAndCompare(model, i, j);
			}
		}
	}

	public void testErrorMarkers() throws Exception {
		with(ReferenceGrammarTestLanguageStandaloneSetup.class);
		// model contains an error due to missing ) at idx 23
		String model = "spielplatz 1 {kind (k 1}"; 
		XtextResource resource = getResourceFromStringAndExpect(model, 1);
		assertEquals(1, resource.getErrors().size());
		assertEquals(1, Iterables.size(resource.getParseResult().getSyntaxErrors()));
		ICompositeNode rootNode = resource.getParseResult().getRootNode();
		ILeafNode leaf = NodeModelUtils.findLeafNodeAtOffset(rootNode, model.length() - 1);
		assertTrue(leaf.getSyntaxErrorMessage() != null);
		// resource.update(23, 0, ")");
		// assertTrue(resource.getParseResult().getParseErrors().isEmpty());
		IParseResult reparse = reparse(resource.getParseResult(), 23, 0, ")");
		rootNode = reparse.getRootNode();
		String expectedFixedModel = "spielplatz 1 {kind (k 1)}";
		String fixedModel = rootNode.getText();
		assertEquals("serialized model as expected", expectedFixedModel, fixedModel);
		resource = getResourceFromString(fixedModel);
		assertEquals("full reparse is fine", 0, resource.getErrors().size());
		assertFalse("partial reparse is fine", reparse.hasSyntaxErrors());
	}

	public void testGrammarElementAssigned() throws Exception {
		with(ReferenceGrammarTestLanguageStandaloneSetup.class);
		String model = "spielplatz 1 {kind (k 1)\n}";
		XtextResource resource = getResourceFromString(model);
		ICompositeNode rootNode = resource.getParseResult().getRootNode();
		checkGrammarAssigned(rootNode);
		IParseResult reparse = reparse(resource.getParseResult(), model.length() - 2, 0, "\n");
		rootNode = reparse.getRootNode();
		checkGrammarAssigned(rootNode);
	}

	public void testParseIsPartial() throws Exception {
		with(ReferenceGrammarTestLanguageStandaloneSetup.class);
		String model = "spielplatz 1 {kind (k 1)\n}";
		XtextResource resource = getResourceFromString(model);
		ICompositeNode rootNode = resource.getParseResult().getRootNode();
		resource.update(model.indexOf("k 1"), 3, "l 2");
		assertSame(rootNode, resource.getParseResult().getRootNode());
	}

	public void testParseIsPartialTwice() throws Exception {
		with(ReferenceGrammarTestLanguageStandaloneSetup.class);
		String model = "spielplatz 1 {kind (k 1)\n}";
		XtextResource resource = getResourceFromString(model);
		ICompositeNode rootNode = resource.getParseResult().getRootNode();
		resource.update(model.indexOf("k 1"), 3, "l 2");
		resource.update(model.indexOf("k 1"), 3, "m 3");
		assertSame(rootNode, resource.getParseResult().getRootNode());
	}

	private ILeafNode findLeafNodeByText(ICompositeNode root, String model, String text) {
		return NodeModelUtils.findLeafNodeAtOffset(root, model.indexOf(text));
	}

	public void testPartialParseConcreteRuleInnermostToken() throws Exception {
		with(PartialParserTestLanguageStandaloneSetup.class);
		String model = 
				"container c1 {\n" +
				"  children {\n" +
				"    -> C ( ch1 )\n" +
				"  }" +
				"}";
		XtextResource resource = getResourceFromString(model);
		assertTrue(resource.getErrors().isEmpty());
		ICompositeNode root = resource.getParseResult().getRootNode();
		ILeafNode childrenLeaf = findLeafNodeByText(root, model, "children");
		ILeafNode ch1Leaf = findLeafNodeByText(root, model, "ch1");
		resource.update(model.indexOf("ch1") + 1, 1, "h");
		resource.update(model.indexOf("ch1") + 1, 1, "h");
		assertSame(root, resource.getParseResult().getRootNode());
		assertSame(childrenLeaf, findLeafNodeByText(root, model, "children"));
		assertNotSame(ch1Leaf, findLeafNodeByText(root, model, "ch1"));
	}

	public void testPartialParseConcreteRuleInnerToken() throws Exception {
		with(PartialParserTestLanguageStandaloneSetup.class);
		String model = "container c1 {\n" +
				"  children {\n" +
				"    -> C ( ch1 )\n" +
				"  }" +
				"}";
		XtextResource resource = getResourceFromString(model);
		assertTrue(resource.getErrors().isEmpty());
		ICompositeNode root = resource.getParseResult().getRootNode();
		ILeafNode childrenLeaf = findLeafNodeByText(root, model, "children");
		ILeafNode cLeaf = findLeafNodeByText(root, model, "C");
		resource.update(model.indexOf("C"), 1, "C");
		resource.update(model.indexOf("C"), 1, "C");
		assertSame(root, resource.getParseResult().getRootNode());
		assertSame(childrenLeaf, findLeafNodeByText(root, model, "children"));
		assertNotSame(cLeaf, findLeafNodeByText(root, model, "ch1"));
	}

	public void testPartialParseConcreteRuleFirstInnerToken() throws Exception {
		with(PartialParserTestLanguageStandaloneSetup.class);
		String model = "container c1 {\n" +
				"  children {\n" +
				"    -> C ( ch1 )\n" +
				"  }" +
				"}";
		XtextResource resource = getResourceFromString(model);
		assertTrue(resource.getErrors().isEmpty());
		ICompositeNode root = resource.getParseResult().getRootNode();
		ILeafNode childrenLeaf = findLeafNodeByText(root, model, "children");
		ILeafNode arrowLeaf = findLeafNodeByText(root, model, "->");
		resource.update(model.indexOf("->"), 2, "->");
		resource.update(model.indexOf("->"), 2, "->");
		assertSame(root, resource.getParseResult().getRootNode());
		assertSame(childrenLeaf, findLeafNodeByText(root, model, "children"));
		assertNotSame(arrowLeaf, findLeafNodeByText(root, model, "->"));
	}

	public void testPartialParseConcreteRuleFirstToken() throws Exception {
		with(PartialParserTestLanguageStandaloneSetup.class);
		String model = "container c1 {\n" +
				"  children {\n" +
				"    -> C ( ch1 )\n" +
				"  }" +
				"}";
		XtextResource resource = getResourceFromString(model);
		assertTrue(resource.getErrors().isEmpty());
		ICompositeNode root = resource.getParseResult().getRootNode();
		ILeafNode children = findLeafNodeByText(root, model, "children");
		resource.update(model.indexOf("n {") + 2, 1, "{");
		resource.update(model.indexOf("n {") + 2, 1, "{");
		assertSame(root, resource.getParseResult().getRootNode());
		assertNotSame(children, findLeafNodeByText(root, model, "children"));
	}

	private void checkGrammarAssigned(ICompositeNode rootNode) {
		BidiTreeIterator<INode> iterator = rootNode.iterator();
		while(iterator.hasNext()) {
			INode next = iterator.next();
			if (next != rootNode) {
				assertNotNull(next.getGrammarElement());
			}
		}
	}

	public void testNodeState() throws Exception {
		with(SimpleExpressionsTestLanguageStandaloneSetup.class);
		String model = 
			    "(a\r\n" +
				"+(b\r\n" +
				"*c\r\n" +
				")+d\r\n" +
				")";
		IParseResult parseResult = getParseResult(model);
		ICompositeNode rootNode = parseResult.getRootNode();
		Iterator<ILeafNode> iter = rootNode.getLeafNodes().iterator();
		boolean found = false;
		while (iter.hasNext()) {
			ILeafNode leaf = iter.next();
			if (leaf.getText().equals("c")) {
				assertEquals("before", 10, leaf.getTotalOffset());
				assertEquals("before", 3, leaf.getTotalStartLine());
				found = true;
			}
		}
		assertTrue("node c found", found);
		IParseResult reparse = reparse(parseResult, model.indexOf('c'), 1, "xy");
		assertFalse(reparse.hasSyntaxErrors());
		iter = rootNode.getLeafNodes().iterator();
		found = false;
		while (iter.hasNext()) {
			ILeafNode leaf = iter.next();
			if (leaf.getText().equals("xy")) {
				assertEquals("after", 3, leaf.getTotalStartLine());
				assertEquals("after", 10, leaf.getTotalOffset());
				found = true;
			}
		}
		assertTrue("node xy found", found);
	}

	private void partiallyParseAndCompare(String model, int offset, int length) throws Exception {
		IParseResult fullParseResult = getParser().parse(new StringReader(model));
		IParseResult oldParseResult = getParser().parse(new StringReader(model));
		IParseResult partialParseResult = reparse(oldParseResult, offset, length, model.substring(offset, offset + length));
		assertSameStructure(fullParseResult.getRootNode(), partialParseResult.getRootNode());
		comparator.assertSameStructure(fullParseResult.getRootASTElement(), partialParseResult.getRootASTElement());
	}
	
	/**
	 * @see https://bugs.eclipse.org/bugs/show_bug.cgi?id=273209
	 */
	public void testBug273209_01() throws Exception {
		with(XtextStandaloneSetup.class);
		String model = "grammar org.eclipse.Bug273209_01 with org.eclipse.xtext.common.Terminals \n" +
				"generate testLanguage 'http://www.eclipse.org/2009/tmf/xtext/partialParsing/Bug273209/1'\n" +
				"Model : \n" +
				"        'model' ':' name=ID ';'*;";
		XtextResource resource = getResourceFromString(model);
		assertTrue(resource.getErrors().toString(), resource.getErrors().isEmpty());
		model = resource.getParseResult().getRootNode().getText();
		resource.update(model.indexOf("*;") + 1, 1, "");
		assertEquals(resource.getErrors().toString(), 1, resource.getErrors().size());
		model = resource.getParseResult().getRootNode().getText();
		resource.update(model.indexOf("*") + 1, 0, " ");
		assertEquals(resource.getErrors().toString(), 1, resource.getErrors().size());
		model = resource.getParseResult().getRootNode().getText();
		resource.update(model.indexOf("* ") + 2, 0, ";");
		assertTrue(resource.getErrors().toString(), resource.getErrors().isEmpty());
	}
	
	/**
	 * @see https://bugs.eclipse.org/bugs/show_bug.cgi?id=273209
	 */
	public void testBug273209_02() throws Exception {
		with(XtextStandaloneSetup.class);
		String model = "grammar org.eclipse.Bug273209_01 with org.eclipse.xtext.common.Terminals \n" +
				"generate testLanguage 'http://www.eclipse.org/2009/tmf/xtext/partialParsing/Bug273209/2'\n" +
				"importa : \n" +
				"        name=ID;\n" +
				"Model : \n" +
				"        import;";
		XtextResource resource = getResourceFromStringAndExpect(model, 1);
		assertEquals(resource.getErrors().toString(), 1, resource.getErrors().size());
		model = resource.getParseResult().getRootNode().getText();
		resource.update(model.indexOf("import;") + "import".length(), 1, "");
		assertEquals(resource.getErrors().toString(), 1, resource.getErrors().size());
		model = resource.getParseResult().getRootNode().getText();
		resource.update(model.indexOf(" import") + " import".length(), 0, "a");
		assertEquals(resource.getErrors().toString(), 1, resource.getErrors().size());
		model = resource.getParseResult().getRootNode().getText();
		resource.update(model.indexOf(" importa") + " importa".length(), 0, ";");
		assertTrue(resource.getErrors().toString(), resource.getErrors().isEmpty());
	}
	
	/**
	 * @see https://bugs.eclipse.org/bugs/show_bug.cgi?id=273209
	 */
	public void testBug273209_03() throws Exception {
		with(XtextStandaloneSetup.class);
		String model = "grammar org.eclipse.Bug273209_01 with org.eclipse.xtext.common.Terminals \n" +
				"generate testLanguage 'http://www.eclipse.org/2009/tmf/xtext/partialParsing/Bug273209/3'\n" +
				"Model : \n" +
				"        ('model' ':' name=ID ';'*);";
		XtextResource resource = getResourceFromString(model);
		assertTrue(resource.getErrors().toString(), resource.getErrors().isEmpty());
		model = resource.getParseResult().getRootNode().getText();
		resource.update(model.indexOf("*);") + 1, 2, "");
		assertEquals(resource.getErrors().toString(), 1, resource.getErrors().size());
		model = resource.getParseResult().getRootNode().getText();
		resource.update(model.indexOf("*") + 1, 0, " ");
		assertEquals(resource.getErrors().toString(), 1, resource.getErrors().size());
		model = resource.getParseResult().getRootNode().getText();
		resource.update(model.indexOf("* ") + 2, 0, ");");
		assertTrue(resource.getErrors().toString(), resource.getErrors().isEmpty());
	}
	
	public void testReparseEmptyString() throws Exception {
		with(XtextStandaloneSetup.class);
		String model = "grammar org.eclipse.Bug273209_01 with org.eclipse.xtext.common.Terminals \n" +
		"generate testLanguage 'http://www.eclipse.org/2009/tmf/xtext/partialParsing/Bug273209/3'\n" +
		"Model : \n" +
		"        ('model' ':' name=ID ';'*);";
		XtextResource resource = getResourceFromString(model);
		assertTrue(resource.getErrors().toString(), resource.getErrors().isEmpty());
		resource.update(0, model.length(), "");
		assertTrue(resource.getContents().isEmpty());
	}
}
