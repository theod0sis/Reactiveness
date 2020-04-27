package gr.aueb.reactiveness.actions;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import gr.aueb.reactiveness.refactor.AsyncTaskRefactor;
import gr.aueb.reactiveness.utils.ReactivenessUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The main action that refactors asyncTask to RxJava2.
 */
public class RxJavaAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        AsyncTaskRefactor refactor = new AsyncTaskRefactor();
        if (project == null || project.isDisposed()) {
            return;
        }
        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);

        // retrieve all virtualFiles from project
        Collection<VirtualFile> virtualFiles = FileBasedIndex.getInstance()
            .getContainingFiles(FileTypeIndex.NAME, JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project));
        List<PsiClass> validClasses = new ArrayList<>();
        Map<PsiClass,PsiClass> parentInnerClass = new HashMap();
        virtualFiles.forEach(virtualFile -> {
            //check if the file has .java extension
            if (JavaFileType.DEFAULT_EXTENSION.equalsIgnoreCase(virtualFile.getFileType().getName())) {
                PsiJavaFile psiJavaFile = (PsiJavaFile) PsiManager.getInstance(project).findFile(virtualFile);
                // every Java file has to have one main Class
                if (Objects.requireNonNull(psiJavaFile).getClasses().length == 0) {
                    return;
                }
                PsiClass javaFileClass = Objects.requireNonNull(psiJavaFile).getClasses()[0];
                if (ReactivenessUtils.findIfExtendsAsyncTask(javaFileClass) && ReactivenessUtils
                    .findIfDoInBackgroundExist(javaFileClass)) {
                    validClasses.add(javaFileClass);

                }
                //search also for inner classes
                for (PsiClass javaInnerClass : javaFileClass.getInnerClasses()) {
                    if (ReactivenessUtils.findIfExtendsAsyncTask(javaInnerClass) && ReactivenessUtils
                        .findIfDoInBackgroundExist(javaInnerClass)) {
                        parentInnerClass.put(javaFileClass,javaInnerClass);
                    }
                }
            }
        });
        refactor.refactorInnerAsyncTask(elementFactory,parentInnerClass);
    }

    @Override
    public void update(AnActionEvent e) {

    }

}