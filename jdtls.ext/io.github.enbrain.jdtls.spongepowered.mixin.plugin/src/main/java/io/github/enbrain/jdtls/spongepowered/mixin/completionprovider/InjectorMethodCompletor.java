package io.github.enbrain.jdtls.spongepowered.mixin.completionprovider;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.internal.compiler.env.IBinaryMethod;
import org.eclipse.jdt.internal.core.BinaryMethod;

import io.github.enbrain.jdtls.spongepowered.mixin.CompletionItem;
import io.github.enbrain.jdtls.spongepowered.mixin.Util;

/**
 * Provides completion items for {@code @Inject(method = "...")}.
 */
public class InjectorMethodCompletor implements Completor {
    @Override
    public List<CompletionItem> complete(ASTNode root, ASTNode current) throws JavaModelException {
        List<CompletionItem> result = new ArrayList<>();

        if (current instanceof StringLiteral) {
            Annotation injectorAnnotation = Util.getAnnotationFromMember(current.getParent(), "method");
            if (injectorAnnotation != null) {
                ITypeBinding injectorBinding = injectorAnnotation.getTypeName().resolveTypeBinding();
                if (injectorBinding != null) {
                    String injectorName = injectorBinding.getQualifiedName();
                    if (Util.INJECTORS.contains(injectorName)) {
                        for (IType targetClass : Util.getTargetClasses(root, injectorAnnotation)) {
                            List<BinaryMethod> binaryMethods = new ArrayList<>();
                            for (IMethod method : targetClass.getMethods()) {
                                if (method instanceof BinaryMethod binaryMethod) {
                                    binaryMethods.add(binaryMethod);
                                }
                            }
                            for (BinaryMethod targetMethod : binaryMethods) {
                                boolean fullName = false;
                                for (BinaryMethod targetMethod2 : binaryMethods) {
                                    fullName |= getName(targetMethod).equals(getName(targetMethod2))
                                            && !getFullName(targetMethod).equals(getFullName(targetMethod2));
                                }

                                result.add(
                                        new CompletionItem(fullName ? getFullName(targetMethod) : getName(targetMethod),
                                                Util.METHOD_ITEM));
                            }
                        }
                    }
                }
            }
        }

        return result;
    }

    private static String getFullName(BinaryMethod method) throws JavaModelException {
        IBinaryMethod info = (IBinaryMethod) method.getElementInfo();
        String descriptor = new String(info.getMethodDescriptor());
        return getName(method) + descriptor;
    }

    private static String getName(BinaryMethod method) throws JavaModelException {
        return method.isConstructor() ? "<init>" : method.getElementName();
    }
}
