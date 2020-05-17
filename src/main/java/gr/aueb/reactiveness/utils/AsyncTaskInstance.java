package gr.aueb.reactiveness.utils;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;

import java.util.Arrays;
import java.util.Optional;

/**
 * The type Async task instance.
 *
 * @author taggelis
 */
public class AsyncTaskInstance {

    private final boolean onProgressUpdateExist;
    private final boolean onPreExecuteExist;
    private final PsiClass classInstance;
    private final PsiMethod[] allMethods;
    private final PsiField[] allFields;
    private final int textOffset;

    /**
     * Instantiates a new Async task instance.
     *
     * @param asyncTaskClass the async task class
     */
    public AsyncTaskInstance(final PsiClass asyncTaskClass) {
        this.classInstance= asyncTaskClass;
        this.onProgressUpdateExist = onProgressUpdateExist(asyncTaskClass);
        this.onPreExecuteExist = onPreExecuteExist(asyncTaskClass);
        this.allMethods = Optional.ofNullable(asyncTaskClass).isPresent()?asyncTaskClass.getAllMethods(): new PsiMethod[0];
        this.allFields = Optional.ofNullable(asyncTaskClass).isPresent()?asyncTaskClass.getAllFields(): new PsiField[0];
        this.textOffset = Optional.ofNullable(asyncTaskClass).isPresent()?asyncTaskClass.getTextOffset(): 0;
    }

    /**
     * Gets class instance.
     *
     * @return the class instance
     */
    public PsiClass getClassInstance() {
        return classInstance;
    }

    /**
     * Is on progress update exist boolean.
     *
     * @return the boolean
     */
    public boolean isOnProgressUpdateExist() {
        return onProgressUpdateExist;
    }

    /**
     * Is on pre execute exist boolean.
     *
     * @return the boolean
     */
    public boolean isOnPreExecuteExist() {
        return onPreExecuteExist;
    }

    /**
     * Get all fields psi field [ ].
     *
     * @return the psi field [ ]
     */
    public PsiField[] getAllFields() {
        return allFields;
    }

    /**
     * Get all methods psi method [ ].
     *
     * @return the psi method [ ]
     */
    public PsiMethod[] getAllMethods() {
        return allMethods;
    }

    /**
     * Gets text offset.
     *
     * @return the text offset
     */
    public int getTextOffset() {
        return textOffset;
    }

    private boolean onProgressUpdateExist(final PsiClass asyncTask){
        if(asyncTask == null ){
            return false;
        }
        return Arrays.stream(asyncTask.getAllMethods())
            .anyMatch(psiMethod -> psiMethod.getName().equals("onProgressUpdate"));
    }

    private boolean onPreExecuteExist(final PsiClass asyncTask){
        if(asyncTask == null ){
            return false;
        }
        return Arrays.stream(asyncTask.getAllMethods())
            .anyMatch(psiMethod -> psiMethod.getName().equals("onProgressUpdate"));
    }
}
