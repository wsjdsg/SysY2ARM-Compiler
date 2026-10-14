package frontend.visitor;

import frontend.sym.InitSym;
import mir.Constant;
import mir.Instr;
import mir.Value;
import mir.type.SymType;
import tools.MyList;
import tools.MyListNode;

import java.util.*;

import static mir.MyModule.memSet;
import static frontend.visitor.MyVisitor.curBasicBlock;
import static frontend.visitor.MyVisitor.transDataType;
import static mir.Constant.FloatConst.CONST_FLOAT_0;
import static mir.Constant.IntConst.CONST_INT_0;
import static mir.type.SymType.BasicType.Basic_INT;

public class ForDefInit {

    public static class ForValDef {
        public static void forInitZero(Value pointer, SymType pointToType) {
            ArrayList<Value> dimLens = new ArrayList<>();
            dimLens.add(CONST_INT_0);
            int size = 4;       // i32
            while (pointToType instanceof SymType.ArrayType) {
                size *= ((SymType.ArrayType) pointToType).getSize();
                pointToType = ((SymType.ArrayType) pointToType).getBaseType();
                dimLens.add(CONST_INT_0);
            }

            Value pointerI = new Instr.GepInstr(pointToType, pointer, dimLens, curBasicBlock);
            if (((SymType.PointerType) pointerI.getType()).getPointToType().isFloatType()) {
                pointerI = new Instr.BitcastInstr(pointerI, new SymType.PointerType(Basic_INT), curBasicBlock);
            }
            ArrayList<Value> params = new ArrayList<>();
            params.add(pointerI);
            params.add(CONST_INT_0);
            params.add(new Constant.IntConst(size));
            new Instr.CallInstr(memSet, params, curBasicBlock);
        }

    }

    public static class ForArrayDef {
        public static void forInitLocalArray(InitSym initSym, Value pointer, SymType pointToType) {
            ForArrayFlatten forArrayFlatten = initSym.flatten();
            HashSet<Value> allValues = forArrayFlatten.getAllValues();

            if (forArrayFlatten.isAllZero()) {
                ForValDef.forInitZero(pointer, pointToType);
            } else {
                boolean ifMemSeted = false;
                if (forArrayFlatten.getEleSum() / 5 > allValues.size() / 3) {       //平衡memset和store初始化0的性能
                    ForValDef.forInitZero(pointer, pointToType);
                    ifMemSeted = true;
                }

                ArrayList<Value> dimLens = new ArrayList<>();
                for (int i = 0; i <= ((SymType.ArrayType) pointToType).getDimSize(); i++) {
                    dimLens.add(CONST_INT_0);
                }

                SymType.BasicType eleType = ((SymType.ArrayType) pointToType).getEleType();
                Value pointerI = new Instr.GepInstr(eleType, pointer, dimLens, curBasicBlock);
                if (!(forArrayFlatten.getFirst().value.equals(CONST_INT_0) ||
                        forArrayFlatten.getFirst().value.equals(CONST_FLOAT_0)) || !ifMemSeted) {
                    new Instr.StoreInstr(transDataType(forArrayFlatten.getFirst().value, eleType), pointerI, curBasicBlock);
                }
                LinkedHashMap<Integer, Value> allEles = forArrayFlatten.getAllNonZero();
                if(!ifMemSeted) {
                    for(int i = 0; i < forArrayFlatten.getEleSum(); i++) {
                        allEles.putIfAbsent(i, CONST_INT_0);
                    }
                }

                for(Map.Entry<Integer, Value> e : allEles.entrySet()) {
                    if(e.getKey().equals(0)) {
                        continue;
                    }
                    dimLens = new ArrayList<>();
                    dimLens.add(Constant.IntConst.getIntConstByNum(e.getKey()));
                    Value ptr = new Instr.GepInstr(eleType, pointerI, dimLens, curBasicBlock);
                    new Instr.StoreInstr(transDataType(e.getValue(), eleType), ptr, curBasicBlock);
                }
            }
        }

    }

    public static class ForArrayFlatten extends MyList<ForArrayFlatten.Elements> {
        public static class Elements extends MyListNode {
            public Value value;
            public int eleCnt;

            public Elements(Value value, int eleCnt) {
                this.value = value;
                this.eleCnt = eleCnt;
            }

            public boolean canBeMerged(Elements elements) {
                return elements.equals(this.getNext()) && this.value.equals(elements.value);
            }

            public void mergeWith(Elements elements) {
                this.eleCnt += elements.eleCnt;
                elements.remove();
            }

            @Override
            public String toString() {
                String str = null;
                if (value instanceof Constant.IntConst) {
                    str = value.toString();
                } else if (value instanceof Constant.FloatConst) {
                    str = String.valueOf(Float.floatToIntBits(((Constant.FloatConst) value).floatVal));
                }
                if (eleCnt == 1) {
                    return ".word\t" + str;
                } else {
                    return ".fill\t" + eleCnt + ", 4, " + str;
                }
            }

        }

        public boolean isAllZero() {
            for (Elements e : this) {
                if (!(e.value.equals(CONST_INT_0) || e.value.equals(CONST_FLOAT_0))) {
                    return false;
                }
            }
            return true;
        }

        public HashSet<Value> getAllValues() {
            HashSet<Value> allValues = new HashSet<>();
            for (Elements e : this) {
                allValues.add(e.value);
            }
            return allValues;
        }

        public LinkedHashMap<Integer, Value> getAllNonZero() {
            int offset = 0;
            LinkedHashMap<Integer, Value> nonZeros = new LinkedHashMap<>();
            for(Elements e : this) {
                if(e.value.equals(CONST_INT_0) || e.value.equals(CONST_FLOAT_0)) {
                    offset += e.eleCnt;
                } else {
                    for(int j = 0; j < e.eleCnt; j++) {
                        nonZeros.put(offset, e.value);
                        offset++;
                    }
                }
            }
            return nonZeros;
        }

        public int getEleSum() {
            int sum = 0;
            for (Elements e : this) {
                sum += e.eleCnt;
            }
            return sum;
        }

        public int getTotalSizeInBytes() {
            return getEleSum() * 4;
        }

        public void mergeAll() {
            for (MyListNode cur = getFirst(); cur.hasNext(); cur = cur.getNext()) {
                Elements elements = (Elements) cur;
                while (elements.getNext() != tail && elements.getNext() instanceof Elements
                        && elements.canBeMerged((Elements) elements.getNext())) {
                    elements.mergeWith((Elements) elements.getNext());
                    size--;
                }
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (Elements e : this) {
                sb.append(e).append("\n");
            }
            return sb.toString();
        }
    }

}
