package frontend.visitor;

import frontend.ast.Node;
import frontend.ast.WordType;
import frontend.sym.Calculate;
import frontend.sym.InitSym;
import frontend.sym.SymTable;
import frontend.sym.Symbol;
import mir.*;
import mir.type.DataType;
import mir.type.SymType;

import java.util.ArrayList;
import java.util.Stack;

import static frontend.sym.Calculate.calBinaryExp;
import static mir.Constant.FloatConst.CONST_FLOAT_0;
import static mir.Constant.IntConst.CONST_INT_0;
import static mir.type.SymType.BasicType.*;

public class MyVisitor {
    public static SymTable curSymTable = new SymTable();
    public static BasicBlock curBasicBlock = new BasicBlock();
    public static Func curFunc = null;
    private static Stack<BasicBlock> loopFollows = new Stack<>();   //存储循环后面的第一个基本块
    private static Stack<BasicBlock> loopEntries = new Stack<>();   //存储循环入口
    private static boolean curIsGlobal = true;
    private static boolean curIsCond = false;
    private MyModule myModule = new MyModule();
    private StringBuilder code = new StringBuilder();


    public MyVisitor() {
    }

    public static Value transDataType(Value a1, SymType.BasicType targetType) {
        if (((SymType.BasicType) a1.getType()).dataType == DataType.I32) {
            switch (targetType.dataType) {
                case I32:
                    return a1;
                case FLOAT:
                    return new Instr.SitofpInstr(a1, curBasicBlock);
                case BOOL:
                    return new Instr.IcmpInstr(curBasicBlock, Instr.IcmpInstr.IcmpOp.NE, a1, CONST_INT_0);
                case CHAR:
                    return new Instr.TruncInstr(a1, curBasicBlock);
            }
        } else if (((SymType.BasicType) a1.getType()).dataType == DataType.FLOAT) {
            switch (targetType.dataType) {
                case I32:
                    return new Instr.FptosiInstr(a1, curBasicBlock);
                case FLOAT:
                    return a1;
                case BOOL:
                    return new Instr.FcmpInstr(curBasicBlock, Instr.FcmpInstr.FcmpOp.ONE, a1, CONST_FLOAT_0);
                case CHAR:
                    return new Instr.TruncInstr(new Instr.FptosiInstr(a1, curBasicBlock), curBasicBlock);
            }
        } else if (((SymType.BasicType) a1.getType()).dataType == DataType.BOOL) {
            switch (targetType.dataType) {
                case I32:
                    return new Instr.ZextInstr(a1, curBasicBlock);
                case FLOAT:
                    return new Instr.SitofpInstr(new Instr.ZextInstr(a1, curBasicBlock), curBasicBlock);
                case BOOL:
                    return a1;
                case CHAR:
                    return new Instr.ZextInstr(a1, curBasicBlock, Basic_Char);
            }
        } else if (((SymType.BasicType) a1.getType()).dataType == DataType.CHAR) {
            switch (targetType.dataType) {
                case I32:
                    return new Instr.ZextInstr(a1, curBasicBlock);
                case FLOAT:
                    return new Instr.SitofpInstr(new Instr.ZextInstr(a1, curBasicBlock), curBasicBlock);
                case BOOL:
                    return new Instr.TruncInstr(a1, curBasicBlock);
                case CHAR:
                    return a1;
            }
        }
        return null;
    }

    public String getCode() {
        return code.toString();
    }

    public void visitRoot(Node root) throws Exception {
        for (Node.CompUnit compUnit : root.compUnits) {
            visitCompUnit(compUnit);
        }
    }

    public void visitCompUnit(Node.CompUnit compUnit) throws Exception {
        if (compUnit instanceof Node.FuncDef) {
            visitFuncDef((Node.FuncDef) compUnit);
        } else if (compUnit instanceof Node.Decl) {
            visitDecl((Node.Decl) compUnit);
        }
    }

