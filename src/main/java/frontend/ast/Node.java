//AST节点类

package frontend.ast;

import java.util.ArrayList;

public class Node {
    public ArrayList<CompUnit> compUnits;

    public Node(ArrayList<CompUnit> compUnits) {
        this.compUnits = compUnits;
    }

    public ArrayList<CompUnit> getCompUnits() {
        return compUnits;
    }

    public interface CompUnit {
    }


    public static class Decl implements CompUnit, BlockItem {
        public boolean ifConst;
        public WordType bType;
        public ArrayList<Def> defs;

        public Decl(boolean ifConst, WordType bType, ArrayList<Def> defs) {
            this.ifConst = ifConst;
            this.bType = bType;
            this.defs = defs;
        }
    }

    public static class Def {
        public Word ident;
        public ArrayList<Exp> indexes;
        public InitVal initVal;

        public Def(Word ident, ArrayList<Exp> indexes, InitVal initVal) {
            this.ident = ident;
            this.indexes = indexes;
            this.initVal = initVal;
        }
    }

    public interface InitVal {
    }

    public static class InitArray implements InitVal {
        public ArrayList<InitVal> initVals;
        public int index = 0;

        public InitArray(ArrayList<InitVal> initVals) {
            this.initVals = initVals;
        }

        public boolean hasInited() {
            return index < initVals.size();
        }

        public InitVal getInitEle() {
            return initVals.get(index);
        }
    }

    public static class FuncDef implements CompUnit {
        public WordType funcType;
        public Word ident;
        public ArrayList<FuncFParam> funcFParams;
        public Block funcBody;

        public FuncDef(WordType funcType, Word ident, ArrayList<FuncFParam> funcFParams, Block funcBody) {
            this.funcType = funcType;
            this.ident = ident;
            this.funcFParams = funcFParams;
            this.funcBody = funcBody;
        }
    }

    public static class FuncFParam {
        public WordType bType;
        public Word ident;
        public boolean ifArray;
        public ArrayList<Exp> dimLen;

        public FuncFParam(WordType bType, Word ident, boolean ifArray, ArrayList<Exp> dimLen) {
            this.bType = bType;
            this.ident = ident;
            this.ifArray = ifArray;
            this.dimLen = dimLen;
        }
    }

    public interface BlockItem {
    }

    public interface Stmt extends BlockItem {
    }

    public static class Assign implements Stmt {
        public Lval lval;
        public Exp exp;

        public Assign(Lval lval, Exp exp) {
            this.lval = lval;
            this.exp = exp;
        }
    }

    public static class ExpStmt implements Stmt {
        public Exp expCanNull;

        public ExpStmt(Exp expCanNull) {
            this.expCanNull = expCanNull;
        }
    }

    public static class Block implements Stmt {
        public ArrayList<BlockItem> blockItems;

        public Block(ArrayList<BlockItem> blockItems) {
            this.blockItems = blockItems;
        }
    }

    public static class IfStmt implements Stmt {
        public LorExp cond;
        public Stmt thenAct;
        public Stmt elseAct;

        public IfStmt(LorExp cond, Stmt thenAct, Stmt elseAct) {
            this.cond = cond;
            this.thenAct = thenAct;
            this.elseAct = elseAct;
        }
    }

    public static class WhileStmt implements Stmt {
        public LorExp cond;
        public Stmt whileBody;

        public WhileStmt(LorExp cond, Stmt thenAct) {
            this.cond = cond;
            this.whileBody = thenAct;
        }
    }

    public static class BreakStmt implements Stmt {
    }

    public static class ContinueStmt implements Stmt {
    }

    public static class ReturnStmt implements Stmt {
        public Exp returnVal;

        public ReturnStmt(Exp returnVal) {
            this.returnVal = returnVal;
        }
    }

    public interface PrimaryExp {
    }

    public interface Exp extends PrimaryExp, InitVal {
    }

    public static class UnaryExp implements Exp {
        public ArrayList<WordType> unaryOp;
        public PrimaryExp primaryExp;

        public UnaryExp(ArrayList<WordType> unaryOp, PrimaryExp primaryExp) {
            this.unaryOp = unaryOp;
            this.primaryExp = primaryExp;
        }
    }

    public static class BinaryExp implements Exp {
        public UnaryExp first;
        public ArrayList<WordType> ops;
        public ArrayList<UnaryExp> follows;

        public BinaryExp(UnaryExp first, ArrayList<WordType> op, ArrayList<UnaryExp> follows) {
            this.first = first;
            this.ops = op;
            this.follows = follows;
        }
    }

    public static class LorExp implements Exp {
        public LandExp first;
        public ArrayList<WordType> ops;
        public ArrayList<LandExp> follows;

        public LorExp(LandExp first, ArrayList<WordType> ops, ArrayList<LandExp> follows) {
            this.first = first;
            this.ops = ops;
            this.follows = follows;
        }
    }

    public static class LandExp implements Exp {
        public BinaryExp first;
        public ArrayList<WordType> ops;
        public ArrayList<BinaryExp> follows;

        public LandExp(BinaryExp first, ArrayList<WordType> ops, ArrayList<BinaryExp> follows) {
            this.first = first;
            this.ops = ops;
            this.follows = follows;
        }
    }

    public static class Lval implements PrimaryExp {
        public Word ident;
        public boolean ifArray;
        public ArrayList<Exp> dimLen;

        public Lval(Word ident, boolean ifArray, ArrayList<Exp> dimLen) {
            this.ident = ident;
            this.ifArray = ifArray;
            this.dimLen = dimLen;
        }
    }

    public static class Number implements PrimaryExp {
        public Word number;
        public boolean isIntConst;
        public boolean isFloatConst;
        public int intConstVal;
        public float floatConstVal;

        public Number(Word number) {
            this.number = number;

            if (number.isIntConst()) {
                this.isIntConst = true;
                this.intConstVal =
                        number.getType() == WordType.DEC_INT ? Integer.parseInt(number.getContent()) :
                                number.getType() == WordType.OCT_INT ? Integer.parseInt(number.getContent().substring(1), 8) :
                                        number.getType() == WordType.HEX_INT ? Integer.parseInt(number.getContent().substring(2), 16) : 0;
                this.floatConstVal = (float) this.intConstVal;
            } else if (number.isFloatConst()) {
                this.isFloatConst = true;
                this.floatConstVal = Float.parseFloat(number.getContent());
                this.intConstVal = (int) this.floatConstVal;
            }
        }
    }

    public static class FuncRParam {
        public boolean ifExp;
        public Exp exp;
        public String str;

        public FuncRParam(boolean ifExp, Exp exp, String str) {
            this.ifExp = ifExp;
            this.exp = exp;
            this.str = str;
        }
    }

    public static class FuncCall implements PrimaryExp {
        public Word ident;
        public ArrayList<FuncRParam> funcRParams;

        public FuncCall(Word ident, ArrayList<FuncRParam> funcRParams) {
            this.ident = ident;
            this.funcRParams = funcRParams;
        }
    }

}
