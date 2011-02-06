/*******************************************************************************
 * Copyright (c) 2010 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.xtext.xbase.scoping;

import static com.google.common.collect.Iterables.*;

import java.util.List;

import org.apache.log4j.Logger;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.common.types.JvmConstructor;
import org.eclipse.xtext.common.types.JvmDeclaredType;
import org.eclipse.xtext.common.types.JvmFormalParameter;
import org.eclipse.xtext.common.types.JvmIdentifiableElement;
import org.eclipse.xtext.common.types.JvmTypeReference;
import org.eclipse.xtext.common.types.TypesPackage;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.resource.EObjectDescription;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.impl.MapBasedScope;
import org.eclipse.xtext.scoping.impl.SingletonScope;
import org.eclipse.xtext.xbase.XAbstractFeatureCall;
import org.eclipse.xtext.xbase.XAssignment;
import org.eclipse.xtext.xbase.XBinaryOperation;
import org.eclipse.xtext.xbase.XBlockExpression;
import org.eclipse.xtext.xbase.XCasePart;
import org.eclipse.xtext.xbase.XCatchClause;
import org.eclipse.xtext.xbase.XClosure;
import org.eclipse.xtext.xbase.XExpression;
import org.eclipse.xtext.xbase.XFeatureCall;
import org.eclipse.xtext.xbase.XForLoopExpression;
import org.eclipse.xtext.xbase.XMemberFeatureCall;
import org.eclipse.xtext.xbase.XSwitchExpression;
import org.eclipse.xtext.xbase.XUnaryOperation;
import org.eclipse.xtext.xbase.XVariableDeclaration;
import org.eclipse.xtext.xbase.XbasePackage;
import org.eclipse.xtext.xbase.featurecalls.IdentifiableSimpleNameProvider;
import org.eclipse.xtext.xbase.featurecalls.IdentifiableTypeProvider;
import org.eclipse.xtext.xbase.scoping.featurecalls.DefaultJvmFeatureDescriptionProvider;
import org.eclipse.xtext.xbase.scoping.featurecalls.JvmFeatureDescription;
import org.eclipse.xtext.xbase.scoping.featurecalls.JvmFeatureScope;
import org.eclipse.xtext.xbase.scoping.featurecalls.JvmFeatureScopeProvider;
import org.eclipse.xtext.xbase.scoping.featurecalls.StaticMethodsFeatureForTypeProvider;
import org.eclipse.xtext.xbase.scoping.featurecalls.XAssignmentDescriptionProvider;
import org.eclipse.xtext.xbase.scoping.featurecalls.XAssignmentSugarDescriptionProvider;
import org.eclipse.xtext.xbase.scoping.featurecalls.XFeatureCallSugarDescriptionProvider;
import org.eclipse.xtext.xbase.typing.IXExpressionTypeProvider;

import com.google.common.base.Function;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.internal.Lists;

/**
 * @author Sven Efftinge - Initial contribution and API
 */
public class XbaseScopeProvider extends XtypeScopeProvider {

	private final static Logger log = Logger.getLogger(XbaseScopeProvider.class);

	public static final QualifiedName THIS = QualifiedName.create("this");
	public static final QualifiedName ASSIGN = QualifiedName.create("=");
	public static final QualifiedName ADD = QualifiedName.create("+=");

	@Inject
	private JvmFeatureScopeProvider jvmFeatureScopeProvider;

	@Inject
	private Provider<DefaultJvmFeatureDescriptionProvider> defaultFeatureDescProvider;

	@Inject
	private Provider<XFeatureCallSugarDescriptionProvider> sugarFeatureDescProvider;

	@Inject
	private Provider<StaticMethodsFeatureForTypeProvider> staticExtensionMethodsFeaturesForTypeProvider;

	@Inject
	private Provider<XAssignmentDescriptionProvider> assignmentFeatureDescProvider;

	@Inject
	private Provider<XAssignmentSugarDescriptionProvider> assignmentSugarFeatureDescProvider;

	@Inject
	private IXExpressionTypeProvider typeProvider;

	@Inject
	private IdentifiableTypeProvider identifiableTypeProvider;

	@Inject
	private IdentifiableSimpleNameProvider featureNameProvider;

	public void setFeatureNameProvider(IdentifiableSimpleNameProvider featureNameProvider) {
		this.featureNameProvider = featureNameProvider;
	}

	public void setTypeProvider(IXExpressionTypeProvider typeProvider) {
		this.typeProvider = typeProvider;
	}

	protected IXExpressionTypeProvider getTypeProvider() {
		return typeProvider;
	}

