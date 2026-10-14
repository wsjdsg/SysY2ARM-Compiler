//可计算表达式求值类
//exp中同时存在 int 和 float 类型，按 float 返回计算结果

package frontend.sym;

import frontend.ast.Node;
import frontend.ast.Word;
import frontend.ast.WordType;
import mir.Constant;
import mir.Instr;
import mir.Value;

import java.util.*;

import static frontend.visitor.MyVisitor.curBasicBlock;
import static frontend.visitor.MyVisitor.transDataType;
import static mir.type.SymType.BasicType.*;

public class Calculate {
    private static SymTable symTable;

    public Calculate(SymTable symTable) {
        this.symTable = symTable;
    }

    public static Object simpleBinCal(Object a, Object b, WordType op) {
        if (a instanceof Float || b instanceof Float) {
            float a1 = a instanceof Float ? (float) a : (float) ((int) a);
            float b1 = b instanceof Float ? (float) b : (float) ((int) b);
            switch (op) {
                case ADD:
                    return a1 + b1;
                case SUB:
                    return a1 - b1;
                case MUL:
                    return a1 * b1;
                case DIV:
                    return a1 / b1;
                case MOD:
                    return a1 % b1;
                default:
                    return null;
            }
        } else {
            int a1 = (int) a;
            int b1 = (int) b;
            switch (op) {
                case ADD:
                    return a1 + b1;
                case SUB:
                    return a1 - b1;
                case MUL:
                    return a1 * b1;
                case DIV:
                    return a1 / b1;
                case MOD:
                    return a1 % b1;
                default:
                    return null;
            }
        }
    }

    public static Object simpleUnaCal(Object a, WordType op) {
        if (a instanceof Float) {
            Float a1 = (Float) a;
            switch (op) {
                case ADD:
                    return a1;
                case SUB:
                    return -a1;
                case NOT:
                    return a1 == 0.0F ? 1.0F : 0.0F;
                default:
                    return null;
            }
        } else {
            int a1 = (int) a;
            switch (op) {
                case ADD:
                    return a1;
                case SUB:
                    return -a1;
                case NOT:
                    return a1 == 0 ? 1 : 0;
                default:
                    return null;
            }
        }
    }

    public int calIntExp(Node.Exp exp) throws Exception {
        return (int) calExp(exp);
    }

    public float calFloatExp(Node.Exp exp) throws Exception {
        return (float) calExp(exp);
    }

    public static Object calExp(Node.Exp exp) throws Exception {
        if (exp instanceof Node.BinaryExp) {
            return calBinExp((Node.BinaryExp) exp);
        } else if (exp instanceof Node.UnaryExp) {
            return calUnaExp((Node.UnaryExp) exp);
        }
        return null;
    }

    public static Object calBinExp(Node.BinaryExp exp) throws Exception {
        Node.UnaryExp first = exp.first;
        ArrayList<Node.UnaryExp> follows = exp.follows;
        ArrayList<WordType> ops = exp.ops;

        List<Object> inExp = new ArrayList<>();
        inExp.add(calUnaExp(first));
        for (int i = 0; i < ops.size(); i++) {
            inExp.add(ops.get(i));
            inExp.add(calUnaExp(follows.get(i)));
        }

        return calRpnForConst(transToRpn(inExp));
    }

    public static Object calRpnForConst(List<Object> exp) throws Exception {
        Stack<Object> stack = new Stack<>();
        for (Object token : exp) {
            if (!(token instanceof WordType)) {
                stack.push(token);
            } else {
                WordType op = (WordType) token;
                Object ele1 = stack.pop();
                Object ele2 = stack.pop();
                Object result = simpleBinCal(ele2, ele1, op);
                stack.push(result);
            }
        }
        return stack.pop();
    }

    public static Object calUnaExp(Node.UnaryExp exp) throws Exception {
        ArrayList<WordType> ops = exp.unaryOp;
        Object ret = calPrimExp(exp.primaryExp);
        for (int i = 0; i < ops.size(); i++) {
            ret = simpleUnaCal(ret, ops.get(i));
        }
        return ret;
    }

    public static Object calPrimExp(Node.PrimaryExp exp) throws Exception {
        if (exp instanceof Node.Exp) {
            return calExp((Node.Exp) exp);
        } else if (exp instanceof Node.Number) {
            return calNumber((Node.Number) exp);
        } else if (exp instanceof Node.Lval) {
            return calLval((Node.Lval) exp);
        } else {
            return null;
        }
    }

