import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.gridkit.jvmtool.heapdump.HeapWalker;
import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.HeapFactory;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaClass;
import org.netbeans.lib.profiler.heap.ObjectArrayInstance;

public class Main {
    public static void main(String[] args) throws Exception {
        File dump = new File(args[0]);
        System.err.print("Loading " + dump + "...");
        Heap heap = HeapFactory.createFastHeap(dump);
        System.err.println();
        System.err.print("Looking for builds thought be running according to FlowExecutionList");
        JavaClass felC = heap.getJavaClassByName("org.jenkinsci.plugins.workflow.flow.FlowExecutionList");
        List<Instance> fels = felC.getInstances();
        if (fels.size() != 1) {
            throw new IllegalStateException("unexpected FlowExecutionList count: " + fels);
        }
        Instance fel = fels.get(0);
        Instance list = HeapWalker.valueOf(fel, "runningTasks.core"); // ArrayList
        ObjectArrayInstance elementsA = HeapWalker.valueOf(list, "elementData"); // TODO docs claim this would return Object[], but CONVERTERS does not actually do that
        List<Instance> elements = elementsA.getValues();
        int size = HeapWalker.valueOf(list, "size");
        Set<Build> listed = new TreeSet<>();
        for (int i = 0; i < size; i++) {
            System.err.print(".");
            Build b = Build.of(elements.get(i));
            if (!listed.add(b)) {
                System.err.print("(duplicated " + b + ")");
            }
        }
        System.err.println("found " + listed.size() + ".");
        listed.forEach(System.err::println);
        System.err.println();
        System.err.print("Looking for flyweight executors running unlisted builds");
        Set<Build> executing = new TreeSet<>();
        for (Instance ooe : heap.getJavaClassByName("hudson.model.OneOffExecutor").getInstances()) {
            System.err.print(".");
            Instance owner = HeapWalker.valueOf(ooe, "executable.execution.owner");
            if (owner == null) {
                continue; // running something else, fine
            }
            Build b = Build.of(owner);
            if (listed.contains(b)) {
                continue; // actually running, fine
            }
            if (!executing.add(b)) {
                System.err.print("(duplicated " + b + ")");
            }
        }
        System.err.println("found " + executing.size() + ".");
        executing.forEach(System.err::println);
        System.err.println();
        System.err.print("Looking for unlisted builds with program.dat loaded");
        Map<Build, Instance> ctgs = new TreeMap<>();
        for (Instance ctg : heap.getJavaClassByName("org.jenkinsci.plugins.workflow.cps.CpsThreadGroup").getInstances()) {
            System.err.print(".");
            int threadCount = HeapWalker.valueOf(ctg, "threads.size");
            if (threadCount == 0) {
                continue; // completed, ignore
            }
            Instance owner = HeapWalker.valueOf(ctg, "execution.owner");
            Build b = Build.of(owner);
            if (listed.contains(b)) {
                continue; // actually running, fine
            }
            if (ctgs.put(b, ctg) != null) {
                System.err.print("(duplicated " + b + ")");
            }
        }
        System.err.println("found " + ctgs.size() + ".");
        ctgs.forEach((b, ctg) -> {
            Instance owner = HeapWalker.valueOf(ctg, "execution.owner");
            System.err.println(b);
            System.err.println("  threads.size:  " + HeapWalker.valueOf(ctg, "threads.size"));
            System.err.println("  scripts.size: " + HeapWalker.valueOf(ctg, "scripts.size"));
            System.err.println("  execution.heads.size: " + HeapWalker.valueOf(ctg, "execution.heads.size"));
            // TODO check types of heads (but tricky to navigate a TreeMap)
            System.err.println("  closures.size: " + HeapWalker.valueOf(ctg, "closures.size"));
            Instance pp = HeapWalker.valueOf(ctg, "execution.programPromise");
            System.err.println("  execution.programPromise: " + pp.getJavaClass().getName());
            Instance sync = HeapWalker.valueOf(pp, "sync");
            Instance value = HeapWalker.valueOf(sync, "value");
            if (value != null) {
                System.err.println("    value: " + value.getJavaClass().getName());
            }
            Instance exception = HeapWalker.valueOf(sync, "exception");
            if (exception != null) {
                System.err.println("    exception: " + exception.getJavaClass().getName() + ": " + HeapWalker.valueOf(exception, "detailMessage"));
            }
            Instance result = HeapWalker.valueOf(owner, "run.result");
            System.err.println("  result: " + (result != null ? HeapWalker.valueOf(result, "name") : null));
            long startTime = HeapWalker.valueOf(owner, "run.startTime");
            System.err.println("  start time: " + new Date(startTime));
            long duration = HeapWalker.valueOf(owner, "run.duration");
            if (duration != 0) {
                System.err.println("  duration: " + (duration / 1000) + "s");
            }
            boolean firstTime = HeapWalker.valueOf(owner, "run.firstTime");
            System.err.println("  firstTime: " + firstTime);
            Instance completed = HeapWalker.valueOf(owner, "run.completed");
            System.err.println("  completed: " + (completed != null ? HeapWalker.valueOf(completed, "value") : null));
            System.err.println("  logsToCopy? " + (HeapWalker.valueOf(owner, "run.logsToCopy") != null));
        });
        System.err.println();
        System.err.print("Looking for unlisted builds with GroovyClassLoader leaked");
        Set<Build> leaked = new TreeSet<>();
        for (Instance cgstl : heap.getJavaClassByName("org.jenkinsci.plugins.workflow.cps.CpsGroovyShell$TimingLoader").getInstances()) {
            System.err.print(".");
            Build b = Build.of(HeapWalker.valueOf(cgstl, "execution.owner"));
            if (listed.contains(b)) {
                continue; // actually running, fine
            }
            if (!leaked.add(b)) {
                /* Too noisy, and that not interesting:
                System.err.print("(duplicated " + b + ")");
                */
            }
        }
        System.err.println("found " + leaked.size() + ".");
        leaked.forEach(System.err::println);
    }
    static class Build implements Comparable<Build> {
        static Build of(Instance owner) { // WorkflowRun$Owner
            return new Build(HeapWalker.valueOf(owner, "job"), Integer.parseInt(HeapWalker.valueOf(owner, "id")));
        }
        final String job;
        final int num;
        Build(String job, int num) {
            this.job = job;
            this.num = num;
        }
        @Override public int compareTo(Build o) {
            int c = job.compareTo(o.job);
            return c != 0 ? c : num - o.num;
        }
        @Override public String toString() {
            return job + " #" + num;
        }
        @Override public int hashCode() {
            return job.hashCode() ^ num;
        }
        @Override public boolean equals(Object obj) {
            return obj instanceof Build && job.equals(((Build) obj).job) && num == ((Build) obj).num;
        }
    }
}
