package gr.aueb.reactiveness.actions;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.indexing.FileBasedIndex;
import gr.aueb.reactiveness.Utils.Commons;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * The main action that refactors asyncTask to RxJava2.
 */
public class RxJavaAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();

        if (project == null || project.isDisposed()) {
            return;
        }

        Collection<VirtualFile> virtualFiles = FileBasedIndex.getInstance()
            .getContainingFiles(FileTypeIndex.NAME, JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project));

        virtualFiles.forEach(virtualFile -> {
            String fileExtension = virtualFile.getExtension();
            if (fileExtension != null && fileExtension.equals("java")) {
                PsiJavaFile psiJavaFile = (PsiJavaFile) PsiManager.getInstance(project).findFile(virtualFile);
                PsiClass[] javaFileClasses = psiJavaFile.getClasses();

                for (PsiClass javaFileClass : javaFileClasses) {

                    PsiReferenceList extendsList = javaFileClass.getExtendsList();
                    for (PsiClassType referencedType : extendsList.getReferencedTypes()) {
                        System.out.println(psiJavaFile.getName()  + " have classes : " + javaFileClass.getName() + " /n and extends : " + referencedType.getClassName()  );
                        //check if the java class extends asynctask
                        if (Commons.ASYNCTASK.equals(referencedType.getClassName())) {

                            Collection<PsiReference> psiReferences = ReferencesSearch.search(psiJavaFile).findAll();
                            psiReferences.forEach(psiReference -> {
                                System.out.println("psiReference  content :" +psiReference.getCanonicalText());
                            });
                            WriteCommandAction.runWriteCommandAction(project, () -> {

                            });
                        }
                    }
                }

            }

        });

    }

    @Override
    public void update(AnActionEvent e) {

    }

}