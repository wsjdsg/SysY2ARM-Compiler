//递归遍历构建AST的具体操作

package frontend.ast;

import frontend.lexerparser.SysyBaseListener;
import frontend.lexerparser.SysyParser;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;

public class MySysyListener extends SysyBaseListener {
    private Node root;

    public Node getRoot() {
        return root;
    }

    public void enterParse(SysyParser.ParseContext ctx) {
        ArrayList<Node.CompUnit> compUnits = new ArrayList<>();
        for (SysyParser.CompunitContext unit : ctx.compunit()) {
            enterCompunit(unit, compUnits);
        }
        root = new Node(compUnits);
    }

    public void enterCompunit(SysyParser.CompunitContext ctx, ArrayList<Node.CompUnit> compUnits) {
        SysyParser.DeclContext declContext = ctx.decl();
        SysyParser.FuncdefContext funcdefContext = ctx.funcdef();
        if (declContext != null) {
            Node.Decl decl = new Node.Decl(false, null, null);
            enterDecl(declContext, decl);
            compUnits.add(decl);
        } else if (funcdefContext != null) {
            Node.FuncDef funcDef = new Node.FuncDef(null, null, null, null);
            enterFuncdef(funcdefContext, funcDef);
            compUnits.add(funcDef);
        }
    }

    public void enterDecl(SysyParser.DeclContext ctx, Node.Decl decl) {
        decl.ifConst = ctx.CONST() != null;
        WordType bType = null;
        if (ctx.btype().INT() != null) {
            bType = WordType.INT;
        } else if (ctx.btype().FLOAT() != null) {
            bType = WordType.FLOAT;
        }
        decl.bType = bType;
        ArrayList<Node.Def> defs = new ArrayList<>();
        for (SysyParser.DefContext defContext : ctx.def()) {
            enterDef(defContext, defs, bType);
        }
        decl.defs = defs;
    }

    public void enterDef(SysyParser.DefContext ctx, ArrayList<Node.Def> defs, WordType bType) {
        Word ident = new Word(ctx.IDENT().getText(), bType);
        ArrayList<Node.Exp> indexes = new ArrayList<>();
        if (ctx.exp() != null) {
            for (SysyParser.ExpContext expContext : ctx.exp()) {
                enterExp(expContext, indexes);
            }
        }
        Node.InitVal initval = null;
        if (ctx.initval() != null) {
            initval = enterInitvalForDef(ctx.initval());
        }
        Node.Def def = new Node.Def(ident, indexes, initval);
        defs.add(def);
    }

    public void enterExp(SysyParser.ExpContext ctx, ArrayList<Node.Exp> exps) {
        if (ctx.binaryexp() != null) {
            Node.BinaryExp binaryExp = new Node.BinaryExp(null, null, null);
            enterBinaryexp(ctx.binaryexp(), binaryExp);
            exps.add(binaryExp);
        } else if (ctx.unaryexp() != null) {
            Node.UnaryExp unaryExp = new Node.UnaryExp(null, null);
            enterUnaryexp(ctx.unaryexp(), unaryExp);
            exps.add(unaryExp);
        }
    }

    public void enterBinaryexp(SysyParser.BinaryexpContext ctx, Node.BinaryExp binaryExp) {
        int flag = 0;
        Node.UnaryExp first = null;
        ArrayList<Node.UnaryExp> follows = new ArrayList<>();
        for (SysyParser.UnaryexpContext unaryexpContext : ctx.unaryexp()) {
            if (flag == 0) {
                first = new Node.UnaryExp(null, null);
                enterUnaryexp(unaryexpContext, first);
                flag = 1;
            } else {
                Node.UnaryExp unaryExp = new Node.UnaryExp(null, null);
                enterUnaryexp(unaryexpContext, unaryExp);
                follows.add(unaryExp);
            }
        }

        ArrayList<WordType> ops = new ArrayList<>();
        if (ctx.op() != null) {
            for (SysyParser.OpContext opContext : ctx.op()) {
                WordType op = enterOps(opContext);
                ops.add(op);
            }
        }
        binaryExp.follows = follows;
        binaryExp.first = first;
        binaryExp.ops = ops;
    }

    public WordType enterOps(SysyParser.OpContext ctx) {
        if (ctx.MUL() != null) {
            return WordType.MUL;
        } else if (ctx.DIV() != null) {
            return WordType.DIV;
        } else if (ctx.MOD() != null) {
            return WordType.MOD;
        } else if (ctx.ADD() != null) {
            return WordType.ADD;
        } else if (ctx.SUB() != null) {
            return WordType.SUB;
        } else if (ctx.LT() != null) {
            return WordType.LT;
        } else if (ctx.GT() != null) {
            return WordType.GT;
        } else if (ctx.LE() != null) {
            return WordType.LE;
        } else if (ctx.GE() != null) {
            return WordType.GE;
        } else if (ctx.EQ() != null) {
            return WordType.EQ;
        } else if (ctx.NEQ() != null) {
            return WordType.NEQ;
        }

        return null;
    }

