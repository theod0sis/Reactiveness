package gr.aueb.reactiveness.utils;


import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReferenceList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The type Reactiveness utils.
 *
 * @author taggelis
 */
public final class ReactivenessUtils {

    private ReactivenessUtils() {
    }


    /**
     * Find if extends async task boolean.
     *
     * @param psiClass the psi class
     * @return the boolean
     */
    public static boolean findIfExtendsAsyncTask(final PsiClass psiClass) {
        PsiReferenceList extendsList = psiClass.getExtendsList();
        for (PsiClassType referencedType : Objects.requireNonNull(extendsList).getReferencedTypes()) {
            if (Commons.ASYNCTASK.equals(referencedType.getClassName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find if do in background exist boolean.
     *
     * @param javaFileClass the java file class
     * @return the boolean
     */
    public static boolean findIfDoInBackgroundExist(final PsiClass javaFileClass) {
        PsiMethod[] methods = javaFileClass.getAllMethods();
        Optional<PsiMethod> backgroundMethod = Arrays.stream(methods)
            .filter(method -> Commons.DO_IN_BACKGROUND.equalsIgnoreCase(method.getName())).findFirst();
        return backgroundMethod.isPresent();
    }

    /**
     * Find if extends activity boolean.
     *
     * @param javaFileClass the java file class
     * @return the boolean
     */
    public static boolean findIfExtendsActivity(final PsiClass javaFileClass) {
        if (javaFileClass.getExtendsList() == null || javaFileClass.getExtendsList().getReferencedTypes().length == 0) {
            return false;
        }
        return javaFileClass.getExtendsList().getReferencedTypes()[0].getClassName().contains(Commons.ACTIVITY_CLASS);
    }

    /**
     * Add import.
     *
     * @param elementFactory     the element factory
     * @param fullyQualifiedName the fully qualified name
     * @param psiClass           the psi class
     */
    public static void addImport(PsiElementFactory elementFactory, String fullyQualifiedName, PsiClass psiClass) {
        final PsiFile file = psiClass.getContainingFile();
        if (!(file instanceof PsiJavaFile)) {
            return;
        }
        final PsiJavaFile javaFile = (PsiJavaFile) file;
        final PsiImportList importList = javaFile.getImportList();
        if (importList == null) {
            return;
        }
        // Check if already imported
        for (PsiImportStatementBase is : importList.getAllImportStatements()) {
            String impQualifiedName = Objects.requireNonNull(is.getImportReference()).getQualifiedName();
            if (fullyQualifiedName.equals(impQualifiedName)) {
                return; // Already imported so nothing needed
            }
        }
        // Not imported yet so add it
        importList.add(elementFactory.createImportStatementOnDemand(fullyQualifiedName));
    }

    /**
     * Find if there is any anonymous asyncTask inside the class.
     *
     * @param javaClass the java class
     * @return the boolean
     */
    public static boolean findAnonymousAsyncTaskExist(final PsiClass javaClass) {
        final boolean[] anonymousExists = {false};
        for (PsiMethod method : javaClass.getMethods()) {
            method.accept(new JavaRecursiveElementWalkingVisitor() {
                @Override
                public void visitNewExpression(PsiNewExpression newExpression) {
                    super.visitNewExpression(newExpression);
                    if (!anonymousExists[0] && newExpression.getText().contains("new AsyncTask")) {
                        anonymousExists[0] = true;
                    }
                }
            });
            if (anonymousExists[0]) {
                return true;
            }
        }
        return anonymousExists[0];
    }

    /**
     * Find all new AsyncTask expressions and return a list of them.
     *
     * @param javaClass the java class
     * @return the boolean
     */
    public static List<PsiNewExpression> findAnonymousAsyncTaskExpression(final PsiClass javaClass) {
        List<PsiNewExpression> psiNewExpressionList = new ArrayList<>();
        for (PsiMethod method : javaClass.getMethods()) {
            method.accept(new JavaRecursiveElementWalkingVisitor() {
                @Override
                public void visitNewExpression(PsiNewExpression newExpression) {
                    super.visitNewExpression(newExpression);
                    if (newExpression.getText().startsWith("new AsyncTask")) {
                        psiNewExpressionList.add(newExpression);
                    }
                }
            });
        }
        return psiNewExpressionList;
    }

    /**
     * Search if composite disposable exists boolean.
     *
     * @param javaClass the java class
     * @return the boolean
     */
    public static boolean searchIfCompositeDisposableExists(final PsiClass javaClass) {
        PsiField[] psiFilds = javaClass.getAllFields();
        for (PsiField field : psiFilds) {
            if("CompositeDisposable".equals(field.getNameIdentifier().getText())){
                return true;
            }
        }
        return false;
    }
}