    public SymType transWTtoST(WordType wordType) {
        switch (wordType) {
            case INT:
                return Basic_INT;
            case FLOAT:
                return Basic_FLOAT;
            case VOID:
                return new SymType.VoidType();
        }
        return null;
    }

    public void visitFuncDef(Node.FuncDef funcDef) throws Exception {
        curSymTable = new SymTable(curSymTable);

        BasicBlock entry = new BasicBlock();
        curBasicBlock = entry;

        curIsGlobal = false;

        String name = funcDef.ident.getContent();
        SymType retType = transWTtoST(funcDef.funcType);
        ArrayList<Func.Param> params = new ArrayList<>();

        for (Node.FuncFParam funcFParam : funcDef.funcFParams) {
            SymType paramType = visitFuncFParam(funcFParam);
            Func.Param param = new Func.Param(paramType);
            Value pointer = new Instr.AllocaInstr(paramType, curBasicBlock);
            new Instr.StoreInstr(param, pointer, curBasicBlock);
            curSymTable.addSymbol(new Symbol(funcFParam.ident.getContent(),
                    false, paramType, null, pointer));
            params.add(param);
        }
        Func func = new Func(name, false, retType, params);
        func.setFuncBody(entry);
        myModule.funcs.insertAtTail(func);
        entry.setBelongFunc(func, true);
        curFunc = func;
        visitBlock(funcDef.funcBody, false);
        if (!curBasicBlock.hasTerminator()) {
            switch (funcDef.funcType) {
                case VOID:
                    new Instr.RetInstr(curBasicBlock);
                    break;
                case INT:
                    new Instr.RetInstr(CONST_INT_0, curBasicBlock);
                    break;
                case FLOAT:
                    new Instr.RetInstr(CONST_FLOAT_0, curBasicBlock);
            }
        }
        curSymTable = curSymTable.getParent();
        curFunc = null;
        curBasicBlock = null;
        curIsGlobal = true;
    }

    public SymType visitFuncFParam(Node.FuncFParam funcFParam) throws Exception {
        if (funcFParam.ifArray) {
            ArrayList<Integer> dimLens = new ArrayList<>();
            for (Node.Exp len : funcFParam.dimLen) {
                int length = new Calculate(curSymTable).calIntExp(len);
                dimLens.add(length);
            }
            SymType type = transWTtoST(funcFParam.bType);
            for (int i = dimLens.size() - 1; i >= 0; i--) {
                int length = dimLens.get(i);
                type = new SymType.ArrayType(length, type);
            }
            return new SymType.PointerType(type);
        } else {
            return transWTtoST(funcFParam.bType);
        }
    }

    public void visitBlock(Node.Block block, boolean deepInBlock) throws Exception {
        if (deepInBlock) {
            curSymTable = new SymTable(curSymTable);
        }

        for (Node.BlockItem blockItem : block.blockItems) {
            visitBlockItem(blockItem);
        }

        if (deepInBlock) {
            curSymTable = curSymTable.getParent();
        }
    }

    public void visitBlockItem(Node.BlockItem blockItem) throws Exception {
        if (blockItem instanceof Node.Decl) {
            visitDecl((Node.Decl) blockItem);
        } else if (blockItem instanceof Node.Stmt) {
            visitStmt((Node.Stmt) blockItem);
        }
    }

    public void visitDecl(Node.Decl decl) throws Exception {
        for (Node.Def def : decl.defs) {
            visitDef(def, decl.ifConst, decl.bType);
        }
    }

