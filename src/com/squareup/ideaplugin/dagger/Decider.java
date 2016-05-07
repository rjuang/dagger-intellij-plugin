package com.squareup.ideaplugin.dagger;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsageTarget;

import java.util.List;
import java.util.Set;

import static com.squareup.ideaplugin.dagger.DaggerConstants.CLASS_INJECT;
import static com.squareup.ideaplugin.dagger.DaggerConstants.CLASS_PROVIDES;

public interface Decider {

  boolean shouldShow(UsageTarget target, Usage usage);

  /** Construct with a PsiMethod from a Provider to find where this is injected. */
  class ProvidesMethodDecider implements Decider {
    private final PsiClass returnType;
    private final Set<String> qualifierAnnotations;
    private final List<PsiType> typeParameters;

    public ProvidesMethodDecider(PsiMethod psiMethod) {
      this.returnType = PsiConsultantImpl.getReturnClassFromMethod(psiMethod);
      this.qualifierAnnotations = PsiConsultantImpl.getQualifierAnnotations(psiMethod);
      this.typeParameters = PsiConsultantImpl.getTypeParameters(psiMethod);
    }

    @Override public boolean shouldShow(UsageTarget target, Usage usage) {
      PsiElement element = ((UsageInfo2UsageAdapter) usage).getElement();

      PsiField field = PsiConsultantImpl.findField(element);
      if (field != null //
          && PsiConsultantImpl.hasAnnotation(field, CLASS_INJECT) //
          && PsiConsultantImpl.hasQuailifierAnnotations(field, qualifierAnnotations)
          && PsiConsultantImpl.hasTypeParameters(field, typeParameters)) {
        return true;
      }

      PsiMethod method = PsiConsultantImpl.findMethod(element);
      if (method != null && PsiConsultantImpl.hasAnnotation(method, CLASS_INJECT)) {
        PsiParameter[] parameters = method.getParameterList().getParameters();
        for (PsiParameter parameter : parameters) {
          PsiClass parameterClass = PsiConsultantImpl.checkForLazyOrProvider(parameter);
          if (parameterClass.equals(returnType) && PsiConsultantImpl.hasQuailifierAnnotations(
              parameter, qualifierAnnotations)
              && PsiConsultantImpl.hasTypeParameters(parameter, typeParameters)) {
            return true;
          }
        }
      }

      return false;
    }
  }

  /**
   * Construct with a PsiParameter from an @Inject constructor and then use this to ensure the
   * usage fits.
   */
  class ParameterInjectDecider extends IsAProviderDecider {
    public ParameterInjectDecider(PsiParameter psiParameter) {
      super(PsiConsultantImpl.getQualifierAnnotations(psiParameter),
          PsiConsultantImpl.getTypeParameters(psiParameter));
    }
  }

  /**
   * Construct with a PsiField annotated w/ @Inject and then use this to ensure the
   * usage fits.
   */
  class FieldInjectDecider extends IsAProviderDecider {
    public FieldInjectDecider(PsiField psiField) {
      super(PsiConsultantImpl.getQualifierAnnotations(psiField),
          PsiConsultantImpl.getTypeParameters(psiField));
    }
  }

  class IsAProviderDecider implements Decider {
    private final Set<String> qualifierAnnotations;
    private final List<PsiType> typeParameters;

    IsAProviderDecider(Set<String> qualifierAnnotations, List<PsiType> typeParameters) {
      this.qualifierAnnotations = qualifierAnnotations;
      this.typeParameters = typeParameters;
    }

    @Override public boolean shouldShow(UsageTarget target, Usage usage) {
      PsiElement element = ((UsageInfo2UsageAdapter) usage).getElement();

      // Is it a constructor annotated w/ @Inject?
      // I don't even know how to get to the constructor!

      // Is it a @Provides method?
      PsiMethod psimethod = PsiConsultantImpl.findMethod(element);
      return psimethod != null
          // Ensure it has an @Provides.
          && PsiConsultantImpl.hasAnnotation(psimethod, CLASS_PROVIDES)

          // Check for Qualifier annotations.
          && PsiConsultantImpl.hasQuailifierAnnotations(psimethod, qualifierAnnotations)

          // Right return type.
          && PsiConsultantImpl.getReturnClassFromMethod(psimethod)
          .getName()
          .equals(target.getName())

          // Right type parameters.
          && PsiConsultantImpl.hasTypeParameters(psimethod, typeParameters);
    }
  }
}
