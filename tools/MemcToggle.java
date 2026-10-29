import java.lang.reflect.Method;

/**
 * Toggles the TCL TV's MEMC (Motion) setting OFF and back to fix audio/video sync drift.
 * Runs via: CLASSPATH=/data/local/tmp/memc.dex:/system_ext/framework/com.tcl.tv.display.jar app_process / MemcToggle toggle [delayMs]
 */
public class MemcToggle {
    public static void main(String[] args) throws Exception {
        Class<?> pqClass = Class.forName("com.tcl.tv.pq.TvPqManager");
        Object pq = pqClass.getMethod("getInstance").invoke(null);
        Class<?> applyType = Class.forName("com.tcl.tv.pq.EnApplyType");
        Class<?> memcType = Class.forName("com.tcl.tv.pq.EnPqMemcType");
        Class<?> actType = Class.forName("com.tcl.tv.pq.EnTclActType");

        int config = (int) pqClass.getMethod("getMemcConfig", int.class).invoke(pq, 0);
        int blur = (int) pqClass.getMethod("getMemcBlur", int.class).invoke(pq, 0);
        int judder = (int) pqClass.getMethod("getMemcJudder", int.class).invoke(pq, 0);
        System.out.println("memcConfig=" + config + " blur=" + blur + " judder=" + judder);

        Object[] memcValues = (Object[]) memcType.getMethod("values").invoke(null);
        Object[] actValues = (Object[]) actType.getMethod("values").invoke(null);

        if (args.length > 0 && args[0].equals("toggle")) {
            if (config == 0) {
                System.out.println("MEMC is already OFF, nothing to toggle");
                return;
            }
            long delay = args.length > 1 ? Long.parseLong(args[1]) : 3000;

            Method setConfig = pqClass.getMethod("setMemcConfig", int.class, applyType, memcType, actType);
            Object memcOff = memcValues[0]; // EN_PQ_MEMC_OFF
            Object origMemc = memcValues[config];
            Object apply = ((Object[]) applyType.getMethod("values").invoke(null))[0]; // EN_APPLY_CURRENT
            Object act = actValues[2]; // EN_TCL_ACT_EXEC_SAVE

            System.out.println("Setting MEMC to OFF...");
            boolean r1 = (boolean) setConfig.invoke(pq, 0, apply, memcOff, act);
            System.out.println("  result: " + r1);

            Thread.sleep(delay);

            System.out.println("Restoring MEMC to " + origMemc + "...");
            boolean r2 = (boolean) setConfig.invoke(pq, 0, apply, origMemc, act);
            System.out.println("  result: " + r2);

            int after = (int) pqClass.getMethod("getMemcConfig", int.class).invoke(pq, 0);
            System.out.println("After: memcConfig=" + after);
        }
    }
}
