package gr.aueb.reactiveness.utils;

import java.util.Arrays;
import java.util.List;

/**
 * @author taggelis
 */
public final class Commons {

    private Commons() {
    }

    public static final String ASYNCTASK = "AsyncTask";
    public static final String DO_IN_BACKGROUND = "doInBackground";
    public static final List<String> ASYNC_TASK_METHODS = Arrays
        .asList("onProgressUpdate", "onPreExecute", "onPostExecute");
    public static final String ACTIVITY_CLASS = "Activity";
    public static final String PROGRESS_SUBJECT = "progressSubject";
}
