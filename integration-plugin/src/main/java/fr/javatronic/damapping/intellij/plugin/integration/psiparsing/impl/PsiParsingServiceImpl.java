/**
 * Copyright (C) 2013 Sébastien Lesaint (http://www.javatronic.fr/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.javatronic.damapping.intellij.plugin.integration.psiparsing.impl;

import fr.javatronic.damapping.intellij.plugin.integration.psiparsing.PsiContext;
import fr.javatronic.damapping.intellij.plugin.integration.psiparsing.PsiImportListUtil;
import fr.javatronic.damapping.intellij.plugin.integration.psiparsing.PsiParsingService;
import fr.javatronic.damapping.processor.model.DAAnnotation;
import fr.javatronic.damapping.processor.model.DAEnumValue;
import fr.javatronic.damapping.processor.model.DAInterface;
import fr.javatronic.damapping.processor.model.DAMethod;
import fr.javatronic.damapping.processor.model.DAModifier;
import fr.javatronic.damapping.processor.model.DAName;
import fr.javatronic.damapping.processor.model.DAParameter;
import fr.javatronic.damapping.processor.model.DASourceClass;
import fr.javatronic.damapping.processor.model.DAType;
import fr.javatronic.damapping.processor.model.factory.DANameFactory;
import fr.javatronic.damapping.processor.model.function.ToGuavaFunctionOrMapperMethod;
import fr.javatronic.damapping.processor.model.impl.DAAnnotationImpl;
import fr.javatronic.damapping.processor.model.impl.DAEnumValueImpl;
import fr.javatronic.damapping.processor.model.impl.DAInterfaceImpl;
import fr.javatronic.damapping.processor.model.impl.DAMethodImpl;
import fr.javatronic.damapping.processor.model.impl.DAParameterImpl;
import fr.javatronic.damapping.processor.model.impl.DASourceClassImpl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiReferenceList;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.FluentIterable.from;

/**
 * PsiParsingServiceImpl -
 *
 * @author Sébastien Lesaint
 */
public class PsiParsingServiceImpl implements PsiParsingService {
  private static final Logger LOGGER = Logger.getInstance(PsiParsingServiceImpl.class.getName());

  private final DANameExtractor daNameExtractor;
  private final DATypeExtractor daTypeExtractor;
  private final DAModifierExtractor daModifierExtractor;

  public PsiParsingServiceImpl(DANameExtractor daNameExtractor, DATypeExtractor daTypeExtractor,
                               DAModifierExtractor daModifierExtractor) {
    this.daNameExtractor = daNameExtractor;
    this.daTypeExtractor = daTypeExtractor;
    this.daModifierExtractor = daModifierExtractor;
  }

  public PsiParsingServiceImpl() {
    this.daNameExtractor = new DANameExtractorImpl();
    this.daTypeExtractor = new DATypeExtractorImpl(daNameExtractor);
    this.daModifierExtractor = new DAModifierExtractorImpl();
  }

  @Override
  public DASourceClass parse(PsiClass psiClass) {
    checkArgument(!psiClass.isAnnotationType(), "Annotation annoted with @Mapper is not supported");
    checkArgument(!psiClass.isInterface(), "Interface annoted with @Mapper is not supported");

    try {
      DASourceClassImpl.Builder builder = daSourceBuilder(psiClass, daTypeExtractor.forClassOrEnum(psiClass));

      PsiImportList psiImportList = PsiImportListUtil.extractPsiImportList(psiClass).orNull();
      DAName packageName = daNameExtractor.extractPackageName(psiClass);
      PsiContext psiContext = new PsiContext(psiImportList, packageName);
      List<DAInterface> daInterfaces = extractInterfaces(psiClass, psiContext);
      DASourceClass res = builder
          .withAnnotations(extractAnnotations(psiClass.getModifierList(), psiContext))
          .withModifiers(daModifierExtractor.extractModifiers(psiClass))
          .withInterfaces(daInterfaces)
          .withMethods(extractMethods(psiClass, psiContext, daInterfaces))
          .build();
      return res;
    }
    catch (Throwable r) {
      LOGGER.error("An exception occured while parsing Psi tree", r);
      throw new RuntimeException(r);
    }
  }

  private static DASourceClassImpl.Builder daSourceBuilder(PsiClass psiClass, DAType daType) {
    if (psiClass.isEnum()) {
      return DASourceClassImpl.enumBuilder(daType, extractEnumValues(psiClass));
    }
    else {
      return DASourceClassImpl.classbuilder(daType);
    }
  }

  private static List<DAEnumValue> extractEnumValues(PsiClass psiClass) {
    return from(Arrays.asList(psiClass.getChildren()))
        .filter(PsiEnumConstant.class)
        .transform(PsiEnumConstantDAEnumValue.INSTANCE)
        .filter(Predicates.notNull())
        .toList();
  }

  private List<DAAnnotation> extractAnnotations(@Nullable PsiModifierList modifierList,
                                                @Nullable final PsiContext psiContext) {
    if (modifierList == null) {
      return null;
    }

    return from(Arrays.asList(modifierList.getChildren()))
        .filter(PsiAnnotation.class)
        .transform(new Function<PsiAnnotation, DAAnnotation>() {
          @Nullable
          @Override
          public DAAnnotation apply(@Nullable PsiAnnotation psiAnnotation) {
            DAAnnotation res = new DAAnnotationImpl(
                daTypeExtractor.forAnnotation(psiAnnotation, psiContext)
            );
            return res;
          }
        }
        )
        .toList();
  }