    public void enterUnaryexp(SysyParser.UnaryexpContext ctx, Node.UnaryExp unaryExp) {
        ArrayList<WordType> unaryOps = new ArrayList<>();
        if (ctx.unaryop() != null) {
            for (SysyParser.UnaryopContext unaryopContext : ctx.unaryop()) {
                enterUnaryop(unaryopContext, unaryOps);
            }
        }

        Node.PrimaryExp primaryExp = enterPrimaryexpForFunc(ctx.primaryexp());
        unaryExp.unaryOp = unaryOps;
        unaryExp.primaryExp = primaryExp;
    }

    public void enterUnaryop(SysyParser.UnaryopContext ctx, ArrayList<WordType> unaryOps) {
        if (ctx.ADD() != null) {
            unaryOps.add(WordType.ADD);
        } else if (ctx.SUB() != null) {
            unaryOps.add(WordType.SUB);
        } else if (ctx.NOT() != null) {
            unaryOps.add(WordType.NOT);
        }
    }

    public Node.PrimaryExp enterPrimaryexpForFunc(SysyParser.PrimaryexpContext ctx) {
        if (ctx.exp() != null) {
            return enterExpForFunc(ctx.exp());
        } else if (ctx.funccall() != null) {
            return enterFunccallForFunc(ctx.funccall());
        } else if (ctx.lval() != null) {
            return enterLvalForFunc(ctx.lval());
        } else if (ctx.number() != null) {
            return enterNumberForFunc(ctx.number());
        }
        return null;
    }

    public Node.Exp enterExpForFunc(SysyParser.ExpContext ctx) {
        if (ctx.binaryexp() != null) {
            Node.BinaryExp binaryExp = new Node.BinaryExp(null, null, null);
            enterBinaryexp(ctx.binaryexp(), binaryExp);
            return binaryExp;
        } else if (ctx.unaryexp() != null) {
            Node.UnaryExp unaryExp = new Node.UnaryExp(null, null);
            enterUnaryexp(ctx.unaryexp(), unaryExp);
            return unaryExp;
        }
        return null;
    }

    public Node.FuncCall enterFunccallForFunc(SysyParser.FunccallContext ctx) {
        String content = ctx.IDENT().getText();
        Word ident = new Word(content, null);
        ArrayList<Node.FuncRParam> funcRParams = new ArrayList<>();
        if (ctx.funcrparams() != null) {
            enterFuncrparams(ctx.funcrparams(), funcRParams);
        }
        return new Node.FuncCall(ident, funcRParams);
    }

    public void enterFuncrparams(SysyParser.FuncrparamsContext ctx,
                                 ArrayList<Node.FuncRParam> funcRParams) {
        if (ctx.STR() != null) {
            for (TerminalNode s : ctx.STR()) {
                String str = s.getText();
                Node.FuncRParam funcRParam = new Node.FuncRParam(false, null, str);
                funcRParams.add(funcRParam);
            }
        }

        if (ctx.exp() != null) {
            for (SysyParser.ExpContext expContext : ctx.exp()) {
                Node.Exp exp = enterExpForFunc(expContext);
                Node.FuncRParam funcRParam = new Node.FuncRParam(true, exp, null);
                funcRParams.add(funcRParam);
            }
        }
    }

    public Node.Lval enterLvalForFunc(SysyParser.LvalContext ctx) {
        String content = ctx.IDENT().getText();
        Word ident = new Word(content, null);
        ArrayList<Node.Exp> dimLens = new ArrayList<>();
        if (ctx.exp() != null) {
            for (SysyParser.ExpContext expContext : ctx.exp()) {
                Node.Exp exp = enterExpForFunc(expContext);
                dimLens.add(exp);
            }
        }
        boolean ifArray = dimLens.size() > 0;
        return new Node.Lval(ident, ifArray, dimLens);
    }

    public Node.Number enterNumberForFunc(SysyParser.NumberContext ctx) {
        if (ctx.intconst() != null) {
            return enterIntconstForFunc(ctx.intconst());
        } else if (ctx.floatconst() != null) {
            return enterFloatconstForFunc(ctx.floatconst());
        }
        return null;
    }

