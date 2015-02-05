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
import fr.javatronic.damapping.intellij.plugin.integration.psiparsing.PsiTypeElementUtil;
import fr.javatronic.damapping.processor.model.DAName;
import fr.javatronic.damapping.processor.model.DAType;
import fr.javatronic.damapping.processor.model.DATypeKind;
import fr.javatronic.damapping.processor.model.factory.DANameFactory;
import fr.javatronic.damapping.processor.model.factory.DATypeFactory;
import fr.javatronic.damapping.processor.model.impl.DATypeImpl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.google.common.base.Function;
import com.google.common.base.Optional;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiTypeElement;

import static com.google.common.collect.FluentIterable.from;
import static fr.javatronic.damapping.intellij.plugin.integration.psiparsing.PsiTypeElementUtil.isArray;

/**
 * DATypeExtractorImpl -
 *
 * @author Sébastien Lesaint
 */
public class DATypeExtractorImpl implements DATypeExtractor {
  private static final Logger LOGGER = Logger.getInstance(DATypeExtractorImpl.class.getName());

  private final DANameExtractor daNameExtractor;

  public DATypeExtractorImpl(DANameExtractor daNameExtractor) {
    this.daNameExtractor = daNameExtractor;
  }

  @Override
  @Nonnull
  public DAType forClassOrEnum(PsiClass psiClass) {
    return DATypeImpl.typeBuilder(DATypeKind.DECLARED, DANameFactory.from(psiClass.getName()))
                 .withQualifiedName(daNameExtractor.qualifiedName(psiClass))
                 .withTypeArgs(extractTypeArgs(psiClass))
                 .withSuperBound(extractSuperBound(psiClass))
                 .withExtendsBound(extractExtendsBound(psiClass))
                 .build();
  }

  /**
   * Extracts the super bounds of the class annoted/enum with @Mapper.
   * No implementation since this information is not relevant to DAMapping class generation yet.
   */
  private DAType extractSuperBound(PsiClass psiClass) {
    // not used yet in DAMapping
    return null;
  }

  /**
   * Extracts the extends bounds of the class annoted/enum with @Mapper.
   * No implementation since this information is not relevant to DAMapping class generation yet.
   */
  private DAType extractExtendsBound(PsiClass psiClass) {
    // not used yet in DAMapping
    return null;
  }

  /**
   * Extracts type arguments (ie. generics) of the class annoted/enum with @Mapper.
   * No implementation since this information is not relevant to DAMapping class generation yet.
   */
  private List<DAType> extractTypeArgs(PsiClass psiClass) {
    // not used yet in DAMapping
    return null;
  }

  private DAType extractDAType(@Nonnull PsiTypeElement typeElement, PsiContext psiContext) {
    if (PsiTypeElementUtil.isVoid(typeElement)) {
      return DATypeFactory.voidDaType();
    }
    PsiTypeElement realTypeElement;
    DATypeImpl.Builder builder;
    if (isArray(typeElement)) {
      realTypeElement = from(Arrays.asList(typeElement.getChildren()))
          .filter(PsiTypeElement.class)
          .first()
          .get();
      builder = DATypeImpl.arrayBuilder(extractDATypeKind(realTypeElement), daNameExtractor.simpleName(realTypeElement));
    }
    else {
      realTypeElement = typeElement;
      builder = DATypeImpl.typeBuilder(extractDATypeKind(typeElement), daNameExtractor.simpleName(realTypeElement));
    }

    return builder
        .withQualifiedName(daNameExtractor.qualifiedName(realTypeElement, psiContext))
        .withTypeArgs(extractTypeArgs(realTypeElement, psiContext))
        .withExtendsBound(extractExtendsBound(realTypeElement, psiContext))
        .build();
  }

  private DAType extractExtendsBound(PsiTypeElement typeElement, PsiContext psiContext) {
    if (!PsiTypeElementUtil.isExtendsWildcard(typeElement)) {
      return null;
    }

    Optional<PsiTypeElement> extendsBoundTypeElement = from(Arrays.asList(typeElement.getChildren()))
        .filter(PsiTypeElement.class)
        .first();
    if (extendsBoundTypeElement.isPresent()) {
      return extractDAType(extendsBoundTypeElement.get(), psiContext);
    }
    LOGGER.error("Can not find PsiTypeElement as the extends bound in the children array. This is very unlikely when extends keyword exists...");
    return null;
  }

