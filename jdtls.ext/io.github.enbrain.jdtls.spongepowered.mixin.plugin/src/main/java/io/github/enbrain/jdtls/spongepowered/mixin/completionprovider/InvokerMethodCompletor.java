package io.github.enbrain.jdtls.spongepowered.mixin.completionprovider;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.IMethod;
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
        Set<String> result = new HashSet<>();

        for (IType target : targetClasses) {
            for (IMethod method : target.getMethods()) {
                result.add(method.isConstructor() ? "<init>" : method.getElementName());
            }
        }

        return List.copyOf(result);
    }

    @Override
    protected int getCompletionItemKind() {
        return Util.METHOD_ITEM;
    }
}
