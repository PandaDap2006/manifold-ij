package manifold.ij.extensions;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.impl.source.tree.java.PsiLocalVariableImpl;
import com.intellij.psi.impl.source.tree.java.PsiMethodCallExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiUtil;
import manifold.ext.api.Jailbreak;
import manifold.ij.psi.ManLightMethodBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Unfortunately IJ doesn't provide a way to augment a type with interfaces, so we are stuck with suppressing errors
 */
public class ManHighlightInfoFilter implements HighlightInfoFilter
{
  /**
   * Override to filter errors related to type incompatibilities arising from a
   * manifold extension adding an interface to an existing classpath class (as opposed
   * to a source file).  Basically suppress "incompatible type errors" or similar
   * involving a structural interface extension.
   */
  @Override
  public boolean accept( @NotNull HighlightInfo hi, @Nullable PsiFile file )
  {
    if( hi.getDescription() == null ||
        hi.getSeverity() != HighlightSeverity.ERROR ||
        file == null )
    {
      return true;
    }

    PsiElement firstElem = file.findElementAt( hi.getStartOffset() );
    if( firstElem == null )
    {
      return true;
    }

    if( filterAmbiguousMethods( hi, firstElem ) )
    {
      return false;
    }

    PsiElement elem = firstElem.getParent();
    if( elem == null )
    {
      return true;
    }

    if( filterIllegalEscapedCharDollars( hi, firstElem, elem ) )
    {
      return false;
    }

    if( filterCannotAssignToFinalIfJailbreak( hi, firstElem ) )
    {
      return false;
    }

    if( isInvalidStaticMethodOnInterface( hi ) )
    {
      PsiElement parent = elem.getParent();
      if( !(parent instanceof PsiMethodCallExpressionImpl) )
      {
        return true;
      }
      PsiMethodCallExpressionImpl methodCall = (PsiMethodCallExpressionImpl)parent;
      PsiReferenceExpressionImpl qualifierExpression = (PsiReferenceExpressionImpl)methodCall.getMethodExpression().getQualifierExpression();
      PsiElement lhsType = qualifierExpression == null ? null : qualifierExpression.resolve();
      if( lhsType instanceof ManifoldPsiClass )
      {
        PsiMethod psiMethod = methodCall.resolveMethod();
        if( psiMethod != null )
        {
          // ignore "Static method may be invoked on containing interface class only" errors
          // where the method really is directly on the interface, albeit the delegate
          return !psiMethod.getContainingClass().isInterface();
        }
      }
      return true;
    }

    //##
    //## structural interface extensions cannot be added to the psiClass, so for now we suppress "incompatible type errors" or similar involving a structural interface extension.
    //##
    Boolean x = acceptInterfaceError( hi, firstElem, elem );
    if( x != null ) return x;

    return true;
  }

  private boolean filterCannotAssignToFinalIfJailbreak( HighlightInfo hi, PsiElement firstElem )
  {
    if( !hi.getDescription().startsWith( "Cannot assign a value to final variable" ) )
    {
      return false;
    }

    PsiType type = ((PsiReferenceExpressionImpl)firstElem.getParent()).getType();
    return type != null && type.findAnnotation( Jailbreak.class.getTypeName() ) != null;
  }