    public void visitDef(Node.Def def, boolean ifConst, WordType type) throws Exception {
        boolean ifCanBeCaled = ifConst || curIsGlobal;

        String ident = def.ident.getContent();
        SymType defType = transWTtoST(type);
        ArrayList<Integer> dimLens = new ArrayList<>();
        //System.out.println(ident);

        for (Node.Exp len : def.indexes) {
            dimLens.add(new Calculate(curSymTable).calIntExp(len));
        }
        for (int i = dimLens.size() - 1; i >= 0; i--) {
            defType = new SymType.ArrayType(dimLens.get(i), defType);
        }

        Node.InitVal defInit = def.initVal;
        InitSym initSym = null;

        //如果有初值
        if (defInit != null) {
            if (defType instanceof SymType.BasicType) {
                if (ifCanBeCaled) {
                    initSym = visitInitVal((Node.Exp) defInit, (BasicType) defType);
                } else {
                    initSym = new InitSym.InitExp(defType, visitExp((Node.Exp) defInit));
                }
            } else if (defType instanceof SymType.ArrayType) {
                if (((Node.InitArray) defInit).initVals.size() == 0) {
                    initSym = new InitSym.InitZero(defType);
                } else {
                    initSym = visitInitArray((Node.InitArray) defInit, (SymType.ArrayType) defType, ifConst, ifCanBeCaled);
                }
            }
        }

        //如果是全局变量且无初值，则初始化为0
        if (defInit == null && curIsGlobal) {
            if (defType instanceof SymType.BasicType) {
                if (defType.isIntType()) {
                    initSym = new InitSym.InitValue(defType, CONST_INT_0);
                } else if (defType.isFloatType()) {
                    initSym = new InitSym.InitValue(defType, CONST_FLOAT_0);
                }
            } else if (defType instanceof SymType.ArrayType) {
                initSym = new InitSym.InitZero(defType);
            }
        }

        Value address;
        if (!curIsGlobal) {
            address = new Instr.AllocaInstr(defType, curBasicBlock);
            if (initSym != null) {
                if (initSym instanceof InitSym.InitValue) {
                    Value value = transDataType(((InitSym.InitValue) initSym).getValue(), (BasicType) defType);
                    new Instr.StoreInstr(value, address, curBasicBlock);
                } else if (initSym instanceof InitSym.InitExp) {
                    Value value = transDataType(((InitSym.InitExp) initSym).getExp(), (BasicType) defType);
                    new Instr.StoreInstr(value, address, curBasicBlock);
                } else if (initSym instanceof InitSym.InitZero) {
                    ForDefInit.ForValDef.forInitZero(address, defType);
                } else {
                    ForDefInit.ForArrayDef.forInitLocalArray(initSym, address, defType);
                }
            }

        } else {
            address = new GlobalValue.DefGlobalVal(defType, def, initSym, ifConst);
        }
        Symbol symbol = new Symbol(ident, ifConst, defType, initSym, address);
        curSymTable.addSymbol(symbol);
        if (curIsGlobal) {
            GlobalValue.DefGlobalVal defGlobalVal = (GlobalValue.DefGlobalVal) address;
            myModule.globalValues.insertAtTail(defGlobalVal);
        }
    }

    public InitSym.InitValue visitInitVal(Node.Exp exp, BasicType basicType) throws Exception {
        Object ret = new Calculate(curSymTable).calExp(exp);
        if (ret instanceof Integer) {
            switch (basicType.dataType) {
                case I32:
                    return new InitSym.InitValue(Basic_INT, new Constant.IntConst((Integer) ret));
                case FLOAT:
                    return new InitSym.InitValue(Basic_FLOAT, new Constant.FloatConst((float) ((int) ret)));
                default:
                    return null;
            }
        } else if (ret instanceof Float) {
            switch (basicType.dataType) {
                case I32:
                    return new InitSym.InitValue(Basic_INT, new Constant.IntConst((int) ((float) ret)));
                case FLOAT:
                    return new InitSym.InitValue(Basic_FLOAT, new Constant.FloatConst((float) ((float) ret)));
                default:
                    return null;
            }
        }
        return null;
    }

    public Value visitExp(Node.Exp exp) {
        if (exp instanceof Node.UnaryExp) {
            return visitUnaryExp((Node.UnaryExp) exp);
        } else if (exp instanceof Node.BinaryExp) {
            return visitBinaryExp((Node.BinaryExp) exp);
        }
        return null;
    }

