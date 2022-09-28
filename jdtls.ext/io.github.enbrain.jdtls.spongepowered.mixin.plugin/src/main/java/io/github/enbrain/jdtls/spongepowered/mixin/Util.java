package io.github.enbrain.jdtls.spongepowered.mixin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IMemberValuePairBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.internal.core.JavaElement;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.objectweb.asm.Opcodes;

public final class Util {
    private Util() {
    }

    public static final String JAVA_VERSION = JavaCore.VERSION_18;

    public static final int ASM_VERSION = Opcodes.ASM9;

    public static final Set<String> INJECTORS = Set.of(
            "org.spongepowered.asm.mixin.injection.Inject",
            "org.spongepowered.asm.mixin.injection.ModifyArg",
            "org.spongepowered.asm.mixin.injection.ModifyArgs",
            "org.spongepowered.asm.mixin.injection.ModifyConstant",
            "org.spongepowered.asm.mixin.injection.ModifyVariable",
            "org.spongepowered.asm.mixin.injection.Redirect",
            "com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation",
            "com.llamalad7.mixinextras.injector.ModifyExpressionValue",
            "com.llamalad7.mixinextras.injector.ModifyReceiver",
            "com.llamalad7.mixinextras.injector.ModifyReturnValue",
            "com.llamalad7.mixinextras.injector.WrapWithCondition");

    public static final String INJECTION_POINT_ANNOTATION = "org.spongepowered.asm.mixin.injection.At";

    public static final String MIXIN_ANNOTATION = "org.spongepowered.asm.mixin.Mixin";

    public static final String ACCESSOR_ANNOTATION = "org.spongepowered.asm.mixin.gen.Accessor";

    public static final String INVOKER_ANNOTATION = "org.spongepowered.asm.mixin.gen.Invoker";

    public static final String SHADOW_ANNOTATION = "org.spongepowered.asm.mixin.Shadow";

    public static final int METHOD_ITEM = 2;

    public static final int FIELD_ITEM = 4;

    public static final int VALUE_ITEM = 11;

    public static final List<String> INJECTION_POINT_TYPES = List.of(
            "HEAD",
            "RETURN",
            "TAIL",
            "INVOKE",
            "INVOKE_STRING",
            "INVOKE_ASSIGN",
            "FIELD",
            "NEW",
            "JUMP",
            "CONSTANT");

    public static Annotation getAnnotationFromMember(ASTNode node, String member) {
        if (node instanceof MemberValuePair pair
                && pair.getName().getIdentifier().equals(member)
                && pair.getParent() instanceof NormalAnnotation annotation) {
            return annotation;
        }

        if (member.equals("value") && node instanceof SingleMemberAnnotation annotation) {
            return annotation;
        }

        return null;
    }

    public static List<IType> getTargetClasses(ASTNode root, ASTNode node) throws JavaModelException {
        List<IType> result = new ArrayList<>();

        TypeDeclaration typeDeclaration = getEnclosingClass(root, node);
        if (typeDeclaration != null) {
            ITypeBinding classBinding = typeDeclaration.resolveBinding();
            IAnnotationBinding[] classAnnotations = classBinding.getAnnotations();
            for (IAnnotationBinding annotation : classAnnotations) {
                String name = annotation.getAnnotationType().getQualifiedName();
                if (name.equals(MIXIN_ANNOTATION)) {
                    for (IMemberValuePairBinding pair : annotation.getAllMemberValuePairs()) {
                        if (pair.getName().equals("value")) {
                            Object[] targets = (Object[]) pair.getValue();
                            for (Object target : targets) {
                                if (target instanceof ITypeBinding targetBinding) {
                                    result.add((IType) targetBinding.getJavaElement());
                                }
                            }
                        } else if (pair.getName().equals("targets")) {
                            Object[] targets = (Object[]) pair.getValue();
                            for (Object target : targets) {
                                if (target instanceof String targetString) {
                                    for (IType type : findTypes(targetString)) {
                                        result.add(type);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return result;
    }

    public static ASTNode getEnclosingNode(ASTNode root, ASTNode node, Predicate<ASTNode> predicate) {
        node = node.getParent();
        while (!predicate.test(node) && node != root) {
            node = node.getParent();
        }
        return predicate.test(node) ? node : null;
    }

    public static List<String> collectFields(List<IType> targetClasses) throws JavaModelException {
        Set<String> result = new HashSet<>();

        for (IType target : targetClasses) {
            if (target instanceof JavaElement element) {
                for (Object object : element.getChildrenOfType(8)) {
                    if (object instanceof IField f) {
                        result.add(f.getElementName());
                    }
                }
            }
        }

        return List.copyOf(result);
    }

    public static List<String> collectMethods(List<IType> targetClasses) throws JavaModelException {
        Set<String> result = new HashSet<>();

        for (IType target : targetClasses) {
            for (IMethod method : target.getMethods()) {
                result.add(method.isConstructor() ? "<init>" : method.getElementName());
            }
        }

        return List.copyOf(result);
    }

    private static TypeDeclaration getEnclosingClass(ASTNode root, ASTNode node) {
        return (TypeDeclaration) getEnclosingNode(root, node, n -> n instanceof TypeDeclaration);
    }

    private static List<IType> findTypes(String className) throws JavaModelException {
        List<IType> result = new ArrayList<>();

        String name = className.replace("/", ".").replace("$", ".");

        for (IJavaProject project : ProjectUtils.getJavaProjects()) {
            IType type = findType(project, name);
            if (type != null) {
                result.add(type);
            }
        }

        return result;
    }

    private static IType findType(IJavaProject project, String className) throws JavaModelException {
        IType type = project.findType(className);
        if (type != null) {
            return type;
        }

        int i = className.lastIndexOf('.');
        if (i > 0) {
            IType outerType = findType(project, className.substring(0, i));
            if (outerType != null) {
                IType innerType = outerType.getType(className.substring(i + 1));
                if (innerType != null) {
                    return innerType;
                }
            }
        }

        return null;
    }
}