    public Node.Number enterIntconstForFunc(SysyParser.IntconstContext ctx) {
        if (ctx.DEC_INT() != null) {
            String s = ctx.DEC_INT().getText();
            Word num = new Word(s, WordType.DEC_INT);
            return new Node.Number(num);
        } else if (ctx.HEX_INT() != null) {
            String s = ctx.HEX_INT().getText();
            Word num = new Word(s, WordType.HEX_INT);
            return new Node.Number(num);
        } else if (ctx.OCT_INT() != null) {
            String s = ctx.OCT_INT().getText();
            Word num = new Word(s, WordType.OCT_INT);
            return new Node.Number(num);
        }
        return null;
    }

    public Node.Number enterFloatconstForFunc(SysyParser.FloatconstContext ctx) {
        if (ctx.DEC_FLOAT() != null) {
            String s = ctx.DEC_FLOAT().getText();
            Word num = new Word(s, WordType.DEC_FLOAT);
            return new Node.Number(num);
        } else if (ctx.HEX_FLOAT() != null) {
            String s = ctx.HEX_FLOAT().getText();
            Word num = new Word(s, WordType.HEX_FLOAT);
            return new Node.Number(num);
        }
        return null;
    }

    public Node.InitVal enterInitvalForDef(SysyParser.InitvalContext ctx) {
        if (ctx.exp() != null) {
            return enterExpForFunc(ctx.exp());
        } else if (ctx.initarray() != null) {
            return enterInitarrayForDef(ctx.initarray());
        }
        return null;
    }

    public Node.InitArray enterInitarrayForDef(SysyParser.InitarrayContext ctx) {
        ArrayList<Node.InitVal> initVals = new ArrayList<>();
        if (ctx.initval() != null) {
            for (SysyParser.InitvalContext initvalContext : ctx.initval()) {
                Node.InitVal initVal = enterInitvalForDef(initvalContext);
                initVals.add(initVal);
            }
        }
        return new Node.InitArray(initVals);
    }

    public void enterFuncdef(SysyParser.FuncdefContext ctx, Node.FuncDef funcDef) {
        WordType funcType = enterFunctypeForFunc(ctx.functype());
        Word ident = new Word(ctx.IDENT().getText(), null);
        ArrayList<Node.FuncFParam> funcFParams = new ArrayList<>();
        if (ctx.funcfparams() != null) {
            enterFuncfparams(ctx.funcfparams(), funcFParams);
        }
        Node.Block block = enterBlockForFunc(ctx.block());
        funcDef.funcType = funcType;
        funcDef.ident = ident;
        funcDef.funcFParams = funcFParams;
        funcDef.funcBody = block;
    }

    public WordType enterFunctypeForFunc(SysyParser.FunctypeContext ctx) {
        if (ctx.INT() != null) {
            return WordType.INT;
        } else if (ctx.FLOAT() != null) {
            return WordType.FLOAT;
        } else if (ctx.VOID() != null) {
            return WordType.VOID;
        }
        return null;
    }

    public void enterFuncfparams(SysyParser.FuncfparamsContext ctx,
                                 ArrayList<Node.FuncFParam> funcFParams) {
        for (SysyParser.FuncfparamContext funcfparamContext : ctx.funcfparam()) {
            enterFuncfparam(funcfparamContext, funcFParams);
        }
    }

    public void enterFuncfparam(SysyParser.FuncfparamContext ctx,
                                ArrayList<Node.FuncFParam> funcFParams) {
        WordType bType = enterBtypeForFunc(ctx.btype());
        Word ident = new Word(ctx.IDENT().getText(), bType);
        ArrayList<Node.Exp> indexes = new ArrayList<>();
        if (ctx.exp() != null) {
            for (SysyParser.ExpContext expContext : ctx.exp()) {
                Node.Exp exp = enterExpForFunc(expContext);
                indexes.add(exp);
            }
        }
        boolean ifArray = !ctx.LBRACK().isEmpty();
        Node.FuncFParam funcFParam = new Node.FuncFParam(bType, ident, ifArray, indexes);
        funcFParams.add(funcFParam);
    }

    public WordType enterBtypeForFunc(SysyParser.BtypeContext ctx) {
        if (ctx.INT() != null) {
            return WordType.INT;
        } else if (ctx.FLOAT() != null) {
            return WordType.FLOAT;
        }
        return null;
    }

    public Node.Block enterBlockForFunc(SysyParser.BlockContext ctx) {
        ArrayList<Node.BlockItem> blockItems = new ArrayList<>();
        if (ctx.blockitem() != null) {
            for (SysyParser.BlockitemContext blockitemContext : ctx.blockitem()) {
                Node.BlockItem blockItem = enterBlockitemForFunc(blockitemContext);
                blockItems.add(blockItem);
            }
        }
        return new Node.Block(blockItems);
    }

