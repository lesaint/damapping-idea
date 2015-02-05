package fr.javatronic.damapping.intellij.plugin.integration.psiparsing;

import java.util.Arrays;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.google.common.base.Optional;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiImportList;

import static com.google.common.collect.FluentIterable.from;

/**
 * PsiImportListUtil -
 *
 * @author Sébastien Lesaint
 */
public final class PsiImportListUtil {
  private PsiImportListUtil() {
    // prevents instantiation
  }

  @Nonnull
  public static Optional<PsiImportList> extractPsiImportList(@Nullable PsiClass psiClass) {
    if (psiClass == null || psiClass.getParent() == null) {
      return Optional.absent();
    }

    return from(Arrays.asList(psiClass.getParent().getChildren()))
        .filter(PsiImportList.class)
        .first();
  }
}