	public void setSugarFeatureDescProvider(Provider<XFeatureCallSugarDescriptionProvider> sugarFeatureDescProvider) {
		this.sugarFeatureDescProvider = sugarFeatureDescProvider;
	}

	public void setDefaultFeatureDescProvider(Provider<DefaultJvmFeatureDescriptionProvider> defaultFeatureDescProvider) {
		this.defaultFeatureDescProvider = defaultFeatureDescProvider;
	}

	@Override
	public IScope getScope(EObject context, EReference reference) {
		try {
			if (isFeatureCallScope(reference)) {
				if (!(context instanceof XAbstractFeatureCall)) {
					return IScope.NULLSCOPE;
				}
				return createFeatureCallScope((XAbstractFeatureCall) context, reference);
			}
			if (isConstructorCallScope(reference)) {
				return createConstructorCallScope(context, reference);
			}
			return super.getScope(context, reference);
		} catch (RuntimeException e) {
			log.error("error during scoping", e);
			throw e;
		}
	}

	protected IScope createConstructorCallScope(EObject context, EReference reference) {
		final IScope scope = super.getScope(context, reference);
		return new IScope() {

			public Iterable<IEObjectDescription> getAllElements() {
				Iterable<IEObjectDescription> original = scope.getAllElements();
				return createFeatureDescriptions(original);
			}

			protected Iterable<IEObjectDescription> createFeatureDescriptions(Iterable<IEObjectDescription> original) {
				Iterable<IEObjectDescription> result = transform(original,
						new Function<IEObjectDescription, IEObjectDescription>() {
							public IEObjectDescription apply(IEObjectDescription from) {
								final JvmConstructor constructor = (JvmConstructor) from.getEObjectOrProxy();
								return new JvmFeatureDescription(from.getQualifiedName(), constructor, null,
										constructor.getCanonicalName(), true, null, false);
							}
						});
				return result;
			}

			public Iterable<IEObjectDescription> getElements(EObject object) {
				Iterable<IEObjectDescription> original = scope.getElements(object);
				return createFeatureDescriptions(original);
			}

			public Iterable<IEObjectDescription> getElements(QualifiedName name) {
				Iterable<IEObjectDescription> original = scope.getElements(name);
				return createFeatureDescriptions(original);
			}

			public IEObjectDescription getSingleElement(EObject object) {
				throw new UnsupportedOperationException();
			}

			public IEObjectDescription getSingleElement(QualifiedName name) {
				throw new UnsupportedOperationException();
			}

		};
	}

	protected boolean isConstructorCallScope(EReference reference) {
		return reference.getEReferenceType() == TypesPackage.Literals.JVM_CONSTRUCTOR;
	}

	public boolean isFeatureCallScope(EReference reference) {
		return reference == XbasePackage.Literals.XABSTRACT_FEATURE_CALL__FEATURE;
	}

	/**
	 * creates the feature scope for {@link XAbstractFeatureCall}, including the local variables in case it is a feature
	 * call without receiver (XFeatureCall).
	 */
	protected IScope createFeatureCallScope(final XAbstractFeatureCall call, EReference reference) {
		if (call instanceof XFeatureCall
				|| ((call instanceof XAssignment) && ((XAssignment) call).getAssignable() == null)) {
			IScope result = createSimpleFeatureCallScope(call, reference, false, -1);
			return result;
		}
		final XExpression syntacticalReceiver = getSyntacticalReceiver(call);
		IScope result = createFeatureCallScopeForReceiver(call, syntacticalReceiver, reference);
		return result;
	}

	/**
	 * This method serves as an entry point for the content assist scoping for simple feature calls.
	 * @param context the context e.g. a for loop expression, a block or a catch clause
	 * @param reference the reference who's value shall be scoped. Not necessarily a feature of the context.
	 * @param includeCurrentBlock <code>false</code> in the context of scoping but content assist will not have the
	 *   actual value holder of the reference at hand so it passes its container to this method and expects the 
	 *   declared variables to be exposed in the scope.
	 * @param idx the index in an expression list of a block. Otherwise to be ignored.
	 */
	public IScope createSimpleFeatureCallScope(final EObject context, EReference reference, boolean includeCurrentBlock, int idx) {
		DelegatingScope implicitThis = new DelegatingScope();
		IScope localVariableScope = createLocalVarScope(context, reference, implicitThis, includeCurrentBlock, idx);
		IScope featureScopeForThis = createImplicitFeatureCallScope(context, localVariableScope);
		if (featureScopeForThis != null)
			implicitThis.setDelegate(featureScopeForThis);
		return localVariableScope;
	}

