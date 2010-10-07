/*******************************************************************************
 * Copyright (c) 2010 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.xtext.xbase.impl;

import java.util.List;

import org.eclipse.emf.common.util.EList;
import org.eclipse.xtext.parsetree.AbstractNode;
import org.eclipse.xtext.parsetree.LeafNode;
import org.eclipse.xtext.parsetree.NodeUtil;
import org.eclipse.xtext.xbase.XbasePackage;

/**
 * @author Sven Efftinge - Initial contribution and API
 */
public class XFeatureCallImplCustom extends XFeatureCallImpl {
	
	@Override
	public String getFeatureName() {
		List<AbstractNode> list = NodeUtil.findNodesForFeature(this, XbasePackage.Literals.XFEATURE_CALL__FEATURE);
		if (list.size()!=1) {
			throw new IllegalStateException("A feature call should have exactly one leafnode for the 'feature' reference, but was "+list.size());
		}
		AbstractNode abstractNode = list.get(0);
		if (abstractNode instanceof LeafNode) {
			return abstractNode.serialize();
		}
		EList<LeafNode> leafNodes = abstractNode.getLeafNodes();
		StringBuilder result = new StringBuilder();
		for (LeafNode leafNode : leafNodes) {
			if (!leafNode.isHidden())
				result.append(leafNode.getText());
		}
		return result.toString();
	}
}
