package io.github.enbrain.jdtls.spongepowered.mixin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IMemberValuePairBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.env.IBinaryMethod;
import org.eclipse.jdt.internal.core.BinaryMethod;
import org.eclipse.jdt.internal.core.JavaElement;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.handlers.JsonRpcHelpers;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public final class CompletionHandler {
    private static final String JAVA_VERSION = JavaCore.VERSION_18;

    private static final int ASM_VERSION = Opcodes.ASM9;

    private static final Set<String> INJECTORS = Set.of(
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

    private static final String INJECTION_POINT_ANNOTATION = "org.spongepowered.asm.mixin.injection.At";

    private static final List<String> INJECTION_POINT_TYPES = List.of(
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

    private CompletionHandler() {
    }

    public static List<CompletionItem> complete(String uri, int line, int column) throws JavaModelException {
        List<CompletionItem> result = new ArrayList<>();

        ICompilationUnit unit = JDTUtils.resolveCompilationUnit(uri);

        if (unit != null) {
            int offset = JsonRpcHelpers.toOffset(unit.getBuffer(), line, column);
            ASTNode root = parse(unit);
            ASTNode current = NodeFinder.perform(root, offset, 1);

            result.addAll(completeInjectorMethod(root, current));
            result.addAll(completeInjectionPointType(root, current));
            result.addAll(completeInjectionPointTarget(root, current));
            result.addAll(completeAccessorField(root, current));
            result.addAll(completeInvokerMethod(root, current));
        }

        return result;
    }

    private static List<CompletionItem> completeInjectorMethod(ASTNode root, ASTNode current)
            throws JavaModelException {
        List<CompletionItem> result = new ArrayList<>();

        if (current instanceof StringLiteral) {
            Annotation injectorAnnotation = isMemberInAnnotation(current.getParent(), "method");
            if (injectorAnnotation != null) {
                ITypeBinding injectorBinding = injectorAnnotation.getTypeName().resolveTypeBinding();
                if (injectorBinding != null) {
                    String injectorName = injectorBinding.getQualifiedName();
                    if (INJECTORS.contains(injectorName)) {
                        for (IType targetClass : getTargetClasses(root, injectorAnnotation)) {
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

                                result.add(new CompletionItem(
                                        fullName ? getFullName(targetMethod) : getName(targetMethod), 2));

                            }
                        }
                    }
                }
            }
        }

        return result;
    }

    private static List<CompletionItem> completeInjectionPointType(ASTNode root, ASTNode current) {
        List<CompletionItem> result = new ArrayList<>();

        if (current instanceof StringLiteral) {
            Annotation annotation = isMemberInAnnotation(current.getParent(), "value");
            if (annotation != null) {
                ITypeBinding annotationType = annotation.getTypeName().resolveTypeBinding();
                if (annotationType != null) {
                    String annotationName = annotationType.getQualifiedName();

                    if (annotationName.equals(INJECTION_POINT_ANNOTATION)) {
                        for (String injectionPointType : INJECTION_POINT_TYPES) {
                            result.add(new CompletionItem(injectionPointType, 11));
                        }
                    }
                }
            }
        }

        return result;
    }

    private static List<CompletionItem> completeInjectionPointTarget(ASTNode root, ASTNode current)
            throws JavaModelException {
        List<CompletionItem> result = new ArrayList<>();

        if (current instanceof StringLiteral) {
            Annotation injectionPointAnnotation = isMemberInAnnotation(current.getParent(), "target");
            if (injectionPointAnnotation != null) {
                ITypeBinding injectionPointBinding = injectionPointAnnotation.getTypeName().resolveTypeBinding();
                if (injectionPointBinding != null) {
                    String injectionPointAnnotationName = injectionPointBinding.getQualifiedName();
                    if (injectionPointAnnotationName.equals(INJECTION_POINT_ANNOTATION)) {
                        Expression injectionPointType = getMemberValueInAnnotation(injectionPointAnnotation, "value");
                        if (injectionPointType instanceof StringLiteral injectionPointTypeStringLiteral) {
                            String injectionPointTypeString = injectionPointTypeStringLiteral.getLiteralValue();
                            Annotation injectorAnnotation = getEnclosingInjector(root, injectionPointAnnotation);
                            if (injectorAnnotation != null) {
                                Expression targetMethodExpr = getMemberValueInAnnotation(injectorAnnotation,
                                        "method");
                                if (targetMethodExpr instanceof StringLiteral injectorMethodStringLiteral) {
                                    String targetMethod = injectorMethodStringLiteral.getLiteralValue();
                                    List<IType> targetClasses = getTargetClasses(root, injectorAnnotation);
                                    switch (injectionPointTypeString) {
                                        case "INVOKE":
                                            for (String method : collectInvokedMethods(targetClasses, targetMethod)) {
                                                result.add(new CompletionItem(method, 2));
                                            }
                                            break;
                                        case "INVOKE_STRING":
                                            for (String method : collectStringInvokedMethods(targetClasses,
                                                    targetMethod)) {
                                                result.add(new CompletionItem(method, 2));
                                            }
                                            break;
                                        case "INVOKE_ASSIGN":
                                            for (String method : collectNonVoidInvokedMethods(targetClasses,
                                                    targetMethod)) {
                                                result.add(new CompletionItem(method, 2));
                                            }
                                            break;
                                        case "FIELD":
                                            Expression opcodeExpr = getMemberValueInAnnotation(injectionPointAnnotation,
                                                    "opcode");
                                            Integer opcode = null;
                                            if (opcodeExpr != null) {
                                                Object opcodeObject = opcodeExpr.resolveConstantExpressionValue();
                                                if (opcodeObject instanceof Integer opcodeInteger) {
                                                    opcode = opcodeInteger;
                                                }
                                            }

                                            for (String field : collectAccessedFields(targetClasses, targetMethod,
                                                    opcode)) {
                                                result.add(new CompletionItem(field, 4));
                                            }
                                            break;
                                        case "NEW":
                                            for (String field : collectInvokedConstructors(targetClasses,
                                                    targetMethod)) {
                                                result.add(new CompletionItem(field, 2));
                                            }
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

    private static List<CompletionItem> completeAccessorField(ASTNode root, ASTNode current) throws JavaModelException {
        List<CompletionItem> result = new ArrayList<>();

        if (current instanceof StringLiteral) {
            Annotation accessorAnnotation = isMemberInAnnotation(current.getParent(), "value");
            if (accessorAnnotation != null) {
                ITypeBinding annotationType = accessorAnnotation.getTypeName().resolveTypeBinding();
                if (annotationType != null) {
                    String annotationName = annotationType.getQualifiedName();
                    if (annotationName.equals("org.spongepowered.asm.mixin.gen.Accessor")) {
                        List<IType> targetClasses = getTargetClasses(root, accessorAnnotation);
                        for (String field : collectMemberFields(targetClasses)) {
                            result.add(new CompletionItem(field, 4));
                        }
                    }
                }
            }
        }

        return result;
    }

    private static List<CompletionItem> completeInvokerMethod(ASTNode root, ASTNode current) throws JavaModelException {
        List<CompletionItem> result = new ArrayList<>();

        if (current instanceof StringLiteral) {
            Annotation invokerAnnotation = isMemberInAnnotation(current.getParent(), "value");
            if (invokerAnnotation != null) {
                ITypeBinding annotationType = invokerAnnotation.getTypeName().resolveTypeBinding();
                if (annotationType != null) {
                    String annotationName = annotationType.getQualifiedName();
                    if (annotationName.equals("org.spongepowered.asm.mixin.gen.Invoker")) {
                        List<IType> targetClasses = getTargetClasses(root, invokerAnnotation);
                        for (String method : collectMemberMethods(targetClasses)) {
                            result.add(new CompletionItem(method, 2));
                        }
                    }
                }
            }
        }

        return result;
    }

    private static ASTNode parse(ICompilationUnit unit) {
        ASTParser parser = ASTParser.newParser(AST.JLS18);

        Map<String, String> options = JavaCore.getOptions();
        options.put(JavaCore.COMPILER_COMPLIANCE, JAVA_VERSION);
        options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JAVA_VERSION);
        options.put(JavaCore.COMPILER_SOURCE, JAVA_VERSION);

        parser.setSource(unit);
        parser.setResolveBindings(true);
        parser.setStatementsRecovery(true);

        JavaCore.setComplianceOptions(JAVA_VERSION, options);
        parser.setCompilerOptions(options);

        return parser.createAST(new NullProgressMonitor());
    }

    private static Annotation isMemberInAnnotation(ASTNode node, String member) {
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

    @SuppressWarnings("unchecked")
    private static Expression getMemberValueInAnnotation(Annotation annotation, String member) {
        if (annotation instanceof NormalAnnotation normalAnnotation) {
            for (MemberValuePair pair : (List<MemberValuePair>) normalAnnotation.values()) {
                if (pair.getName().getIdentifier().equals(member)) {
                    return pair.getValue();
                }
            }
        }
        if (member.equals("value") && annotation instanceof SingleMemberAnnotation singleMemberAnnotation) {
            return singleMemberAnnotation.getValue();
        }

        return null;
    }

    private static List<IType> getTargetClasses(ASTNode root, ASTNode node) throws JavaModelException {
        List<IType> result = new ArrayList<>();

        TypeDeclaration typeDeclaration = getEnclosingClass(root, node);
        if (typeDeclaration != null) {
            ITypeBinding classBinding = typeDeclaration.resolveBinding();
            IAnnotationBinding[] classAnnotations = classBinding.getAnnotations();
            for (IAnnotationBinding annotation : classAnnotations) {
                String name = annotation.getAnnotationType().getQualifiedName();
                if (name.equals("org.spongepowered.asm.mixin.Mixin")) {
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

    private static TypeDeclaration getEnclosingClass(ASTNode root, ASTNode node) {
        return (TypeDeclaration) getEnclosingNode(root, node, n -> n instanceof TypeDeclaration);
    }

    private static Annotation getEnclosingInjector(ASTNode root, ASTNode node) {
        return (Annotation) getEnclosingNode(root, node, n -> {
            if (n instanceof Annotation annotation) {
                ITypeBinding binding = annotation.getTypeName().resolveTypeBinding();
                if (binding != null) {
                    String name = binding.getQualifiedName();
                    if (INJECTORS.contains(name)) {
                        return true;
                    }
                }
            }
            return false;
        });
    }

    private static ASTNode getEnclosingNode(ASTNode root, ASTNode node, Predicate<ASTNode> predicate) {
        node = node.getParent();
        while (!predicate.test(node) && node != root) {
            node = node.getParent();
        }
        return predicate.test(node) ? node : null;
    }

    private static String getFullName(BinaryMethod method) throws JavaModelException {
        IBinaryMethod info = (IBinaryMethod) method.getElementInfo();
        String descriptor = new String(info.getMethodDescriptor());
        return getName(method) + descriptor;
    }

    private static String getName(BinaryMethod method) throws JavaModelException {
        return method.isConstructor() ? "<init>" : method.getElementName();
    }

    private static List<String> collectInvokedMethods(List<IType> targetClasses, String method)
            throws JavaModelException {
        Set<String> result = new HashSet<>();

        visitMethodInClasses(targetClasses, method, new MethodVisitor(ASM_VERSION) {
            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                result.add("L" + owner + ";" + name + descriptor);
            }
        });

        return List.copyOf(result);
    }

    private static List<String> collectStringInvokedMethods(List<IType> targetClasses, String method)
            throws JavaModelException {
        Set<String> result = new HashSet<>();

        visitMethodInClasses(targetClasses, method, new MethodVisitor(ASM_VERSION) {
            private boolean isLastLdc = false;

            @Override
            public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
                this.isLastLdc = false;
            }

            @Override
            public void visitInsn(int opcode) {
                this.isLastLdc = false;
            }

            @Override
            public void visitIntInsn(int opcode, int operand) {
                this.isLastLdc = false;
            }

            @Override
            public void visitVarInsn(int opcode, int varIndex) {
                this.isLastLdc = false;
            }

            @Override
            public void visitTypeInsn(int opcode, String type) {
                this.isLastLdc = false;
            }

            @Override
            public void visitFieldInsn(
                    int opcode, String owner, String name, String descriptor) {
                this.isLastLdc = false;
            }

            @Override
            public void visitMethodInsn(
                    int opcodeAndSource,
                    String owner,
                    String name,
                    String descriptor,
                    boolean isInterface) {
                if (this.isLastLdc) {
                    Type[] argTypes = Type.getArgumentTypes(descriptor);
                    Type returnType = Type.getReturnType(descriptor);
                    if (argTypes.length == 1 && argTypes[0].getClassName().equals("java.lang.String")
                            && returnType.getSort() == Type.VOID) {
                        result.add("L" + owner + ";" + name + descriptor);
                    }
                }
                this.isLastLdc = false;
            }

            @Override
            public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle,
                    Object... bootstrapMethodArguments) {
                this.isLastLdc = false;
            }

            @Override
            public void visitJumpInsn(int opcode, Label label) {
                this.isLastLdc = false;
            }

            @Override
            public void visitLabel(Label label) {
                this.isLastLdc = false;
            }

            @Override
            public void visitLdcInsn(Object value) {
                this.isLastLdc = true;
            }

            @Override
            public void visitIincInsn(int varIndex, int increment) {
                this.isLastLdc = false;
            }

            @Override
            public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
                this.isLastLdc = false;
            }

            @Override
            public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
                this.isLastLdc = false;
            }

            @Override
            public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
                this.isLastLdc = false;
            }

        });

        return List.copyOf(result);
    }

    private static List<String> collectNonVoidInvokedMethods(List<IType> targetClasses, String method)
            throws JavaModelException {
        Set<String> result = new HashSet<>();

        visitMethodInClasses(targetClasses, method, new MethodVisitor(ASM_VERSION) {
            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                Type returnType = Type.getReturnType(descriptor);
                if (returnType.getSort() != Type.VOID) {
                    result.add("L" + owner + ";" + name + descriptor);
                }
            }
        });

        return List.copyOf(result);
    }

    private static List<String> collectInvokedConstructors(List<IType> targetClasses, String method)
            throws JavaModelException {
        Set<String> result = new HashSet<>();

        visitMethodInClasses(targetClasses, method, new MethodVisitor(ASM_VERSION) {
            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                if (name.equals("<init>")) {
                    result.add("L" + owner + ";" + name + descriptor);
                }
            }
        });

        return List.copyOf(result);
    }

    private static List<String> collectAccessedFields(List<IType> targetClasses, String method, Integer opcode)
            throws JavaModelException {
        Set<String> result = new HashSet<>();

        visitMethodInClasses(targetClasses, method, new MethodVisitor(ASM_VERSION) {
            @Override
            public void visitFieldInsn(int actualOpcode, String owner, String name, String descriptor) {
                if (opcode == null || opcode.intValue() == actualOpcode) {
                    result.add("L" + owner + ";" + name + ":" + descriptor);
                }
            }
        });

        return List.copyOf(result);
    }

    private static List<String> collectMemberFields(List<IType> targetClasses) throws JavaModelException {
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

    private static List<String> collectMemberMethods(List<IType> targetClasses) throws JavaModelException {
        Set<String> result = new HashSet<>();

        for (IType target : targetClasses) {
            for (IMethod method : target.getMethods()) {
                result.add(method.isConstructor() ? "<init>" : method.getElementName());
            }
        }

        return List.copyOf(result);
    }

    private static void visitMethodInClasses(List<IType> targetClasses, String method, MethodVisitor visitor)
            throws JavaModelException {
        for (IType targetClass : targetClasses) {
            ClassReader reader = new ClassReader(targetClass.getClassFile().getBytes());
            reader.accept(new ClassVisitor(ASM_VERSION) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                        String signature, String[] exceptions) {
                    if (name.equals(method) || (name + descriptor).equals(method)) {
                        return visitor;
                    } else {
                        return null;
                    }
                }
            }, 0);
        }
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