    public Node.BlockItem enterBlockitemForFunc(SysyParser.BlockitemContext ctx) {
        if (ctx.decl() != null) {
            Node.Decl decl = new Node.Decl(false, null, null);
            enterDecl(ctx.decl(), decl);
            return decl;
        } else if (ctx.stmt() != null) {
            return enterStmtForFunc(ctx.stmt());
        }
        return null;
    }

    public Node.Stmt enterStmtForFunc(SysyParser.StmtContext ctx) {
        if (ctx.assign() != null) {
            return enterAssignForStmt(ctx.assign());
        } else if (ctx.expstmt() != null) {
            return enterExpstmtForStmt(ctx.expstmt());
        } else if (ctx.block() != null) {
            return enterBlockForFunc(ctx.block());
        } else if (ctx.ifstmt() != null) {
            return enterIfstmtForStmt(ctx.ifstmt());
        } else if (ctx.whilestmt() != null) {
            return enterWhilestmtForStmt(ctx.whilestmt());
        } else if (ctx.breakstmt() != null) {
            return enterBreakstmtForStmt(ctx.breakstmt());
        } else if (ctx.continuestmt() != null) {
            return enterContinuestmtForStm(ctx.continuestmt());
        } else if (ctx.returnstmt() != null) {
            return enterReturnstmtForStmt(ctx.returnstmt());
        }

        return null;
    }

    public Node.Assign enterAssignForStmt(SysyParser.AssignContext ctx) {
        Node.Lval lval = enterLvalForFunc(ctx.lval());
        Node.Exp exp = enterExpForFunc(ctx.exp());
        return new Node.Assign(lval, exp);
    }

    public Node.ExpStmt enterExpstmtForStmt(SysyParser.ExpstmtContext ctx) {
        if(ctx.exp() != null) {
            Node.Exp exp = enterExpForFunc(ctx.exp());
            return new Node.ExpStmt(exp);
        }
        return new Node.ExpStmt(null);
    }

    public Node.LorExp enterLorExpForStmt(SysyParser.LorexpContext ctx) {
        ArrayList<Node.LandExp> landExps = new ArrayList<>();
        ArrayList<WordType> ops = new ArrayList<>();
        for(SysyParser.LandexpContext landexpContext : ctx.landexp()) {
            landExps.add(enterLandExpForStmt(landexpContext));
            ops.add(WordType.LOGIC_OR);
        }
        Node.LandExp first = landExps.remove(0);
        ops.remove(0);
        return new Node.LorExp(first, ops, landExps);
    }

    public Node.LandExp enterLandExpForStmt(SysyParser.LandexpContext ctx) {
        ArrayList<Node.BinaryExp> binaryExps = new ArrayList<>();
        ArrayList<WordType> ops = new ArrayList<>();
        for(SysyParser.BinaryexpContext binaryexpContext : ctx.binaryexp()) {
            Node.BinaryExp binaryExp = new Node.BinaryExp(null, null, null);
            enterBinaryexp(binaryexpContext, binaryExp);
            binaryExps.add(binaryExp);
            ops.add(WordType.LOGIC_AND);
        }
        Node.BinaryExp first = binaryExps.remove(0);
        ops.remove(0);
        return new Node.LandExp(first, ops, binaryExps);
    }

    public Node.IfStmt enterIfstmtForStmt(SysyParser.IfstmtContext ctx) {
        Node.LorExp lorExp = enterLorExpForStmt(ctx.cond().lorexp());
        Node.Stmt thenAct = enterStmtForFunc(ctx.stmt(0));
        Node.Stmt elseAct = null;
        if(ctx.stmt().size() > 1) {
            elseAct = enterStmtForFunc(ctx.stmt(1));
        }
        return new Node.IfStmt(lorExp, thenAct, elseAct);
    }

    public Node.WhileStmt enterWhilestmtForStmt(SysyParser.WhilestmtContext ctx) {
        Node.LorExp lorExp = enterLorExpForStmt(ctx.cond().lorexp());
        Node.Stmt stmt = enterStmtForFunc(ctx.stmt());
        return new Node.WhileStmt(lorExp, stmt);
    }

    public Node.BreakStmt enterBreakstmtForStmt(SysyParser.BreakstmtContext ctx) {
        return new Node.BreakStmt();
    }

    public Node.ContinueStmt enterContinuestmtForStm(SysyParser.ContinuestmtContext ctx) {
        return new Node.ContinueStmt();
    }

    public Node.ReturnStmt enterReturnstmtForStmt(SysyParser.ReturnstmtContext ctx) {
        if(ctx.exp() != null) {
            Node.Exp exp = enterExpForFunc(ctx.exp());
            return new Node.ReturnStmt(exp);
        }
        return new Node.ReturnStmt(null);
    }

}
