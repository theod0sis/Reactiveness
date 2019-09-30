package gr.aueb.reactiveness.utils;


import javax.annotation.Nullable;

/**
 * The type Reactiveness utils.
 *
 * @author taggelis
 */
public final class ReactivenessUtils {

    private ReactivenessUtils() {
    }


    /**
     * Gets super class name.
     *
     * @param javaClass the java class
     * @return the super class name
     */
    public static String getSuperClassName(@Nullable Class javaClass) {
        return javaClass != null ? javaClass.getSuperclass().getName() : null;
    }

}