  private List<DAType> extractTypeArgs(PsiTypeElement typeElement, final PsiContext psiContext) {
    if (PsiTypeElementUtil.isWildcard(typeElement) || PsiTypeElementUtil.isVoid(typeElement)) {
      return Collections.emptyList();
    }

    PsiJavaCodeReferenceElement referenceElement = typeElement.getInnermostComponentReferenceElement();
    if (referenceElement == null) {
      return Collections.emptyList();
    }

    if (referenceElement.getParameterList() == null) {
      return Collections.emptyList();
    }

    List<DAType> daTypes = from(Arrays.asList(referenceElement.getParameterList().getTypeParameterElements()))
        .transform(new Function<PsiTypeElement, DAType>() {
          @Nullable
          @Override
          public DAType apply(@Nullable PsiTypeElement psiTypeElement) {
            return extractDAType(psiTypeElement, psiContext);
          }
        }
        ).toList();
    return daTypes;
  }

  @Override
  @Nonnull
  public DAType forInterface(PsiJavaCodeReferenceElement referenceElement, PsiContext psiContext) {
    return DATypeImpl.typeBuilder(
        extractDATypeKind(referenceElement), daNameExtractor.simpleName(referenceElement)
    )
                 .withQualifiedName(daNameExtractor.interfaceQualifiedName(referenceElement, psiContext))
                 .withTypeArgs(extractTypeArgs(referenceElement, psiContext))
                 .build();
  }

  private List<DAType> extractTypeArgs(PsiJavaCodeReferenceElement referenceElement, final @Nullable PsiContext psiContext) {
    List<DAType> daTypes = from(Arrays.asList(referenceElement.getParameterList().getTypeParameterElements()))
        .transform(new Function<PsiTypeElement, DAType>() {

          @Nullable
          @Override
          public DAType apply(@Nullable PsiTypeElement psiTypeElement) {
            return extractDAType(psiTypeElement, psiContext);
          }
        }
        ).toList();
    return daTypes;
  }

  private DATypeKind extractDATypeKind(PsiTypeElement psiTypeElement) {
    if (PsiTypeElementUtil.isPrimitive(psiTypeElement)) {
      return DATypeKind.valueOf(psiTypeElement.getText().toUpperCase(Locale.ENGLISH));
    }
    if (PsiTypeElementUtil.isWildcard(psiTypeElement)) {
      return DATypeKind.WILDCARD;
    }
    return DATypeKind.DECLARED;
  }

  private DATypeKind extractDATypeKind(PsiJavaCodeReferenceElement referenceElement) {
    return DATypeKind.DECLARED;
  }

  @Override
  @Nonnull
  public DAType forParameter(PsiParameter psiParameter, PsiContext psiContext) {
    Optional<PsiTypeElement> typeElement = from(Arrays.asList(psiParameter.getChildren())).filter(PsiTypeElement.class)
        .first();
    if (!typeElement.isPresent()) {
      throw new IllegalArgumentException("PsiParameter has no PsiTypeElement");
    }
    return extractDAType(typeElement.get(), psiContext);
  }

  @Override
  @Nullable
  public DAType forReturnType(PsiMethod psiMethod, PsiContext psiContext) {
    if (psiMethod.isConstructor()) {
      return null;
    }
    return extractDAType(psiMethod.getReturnTypeElement(), psiContext);
  }

  @Override
  @Nonnull
  public DAType forAnnotation(PsiAnnotation psiAnnotation, PsiContext psiContext) {
    DAName qualifiedName = daNameExtractor.qualifiedName(psiAnnotation, psiContext);
    DAName simpleName = DANameFactory.simpleFromQualified(qualifiedName);
    if (qualifiedName.equals(simpleName)) {
      qualifiedName = daNameExtractor.qualifiedName(simpleName.getName(), psiContext);
    }
    return DATypeImpl.typeBuilder(DATypeKind.DECLARED, simpleName)
                 .withQualifiedName(qualifiedName)
                 .build();
  }
}
