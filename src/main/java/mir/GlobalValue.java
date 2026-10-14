//全局变量类

package mir;

import frontend.ast.Node;
import frontend.sym.InitSym;
import mir.type.SymType;

public class GlobalValue extends Value {
    private static int strGvNum = 0;
    public boolean ifConst;

    public GlobalValue() {
    }

    public GlobalValue(SymType type, boolean ifConst) {
        super(type);
    }

    public static class DefGlobalVal extends GlobalValue {
        public Node.Def def;
        public InitSym initValue;

        public DefGlobalVal(SymType type, Node.Def def, InitSym initValue, boolean ifConst) {
            super(new SymType.PointerType(type), ifConst);
            pre = GLOBAL_PRE;
            name = "GLB_" + def.ident.getContent();
            this.def = def;
            this.initValue = initValue;
        }

        @Override
        public String toString() {
            return getName() + " = dso_local global " + initValue.toString() + "\n";
        }
    }

    public static class StrGlobalVal extends GlobalValue {
        public String content;

        public StrGlobalVal(String content) {
            super(new SymType.PointerType(SymType.BasicType.Basic_Char), true);
            this.content = content;
            pre = GLOBAL_PRE;
            name = "str" + strGvNum;
            strGvNum++;
        }

        @Override
        public String toString() {
            return pre + name + " = private unnamed_addr constant " +
                    "[" + (content.length() + 1) + " x i8] c\"" + content + "\\00\"" + "\n";
        }
    }

    public static class UndefGlobalVal extends GlobalValue {
        public UndefGlobalVal() {
        }


        public String getName() {
            return "undef_globalval";
        }

        public UndefGlobalVal(SymType type) {
            super(type, false);
        }
    }
}
