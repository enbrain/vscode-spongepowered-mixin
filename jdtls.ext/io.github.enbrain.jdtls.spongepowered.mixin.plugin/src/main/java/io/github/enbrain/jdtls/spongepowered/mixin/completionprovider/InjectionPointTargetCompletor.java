package io.github.enbrain.jdtls.spongepowered.mixin.completionprovider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import io.github.enbrain.jdtls.spongepowered.mixin.CompletionItem;
import io.github.enbrain.jdtls.spongepowered.mixin.Util;

/**
 * Provides completion items for {@code @At(target = "...")}.
 */
public class InjectionPointTargetCompletor implements Completor {
    @Override
    public List<CompletionItem> complete(ASTNode root, ASTNode current) throws JavaModelException {
        List<CompletionItem> result = new ArrayList<>();

        if (current instanceof StringLiteral) {
            Annotation injectionPointAnnotation = Util.getAnnotationFromMember(current.getParent(), "target");
            if (injectionPointAnnotation != null) {
                ITypeBinding injectionPointBinding = injectionPointAnnotation.getTypeName().resolveTypeBinding();
                if (injectionPointBinding != null) {
                    String injectionPointAnnotationName = injectionPointBinding.getQualifiedName();
                    if (injectionPointAnnotationName.equals(Util.INJECTION_POINT_ANNOTATION)) {
                        Expression injectionPointType = getMemberValueInAnnotation(injectionPointAnnotation, "value");
                        if (injectionPointType instanceof StringLiteral injectionPointTypeStringLiteral) {
                            String injectionPointTypeString = injectionPointTypeStringLiteral.getLiteralValue();
                            Annotation injectorAnnotation = getEnclosingInjector(root, injectionPointAnnotation);
                            if (injectorAnnotation != null) {
                                Expression targetMethodExpr = getMemberValueInAnnotation(injectorAnnotation,
                                        "method");
                                if (targetMethodExpr instanceof StringLiteral injectorMethodStringLiteral) {
                                    String targetMethod = injectorMethodStringLiteral.getLiteralValue();
                                    List<IType> targetClasses = Util.getTargetClasses(root, injectorAnnotation);
                                    switch (injectionPointTypeString) {
                                        case "INVOKE":
                                            for (String method : collectInvokedMethods(targetClasses, targetMethod)) {
                                                result.add(new CompletionItem(method, Util.METHOD_ITEM));
                                            }
                                            break;
                                        case "INVOKE_STRING":
                                            for (String method : collectStringInvokedMethods(targetClasses,
                                                    targetMethod)) {
                                                result.add(new CompletionItem(method, Util.METHOD_ITEM));
                                            }
                                            break;
                                        case "INVOKE_ASSIGN":
                                            for (String method : collectNonVoidInvokedMethods(targetClasses,
                                                    targetMethod)) {
                                                result.add(new CompletionItem(method, Util.METHOD_ITEM));
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
                                                result.add(new CompletionItem(field, Util.FIELD_ITEM));
                                            }
                                            break;
                                        case "NEW":
                                            for (String field : collectInvokedConstructors(targetClasses,
                                                    targetMethod)) {
                                                result.add(new CompletionItem(field, Util.METHOD_ITEM));
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

    private static Annotation getEnclosingInjector(ASTNode root, ASTNode node) {
        return (Annotation) Util.getEnclosingNode(root, node, n -> {
            if (n instanceof Annotation annotation) {
                ITypeBinding binding = annotation.getTypeName().resolveTypeBinding();
                if (binding != null) {
                    String name = binding.getQualifiedName();
                    if (Util.INJECTORS.contains(name)) {
                        return true;
                    }
                }
            }
            return false;
        });
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

    private static List<String> collectInvokedMethods(List<IType> targetClasses, String method)
            throws JavaModelException {
        Set<String> result = new HashSet<>();

        visitMethodInClasses(targetClasses, method, new MethodVisitor(Util.ASM_VERSION) {
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

        visitMethodInClasses(targetClasses, method, new MethodVisitor(Util.ASM_VERSION) {
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

        visitMethodInClasses(targetClasses, method, new MethodVisitor(Util.ASM_VERSION) {
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

        visitMethodInClasses(targetClasses, method, new MethodVisitor(Util.ASM_VERSION) {
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

        visitMethodInClasses(targetClasses, method, new MethodVisitor(Util.ASM_VERSION) {
            @Override
            public void visitFieldInsn(int actualOpcode, String owner, String name, String descriptor) {
                if (opcode == null || opcode.intValue() == actualOpcode) {
                    result.add("L" + owner + ";" + name + ":" + descriptor);
                }
            }
        });

        return List.copyOf(result);
    }

    private static void visitMethodInClasses(List<IType> targetClasses, String method, MethodVisitor visitor)
            throws JavaModelException {
        for (IType targetClass : targetClasses) {
            ClassReader reader = new ClassReader(targetClass.getClassFile().getBytes());
            reader.accept(new ClassVisitor(Util.ASM_VERSION) {
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
}
