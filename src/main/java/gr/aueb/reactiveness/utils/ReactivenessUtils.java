package gr.aueb.reactiveness.utils;


import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceList;

import java.util.Arrays;
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


    public static boolean findIfExtendsAsyncTask(final PsiClass psiClass){
        PsiReferenceList extendsList = psiClass.getExtendsList();
        for (PsiClassType referencedType : Objects.requireNonNull(extendsList).getReferencedTypes()) {
            if (Commons.ASYNCTASK.equals(referencedType.getClassName())) {
               return true;
            }
        }
        return false;
    }

    public static boolean findIfDoInBackgroundExist(final PsiClass javaFileClass) {
        PsiMethod[] methods = javaFileClass.getAllMethods();
        Optional<PsiMethod> backgroundMethod = Arrays.stream(methods)
            .filter(method -> Commons.DO_IN_BACKGROUND.equalsIgnoreCase(method.getName())).findFirst();
        return backgroundMethod.isPresent();
    }

}
