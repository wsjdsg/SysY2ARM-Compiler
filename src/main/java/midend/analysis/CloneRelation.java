package midend.analysis;

import mir.Value;

import java.util.HashMap;

public class CloneRelation {
    public static HashMap<Value, Value> srcValtoCloneVal = new HashMap<>();
    public static HashMap<Loop, Loop> srcLooptoCloneLoop = new HashMap<>();

    public static void clear() {
        srcValtoCloneVal.clear();
        srcLooptoCloneLoop.clear();
    }

    public static void putCloneVal(Value srcBB, Value cloneBB) {
        srcValtoCloneVal.put(srcBB, cloneBB);
    }

    public static Value getCloneVal(Value srcBB) {
        return srcValtoCloneVal.getOrDefault(srcBB, srcBB);
    }

    public static void putCloneLoop(Loop srcLoop, Loop cloneLoop) {
        srcLooptoCloneLoop.put(srcLoop, cloneLoop);
    }

    public static Loop getCloneLoop(Loop srcLoop) {
        return srcLooptoCloneLoop.getOrDefault(srcLoop, srcLoop);
    }

}
