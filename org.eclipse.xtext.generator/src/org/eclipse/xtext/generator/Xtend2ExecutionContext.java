/*******************************************************************************
 * Copyright (c) 2011 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.xtext.generator;

import org.eclipse.xpand2.XpandExecutionContext;

/**
 * @author Moritz Eysholdt - Initial contribution and API
 * @author Jan Koehnlein
 * @since 2.0
 */
@Deprecated
public class Xtend2ExecutionContext {
	XpandExecutionContext legacyContext;

	/**
	 * @since 2.8
	 */
	public Xtend2ExecutionContext(XpandExecutionContext legacyContext) {
		super();
		this.legacyContext = legacyContext;
	}

	/**
	 * @since 2.0
	 */
	public void writeFile(String outletName, String filename, CharSequence contents) {
		legacyContext.getOutput().openFile(filename, outletName);
		legacyContext.getOutput().write(contents.toString());
		legacyContext.getOutput().closeFile();
	}
	
	/**
	 * @since 2.4
	 */
	public void append(CharSequence contents) {
		legacyContext.getOutput().write(contents.toString());
	}
		
	/**
	 * @since 2.4
	 */
	public XpandExecutionContext getXpandExecutionContext() {
		return legacyContext;
	}
	
}