  private List<DAInterface> extractInterfaces(final PsiClass psiClass, @Nonnull final PsiContext psiContext) {
    PsiReferenceList implementsList = psiClass.getImplementsList();
    if (implementsList != null /* null for anonymous classes */
        && implementsList.getRole() == PsiReferenceList.Role.IMPLEMENTS_LIST) {
      return from(Arrays.asList(implementsList.getReferenceElements()))
          .transform(new Function<PsiJavaCodeReferenceElement, DAInterface>() {
            @Override
            public DAInterface apply(@Nullable PsiJavaCodeReferenceElement referenceElement) {
              return new DAInterfaceImpl(daTypeExtractor.forInterface(referenceElement, psiContext));
            }
          }
          )
          .toList();
    }
    return Collections.emptyList();
  }

  private List<DAMethod> extractMethods(PsiClass psiClass, final PsiContext psiContext, final List<DAInterface> daInterfaces) {
    List<DAMethod> daMethods = from(Arrays.asList(psiClass.getChildren()))
        .filter(PsiMethod.class)
        // transform PsiMethod to DAMethod
        .transform(new PsiMethodToDAMethod(psiContext))
        .transform(new DAMethodToGuavaFunctionOrMapperDAMethod(daInterfaces))
        .toList();

    // if No default constructor has been defined explicutly, we add one
    if (!Iterables.any(daMethods, DAMethodConstructor.INSTANCE)) {
      return ImmutableList.copyOf(
          Iterables.concat(Collections.singletonList(instanceDefaultConstructor(psiClass)), daMethods)
      );
    }
    return daMethods;
  }

  private DAMethod instanceDefaultConstructor(PsiClass psiClass) {
    return DAMethodImpl.constructorBuilder()
                   .withName(DANameFactory.from(psiClass.getName()))
                   .withModifiers(Collections.singleton(DAModifier.PUBLIC))
                   .withReturnType(daTypeExtractor.forClassOrEnum(psiClass))
                   .build();
  }

  private List<DAParameter> extractParameters(PsiMethod psiMethod, final PsiContext psiContext) {
    Optional<PsiParameterList> optional = from(Arrays.asList(psiMethod.getChildren())).filter(PsiParameterList.class)
        .first();
    if (!optional.isPresent()) {
      return Collections.emptyList();
    }

    return from(Arrays.asList(optional.get().getParameters()))
        .transform(new Function<PsiParameter, DAParameter>() {
          @Nullable
          @Override
          public DAParameter apply(@Nullable PsiParameter psiParameter) {
            return DAParameterImpl
                .builder(
                    DANameFactory.from(psiParameter.getName()), daTypeExtractor.forParameter(psiParameter, psiContext)
                ).withModifiers(daModifierExtractor.extractModifiers(psiParameter))
                .withAnnotations(extractAnnotations(psiParameter.getModifierList(), psiContext))
                .build();
          }
        }
        ).toList();
  }

  private static enum PsiEnumConstantDAEnumValue implements Function<PsiEnumConstant, DAEnumValue> {
    INSTANCE;

    @Nullable
    @Override
    public DAEnumValue apply(@Nullable PsiEnumConstant psiEnumConstant) {
      if (psiEnumConstant == null) {
        return null;
      }
      return new DAEnumValueImpl(psiEnumConstant.getName());
    }
  }

  private static enum DAMethodConstructor implements Predicate<DAMethod> {
    INSTANCE;

    @Override
    public boolean apply(@Nullable DAMethod daMethod) {
      return daMethod != null && daMethod.isConstructor();
    }
  }

  /**
   * This function tranforms a PsiMethod object into a DAMethod object (either a constructor or a method).
   */
  private class PsiMethodToDAMethod implements Function<PsiMethod, DAMethod> {
    private final PsiContext psiContext;

    public PsiMethodToDAMethod(PsiContext psiContext) {
      this.psiContext = psiContext;
    }

    @Nullable
    @Override
    public DAMethod apply(@Nullable PsiMethod psiMethod) {
      if (psiMethod == null) {
        return null;
      }
      return daMethodBuilder(psiMethod)
          .withName(DANameFactory.from(psiMethod.getName()))
          .withAnnotations(extractAnnotations(psiMethod.getModifierList(), psiContext))
          .withModifiers(daModifierExtractor.extractModifiers(psiMethod))
          .withParameters(extractParameters(psiMethod, psiContext))
          .withReturnType(daTypeExtractor.forReturnType(psiMethod, psiContext))
          .build();
    }

    private DAMethodImpl.Builder daMethodBuilder(PsiMethod psiMethod) {
      if (psiMethod.isConstructor()) {
        return DAMethodImpl.constructorBuilder();
      }
      return DAMethodImpl.methodBuilder();
    }
  }

  /**
   * This function effectively sets the guava function flag or the mapper flag on DAMethod objects by creating a new
   * DAMethod object from them which have the right flag set.
   */
  private static class DAMethodToGuavaFunctionOrMapperDAMethod
      extends ToGuavaFunctionOrMapperMethod<DAMethod>
      implements Function<DAMethod, DAMethod> {

    public DAMethodToGuavaFunctionOrMapperDAMethod(List<DAInterface> daInterfaces) {
      super(daInterfaces);
    }

    @Nonnull
    @Override
    protected DAMethod toMapperMethod(@Nonnull DAMethod daMethod) {
      return DAMethodImpl.makeMapperMethod(daMethod);
    }

    @Nonnull
    @Override
    protected DAMethod toGuavaFunction(@Nonnull DAMethod daMethod) {
      return DAMethodImpl.makeGuavaFunctionApplyMethod(daMethod);
    }
  }
}