    public static Object calNumber(Node.Number exp) {
        Word num = exp.number;
        String content = num.getContent();
        switch (num.getType()) {
            case DEC_INT:
                return Integer.parseInt(content);
            case OCT_INT:
                return Integer.parseInt(content.substring(1), 8);
            case HEX_INT:
                return Integer.parseInt(content.substring(2), 16);
            case DEC_FLOAT:
            case HEX_FLOAT:
                return Float.parseFloat(content);
            default:
                return null;
        }
    }

    public static Object calLval(Node.Lval exp) throws Exception {
        String ident = exp.ident.getContent();
        Symbol symbol = symTable.getSymbol(ident, true);

        InitSym init = symbol.getInitValue();
        if (init == null) {
            throw new Exception("Error: Object without initialization cannot be calculated");
        }

        ArrayList<Integer> indexes = new ArrayList<>();
        for (Node.Exp index : exp.dimLen) {
            indexes.add((Integer) calExp(index));
        }

        for (Integer index : indexes) {
            if (init instanceof InitSym.InitValue) {
                throw new Exception("Error: Not Array/Zero init");
            } else if (init instanceof InitSym.InitZero) {
                return 0;
            } else {
                init = ((InitSym.InitArray) init).getEle(index);
            }
        }

        if (!(init instanceof InitSym.InitValue)) {
            throw new Exception("Error: Not Value init");
        }

        Value ret = ((InitSym.InitValue) init).getValue();
        return ((Constant) ret).getConstValue();
    }

    public static boolean isCalOp(WordType op) {
        return op == WordType.ADD || op == WordType.SUB || op == WordType.MUL ||
                op == WordType.DIV || op == WordType.MOD;
    }

    public static boolean isCmpOp(WordType op) {
        return op == WordType.LE || op == WordType.GE || op == WordType.EQ ||
                op == WordType.NEQ || op == WordType.LT || op == WordType.GT;
    }

    public static boolean isLogOp(WordType op) {
        return op == WordType.LOGIC_AND || op == WordType.LOGIC_OR;
    }

    public static Instr.AluInstr.AluOp genIntOp(WordType wordType) {
        switch (wordType) {
            case ADD:
                return Instr.AluInstr.AluOp.ADD;
            case SUB:
                return Instr.AluInstr.AluOp.SUB;
            case MUL:
                return Instr.AluInstr.AluOp.MUL;
            case DIV:
                return Instr.AluInstr.AluOp.DIV;
            case MOD:
                return Instr.AluInstr.AluOp.REM;
        }
        return null;
    }

    public static Instr.AluInstr.AluOp genFloatOp(WordType wordType) {
        switch (wordType) {
            case ADD:
                return Instr.AluInstr.AluOp.FADD;
            case SUB:
                return Instr.AluInstr.AluOp.FSUB;
            case MUL:
                return Instr.AluInstr.AluOp.FMUL;
            case DIV:
                return Instr.AluInstr.AluOp.FDIV;
            case MOD:
                return Instr.AluInstr.AluOp.FREM;
        }
        return null;
    }

    public static Instr.IcmpInstr.IcmpOp genIcmpOp(WordType wordType) {
        switch (wordType) {
            case LE:
                return Instr.IcmpInstr.IcmpOp.SLE;
            case LT:
                return Instr.IcmpInstr.IcmpOp.SLT;
            case GE:
                return Instr.IcmpInstr.IcmpOp.SGE;
            case GT:
                return Instr.IcmpInstr.IcmpOp.SGT;
            case EQ:
                return Instr.IcmpInstr.IcmpOp.EQ;
            case NEQ:
                return Instr.IcmpInstr.IcmpOp.NE;
        }
        return null;
    }

    public static Instr.FcmpInstr.FcmpOp genFcmpOp(WordType wordType) {
        switch (wordType) {
            case LE:
                return Instr.FcmpInstr.FcmpOp.OLE;
            case LT:
                return Instr.FcmpInstr.FcmpOp.OLT;
            case GE:
                return Instr.FcmpInstr.FcmpOp.OGE;
            case GT:
                return Instr.FcmpInstr.FcmpOp.OGT;
            case EQ:
                return Instr.FcmpInstr.FcmpOp.OEQ;
            case NEQ:
                return Instr.FcmpInstr.FcmpOp.ONE;
        }
        return null;
    }

