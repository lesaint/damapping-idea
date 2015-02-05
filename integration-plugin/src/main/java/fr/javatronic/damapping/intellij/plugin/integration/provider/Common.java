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
package fr.javatronic.damapping.intellij.plugin.integration.provider;

import fr.javatronic.damapping.annotation.Mapper;
import fr.javatronic.damapping.intellij.plugin.integration.psiparsing.PsiImportListUtil;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiImportList;

import static com.google.common.collect.FluentIterable.from;
import static java.util.Arrays.asList;

/**
 * Common -
 *
 * @author Sébastien Lesaint
 */
public class Common {
  private static final String MAPPER_ANNOTATION_TEXT = "@" + Mapper.class.getSimpleName();
  private static final String MAPPER_QUALIFIED_ANNOTATION_TEXT = "@" + Mapper.class.getName();

  public static boolean hasMapperAnnotation(PsiClass psiClass) {
    if (psiClass.getModifierList() == null || psiClass.getModifierList().getAnnotations() == null) {
      return false;
    }

    // look for annotation @Mapper on class
    boolean hasMapperAnnotation = !from(asList(psiClass.getModifierList().getAnnotations()))
        .filter(new MapperPsiAnnotation(psiClass))
        .isEmpty();
    return hasMapperAnnotation;
  }

  private static class MapperPsiAnnotation implements Predicate<PsiAnnotation> {
    private final boolean hasMapperAnnotationImport;

    public MapperPsiAnnotation(PsiClass psiClass) {
      Optional<PsiImportList> importList = PsiImportListUtil.extractPsiImportList(psiClass);
      this.hasMapperAnnotationImport = importList.isPresent() && hasMapperAnnotation(importList.get());
    }

    private static boolean hasMapperAnnotation(PsiImportList psiImportList) {
      return psiImportList.findSingleClassImportStatement(Mapper.class.getName()) != null
          || psiImportList.findOnDemandImportStatement(Mapper.class.getPackage().getName()) != null;
    }

    @Override
    public boolean apply(@javax.annotation.Nullable PsiAnnotation psiAnnotation) {
      if (psiAnnotation == null) {
        return false;
      }
      return (MAPPER_ANNOTATION_TEXT.equals(psiAnnotation.getText()) && hasMapperAnnotationImport)
          || MAPPER_QUALIFIED_ANNOTATION_TEXT.equals(psiAnnotation.getText());
    }
  }
}
