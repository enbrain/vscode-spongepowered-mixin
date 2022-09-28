package io.github.enbrain.jdtls.spongepowered.mixin.completionprovider;

import java.util.List;

import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import io.github.enbrain.jdtls.spongepowered.mixin.Util;

/**
 * Provides completion items for {@code @Invoker(value = "...")}.
 */
public class InvokerMethodCompletor extends AccessorOrInvokerMemberCompletor {
    @Override
    protected String getAnnotationClassName() {
        return Util.INVOKER_ANNOTATION;
    }

    @Override
    protected List<String> collectMembers(List<IType> targetClasses) throws JavaModelException {
        return Util.collectMethods(targetClasses);
    }

    @Override
    protected int getCompletionItemKind() {
        return Util.METHOD_ITEM;
    }
}