    public Value visitUnaryExp(Node.UnaryExp unaryExp) {
        Value primaryExp = visitPrimaryExp(unaryExp.primaryExp);

        ArrayList<WordType> unaryOps = unaryExp.unaryOp;
        for (int i = unaryOps.size() - 1; i >= 0; i--) {
            WordType op = unaryOps.get(i);
            if (op == WordType.NOT) {
                if (primaryExp.getType().isIntType()) {
                    primaryExp = new Instr.IcmpInstr(curBasicBlock,
                            Instr.IcmpInstr.IcmpOp.EQ, primaryExp, CONST_INT_0);
                } else if (primaryExp.getType().isFloatType()) {
                    primaryExp = new Instr.FcmpInstr(curBasicBlock,
                            Instr.FcmpInstr.FcmpOp.OEQ, primaryExp, CONST_FLOAT_0);
                } else if (primaryExp.getType().isBoolType()) {
                    primaryExp = transDataType(primaryExp, Basic_INT);
                    primaryExp = new Instr.IcmpInstr(curBasicBlock,
                            Instr.IcmpInstr.IcmpOp.EQ, primaryExp, CONST_INT_0);
                }
            } else if (op == WordType.SUB) {
                if (primaryExp.getType().isIntType()) {
                    primaryExp = new Instr.AluInstr(getBasic_INT(), curBasicBlock,
                            Instr.AluInstr.AluOp.SUB, CONST_INT_0, primaryExp);
                } else if (primaryExp.getType().isFloatType()) {
                    primaryExp = new Instr.AluInstr(getBasic_FLOAT(), curBasicBlock,
                            Instr.AluInstr.AluOp.FSUB, CONST_FLOAT_0, primaryExp);
                } else if (primaryExp.getType().isBoolType()) {
                    primaryExp = new Instr.AluInstr(getBasic_INT(), curBasicBlock,
                            Instr.AluInstr.AluOp.SUB, CONST_INT_0, transDataType(primaryExp, Basic_INT));
                }
            }
        }

        return primaryExp;
    }

    public Value visitPrimaryExp(Node.PrimaryExp primaryExp) {
        if (primaryExp instanceof Node.Exp) {
            return visitExp((Node.Exp) primaryExp);
        } else if (primaryExp instanceof Node.Lval) {
            return visitLval((Node.Lval) primaryExp, false);
        } else if (primaryExp instanceof Node.Number) {
            return visitNumber((Node.Number) primaryExp);
        } else if (primaryExp instanceof Node.FuncCall) {
            return visitFuncCall((Node.FuncCall) primaryExp);
        }
        return null;
    }

    public Value visitLval(Node.Lval lval, boolean retPointer) {
        String ident = lval.ident.getContent();
        Symbol symbol = curSymTable.getSymbol(ident, true);
        if (symbol.getIsConst() && lval.dimLen.isEmpty()) {
            return ((InitSym.InitValue) symbol.getInitValue()).getValue();
        }
        ArrayList<Value> indexes = new ArrayList<>();
        indexes.add(CONST_INT_0);
        Value pointer = symbol.getPointer();
        SymType pointToType = ((PointerType) pointer.getType()).getPointToType();

        for (Node.Exp exp : lval.dimLen) {
            Value index = transDataType(visitExp(exp), Basic_INT);
            if (pointToType instanceof PointerType) {
                Instr instr = new Instr.LoadInstr(pointer, curBasicBlock);
                pointToType = ((PointerType) pointToType).getPointToType();
                pointer = instr;
                indexes.clear();
                indexes.add(index);
            } else if (pointToType instanceof ArrayType) {
                pointToType = ((ArrayType) pointToType).getBaseType();
                indexes.add(index);
            }
        }

        if (!lval.dimLen.isEmpty()) {
            pointer = new Instr.GepInstr(pointToType, pointer, indexes, curBasicBlock);
        }
        if (retPointer) {
            return pointer;
        }
        ArrayList<Value> dimLens = new ArrayList<>();
        dimLens.add(CONST_INT_0);
        dimLens.add(CONST_INT_0);
        if (pointToType instanceof ArrayType) {
            //System.out.println(pointer);
            return new Instr.GepInstr(((ArrayType) pointToType).getBaseType(), pointer, dimLens, curBasicBlock);
        }

        return new Instr.LoadInstr(pointer, curBasicBlock);
    }

