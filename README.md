Uses [this nifty library](https://github.com/aragozin/jvm-tools/tree/master/hprof-heap) to check for Jenkins heap dumps (`jmap -dump:live $pid`) containing “ghost” Pipeline builds.

CloudBees CI HA controller? The list of registered running builds is not easily accessible from Hazelcast.
Instead pass `jenkins-root-configuration-files/org.jenkinsci.plugins.workflow.flow.FlowExecutionList.xml` from a support bundle as a second argument.