	/**
	 * This method serves as an entry point for the content assist scoping for features.
	 * @param context the context provides access to the resource set. If it is an assignment, it 
	 *   will be used to restrict scoping.
	 * @param receiver the receiver of the feature call.
	 */
	public IScope createFeatureCallScopeForReceiver(final XExpression context, final XExpression receiver, EReference reference) {
		if (!isFeatureCallScope(reference))
			return IScope.NULLSCOPE;
		if (receiver == null || receiver.eIsProxy())
			return IScope.NULLSCOPE;
		JvmTypeReference receiverType = typeProvider.getConvertedType(receiver);
		if (receiverType != null) {
			return createFeatureScopeForTypeRef(receiverType, context, getContextType(context), null);
		}
		return IScope.NULLSCOPE;
	}

	protected XExpression getSyntacticalReceiver(final XAbstractFeatureCall call) {
		if (call instanceof XMemberFeatureCall) {
			return ((XMemberFeatureCall) call).getMemberCallTarget();
		}
		if (call instanceof XBinaryOperation) {
			return ((XBinaryOperation) call).getLeftOperand();
		}
		if (call instanceof XUnaryOperation) {
			return ((XUnaryOperation) call).getOperand();
		}
		if (call instanceof XAssignment) {
			return ((XAssignment) call).getAssignable();
		}
		return null;
	}

	/**
	 * override to add any other implicit feature calls.
	 */
	protected IScope createImplicitFeatureCallScope(final EObject call, IScope localVariableScope) {
		JvmFeatureScope featureScopeForThis = null;
		IEObjectDescription thisVariable = localVariableScope.getSingleElement(THIS);
		if (thisVariable != null) {
			EObject thisVal = thisVariable.getEObjectOrProxy();
			JvmTypeReference type = identifiableTypeProvider.getType((JvmIdentifiableElement) thisVal);
			if (type != null) {
				featureScopeForThis = createFeatureScopeForTypeRef(type, call, getContextType(call),
						(JvmIdentifiableElement) thisVariable.getEObjectOrProxy());
			}
		}
		return featureScopeForThis;
	}

	protected JvmDeclaredType getContextType(EObject call) {
		return EcoreUtil2.getContainerOfType(call, JvmDeclaredType.class);
	}

	protected QualifiedName getAssignmentOperator(XAssignment assignment) {
		return ASSIGN;
	}

	protected IScope createLocalVarScope(EObject context, EReference reference, IScope parentScope, boolean includeCurrentBlock, int idx) {
		if (context == null)
			return parentScope;
		if (context.eContainer() != null)
			parentScope = createLocalVarScope(context.eContainer(), reference, parentScope, false, -1);
		if (context.eContainer() instanceof XBlockExpression) {
			XBlockExpression block = (XBlockExpression) context.eContainer();
			parentScope = createLocalVarScopeForBlock(block, block.getExpressions().indexOf(context), parentScope);
		}
		if (context.eContainer() instanceof XForLoopExpression && context.eContainingFeature() == XbasePackage.Literals.XFOR_LOOP_EXPRESSION__EACH_EXPRESSION) {
			XForLoopExpression loop = (XForLoopExpression) context.eContainer();
			parentScope = createLocalScopeForParameter(loop.getDeclaredParam(), parentScope);
		}
		if (context.eContainer() instanceof XCatchClause) {
			XCatchClause catchClause = (XCatchClause) context.eContainer();
			parentScope = createLocalScopeForParameter(catchClause.getDeclaredParam(), parentScope);
		}
		if (context instanceof XClosure) {
			parentScope = createLocalVarScopeForClosure((XClosure) context, parentScope);
		}
		if (context instanceof XCasePart) {
			parentScope = createLocalVarScopeForTypeGuardedCase((XCasePart) context, parentScope);
		}
		if (context instanceof XSwitchExpression) {
			parentScope = createLocalVarScopeForSwitchExpression((XSwitchExpression) context, parentScope);
		}
		if (includeCurrentBlock) {
			if (context instanceof XBlockExpression) {
				XBlockExpression block = (XBlockExpression) context;
				if (!block.getExpressions().isEmpty()) {
					parentScope = createLocalVarScopeForBlock(block, idx, parentScope);
				}
			}
			if (context instanceof XForLoopExpression) {
				parentScope = createLocalScopeForParameter(((XForLoopExpression) context).getDeclaredParam(), parentScope);
			}
			if (context instanceof XCatchClause) {
				parentScope = createLocalScopeForParameter(((XCatchClause) context).getDeclaredParam(), parentScope);
			}
		}
		return parentScope;
	}

	protected IScope createLocalVarScopeForSwitchExpression(XSwitchExpression context, IScope parentScope) {
		if (context.getLocalVarName() != null) {
			return new SingletonScope(EObjectDescription.create(QualifiedName.create(context.getLocalVarName()),
					context), parentScope);
		}
		return parentScope;
	}