    public Value visitNumber(Node.Number number) {
        if (number.isIntConst) {
            return new Constant.IntConst(number.intConstVal);
        } else if (number.isFloatConst) {
            return new Constant.FloatConst(number.floatConstVal);
        }
        return null;
    }

    public Value visitFuncCall(Node.FuncCall funcCall) {
        String ident = funcCall.ident.getContent();
        Func func = myModule.getFuncByName(ident);

        ArrayList<Value> params = new ArrayList<>();
        for (int i = 0; i < funcCall.funcRParams.size(); i++) {
            if (funcCall.funcRParams.get(i).ifExp) {
                Node.Exp exp = funcCall.funcRParams.get(i).exp;
                Value expVal = visitExp(exp);

                if (i < func.getParams().size() && func.getParams().get(i).getType() instanceof BasicType) {
                    expVal = transDataType(expVal, (BasicType) func.getParams().get(i).getType());
                }
                if (i >= func.getParams().size()) {
                    String str = funcCall.funcRParams.get(0).str;       //putf
                    int cnt = 0;
                    for (int j = 0; j < str.length(); j++) {
                        if (str.charAt(j) == '%') {
                            if (j + 1 < str.length() && (str.charAt(j + 1) == 'd' ||
                                    str.charAt(j + 1) == 'c' || str.charAt(j + 1) == 'f')) {
                                cnt++;
                                if (i == cnt) {
                                    switch (str.charAt(j + 1)) {
                                        case 'd':
                                            expVal = transDataType(expVal, Basic_INT);
                                            break;
                                        case 'c':
                                            expVal = transDataType(expVal, Basic_Char);
                                            break;
                                        case 'f':
                                            expVal = transDataType(expVal, Basic_FLOAT);
                                        default:
                                    }
                                }
                            }
                        }
                    }
                }
                params.add(expVal);
            } else {
                String str = funcCall.funcRParams.get(i).str;
                str = str.substring(1, str.length() - 1);

                GlobalValue.StrGlobalVal strGlobalVal = new GlobalValue.StrGlobalVal(str);
                myModule.globalValues.insertAtTail(strGlobalVal);
                ArrayList<Value> dimlens = new ArrayList<>();
                dimlens.add(CONST_INT_0);
                dimlens.add(CONST_INT_0);
                int len = str.length() + 1;
                ArrayType arrayType = new ArrayType(len, Basic_Char);
                PointerType pointerType = new PointerType(arrayType);
                Value poniterMod = new Value(pointerType);
                poniterMod.setName(strGlobalVal.getName());
                Value pointer = new Instr.GepInstr(Basic_Char, poniterMod, dimlens, curBasicBlock, strGlobalVal.getName());
                params.add(pointer);
            }
        }
        return new Instr.CallInstr(func, params, curBasicBlock);
    }

    public Value visitBinaryExp(Node.BinaryExp binaryExp) {
        Value first = visitUnaryExp(binaryExp.first);
        ArrayList<Value> values = new ArrayList<>();
        for (Node.UnaryExp unaryExp : binaryExp.follows) {
            values.add(visitUnaryExp(unaryExp));
        }
        ArrayList<WordType> ops = binaryExp.ops;
        return calBinaryExp(first, values, ops);
    }

