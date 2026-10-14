package mir;

import mir.type.SymType;
import tools.MyList;

import java.util.ArrayList;

import static mir.Func.genParamsFromType;
import static mir.type.SymType.BasicType.*;


public class MyModule {
    //external function--------------------------------
    public static final Func getInt = new Func("getint", true, Basic_INT, new ArrayList<>());
    public static final Func getCh = new Func("getch", true, Basic_INT, new ArrayList<>());
    public static final Func getFloat = new Func("getfloat", true, Basic_FLOAT, new ArrayList<>());
    public static final Func getArray = new Func("getarray", true, Basic_INT, genParamsFromType(new SymType.PointerType(Basic_INT)));
    public static final Func getFarray = new Func("getfarray", true, Basic_INT, genParamsFromType(new SymType.PointerType(Basic_FLOAT)));
    public static final Func putInt = new Func("putint", true, SymType.VoidType.getVoidType(), genParamsFromType(Basic_INT));
    public static final Func putCh = new Func("putch", true, SymType.VoidType.getVoidType(), genParamsFromType(Basic_INT));
    public static final Func putFloat = new Func("putfloat", true, SymType.VoidType.getVoidType(), genParamsFromType(Basic_FLOAT));
    public static final Func putArray = new Func("putarray", true, SymType.VoidType.getVoidType(), genParamsFromType(Basic_INT, new SymType.PointerType(Basic_INT)));
    public static final Func putFarray = new Func("putfarray", true, SymType.VoidType.getVoidType(), genParamsFromType(Basic_INT, new SymType.PointerType(Basic_FLOAT)));
    //public static final Func putF = new Func("putf", true, Basic_INT, genParamsFromType(new SymType.PointerType(Basic_Char)));
    public static final Func startTime = new Func("starttime", true, SymType.VoidType.getVoidType(), genParamsFromType(Basic_INT));
    public static final Func stopTime = new Func("stoptime", true, SymType.VoidType.getVoidType(), genParamsFromType(Basic_INT));
    public static final Func memSet = new Func("memset", true, SymType.VoidType.getVoidType(), genParamsFromType(new PointerType(Basic_INT), Basic_INT, Basic_INT));
    public static boolean curIsExt = false;
    public MyList<Func> funcs = new MyList<>();
    public MyList<GlobalValue> globalValues = new MyList<>();
    //-------------------------------------------------

    public MyModule() {
        curIsExt = true;
        funcs.insertAtTail(getInt);
        funcs.insertAtTail(getCh);
        funcs.insertAtTail(getFloat);
        funcs.insertAtTail(getArray);
        funcs.insertAtTail(getFarray);
        funcs.insertAtTail(putInt);
        funcs.insertAtTail(putCh);
        funcs.insertAtTail(putFloat);
        funcs.insertAtTail(putArray);
        funcs.insertAtTail(putFarray);
        //funcs.insertAtTail(putF);
        funcs.insertAtTail(startTime);
        funcs.insertAtTail(stopTime);
        funcs.insertAtTail(memSet);
        curIsExt = false;
    }


    public Func getFuncByName(String name) {
        for (Func func : funcs) {
            if (func.getName().equals(name)) {
                return func;
            }
        }
        return null;
    }

    public GlobalValue getGlobalValueByName(String preName) {
        //GlobalValue类构造函数中： preName = "GLB_" + ident;
        for (GlobalValue globalValue : globalValues) {
            if (globalValue.getName().equals(preName)) {
                return globalValue;
            }
        }
        return null;
    }

    public MyList<Func> getFuncs() {
        return funcs;
    }
}