	protected IScope createLocalVarScopeForTypeGuardedCase(XCasePart context, IScope parentScope) {
		JvmTypeReference guard = context.getTypeGuard();
		if (guard == null) {
			return parentScope;
		}
		String varName = featureNameProvider.getSimpleName(context);
		if (varName == null) {
			return parentScope;
		}
		return new SingletonScope(EObjectDescription.create(QualifiedName.create(varName), context), parentScope);
	}

	protected IScope createLocalVarScopeForCatchClause(XCatchClause catchClause, int indexOfContextExpressionInBlock,
			IScope parentScope) {
		return createLocalScopeForParameter(catchClause.getDeclaredParam(), parentScope);
	}

	protected IScope createLocalVarScopeForBlock(XBlockExpression block, int indexOfContextExpressionInBlock,
			IScope parentScope) {
		List<IEObjectDescription> descriptions = Lists.newArrayList();
		for (int i = 0; i < indexOfContextExpressionInBlock; i++) {
			XExpression expression = block.getExpressions().get(i);
			if (expression instanceof XVariableDeclaration) {
				XVariableDeclaration varDecl = (XVariableDeclaration) expression;
				if (varDecl.getName() != null) {
					IEObjectDescription desc = createEObjectDescription(varDecl);
					descriptions.add(desc);
				}
			}
		}
		return MapBasedScope.createScope(parentScope, descriptions);
	}

	protected IScope createLocalVarScopeForClosure(XClosure closure, IScope parentScope) {
		List<IEObjectDescription> descriptions = Lists.newArrayList();
		EList<JvmFormalParameter> params = closure.getFormalParameters();
		for (JvmFormalParameter p : params) {
			if (p.getName() != null) {
				IEObjectDescription desc = createEObjectDescription(p);
				descriptions.add(desc);
			}
		}
		return MapBasedScope.createScope(parentScope, descriptions);
	}

	protected JvmFeatureScope createFeatureScopeForTypeRef(JvmTypeReference type, EObject expression,
			JvmDeclaredType currentContext, JvmIdentifiableElement implicitReceiver) {
		if (expression instanceof XAssignment) {
			XAssignmentDescriptionProvider provider1 = assignmentFeatureDescProvider.get();
			XAssignmentSugarDescriptionProvider provider2 = assignmentSugarFeatureDescProvider.get();
			provider1.setContextType(currentContext);
			provider1.setImplicitReceiver(implicitReceiver);
			provider2.setContextType(currentContext);
			provider2.setImplicitReceiver(implicitReceiver);
			return jvmFeatureScopeProvider.createFeatureScopeForTypeRef(type, provider1, provider2);
		} else {
			final DefaultJvmFeatureDescriptionProvider provider1 = defaultFeatureDescProvider.get();
			final XFeatureCallSugarDescriptionProvider provider2 = sugarFeatureDescProvider.get();
			final DefaultJvmFeatureDescriptionProvider provider3 = defaultFeatureDescProvider.get();
			final StaticMethodsFeatureForTypeProvider featuresForTypeProvider = staticExtensionMethodsFeaturesForTypeProvider.get();
			featuresForTypeProvider.setContext(expression);
			provider3.setFeaturesForTypeProvider(featuresForTypeProvider);
			final XFeatureCallSugarDescriptionProvider provider4 = sugarFeatureDescProvider.get();
			provider4.setFeaturesForTypeProvider(featuresForTypeProvider);
			provider1.setContextType(currentContext);
			provider1.setImplicitReceiver(implicitReceiver);
			provider2.setContextType(currentContext);
			provider2.setImplicitReceiver(implicitReceiver);
			provider3.setContextType(currentContext);
			provider3.setImplicitReceiver(implicitReceiver);
			provider4.setContextType(currentContext);
			provider4.setImplicitReceiver(implicitReceiver);
			return jvmFeatureScopeProvider.createFeatureScopeForTypeRef(type, provider1, provider2, provider3,
					provider4);
		}
	}

	protected IScope createLocalScopeForParameter(JvmFormalParameter p, IScope parentScope) {
		return (p.getName() != null) ? new SingletonScope(createEObjectDescription(p), parentScope) : parentScope;
	}

	protected IEObjectDescription createEObjectDescription(JvmFormalParameter p) {
		return EObjectDescription.create(QualifiedName.create(p.getName()), p);
	}

	protected IEObjectDescription createEObjectDescription(XVariableDeclaration varDecl) {
		return EObjectDescription.create(QualifiedName.create(varDecl.getName()), varDecl);
	}

}
