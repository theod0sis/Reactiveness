package gr.aueb.reactiveness.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import gr.aueb.reactiveness.Utils.Commons;
import gr.aueb.reactiveness.Utils.ReactivenessUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * The main action that refactors asyncTask to RxJava2.
 */
public class RxJavaAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        VirtualFile[] files = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);

        if (project == null || project.isDisposed() || files == null || files.length == 0) {
            return;
        }

        //parse all the virtual files to find java classes
        for (VirtualFile file : files) {
            String fileExtension = file.getExtension();
            if (fileExtension != null && fileExtension.equals("java")) {
                PsiJavaFile psiJavaFile = (PsiJavaFile) PsiManager.getInstance(project).findFile(file);
                //check if the java class extends asynctask
                if (Commons.ASYNCTASK
                    .equals(ReactivenessUtils.getSuperClassName(Objects.requireNonNull(psiJavaFile).getClass()))) {

                    WriteCommandAction.runWriteCommandAction(project, () -> {

                    });
                }
            }
        }

    }

    @Override
    public void update(AnActionEvent e) {

    }

}