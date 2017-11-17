import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.gridkit.jvmtool.heapdump.HeapWalker;
import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.HeapFactory;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaClass;
import org.netbeans.lib.profiler.heap.ObjectArrayInstance;

public class Main {
    public static void main(String[] args) throws Exception {
        System.err.println("Parsing...");
        Heap heap = HeapFactory.createFastHeap(new File(args[0]));
        System.err.print("Scanning");
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
        List<Build> listed = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            System.err.print(".");
            listed.add(Build.of(elements.get(i)));
        }
        System.err.println();
        System.err.println("==== Builds thought to be running according to FlowExecutionList: ====");
        Collections.sort(listed);
        for (Build b : listed) {
            System.out.println(b);
        }
        JavaClass ctgC = heap.getJavaClassByName("org.jenkinsci.plugins.workflow.cps.CpsThreadGroup");
        for (Instance ctg : ctgC.getInstances()) {
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
            System.err.println();
            System.err.println("Found unlisted build " + b + " with " + threadCount + " threads");
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
        }
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
        @Override
        public boolean equals(Object obj) {
            return obj instanceof Build && job.equals(((Build) obj).job) && num == ((Build) obj).num;
        }
    }
}