    public InitSym.InitArray visitInitArray(Node.InitArray initArray, SymType.ArrayType arrayType,
                                            boolean ifConst, boolean ifCanBeCaled) throws Exception {
        SymType.BasicType eleType = arrayType.getEleType();
        SymType baseType = arrayType.getBaseType();
        int size = arrayType.getSize();
        int decCnt = 0;
        InitSym.InitArray array = new InitSym.InitArray(arrayType);
        while (decCnt < size && initArray.hasInited()) {
            Node.InitVal initVal = initArray.getInitEle();

            if (baseType instanceof SymType.ArrayType) {
                InitSym initSym;
                if (initVal instanceof Node.InitArray) {
                    initSym = visitInitArray((Node.InitArray) initVal, (SymType.ArrayType) baseType, ifConst, ifCanBeCaled);
                    initArray.index++;
                } else {
                    initSym = visitInitArray(initArray, (SymType.ArrayType) baseType, ifConst, ifCanBeCaled);
                }
                array.addEle(initSym);
            } else if (baseType instanceof SymType.BasicType) {
                Node.Exp initExp;
                if (initVal instanceof Node.Exp) {
                    initExp = (Node.Exp) initVal;
                } else {
                    initExp = (Node.Exp) ((Node.InitArray) initVal).initVals.get(0);
                }
                if (ifCanBeCaled) {
                    array.addEle(visitInitVal(initExp, eleType));
                } else {
                    array.addEle(new InitSym.InitExp(eleType, visitExp(initExp)));
                }
                initArray.index++;
            }
            decCnt++;
        }

        while (decCnt < arrayType.getSize()) {
            if (baseType instanceof SymType.BasicType) {
                if (baseType.isIntType()) {
                    array.addEle(new InitSym.InitValue(Basic_INT, CONST_INT_0));
                } else if (baseType.isFloatType()) {
                    array.addEle(new InitSym.InitValue(SymType.BasicType.Basic_FLOAT, CONST_FLOAT_0));
                }
            } else {
                array.addEle(new InitSym.InitZero(baseType));
            }
            decCnt++;
        }

        return array;
    }

    public void visitStmt(Node.Stmt stmt) throws Exception {
        if (stmt instanceof Node.ExpStmt) {
            visitExpStmt((Node.ExpStmt) stmt);
        } else if (stmt instanceof Node.Assign) {
            visitAssign((Node.Assign) stmt);
        } else if (stmt instanceof Node.Block) {
            visitBlock((Node.Block) stmt, true);
        } else if (stmt instanceof Node.IfStmt) {
            visitIfStmt((Node.IfStmt) stmt);
        } else if (stmt instanceof Node.WhileStmt) {
            visitWhileStmt((Node.WhileStmt) stmt);
        } else if (stmt instanceof Node.BreakStmt) {
            visitBreakStmt();
        } else if (stmt instanceof Node.ContinueStmt) {
            visitContinueStmt();
        } else if (stmt instanceof Node.ReturnStmt) {
            visitReturn((Node.ReturnStmt) stmt);
        }
    }

    public void visitExpStmt(Node.ExpStmt expStmt) {
        Node.Exp exp = expStmt.expCanNull;
        if (exp != null) {
            visitExp(exp);
        }
    }

    public void visitAssign(Node.Assign assign) {
        Value left = visitLval(assign.lval, true);
        Value right = visitExp(assign.exp);

        SymType pointToType = ((PointerType) left.getType()).getPointToType();
        right = transDataType(right, (BasicType) pointToType);
        new Instr.StoreInstr(right, left, curBasicBlock);
    }

    public void visitIfStmt(Node.IfStmt ifStmt) throws Exception {
        Node.Stmt thenAct = ifStmt.thenAct;
        Node.Stmt elseAct = ifStmt.elseAct;
        BasicBlock nextBlock = new BasicBlock(curFunc);
        BasicBlock thenBlock = new BasicBlock(curFunc);
        BasicBlock elseBlock = null;
        if (elseAct != null) {
            elseBlock = new BasicBlock(curFunc);
            curIsCond = true;
            Value cond = visitLorExp(ifStmt.cond, thenBlock, elseBlock);
            new Instr.BranchInstr(cond, thenBlock, elseBlock, curBasicBlock);
            curBasicBlock = thenBlock;
            curIsCond = false;
            visitStmt(thenAct);
            new Instr.JumpInstr(nextBlock, curBasicBlock);
            curBasicBlock = elseBlock;
            visitStmt(elseAct);
        } else {
            curIsCond = true;
            Value cond = visitLorExp(ifStmt.cond, thenBlock, nextBlock);
            new Instr.BranchInstr(cond, thenBlock, nextBlock, curBasicBlock);
            curBasicBlock = thenBlock;
            curIsCond = false;
            visitStmt(thenAct);
        }
        new Instr.JumpInstr(nextBlock, curBasicBlock);
        curBasicBlock = nextBlock;
    }

