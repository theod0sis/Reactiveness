package gr.aueb.reactiveness.utils;


import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiReferenceList;

import java.util.Objects;

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

}
