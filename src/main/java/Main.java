import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
        List<Build> loaded = new ArrayList<>();
        for (Instance ctg : ctgC.getInstances()) {
            System.err.print(".");
            loaded.add(Build.of(HeapWalker.valueOf(ctg, "execution.owner")));
        }
        System.err.println();
        System.err.println("==== Builds with program.dat loaded into memory: ====");
        Collections.sort(loaded);
        for (Build b : loaded) {
            System.out.println(b);
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
    }
}