  private boolean filterAmbiguousMethods( HighlightInfo hi, PsiElement elem )
  {
    if( !hi.getDescription().startsWith( "Ambiguous method call" ) )
    {
      return false;
    }

    while( !(elem instanceof PsiMethodCallExpression) )
    {
      elem = elem.getParent();
      if( elem == null )
      {
        return false;
      }
    }

    PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)elem).getMethodExpression();
    JavaResolveResult[] javaResolveResults = methodExpression.multiResolve( false );
    for( JavaResolveResult result: javaResolveResults )
    {
      if( result instanceof MethodCandidateInfo )
      {
        PsiElement psiMethod = result.getElement();
        if( psiMethod instanceof ManLightMethodBuilder )
        {
          return true;
        }
      }
    }
    return false;
  }

  private boolean filterIllegalEscapedCharDollars( @NotNull HighlightInfo hi, PsiElement firstElem, PsiElement elem )
  {
    return firstElem instanceof PsiJavaToken &&
           ((PsiJavaToken)firstElem).getTokenType() == JavaTokenType.STRING_LITERAL &&
           hi.getDescription().contains( "Illegal escape character" ) &&
           elem.getText().contains( "\\$" );
  }

  @Nullable
  private Boolean acceptInterfaceError( @NotNull HighlightInfo hi, PsiElement firstElem, PsiElement elem )
  {
    if( elem instanceof PsiTypeCastExpression )
    {
      PsiTypeElement castType = ((PsiTypeCastExpression)elem).getCastType();
      if( isStructuralType( castType ) )
      {
//        if( TypeUtil.isStructurallyAssignable( castType.getType(), ((PsiTypeCastExpression)elem).getType(), false ) )
//        {
          // ignore incompatible cast type involving structure
          return false;
//        }
      }
    }
    else if( firstElem instanceof PsiIdentifier )
    {
      PsiTypeElement lhsType = findTypeElement( firstElem );
      if( isStructuralType( lhsType ) )
      {
        PsiType initType = findInitializerType( firstElem );
        if( initType != null )
        {
//          if( TypeUtil.isStructurallyAssignable( lhsType.getType(), initType, false ) )
//          {
            // ignore incompatible type in assignment involving structure
            return false;
//          }
        }
      }
    }
    else if( hi.getDescription().contains( "cannot be applied to" ) )
    {
      PsiMethodCallExpression methodCall = findMethodCall( firstElem );
      if( methodCall != null )
      {
        PsiMethod psiMethod = methodCall.resolveMethod();
        if( psiMethod != null )
        {
          PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
          PsiType[] argTypes = methodCall.getArgumentList().getExpressionTypes();
          for( int i = 0; i < parameters.length; i++ )
          {
            PsiParameter param = parameters[i];
            if( argTypes.length <= i )
            {
              return true;
            }
            if( !isStructuralType( param.getTypeElement() ) )
            {
              if( !param.getType().isAssignableFrom( argTypes[i] ) )
              {
                return true;
              }
            }
//            else
//            {
//              boolean nominal = false;//typeExtensionNominallyExtends( methodCall.getArgumentList().getExpressionTypes()[i], param.getTypeElement() );
//              if( !TypeUtil.isStructurallyAssignable( param.getType(), methodCall.getArgumentList().getExpressionTypes()[i], !nominal ) )
//              {
//                return true;
//              }
//            }
          }
          return true;
        }
      }
    }
    return null;
  }

  private boolean isInvalidStaticMethodOnInterface( HighlightInfo hi )
  {
    return hi.getDescription().contains( "Static method may be invoked on containing interface class only" );
  }

  private PsiType findInitializerType( PsiElement firstElem )
  {
    PsiElement csr = firstElem;
    while( csr != null && !(csr instanceof PsiLocalVariableImpl) )
    {
      csr = csr.getParent();
    }
    if( csr instanceof PsiLocalVariableImpl )
    {
      PsiExpression initializer = ((PsiLocalVariableImpl)csr).getInitializer();
      return initializer == null ? null : initializer.getType();
    }
    return null;
  }

//## todo: implementing this is not efficient to say the least, so for now we will always check for structural assignability
//  private boolean typeExtensionNominallyExtends( PsiType psiType, PsiTypeElement typeElement )
//  {
//    if( !(psiType instanceof PsiClassType) )
//    {
//      return false;
//    }
//
//    PsiClassType rawType = ((PsiClassType)psiType).rawType();
//    rawType.getSuperTypes()
//    ManModule module = ManProject.getModule( typeElement );
//    for( ITypeManifold sp : module.getTypeManifolds() )
//    {
//      if( sp.getContributorKind() == Supplemental )
//      {
//
//      }
//    }
//  }

  private int findArgPos( PsiMethodCallExpression methodCall, PsiElement firstElem )
  {
    PsiExpression[] args = methodCall.getArgumentList().getExpressions();
    for( int i = 0; i < args.length; i++ )
    {
      PsiExpression arg = args[i];
      PsiElement csr = firstElem;
      while( csr != null && csr != firstElem )
      {
        csr = csr.getParent();
      }
      if( csr == firstElem )
      {
        return i;
      }
    }
    throw new IllegalStateException();
  }

  private boolean isStructuralType( PsiTypeElement typeElem )
  {
    if( typeElem != null )
    {
      PsiClass psiClass = PsiUtil.resolveClassInType( typeElem.getType() );
      if( psiClass == null )
      {
        return false;
      }
      PsiAnnotation structuralAnno = psiClass.getModifierList() == null
                                     ? null
                                     : psiClass.getModifierList().findAnnotation( "manifold.ext.api.Structural" );
      if( structuralAnno != null )
      {
        return true;
      }
    }
    return false;
  }

  private PsiTypeElement findTypeElement( PsiElement elem )
  {
    PsiElement csr = elem;
    while( csr != null && !(csr instanceof PsiTypeElement) )
    {
      csr = csr.getParent();
    }
    return (PsiTypeElement)csr;
  }

  private PsiMethodCallExpression findMethodCall( PsiElement elem )
  {
    PsiElement csr = elem;
    while( csr != null && !(csr instanceof PsiMethodCallExpression) )
    {
      csr = csr.getParent();
    }
    return (PsiMethodCallExpression)csr;
  }
}