    private static final Map<WordType, Integer> opPre;

    static {
        opPre = new HashMap<>();
        opPre.put(WordType.MUL, 3);
        opPre.put(WordType.DIV, 3);
        opPre.put(WordType.MOD, 3);
        opPre.put(WordType.ADD, 4);
        opPre.put(WordType.SUB, 4);
        opPre.put(WordType.GT, 6);
        opPre.put(WordType.LT, 6);
        opPre.put(WordType.LE, 6);
        opPre.put(WordType.GE, 6);
        opPre.put(WordType.NEQ, 7);
        opPre.put(WordType.EQ, 7);
        opPre.put(WordType.LOGIC_AND, 11);
        opPre.put(WordType.LOGIC_OR, 12);
    }

    public static Value calBinaryExp(Value first, ArrayList<Value> follows, ArrayList<WordType> ops) {
        List<Object> inExp = new ArrayList<>();
        inExp.add(first);
        for (int i = 0; i < ops.size(); i++) {
            inExp.add(ops.get(i));
            inExp.add(follows.get(i));
        }
        return calRpn(transToRpn(inExp));
    }

    private static List<Object> transToRpn(List<Object> inExp) {
        List<Object> rpnExp = new ArrayList<>();
        Stack<WordType> opStack = new Stack<>();

        for (Object token : inExp) {
            if (!(token instanceof WordType)) {
                rpnExp.add(token);
            } else {
                WordType op = (WordType) token;
                while (!opStack.isEmpty() && opPre.containsKey(opStack.peek())
                        && hasHigherPre(opStack.peek(), op)) {
                    rpnExp.add(opStack.pop());
                }
                opStack.push(op);
            }
        }

        while (!opStack.isEmpty()) {
            rpnExp.add(opStack.pop());
        }

        return rpnExp;
    }

    private static Value calRpn(List<Object> exp) {
        Stack<Value> stack = new Stack<>();
        for (Object token : exp) {
            if (token instanceof Value) {
                stack.push((Value) token);
            } else if (token instanceof WordType) {
                WordType op = (WordType) token;
                Value ele1 = stack.pop();
                Value ele2 = stack.pop();
                if (ele1 instanceof Constant && ele2 instanceof Constant && isCalOp(op)) {
                    Object num = simpleBinCal(((Constant) ele2).getConstValue(), ((Constant) ele1).getConstValue(), op);
                    Value result = null;
                    if (num instanceof Integer) {
                        result = new Constant.IntConst((Integer) num);
                    } else if (num instanceof Float) {
                        result = new Constant.FloatConst((Float) num);
                    }
                    stack.push(result);
                } else {
                    Value result = calTwoEles(ele2, ele1, op);
                    stack.push(result);
                }
            }
        }
        return stack.pop();
    }

    private static boolean hasHigherPre(WordType op1, WordType op2) {
        return opPre.get(op1) <= opPre.get(op2);
    }

    private static Value calTwoEles(Value ele1, Value ele2, WordType op) {
        if (isCalOp(op)) {
            if (ele1.getType().isFloatType() || ele2.getType().isFloatType()) {
                ele1 = transDataType(ele1, Basic_FLOAT);
                ele2 = transDataType(ele2, Basic_FLOAT);
                return new Instr.AluInstr(Basic_FLOAT, curBasicBlock, genFloatOp(op), ele1, ele2);
            } else {
                ele1 = transDataType(ele1, Basic_INT);
                ele2 = transDataType(ele2, Basic_INT);
                return new Instr.AluInstr(Basic_INT, curBasicBlock, genIntOp(op), ele1, ele2);
            }
        } else if (isCmpOp(op)) {
            if (ele1.getType().isFloatType() || ele2.getType().isFloatType()) {
                ele1 = transDataType(ele1, Basic_FLOAT);
                ele2 = transDataType(ele2, Basic_FLOAT);
                return new Instr.FcmpInstr(curBasicBlock, genFcmpOp(op), ele1, ele2);
            } else {
                ele1 = transDataType(ele1, Basic_INT);
                ele2 = transDataType(ele2, Basic_INT);
                return new Instr.IcmpInstr(curBasicBlock, genIcmpOp(op), ele1, ele2);
            }
        } else if (isLogOp(op)) {
            throw new Error("<calTwoEles> cannot support LOGICAL_TYPE");
        }
        return null;
    }

}
