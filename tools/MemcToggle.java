import java.lang.reflect.Method;

/**
 * Toggles the TCL TV's Motion Clarity setting OFF and back to fix audio/video sync drift.
 * Uses setMotionClarity (not setMemcConfig) because it triggers the full pipeline reset
 * including DDM, which is what the TV's own menu does.
 * Runs via: CLASSPATH=/data/local/tmp/memc.dex:/system_ext/framework/com.tcl.tv.display.jar app_process / MemcToggle toggle [delayMs]
 */
public class MemcToggle {
    public static void main(String[] args) throws Exception {
        Class<?> pqClass = Class.forName("com.tcl.tv.pq.TvPqManager");
        Object pq = pqClass.getMethod("getInstance").invoke(null);
        Class<?> applyType = Class.forName("com.tcl.tv.pq.EnApplyType");
        Class<?> actType = Class.forName("com.tcl.tv.pq.EnTclActType");

        int mc = (int) pqClass.getMethod("getMotionClarity", int.class).invoke(pq, 0);
        int config = (int) pqClass.getMethod("getMemcConfig", int.class).invoke(pq, 0);
        System.out.println("motionClarity=" + mc + " memcConfig=" + config);

        if (args.length > 0 && args[0].equals("toggle")) {
            if (mc == 0) {
                System.out.println("Motion Clarity is already OFF, nothing to toggle");
                return;
            }
            long delay = args.length > 1 ? Long.parseLong(args[1]) : 3000;

            Object apply = ((Object[]) applyType.getMethod("values").invoke(null))[0]; // EN_APPLY_CURRENT
            Object act = ((Object[]) actType.getMethod("values").invoke(null))[2]; // EN_TCL_ACT_EXEC_SAVE
            Method setMC = pqClass.getMethod("setMotionClarity", int.class, applyType, int.class, actType);

            System.out.println("Setting Motion Clarity to OFF...");
            boolean r1 = (boolean) setMC.invoke(pq, 0, apply, 0, act);
            System.out.println("  result: " + r1);

            Thread.sleep(delay);

            System.out.println("Restoring Motion Clarity to " + mc + "...");
            boolean r2 = (boolean) setMC.invoke(pq, 0, apply, mc, act);
            System.out.println("  result: " + r2);

            int afterMc = (int) pqClass.getMethod("getMotionClarity", int.class).invoke(pq, 0);
            int afterConfig = (int) pqClass.getMethod("getMemcConfig", int.class).invoke(pq, 0);
            System.out.println("After: motionClarity=" + afterMc + " memcConfig=" + afterConfig);
        }
    }
}
