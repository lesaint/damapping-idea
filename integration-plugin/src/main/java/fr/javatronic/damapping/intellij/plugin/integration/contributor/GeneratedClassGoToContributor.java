package fr.javatronic.damapping.intellij.plugin.integration.contributor;

import com.intellij.navigation.GotoClassContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * GeneratedClassGoToContributor -
 *
 * @author Sébastien Lesaint
 */
public class GeneratedClassGoToContributor implements GotoClassContributor {
  @NotNull
  @Override
  public String[] getNames(Project project, boolean includeNonProjectItems) {
    return new String[] { "Dummy_1Mapper", "Dummy_1MapperImpl" };
  }

  @NotNull
  @Override
  public NavigationItem[] getItemsByName(String name, String pattern, Project project, boolean includeNonProjectItems) {
    return new NavigationItem[0];
  }

  @Nullable
  @Override
  public String getQualifiedName(NavigationItem item) {
    return null;
  }

  @Nullable
  @Override
  public String getQualifiedNameSeparator() {
    return null;
  }
}