    public Value visitLorExp(Node.Exp exp, BasicBlock tBlock, BasicBlock fBlock) {
        if (exp instanceof Node.BinaryExp) {
            return transDataType(visitBinaryExp((Node.BinaryExp) exp), Basic_BOOL);
        }
        BasicBlock nextBlock = fBlock;
        BasicBlock latestBlock = fBlock;
        boolean isNotLast = false;

        Node.LorExp lorExp = (Node.LorExp) exp;
        if (!lorExp.follows.isEmpty()) {
            nextBlock = new BasicBlock(curFunc);
            isNotLast = true;
        }
        Value first = visitLandExp(lorExp.first, nextBlock);

        for (int i = 0; i < lorExp.follows.size(); i++) {
            Node.LandExp second = lorExp.follows.get(i);

            if (isNotLast) {
                isNotLast = false;
            } else {
                nextBlock = new BasicBlock(curFunc);
            }
            new Instr.BranchInstr(first, tBlock, nextBlock, curBasicBlock);
            curBasicBlock = nextBlock;

            if (i < lorExp.ops.size() - 1) {
                isNotLast = true;
                fBlock = new BasicBlock(curFunc);
                nextBlock = fBlock;
            } else {
                fBlock = latestBlock;
            }
            first = visitLandExp(second, fBlock);
        }
        return first;
    }

    public Value visitLandExp(Node.Exp exp, BasicBlock fBlock) {
        if (exp instanceof Node.BinaryExp) {
            return transDataType(visitBinaryExp((Node.BinaryExp) exp), Basic_BOOL);
        }

        Node.LandExp landExp = (Node.LandExp) exp;
        Value first = transDataType(visitBinaryExp(landExp.first), Basic_BOOL);
        for (int i = 0; i < landExp.follows.size(); i++) {
            Node.BinaryExp second = landExp.follows.get(i);
            BasicBlock nextBlock = new BasicBlock(curFunc);
            new Instr.BranchInstr(first, nextBlock, fBlock, curBasicBlock);
            curBasicBlock = nextBlock;
            first = transDataType(visitBinaryExp(second), Basic_BOOL);
        }
        return first;
    }

    public void visitWhileStmt(Node.WhileStmt whileStmt) throws Exception {
        BasicBlock condBlock = new BasicBlock(curFunc);
        new Instr.JumpInstr(condBlock, curBasicBlock);
        curBasicBlock = condBlock;
        curIsCond = true;

        BasicBlock body = new BasicBlock(curFunc);
        BasicBlock next = new BasicBlock(curFunc);
        Value cond = visitLorExp(whileStmt.cond, body, next);

        new Instr.BranchInstr(cond, body, next, curBasicBlock);
        curBasicBlock = body;
        loopEntries.push(condBlock);
        loopFollows.push(next);
        curIsCond = false;

        visitStmt(whileStmt.whileBody);
        loopEntries.pop();
        loopFollows.pop();
        new Instr.JumpInstr(condBlock, curBasicBlock);
        curBasicBlock = next;
    }

    public void visitBreakStmt() {
        new Instr.JumpInstr(loopFollows.peek(), curBasicBlock);
    }

    public void visitContinueStmt() {
        new Instr.JumpInstr(loopEntries.peek(), curBasicBlock);
    }

    public void visitReturn(Node.ReturnStmt returnStmt) {
        if (returnStmt.returnVal == null) {
            new Instr.RetInstr(curBasicBlock);
        } else {
            new Instr.RetInstr(transDataType(visitExp(returnStmt.returnVal),
                    (BasicType) curBasicBlock.getBelongFunc().getRetType()), curBasicBlock);
        }
    }

    public MyModule getMyModel() {
        return myModule;
    }
}
