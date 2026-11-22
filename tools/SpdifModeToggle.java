import java.lang.reflect.Method;

/**
 * Gets or sets the TCL TV's SPDIF output mode (the Audio Output setting).
 * Values: 1=PCM, 3=Auto, 4=Passthrough.
 * Runs via: CLASSPATH=/data/local/tmp/spdifmode.dex:/apex/com.tcl.tcore/javalib/tcl-framework.jar app_process / SpdifModeToggle [get|set <mode>]
 */
public class SpdifModeToggle {
    public static void main(String[] args) throws Exception {
        Class<?> cls = Class.forName("tcl.media.TAudioManager");
        Object mgr = cls.getMethod("getInstance").invoke(null);
        Method getMode = cls.getMethod("getSpdifMode");
        Method setMode = cls.getMethod("setSpdifMode", int.class);

        if (args.length == 0 || args[0].equals("get")) {
            System.out.println("spdifMode=" + getMode.invoke(mgr));
        } else if (args[0].equals("set") && args.length > 1) {
            int target = Integer.parseInt(args[1]);
            int before = (int) getMode.invoke(mgr);
            if (before == target) {
                System.out.println("spdifMode=" + before + " (already)");
            } else {
                setMode.invoke(mgr, target);
                int after = (int) getMode.invoke(mgr);
                System.out.println("spdifMode=" + before + "->" + after);
            }
        } else {
            System.err.println("usage: SpdifModeToggle [get|set <1|3|4>]");
            System.exit(1);
        }
    }
}
