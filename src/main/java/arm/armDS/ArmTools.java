package arm.armDS;

import arm.armDS.ArmInstr.ArmCond;
import arm.arminstructions.*;
import arm.armoperands.*;
import frontend.sym.InitSym;
import frontend.visitor.ForDefInit;
import mir.*;
import mir.type.SymType;
import tools.MyList;
import tools.Pair;

import java.util.ArrayList;
import java.util.HashMap;

import static java.lang.Math.abs;


// 本类存放一些 ir 转 arm 的工具函数
public class ArmTools {
    public static int armInstrOff = 0;
    private static ArmFunction curArmFunction; // 保持为当前正在解析的函数 以对其进行相关维护
    //    private static ArmFunction curHandleFunction;
    // TODO 测试phi消除, 非SSA的效果
    // String: 各个指令对应变量名字 + 全局变量名字
    private HashMap<String, VirtualReg> irNameToVirMap = new HashMap<>(); // 存 name 和捏出来的  virtual  的对应关系
    private HashMap<Func, ArmFunction> irToArmFuncMap = new HashMap<>();
    private HashMap<ArmFunction, Func> armToIrFuncMap = new HashMap<>();
    private HashMap<BasicBlock, ArmBlock> basicToArmBlockMap = new HashMap<>();
    private HashMap<ArmBlock, BasicBlock> armToBasicBlockMap = new HashMap<>();
    private HashMap<Pair<String, String>, ArmLoad> globalVarMap = new HashMap<>(); // pair<string var, string block>
    private ArmModule armModule = new ArmModule();
    private boolean condIsOppo = false;
    private MyModule module;

    public static void addOff(int count) {
        armInstrOff += count;
    }

    public static int getArmInstrOff() {
        return armInstrOff;
    }

    public static ArmFunction getCurHandleFunction() {
        return curArmFunction;
    }

    public static boolean isCorrectArmImme(int imme) {
        // 看看这个imme立即数在arm架构下是否能被编码
        // int shiftBit = 0; // 循环移位位数
        for (int shiftBit = 0; shiftBit <= 32; shiftBit += 2) {
            if ((((imme << shiftBit) | (imme >>> (32 - shiftBit))) & ~0xff) == 0) {
                return true;
            }
        }
        return false;
    }

    public HashMap<ArmFunction, Func> getArmToIrFuncMap() {
        return armToIrFuncMap;
    }

    public HashMap<BasicBlock, ArmBlock> getBasicToArmBlockMap() {
        return basicToArmBlockMap;
    }

    public HashMap<ArmBlock, BasicBlock> getArmToBasicBlockMap() {
        return armToBasicBlockMap;
    }

    public ArmModule getArmModule() {
        return armModule;
    }

    public void parserIrModel(MyModule module) { // 这个函数把armModule填成应有的样子。
        this.module = module;
        MyList<GlobalValue> globalValues = module.globalValues;
        // 存全局变量
        for (GlobalValue globalValue : globalValues) {
            VirtualReg virtualReg = VirtualReg.NewVirtualGlobal(globalValue.getNameWithoutPre());
            irNameToVirMap.put(globalValue.getNameWithoutPre(), virtualReg);
        }
        // 遍历所有函数, new出来对应的arm函数
        for (Func func : module.getFuncs()) {
            if (func.getFuncBody() != null) {
                String funcName = "";
                if (func.getName().equals("starttime") || func.getName().equals("stoptime")) {
                    funcName += "_sysy_";
                    funcName += func.getName();
                } else {
                    funcName += func.getName();
                }
                irToArmFuncMap.put(func, new ArmFunction(funcName));
                armToIrFuncMap.put(irToArmFuncMap.get(func), func);
                armModule.addFunctions(irToArmFuncMap.get(func));
            }
        }
        // 再次遍历, 将函数翻译成arm存进去
        for (Func func : module.getFuncs()) {
            curArmFunction = irToArmFuncMap.get(func);
            parserIrFunction(func);
        }
        // ...
    }

    public void parserIrFunction(Func func) {
        for (BasicBlock basicBlock : func.getBasicBlocks()) {
            basicToArmBlockMap.put(basicBlock,
                    new ArmBlock(irToArmFuncMap.get(func), basicBlock.getName()));
            armToBasicBlockMap.put(basicToArmBlockMap.get(basicBlock), basicBlock);
            irToArmFuncMap.get(func).addArmBlock(
                    basicToArmBlockMap.get(basicBlock)
            );
        }

        for (BasicBlock basicBlock : func.getBasicBlocks()) {
            int basicDepth = 0;
            if (basicBlock.getParentLoop() != null) {
                basicDepth = basicBlock.getParentLoop().getLoopDepth();
            }
            basicToArmBlockMap.get(basicBlock).setLoopDepth(basicDepth);
            parserIrBlock(basicBlock);
        }

    }

//    public ArmOperand getFuncArgOperand(Value val) {
//        if (!irNameToVirMap.containsKey(val.getName())) {
//            VirtualReg vReg = new VirtualReg(val.getName(), );
//        } else {
//            return irNameToVirMap.get(val.getName());
//        }
//    }

    public void parserIrBlock(BasicBlock block) {
        for (Instr instr : block.getInstrs()) {
            parserIrInstr(instr, basicToArmBlockMap.get(block));
        }
    }

    public void parserIrInstr(Instr irInstr, ArmBlock armBlock) { // 对单个的 ir 指令进行解析
        // 维护armFunc中的虚拟寄存器
        if (irInstr.getTag() == Value.Tag.load) { // load 指令
            // 二重指针 ???? TODO
            ArmOperand addr = getArmOperand(((Instr.LoadInstr) irInstr).getAddress(), armBlock, 0); // 拿到 load的地址
            ArmOperand offset = new Imme(0);
            if (((Instr.LoadInstr) irInstr).getType().isIntType()) {
                ArmOperand dst = getArmOperand(irInstr, armBlock, 0);
                new ArmLoad(armBlock, dst, addr, offset); // 需要链接到 armBlock
            } else {
                ArmOperand dst = getArmOperand(irInstr, armBlock, 3);
                new ArmVLoad(armBlock, dst, addr, offset);
            }
        } else if (irInstr.getTag() == Value.Tag.store) {
            // %1 = alloca i32
            ArmOperand addr = getArmOperand(((Instr.StoreInstr) irInstr).getAddress(), armBlock, 0);
            Value srcDataValue = ((Instr.StoreInstr) irInstr).getValue(); // getValue得到的就是要存的数据
            ArmOperand offset = new Imme(0);
            if (((Instr.StoreInstr) irInstr).getValue().getType().isIntType()) {
                ArmOperand srcData = getArmOperand(srcDataValue, armBlock, (srcDataValue instanceof Constant.IntConst) ? 2 : 0);
                // TODO 二重指针????
                // ir_store 的srcData有可能是一个立即数, 有可能是一个变量
                new ArmStore(armBlock, srcData, addr, offset);
            } else {
                ArmOperand srcData;
                if (srcDataValue instanceof Constant.FloatConst) {
                    srcData = movFloatImme(((Constant.FloatConst) srcDataValue).getFloatVal(), armBlock, null);
                } else {
                    srcData = getArmOperand(srcDataValue, armBlock, 3);
                }
                new ArmVStore(armBlock, srcData, addr, offset);
            }
        } else if (irInstr.getTag() == Value.Tag.zext) { // zext扩展到的目标类型就是zext指令本身的类型
            // zext的源操作数一定一个icmp指令
            ArmOperand dst = getArmOperand(irInstr, armBlock, 0); // getName 得到的就是这条指令对应的变量  %l0
            // a1.name 得到的就是这条指令对应的SSA变量名  getname 是带百分号的
            // rhs一定是一个icmp指令
            Value A1 = ((Instr.ZextInstr) irInstr).getA1();
            assert A1 instanceof Instr.IcmpInstr || A1 instanceof Instr.FcmpInstr;
            Instr cmp = A1 instanceof Instr.IcmpInstr ? (Instr.IcmpInstr) A1 : (Instr.FcmpInstr) A1;
            parserIrCmp(cmp, armBlock, dst); // 把比较结果移动进dst
        } else if (irInstr.getTag() == Value.Tag.phi) { // 传进来的armBlock应该要是已经消除好 phi的
            return; // 不需要向 armBlock 中加上任何一条 arm指令, 可能最后会单独处理phi指令, 也就是把phi指令分拆追加到与之相关的几个块的尾部
        } else if (irInstr.getTag() == Value.Tag.call) {
            ArrayList<Value> paras = ((Instr.CallInstr) irInstr).getParams();
            ArmCall call = new ArmCall(armBlock); // 暂时不关联块, 这里只是设置call的当前块是armBlock, 并不添加
            int paraCount = paras.size();
            Func goalFunc = ((Instr.CallInstr) irInstr).getFunc(); // call的对象
            // 这些存起来的参数值在哪里用呢 -> 在解析到函数参数的时候弄出来
            for (int i = 0; i < paraCount; ++i) {
                if (paras.get(i).getType().isFloatType()) {
                    if (paras.get(i) instanceof Constant.FloatConst) {
                        if (i < 4) {
                            FloatPhysicalReg dst = new FloatPhysicalReg(i);
                            movFloatImme(
                                    ((Constant.FloatConst) paras.get(i)).getFloatVal(), armBlock, dst);
                            call.addUseReg(dst);
                        } else { // 参数入栈
                            ArmOperand offset = new Imme((i - paraCount) * 4); // 这是需要有偏移的, 每压一个数要减四
                            new ArmVStore(armBlock, movFloatImme(((Constant.FloatConst) paras.get(i)).getFloatVal(), armBlock, null)
                                    , new PhysicalReg("sp"), offset);
                        }
                    } else {
                        if (i < 4) { // 就是把这个参数移进对应的浮点寄存器
                            FloatPhysicalReg dst = new FloatPhysicalReg(i);
                            new ArmVMov(armBlock, dst, getArmOperand(paras.get(i), armBlock, 3));
                            call.addUseReg(dst);
                        } else { // 把这个 $F1 变量入栈
                            ArmOperand offset = new Imme((i - paraCount) * 4); // 这是需要有偏移的, 每压一个数要减四
                            new ArmVStore(armBlock,
                                    getArmOperand(paras.get(i), armBlock, 3), new PhysicalReg("sp"), offset);
                        }
                    }
                } else {

                    if (i < 4) { // 4个以内的参数存进去
                        ArmOperand para = getArmOperand(paras.get(i), armBlock, paras.get(i) instanceof Constant.IntConst ? 1 : 0);
                        PhysicalReg dst = new PhysicalReg(i); // 要用0 - 3号寄存器去存
                        new ArmMov(armBlock, dst, para);
                        // 需要加就加哪那么多废话
                        call.addUseReg(dst);
                    } else {
                        // 多余的参数压栈, 注意存的顺序, 是把靠后的参数存到了栈顶(原来的sp-4的位置, 认为原来的sp的位置已经存了数据了)
                        ArmOperand para = paras.get(i) instanceof Constant.IntConst ?
                                movImme(((Constant.IntConst) paras.get(i)).getIntVal(), armBlock, null) :
                                getArmOperand(paras.get(i), armBlock, 0);
                        ArmOperand offset = new Imme((i - paraCount) * 4); // 这是需要有偏移的, 每压一个数要减四
                        new ArmStore(armBlock, para, new PhysicalReg("sp"), offset);
                    }
                }
            }
            if (paraCount > 4) {
                ArmOperand offsetSum = new Imme((paraCount - 4) * 4);
                new ArmBinary(armBlock, ArmBinary.BinaryType.sub,
                        new PhysicalReg("sp"), new PhysicalReg("sp"), offsetSum);
            }
            call.setGoalFunc(goalFunc);
            call.setArmBlock(armBlock);
            if (paraCount > 4) { // add sp, sp 4*n
                ArmOperand offsetSum = new Imme((paraCount - 4) * 4);
                new ArmBinary(armBlock, ArmBinary.BinaryType.add,
                        new PhysicalReg("sp"), new PhysicalReg("sp"), offsetSum);
            }
            for (int i = 0; i < 4; ++i) { // FIXME  这是在干啥啊
                call.addDefReg(new PhysicalReg(i));
                call.addDefReg(new FloatPhysicalReg(i));
            }
            if (!(goalFunc).getRetType().isVoidType()) {
                // 不是void类型, 那就要mov一下返回值(从r0中拿)
                if (goalFunc.getRetType().isIntType()) {
                    ArmOperand dst = getArmOperand(irInstr, armBlock, 0);
                    new ArmMov(armBlock, dst, new PhysicalReg("r0"));
                } else {
                    new ArmVMov(armBlock, getArmOperand(irInstr, armBlock, 3),
                            new FloatPhysicalReg("s0"));
                }
            }
            // FIXME 这里又是在干啥
            call.addDefReg(new PhysicalReg("lr")); // lr 寄存器, 指向函数返回的地址
            call.addDefReg(new PhysicalReg("ip"));

        } else if (irInstr.getTag() == Value.Tag.ret) {
            Value retValue = ((Instr.RetInstr) irInstr).getRetValue();
            if (retValue != null) {
                // 存在返回值
                // 函数的运算结果???????
                if (retValue.getType().isIntType()) {
                    ArmOperand op = getArmOperand(retValue, armBlock, retValue instanceof Constant.IntConst ? 1 : 0);
                    new ArmMov(armBlock, new PhysicalReg("r0"), op); // 用r0存返回值
                    armBlock.setIsRetValBlock(true);
                } else { // 浮点
                    FloatPhysicalReg to = new FloatPhysicalReg("s0");
                    if (retValue instanceof Constant.FloatConst) {
                        movFloatImme(((Constant.FloatConst) retValue).getFloatVal(), armBlock, to);
                    } else {
                        new ArmVMov(armBlock, new FloatPhysicalReg("s0"),
                                getArmOperand(retValue, armBlock, 3));
                    }
                    armBlock.setIsRetFloatValBlock(true);
                }
                new ArmReturn(armBlock);
            } else { // 没有返回值的话就单纯跳转回去
                new ArmReturn(armBlock); // 需要能通过指令拿到所在块, 通过所在块拿到函数
            }
        } else if (irInstr.getTag() == Value.Tag.alloca) {
            ArmOperand dst = getArmOperand(irInstr, armBlock, 0);
            // TODO 二重指针的情况的处理 ???   浮点数指针的处理 ???
            // TODO 要么指向整数/浮点, 要么指向数组 ?????
            // FIXME 这里为什么分配内存却要让sp指针的值变大
            SymType contentType = ((Instr.AllocaInstr) irInstr).getContentType();
            if (contentType.isPointerType()) {
                return; // FIXME 如何处理这个二重指针
            }
            ArmOperand op2 = null;
            int addStackSize = 0;
            if (contentType.isIntType() || contentType.isFloatType()) {
                op2 = new Imme(4);
                addStackSize = 4;
            } else if (contentType.isArrayType()) {
                // ArrayType 类型getSize得到的仅仅是一维的长度, getSumSize才是全部的长度
                op2 = new Imme(armBlock.getCurArmFunction().getStackSize());
                int sumSize = ((SymType.ArrayType) contentType).getSumSize() * 4;
                addStackSize = sumSize;
            }
            ArmOperand rhs = getArmOperand(
                    new Constant.IntConst(armBlock.getCurArmFunction().getStackSize())
                    , armBlock, 1);
            new ArmBinary(armBlock, ArmBinary.BinaryType.add, dst,
                    new PhysicalReg("sp"),
                    rhs);
//            System.out.println("-----------------------------" + addStackSize);
            armBlock.getCurArmFunction().addStackSize(addStackSize); // 新分配空间, 让函数的栈变大
        } else if (irInstr.getTag() == Value.Tag.bincal &&
                ((Instr.AluInstr) irInstr).getAluOp() == Instr.AluInstr.AluOp.ADD) {
            // 本指令下: 操作数若要是constant, 那就一定是int constant
            // 处理 ir add指令
            ArmOperand lhs;
            ArmOperand rhs;
            ArmOperand dst = getArmOperand(irInstr, armBlock, 0);
            Value op1 = ((Instr.AluInstr) irInstr).getA1();
            Value op2 = ((Instr.AluInstr) irInstr).getA2();
            // op1是常数 && op2是常数 , 那就怎么也不能转换为add汇编了, 需要直接加, 然后mov
            // op1不是 || op2不是 , 那就转换为add汇编, 第一个参数应该不能是
            if (op1 instanceof Constant && op2 instanceof Constant) {
                // 直接加起来然后move
                int val = (int) (((Constant) op1).getConstValue()) + Integer.parseInt(op2.getName());
                ArmOperand movSrc = new Imme(Integer.parseInt(op1.getName()) + Integer.parseInt(op2.getName()));
                // new ArmMov(armBlock, dst, movSrc);
                movImme(val, armBlock, dst);
            } else { // 挑一个非常数, 放在前面
                if (!(op1 instanceof Constant)) { // 直接op1在前
                    lhs = getArmOperand(op1, armBlock, 0); // op1 是变量
                    rhs = getArmOperand(op2, armBlock, op2 instanceof Constant ? 1 : 0); // op2 可能变量可能常数
                } else { // 把op2放在前面, op1是常数
                    lhs = getArmOperand(op2, armBlock, 0);
                    rhs = getArmOperand(op1, armBlock, 1);
                }
                new ArmBinary(armBlock, ArmBinary.BinaryType.add, dst, lhs, rhs);
            }
        } else if (irInstr.getTag() == Value.Tag.bincal &&
                ((Instr.AluInstr) irInstr).getAluOp() == Instr.AluInstr.AluOp.SUB) {
            // 处理 ir sub指令
            ArmOperand lhs;
            ArmOperand rhs;
            ArmOperand dst = getArmOperand(irInstr, armBlock, 0);
            Value op1 = ((Instr.AluInstr) irInstr).getA1();
            Value op2 = ((Instr.AluInstr) irInstr).getA2();
            // 操作数可能是负数, 在转arm的时候要注意
            if (op1 instanceof Constant.IntConst && op2 instanceof Constant.IntConst) {
                int val = Integer.parseInt(op1.getName()) - Integer.parseInt(op2.getName());
                movImme(val, armBlock, dst);
            } else {
                if (op1 instanceof Constant.IntConst) {
                    // 必须交换位置, 转换为 arm的rsb指令 -> rsb rd, rs, #1 ===> rd = 1 - rs
                    lhs = getArmOperand(op2, armBlock, 0);
                    rhs = getArmOperand(op1, armBlock, 1);
                    new ArmBinary(armBlock, ArmBinary.BinaryType.rsb, dst, lhs, rhs);
                } else { // op1 是寄存器, op2 未知
                    // 原位置
                    lhs = getArmOperand(op1, armBlock, 0);
                    rhs = getArmOperand(op2, armBlock, op2 instanceof Constant ? 1 : 0);
                    new ArmBinary(armBlock, ArmBinary.BinaryType.sub, dst, lhs, rhs);
                }
            }
        } else if (irInstr.getTag() == Value.Tag.bincal
                && ((Instr.AluInstr) irInstr).getAluOp() == Instr.AluInstr.AluOp.MUL) {
            // 乘法的处理
            ArmOperand dst = getArmOperand(irInstr, armBlock, 0);
            Value v1 = ((Instr.AluInstr) irInstr).getA1();
            Value v2 = ((Instr.AluInstr) irInstr).getA2();
            if ((v1 instanceof Constant.IntConst) && (v2 instanceof Constant.IntConst)) {
                int mulRes = Integer.parseInt(v1.getName()) * Integer.parseInt(v2.getName());
                movImme(mulRes, armBlock, dst);
            } else if (!(v1 instanceof Constant.IntConst) && !(v2 instanceof Constant.IntConst)) {
                // 如果两个op都是变量
                new ArmBinary(armBlock, ArmBinary.BinaryType.mul, dst,
                        getArmOperand(v1, armBlock, 0), getArmOperand(v2, armBlock, 0));
            } else {
                // v1 v2 中有且仅有一个常数, 要将常数放进寄存器里相乘
                // 这里相当于要做一个简单的乘法优化, 是针对于移位操作的乘法优化: 对于乘一个和2的幂次相关的数, 可以优化为移位操作
                // 这算是机器相关优化了
                int opConst;
                Value opValue; // 非 const
                if (v1 instanceof Constant.IntConst) {
                    opConst = Integer.parseInt(v1.getName());
                    opValue = v2;
                } else {
                    opConst = Integer.parseInt(v2.getName());
                    opValue = v1;
                }
                ArmOperand opReg = getArmOperand(opValue, armBlock, 0);
                // opConst 是0/二进制有且仅有1位1的数

                // TODO : 关于 '1000_0000' 这种最大负数的处理, 以及乘法越界处理
                if ((opConst & (opConst - 1)) == 0) {
                    mulPowOptimize(armBlock, dst, opReg, opConst);
                } else if (((opConst - 1) & (opConst - 2)) == 0) { // opConst为 (2的幂次方 + 1) ---> opConst - 1 为 2的幂次方
                    // opReg先乘以2的幂次方, 再加1
                    // -128 + 1 == -127
                    mulPowOptimize(armBlock, dst, opReg, opConst - 1);
                    new ArmBinary(armBlock, ArmBinary.BinaryType.add, dst, dst, opReg);
                } else if (((opConst + 1) & opConst) == 0) {
                    // opConst 是 2的幂次方 - 1
                    mulPowOptimize(armBlock, dst, opReg, opConst + 1);
                    new ArmBinary(armBlock, ArmBinary.BinaryType.sub, dst, dst, opReg);
                } else {
                    ArmOperand rhs = movImme(opConst, armBlock, null);
                    new ArmBinary(armBlock, ArmBinary.BinaryType.mul, dst, opReg, rhs);
                }
            }
        } else if (irInstr.getTag() == Value.Tag.bincal
                && ((Instr.AluInstr) irInstr).getAluOp() == Instr.AluInstr.AluOp.DIV) {
            // 除法的处理  TODO 除法优化: 除常数/存前面的结果
            Value A1 = ((Instr.AluInstr) irInstr).getA1();
            Value A2 = ((Instr.AluInstr) irInstr).getA2();
            ArmOperand dst = getArmOperand(irInstr, armBlock, 0);
            ArmOperand lhs = getArmOperand(A1, armBlock, A1 instanceof Constant.IntConst ? 1 : 0);
            ArmOperand rhs = getArmOperand(A2, armBlock, A2 instanceof Constant.IntConst ? 1 : 0);
            if (A1 instanceof Constant.IntConst && A2 instanceof Constant.IntConst) {
                int res = Integer.parseInt(A1.getName()) / Integer.parseInt(A2.getName());
                movImme(res, armBlock, dst);
            } else if (!(A1 instanceof Constant.IntConst) && !(A2 instanceof Constant.IntConst)) {
                // 都不是常数
                // new 一个指令, 这里可能能用数据结构存一下本次的除法结果, 下次再用到这个除法的话就可以直接给结果了
                new ArmBinary(armBlock, ArmBinary.BinaryType.sdiv, dst, lhs, rhs);
            } else {
                // 一个常数一个变量 TODO 如果前面是常数的话, 目前想到的是将其挪进寄存器里然后除, 有无优化办法???
                if (A1 instanceof Constant.IntConst) { // A1 常数
                    ArmOperand newReg = movImme(Integer.parseInt(A1.getName()), armBlock, null);
                    new ArmBinary(armBlock, ArmBinary.BinaryType.sdiv, dst, newReg, rhs);
                } else { // A2 常数
                    ArmOperand newReg = movImme(Integer.parseInt(A2.getName()), armBlock, null);
                    new ArmBinary(armBlock, ArmBinary.BinaryType.sdiv, dst, lhs, newReg);
                }
            }
        } else if (irInstr.getTag() == Value.Tag.bincal
                && (((Instr.AluInstr) irInstr).getAluOp() == Instr.AluInstr.AluOp.REM ||
                ((Instr.AluInstr) irInstr).getAluOp() == Instr.AluInstr.AluOp.FREM)) {
            // 取余
            return;
        } else if (irInstr.getTag() == Value.Tag.gep) { // gep -> get  element ptr 计算目标元素地址
            // gep -> gep [数组类型 * 大小], 数组起始地址,
            SymType.PointerType point = (SymType.PointerType) (((Instr.GepInstr) irInstr).getPointer()).getType();
            ArrayList<Value> indexValues = ((Instr.GepInstr) irInstr).getDimLens();

            //if (!point.getPointToType().isFloatType() && !point.getPointToType().isIntType()) {
            //    indexValues.remove(0); // 把没用的标记删掉
            //}
            // 要么是浮点数组, 要么是整型数组  int a[10]  int b[10][10]    [4 * i32] [4 * i32]*
            // %l0 = alloca [4 x i32] 是指针类型
            // %l1 = getelementptr inbounds [4 x i32], [4 x i32]* %l0, i32 0, i32 1  l0, 全局/局部数组
            ArmOperand instrDst = getArmOperand(irInstr, armBlock, 0);
            ArmOperand arrayBeginAddr = getArmOperand(((Instr.GepInstr) irInstr).getPointer(), armBlock, 0);
            if (point.getPointToType().isFloatType() || point.getPointToType().isIntType()) {
                if (indexValues.get(0) instanceof Constant.IntConst) {
                    int index = ((Constant.IntConst) indexValues.get(0)).getIntVal();
                    new ArmBinary(armBlock, ArmBinary.BinaryType.add, instrDst,
                            arrayBeginAddr, getArmOperand(new Constant.IntConst(index * 4), armBlock, 1));
                } else {
                    new ArmBinary(armBlock, ArmBinary.BinaryType.add,
                            instrDst, arrayBeginAddr, getArmOperand(indexValues.get(0), armBlock, 0), ArmBinary.BinaryType.sl,
                            2);
                }
                return;
            }
            assert point.getPointToType() instanceof SymType.ArrayType;
            SymType.ArrayType pointTo = (SymType.ArrayType) point.getPointToType();
            // 从1开始是有效的长度 1 1 1 1 1
            // a[2][3]
            ArrayList<Integer> arrayDims = pointTo.getDims(); // 数组的各个维度的数组长度[3] [1] [4]
//            int dim = arrayDims.size(); // 这么多维
            int totalDim = arrayDims.size();
            int dim = indexValues.size();
            // indexes arrayDims
            int lastOff = 0;
            // alloca指令的变量 就是 起始地址  [5][6][7]
            for (int i = 1; i <= dim; ++i) { // 第i层数组
                int offUnit = 4;
                for (int j = i - 1; j < totalDim; ++j) { // TODO debug点, 这里是 j = i - 1 ???
                    offUnit *= arrayDims.get(j);
                } // 当前的偏移单位
                // 拿到当前的偏移下标
                // #define
                Value curOff = indexValues.get(i - 1);
                if (curOff instanceof Constant.IntConst) {
                    // 常数
                    // 在原来的基础上再偏移这么多
                    int sumOff = offUnit * ((Constant.IntConst) curOff).getIntVal();
                    if (i == dim) {
                        sumOff += lastOff;
                        if (sumOff == 0) { // 当前偏移是0
                            new ArmMov(armBlock, instrDst, arrayBeginAddr); // 把最新迭代的值给目标, 就结束了
                        } else {
                            new ArmBinary(armBlock, ArmBinary.BinaryType.add, instrDst,
                                    arrayBeginAddr, getArmOperand(new Constant.IntConst(sumOff), armBlock, 1)); // 如果不是0 的话还要再加一下再给目标, 然后结束
                        }
                    } else {
                        lastOff += sumOff;
                    }
                } else if ((offUnit & (offUnit - 1)) == 0) {
                    // 偏移单位是2的幂次  TODO 这条分支????
                    // 这样的话就不乘了直接移位
                    ArmOperand armCurOff = getArmOperand(curOff, armBlock, 0); // 这个操作数是整数变量
                    if (lastOff != 0) {
                        new ArmBinary(armBlock, ArmBinary.BinaryType.add,
                                instrDst, arrayBeginAddr,
                                getArmOperand(new Constant.IntConst(lastOff), armBlock, 1));
                        arrayBeginAddr = instrDst;
                        lastOff = 0;
                    }
                    // 先把 rhs 移位, 然后将其结果和 lhs相加
                    // 先让此次偏移 * 偏移单元 + 累计值
                    new ArmBinary(armBlock, ArmBinary.BinaryType.add,
                            instrDst, arrayBeginAddr, armCurOff, ArmBinary.BinaryType.sl,
                            getHighestOneBit(offUnit));
                    arrayBeginAddr = instrDst;
                } else {
                    // 没有特殊情况
                    ArmOperand armCurOff = getArmOperand(curOff, armBlock, 0); // 这个操作数是整数变量
                    if (lastOff != 0) { // 不是数组 首层 的话, 要new 出来加的操作
                        ArmOperand rhs = new Imme(lastOff);
                        // TODO 这里直接把那个 alloca 右值的值给改了 ???
                        new ArmBinary(armBlock,
                                ArmBinary.BinaryType.add,
                                /*arrayBeginAddr */instrDst, arrayBeginAddr, rhs);
                        arrayBeginAddr = instrDst;
                        lastOff = 0;
                    }
                    ArmOperand offUnitReg = movImme(offUnit, armBlock, null);
                    new ArmFma(armBlock, armCurOff, offUnitReg, arrayBeginAddr, instrDst);
                    arrayBeginAddr = instrDst; // TODO debug????
                }
            }
        } else if (irInstr.getTag() == Value.Tag.branch) { // 有条件选择分支跳转
            ArmBlock thenArmBlock = basicToArmBlockMap.get(((Instr.BranchInstr) irInstr).getThenAct());
            ArmBlock elseArmBlock = basicToArmBlockMap.get(((Instr.BranchInstr) irInstr).getElseAct());

            Value cond = ((Instr.BranchInstr) irInstr).getCond();
            if (cond instanceof Constant.BoolConst) {
                // 条件是常数, 直接跳转
                if ((int) (((Constant.BoolConst) cond).getConstValue()) == 1) {
                    new ArmJump(armBlock, thenArmBlock);
                    armBlock.setTrueNextBlock(thenArmBlock);
                } else { // cond == 0
                    new ArmJump(armBlock, elseArmBlock);
                    armBlock.setTrueNextBlock(elseArmBlock);
                }
                return;
            }
            assert cond instanceof Instr.IcmpInstr || cond instanceof Instr.FcmpInstr;
            if (((Instr) cond).getUsedValues().get(0) instanceof Constant &&
                    ((Instr) cond).getUsedValues().get(1) instanceof Constant) {
                // 虽然cond不是常数, 但是cond代表的icmp的两个比较对象还是常数
                // icmp 1, 2, 如果
                ArmCond cond1;
                if (cond instanceof Instr.IcmpInstr) {
                    cond1 = parserIrCmp((Instr.IcmpInstr) cond, armBlock, null);
                } else {
                    cond1 = parserIrCmp((Instr.FcmpInstr) cond, armBlock, null);
                }

                if (cond1 == ArmCond.any) {
                    armBlock.setTrueNextBlock(thenArmBlock);
                    new ArmJump(armBlock, thenArmBlock);
                } else {
                    armBlock.setTrueNextBlock(elseArmBlock);
                    new ArmJump(armBlock, elseArmBlock);
                }
            } else { // 条件不是常数 cond是一个icmp对象/fcmp对象
                // cmp成立, 则跳转到trueBlock; cmp不成立, 则跳转到falseBlock.
                ArmBlock realTrueNextBlock = thenArmBlock;
                ArmBlock realFalseNextBlock = elseArmBlock;
                boolean realTrueIsDirect = ((Instr.BranchInstr) irInstr).ifThenDirect();
                boolean realFalseIsDirect = ((Instr.BranchInstr) irInstr).ifElseDirect();
                ArmCond armCond;
                if (cond instanceof Instr.IcmpInstr) { // 返回 可arm的 icmp条件
                    armCond = parserIrCmp((Instr.IcmpInstr) cond, armBlock, null);
                } else {
                    armCond = parserIrCmp((Instr.FcmpInstr) cond, armBlock, null);
                }
                // armCond成立->realTrue
                // icmp 成立跳走
                // compare 条件确定, true false 确定
                if (realTrueIsDirect && !realFalseIsDirect) {
                    // 存else 块
//                    if (condIsOppo) {
//                        new ArmBranch(armBlock, armCond, realFalseNextBlock);
//                    } else {
                    new ArmBranch(armBlock, getOppositeCondForBr(armCond), realFalseNextBlock);
//                    }
                } else if (!realTrueIsDirect && realFalseIsDirect) {
                    new ArmBranch(armBlock, armCond, realTrueNextBlock);
                } else {
                    new ArmBranch(armBlock, armCond, realTrueNextBlock);
                    new ArmBranch(armBlock, getOppositeCondForBr(armCond), realFalseNextBlock);
                }
                condIsOppo = false;
                armBlock.setTrueNextBlock(realTrueNextBlock);
                armBlock.setFalseNextBlock(realFalseNextBlock);
            }
        } else if (irInstr.getTag() == Value.Tag.jump) { // 无条件跳转 br label
            ArmBlock goalArmBlock = basicToArmBlockMap.get(((Instr.JumpInstr) irInstr).getTarget());
            new ArmJump(armBlock, goalArmBlock);
        } else if (irInstr.getTag() == Value.Tag.icmp || irInstr.getTag() == Value.Tag.fcmp) {
            return;
        } else if (irInstr.getTag() == Value.Tag.ashr ||
                irInstr.getTag() == Value.Tag.lshr ||
                irInstr.getTag() == Value.Tag.shl) {
            ArmOperand dst = getArmOperand(irInstr, armBlock, 0);
            Value A1;
            Value A2;
            if (irInstr.isAshr()) {
                A1 = ((Instr.AshrInstr) irInstr).getA1();
                A2 = ((Instr.AshrInstr) irInstr).getA2();
            } else if (irInstr.isShl()) {
                A1 = ((Instr.ShlInstr) irInstr).getA1();
                A2 = ((Instr.ShlInstr) irInstr).getA2();
            } else {
                A1 = ((Instr.LshrInstr) irInstr).getA1();
                A2 = ((Instr.LshrInstr) irInstr).getA2();
            }

            if (A1 instanceof Constant.IntConst && A2 instanceof Constant.IntConst) {
                int val1 = Integer.parseInt(A1.getName());
                int val2 = Integer.parseInt(A2.getName());
                int res = 0;
                // 逻辑右移: >>>  算术右移: >>
                switch (irInstr.getTag()) {
                    case ashr:
                        res = val1 >> val2;
                        break;
                    case lshr:
                        res = val1 >>> val2;
                        break;
                    case shl:
                        res = val1 << val2;
                        break;
                    default:
                }
                movImme(res, armBlock, dst);
                return;
            }
            ArmOperand lhs = getArmOperand(A1, armBlock, A1 instanceof Constant.IntConst ? 1 : 0);
            ArmOperand rhs = getArmOperand(A2, armBlock, A2 instanceof Constant.IntConst ? 1 : 0);
            ArmBinary.BinaryType type = null;
            if (A1 instanceof Constant.IntConst) {
                // 把A1存进寄存器里
                lhs = movImme(Integer.parseInt(A1.getName()), armBlock, null);
            }
            switch (irInstr.getTag()) {
                case ashr:
                    type = ArmBinary.BinaryType.asr;
                    break;
                case lshr:
                    type = ArmBinary.BinaryType.lsr;
                    break;
                case shl:
                    type = ArmBinary.BinaryType.sl;
                    break;
                default:
            }
            new ArmBinary(armBlock, type, dst, lhs, rhs);
        } else if (irInstr.getTag() == Value.Tag.fptosi || // float point to signed int 浮点->带符号整数
                irInstr.getTag() == Value.Tag.sitofp) { // signed int to float point 带符号整数->浮点
            // 用VCVT指令来实现
            if (irInstr.getTag() == Value.Tag.fptosi) {
                // %2 = fptosi float 3.14 to i32
                // vcvt.f32.s32 s0(3.14), r0
                // 浮点到整数, 这个
                // vcvt 两个寄存器都必须是 浮点寄存器。 s0(3.14的浮点式32位) -> s1(3.14的整数式32位) -> r0(整数式的32位原封不动地传给r0)
                ArmOperand dst = getArmOperand(irInstr, armBlock, 0); // 结果应是通用寄存器
                ArmOperand rhs = getFloatOpNoImme(((Instr.FptosiInstr) irInstr).getA1(), armBlock);
                ArmOperand midFloatReg = new VirtualReg(true);
                new ArmVConvert(armBlock, ArmVConvert.CvtDataType.f, ArmVConvert.CvtDataType.i, rhs, midFloatReg);
                new ArmVMov(armBlock, dst, midFloatReg);
            } else {
                ArmOperand dst = getArmOperand(irInstr, armBlock, 3); // 浮点寄存器
                // %2 = sitofp i32 4 to float
                Value A1 = ((Instr.SitofpInstr) irInstr).getA1(); // 是整数, 则存进虚拟寄存器, 再用; 是变量, 直接用.
                ArmOperand rhs = getOpNoImme(A1, armBlock); // rhs是一个存了一个整数的寄存器
                new ArmVMov(armBlock, dst, rhs);
                new ArmVConvert(armBlock, ArmVConvert.CvtDataType.i, ArmVConvert.CvtDataType.f, dst, dst); // dst本来装的是一个整数a的32位, 这个把它转变为浮点视角下32位的a
            }
        } else if (irInstr.getTag() == Value.Tag.bitcast) { //
            ArmOperand dst = getArmOperand(irInstr, armBlock, 0);
            Value A1 = ((Instr.BitcastInstr) irInstr).getA1();
            ArmOperand rhs = getArmOperand(A1, armBlock, 0);
            new ArmMov(armBlock, dst, rhs);
        } else if (irInstr.getTag() == Value.Tag.trunc) { // 数位截断
            Value A1 = ((Instr.TruncInstr) irInstr).getA1();
            int op = Integer.parseInt(A1.getName());
            ArmOperand dst = getArmOperand(irInstr, armBlock, 0);
            // 取低8位
            int goal = (op & 0xff);
            movImme(goal, armBlock, dst);
        } else if (irInstr.getTag() == Value.Tag.bincal &&  // 不带浮点数的二元指令前面已经截断过了
                (((Instr.AluInstr) irInstr).getAluOp() == Instr.AluInstr.AluOp.FADD ||
                        ((Instr.AluInstr) irInstr).getAluOp() == Instr.AluInstr.AluOp.FSUB ||
                        ((Instr.AluInstr) irInstr).getAluOp() == Instr.AluInstr.AluOp.FMUL ||
                        ((Instr.AluInstr) irInstr).getAluOp() == Instr.AluInstr.AluOp.FDIV ||
                        ((Instr.AluInstr) irInstr).getAluOp() == Instr.AluInstr.AluOp.FREM)) {
            // 根据ir的指令类型选择相应的arm浮点指令
            Value A1 = ((Instr.AluInstr) irInstr).getA1();
            Value A2 = ((Instr.AluInstr) irInstr).getA2();
            ArmOperand dst = getArmOperand(irInstr, armBlock, 3); // 浮点虚拟寄存器
            if (A1 instanceof Constant.FloatConst && A2 instanceof Constant.FloatConst) {
                Float val1 = ((Constant.FloatConst) A1).getFloatVal();
                Float val2 = ((Constant.FloatConst) A2).getFloatVal();
                Float res;
                switch (((Instr.AluInstr) irInstr).getAluOp()) {
                    case FADD:
                        res = val1 + val2;
                        break;
                    case FSUB:
                        res = val1 - val2;
                        break;
                    case FMUL:
                        res = val1 * val2;
                        break;
                    case FDIV:
                        res = val1 / val2;
                        break;
                    case FREM:
                        res = val1 % val2;
                        break;
                    default:
                        res = 0.0f;
                }
                movFloatImme(res, armBlock, dst);
                return;
            }
            ArmFloatBinary.FloatBinaryType type = ArmFloatBinary.parserIrFloatBinary(
                    ((Instr.AluInstr) irInstr).getAluOp());
            ArmOperand lhs = getFloatOpNoImme(A1, armBlock);
            ArmOperand rhs = getFloatOpNoImme(A2, armBlock);
            new ArmFloatBinary(armBlock, type, dst, lhs, rhs);
        } else if (irInstr instanceof Instr.MoveInstr) {
            SymType type = ((Instr.MoveInstr) irInstr).getType();
            Value srcValue = ((Instr.MoveInstr) irInstr).getSrc();
            Value dstValue = ((Instr.MoveInstr) irInstr).getDst(); // 一定是变量

            ArmOperand dst;
            if (type.isFloatType()) {
                dst = getArmOperand(dstValue, armBlock, 3);
                if (srcValue instanceof Constant.FloatConst) {
                    movFloatImme(((Constant.FloatConst) srcValue).getFloatVal(), armBlock, dst);
                } else {
                    new ArmVMov(armBlock, dst, getArmOperand(srcValue, armBlock, 3));
                }
            } else if (type.isIntType()) {
                dst = getArmOperand(dstValue, armBlock, 0);
                if (srcValue instanceof Constant.IntConst) {
                    movImme(((Constant.IntConst) srcValue).getIntVal(), armBlock, dst);
                } else {
                    new ArmMov(armBlock, dst, getArmOperand(srcValue, armBlock, 0));
                }
            }
        }
    }

    public void mulPowOptimize(ArmBlock ab, ArmOperand dst, ArmOperand opReg, int opConst) {
        if (opConst < 0) {
            // 是 -1, 通过rsb指令来取相反数
            new ArmBinary(ab, ArmBinary.BinaryType.rsb, dst, opReg, new Imme(0));
        } else if (opConst == 0) {
            new ArmMov(ab, dst, new Imme(0));
        } else {
            // opValue左移opConst.getHighest位
            int shiftBit = getHighestOneBit(opConst);
            new ArmBinary(ab, ArmBinary.BinaryType.sl, dst,
                    opReg, new Imme(shiftBit));
        }
    }

    public ArmOperand getFloatOpNoImme(Value v, ArmBlock ab) {
        // v 是一个 浮点 常数/变量, 返回一个浮点寄存器(去除立即数)
        if (v instanceof Constant.FloatConst) {
            // 将 浮点数 存进 浮点寄存器, 然后返回
            return movFloatImme(((Constant.FloatConst) v).getFloatVal(), ab, null);
        } else {
            return getArmOperand(v, ab, 3);
        }
    }


    public ArmOperand getArmOperand(Value v, ArmBlock ab, int type) {
        // TODO 有的变量可能不是指令, 而是全局变量, 这里要加一下
        // 0 -> getName拿到变量名, 返回虚拟寄存器
        // 1 -> 传进来的v是一个常整数, 返回一个立即数armOperand  (考虑编码)
        // 2 -> 同1, 但是不考虑编码
        // 3 -> 同0, 但是 float 虚拟寄存器
        // 4 -> 同1, 但是把常整数存进寄存器
        // %1 = ..
        // %1 =
        // i32 , float,
        Func func = armToIrFuncMap.get(curArmFunction);
        if (v instanceof Func.Param && func.getParams().contains(v)) {
            ArmOperand ret = null;
            Func.Param thisParam = (Func.Param) v;
            String paramName = thisParam.getName();
            if (!irNameToVirMap.containsKey(v.getName()) || irNameToVirMap.get(v.getName()) == null) {
                // 看看这个参数是这个函数的第几个形参
                int i = 0; // 参数的位置
                for (i = 0; i < func.getParams().size(); ++i) {
                    if (func.getParams().get(i).equals(v)) {
                        break;
                    }
                }
                // 拿到函数的第一个块
                ArmBlock entryBlock = basicToArmBlockMap.get(func.getBasicBlocks().getFirst());

                if (i < 4) { // 不可能是常数
                    if (v.getType().isFloatType()) {
                        VirtualReg virForParam = new VirtualReg(paramName, true); // 给形参分配的参数
                        irNameToVirMap.put(paramName, virForParam);
                        FloatPhysicalReg reg = new FloatPhysicalReg(i);
                        // rhs->物理  dst->虚拟
                        new ArmVMov(entryBlock, virForParam, reg, true);
                        return virForParam;
                    } else if (v.getType().isPointerType() || v.getType().isIntType()) {
                        // 整数 浮点数 数组
                        VirtualReg virForParam = new VirtualReg(paramName, false);
                        irNameToVirMap.put(paramName, virForParam);
                        PhysicalReg reg = new PhysicalReg(i);
                        new ArmMov(entryBlock, virForParam, reg, true);
                        return virForParam;
                    } else {
                        System.out.println("error func param condition-1 !");
                        return null;
                    }
                } else {
                    VirtualReg offsetVir = new VirtualReg(false);
                    if (v.getType().isFloatType()) {
                        VirtualReg virForParam = new VirtualReg(paramName, true); // 在new的时候会自动加进函数的虚拟寄存器列表
                        irNameToVirMap.put(paramName, virForParam);
                        VirtualReg storeInt = new VirtualReg(false);
                        // TODO 这里用 VLoad 的话是什么效果 ?????
                        new ArmVMov(entryBlock, virForParam, storeInt, true);
                        new ArmLoad(entryBlock, storeInt, new PhysicalReg("sp"), offsetVir, true); // 前面的时候store进去了, 现在load出来
                        ArmMovInstr m = new ArmMov(entryBlock, offsetVir, new Imme((i - 4) * 4), true); // 存偏移
                        // TODO 看看浮点数5参数对不对
                        curArmFunction.addFuncArgMove(m);
                        return virForParam;
                    } else if (v.getType().isPointerType() || v.getType().isIntType()) {
                        // 整数 浮点数 数组
                        VirtualReg virForParam = new VirtualReg(paramName, false);
                        irNameToVirMap.put(paramName, virForParam);
                        new ArmLoad(entryBlock, virForParam, new PhysicalReg("sp"), offsetVir, true);
                        ArmMovInstr m = new ArmMov(entryBlock, offsetVir, new Imme((i - 4) * 4), true); // 存偏移
                        curArmFunction.addFuncArgMove(m); // 把移动栈偏移参数的mov指令存进函数容器
                        return virForParam;
                    } else {
                        System.out.println("error func param condition-2 !");
                        return null;
                    }
                }
            } else {
                ret = irNameToVirMap.get(v.getName()); // 形参也是, 有了的话就直接返回了
            }
            return ret;
        } else if (v instanceof Func.Param && !((Func) armToIrFuncMap.get(curArmFunction)).getParams().contains(v)) {
            System.out.println("error func param condition-3 !");
            return null;
        }
        // 处理全局变量
        if (v instanceof GlobalValue && irNameToVirMap.containsKey(v.getNameWithoutPre())) {
            // 整数 浮点
            Pair<String, String> p = new Pair<>(v.getNameWithoutPre(), ab.getBlockName());
            if (!globalVarMap.containsKey(p)) { // 全局变量统一不加前缀了
                VirtualReg goalAddr = new VirtualReg(false);
                ArmLoad load = new ArmLoad(ab, goalAddr, irNameToVirMap.get(v.getNameWithoutPre()), new Imme(0));
                globalVarMap.put(p, load);
                return goalAddr;
            } else {
                return globalVarMap.get(p).getDst();
            }
        } else if (v instanceof GlobalValue && !irNameToVirMap.containsKey(v.getNameWithoutPre())) {
            System.out.println("error about problem of globalValue !");
            return null;
        }

        assert type <= 3;
        if (type == 0 || type == 3) { // 是一条 ir 指令, 返回 ir 对应的虚拟寄存器(如果没有, 就new一个)
            // 这里的v不一定是一个指令, 也可能是一个函数形参
            String name;
            if (v instanceof Instr) {
                name = ((Instr) v).getName();
            } else {
                name = ((VirtualValue) v).getName();
            }
            if (!irNameToVirMap.containsKey(name)) { // 该 ir 指令对应的 %变量 还没有分配虚拟寄存器
                if (name.equals("%l411")) {
                    int a = 1;
                }
                irNameToVirMap.put(name, new VirtualReg(name, type != 0));
                return irNameToVirMap.get(name);
            } else {
                return irNameToVirMap.get(name); // 直接返回
            }
        } else if (type == 1) { // 传进来的v 是一个int_const, 且是add 这种指令仅提供12位宽的int_const
            // 第二操作数的处理, 第二操作数如果是立即数, 不能被编码的话, 那就存进虚拟寄存器里
            Constant.IntConst temp = (Constant.IntConst) v;
            int val = Integer.parseInt(temp.getName());
            if (isCorrectArmImme(val)) {
                return new Imme(val);
            } else {
                // 不能编码, 这时候就要让 mov出手了
                return movImme(val, ab, null);
            }
        } else if (type == 2) {
            Constant.IntConst temp = (Constant.IntConst) v;
            int val = Integer.parseInt(temp.getName());
            return movImme(val, ab, null);
        } else if (type == 4) {
            // Constant.IntConst temp = (Constant.IntConst) v;
            // int val = Integer.parseInt(temp.getName());

        }
        System.out.println("this ERROR!");
        return null;
    }

    private int getLowestOneBit(int val) {
        int res = 0;
        val = val >>> 1;
        while (val != 0) {
            val = val >>> 1;
            res++;
        }
        return res;
    }

    public int getHighestOneBit(int val) { // 二进制下标从0开始
        assert val != 0 && (val & (val - 1)) == 0;
        int num = 1;
        for (int i = 0; ; ++i, num <<= 1) {
            if ((val & num) != 0) {
                return i;
            }
        }
    }

    public ArmOperand getOpNoImme(Value v, ArmBlock ab) {
        // v 要么是常整数, 要么是常整数变量
        if (v instanceof Constant.IntConst) {
            return movImme(((Constant.IntConst) v).getIntVal(), ab, null);
        } else {
            return getArmOperand(v, ab, 0);
        }
    }

    public ArmOperand movImme(int val, ArmBlock ab, ArmOperand to) {
        // 这个mov的是非浮点数的立即数
        // 用mov指令来存起来立即数, 指令要存进ab, 返回存了这个立即数的最终 ArmOperand, 是一个虚拟寄存器存着的
        // 符合mov限制 -> 一条
        // 不符合mov限制 -> 多条
//        .. 为啥浮点寄存器分配会把整数分配掉 ??
        ArmOperand dst = (to == null) ? new VirtualReg(false) : to;
        new ArmMov(ab, dst, new Imme(val));
        return dst;
    }

    public ArmOperand movFloatImme(float num, ArmBlock ab, ArmOperand to) {
        // dst!=null 时直接移进去, ==null 时new出新的移进去
        // 将浮点数存进new出来的新的浮点寄存器
        // 如果能编码, 那就直接 vmov, 否则, 分开 vmov
        // 如果不能编码的话, 那就 浮点->int形式二进制->movImme->vmov s, r
        ArmOperand dst = ((to == null) ? new VirtualReg(true) : to);
        ArmOperand rhs = new FloatImme(num);
        if (isCorrectArmFloatImme(num)) {
            new ArmVMov(ab, dst, rhs);
        } else {
            int mid = Float.floatToIntBits(num);
            ArmOperand storeMid = movImme(mid, ab, null); // storeMid是一个通用寄存器
            new ArmVMov(ab, dst, storeMid); // 把通用寄存器的值给浮点寄存器
        }
        return dst;
    }

    public boolean isCorrectArmFloatImme(float imme) {
        float eps = 1e-14f;
        float a = imme * 128;
        for (int r = 0; r < 8; ++r) {
            for (int n = 16; n < 32; ++n) {
                if ((abs((n * (1 << (7 - r)) - a)) < eps) ||
                        (abs((n * (1 << (7 - r)) + a)) < eps))
                    return true;
            }
        }
        return false;
    }

    public ArmCond getOppositeCondForCmp(ArmCond cond) {
        switch (cond) {
            case eq:
                return ArmCond.eq;
            case ne:
                return ArmCond.ne;
            case slt:
                return ArmCond.sgt;
            case sle:
                return ArmCond.sge;
            case sgt:
                return ArmCond.slt;
            case sge:
                return ArmCond.sle;
            default:
                return ArmCond.any;
        }
    }

    public ArmCond getOppositeCondForBr(ArmCond cond) {
        // 拿到和参数cond所相反的条件
        switch (cond) {
            case eq:
                return ArmCond.ne;
            case ne:
                return ArmCond.eq;
            case slt:
                return ArmCond.sge;
            case sle:
                return ArmCond.sgt;
            case sgt:
                return ArmCond.sle;
            case sge:
                return ArmCond.slt;
            default:
                return ArmCond.any;
        }
    }

    public ArmCond iCmpCondToArmCond(Instr.IcmpInstr.IcmpOp cmpOp) {
        ArmCond armCond = ArmCond.any;
        switch (cmpOp) {
            case EQ:
                armCond = ArmCond.eq;
                break;
            case NE:
                armCond = ArmCond.ne;
                break;
            case SLT:
                armCond = ArmCond.slt;
                break;
            case SLE:
                armCond = ArmCond.sle;
                break;
            case SGT:
                armCond = ArmCond.sgt;
                break;
            case SGE:
                armCond = ArmCond.sge;
                break;
            default:
        }
        return armCond;
    }

    public ArmCond fCmpCondToArmCond(Instr.FcmpInstr.FcmpOp cmpOp) {
        ArmCond armCond = ArmCond.any;
        switch (cmpOp) {
            case OEQ:
                armCond = ArmCond.eq;
                break;
            case ONE:
                armCond = ArmCond.ne;
                break;
            case OLT:
                armCond = ArmCond.slt;
                break;
            case OLE:
                armCond = ArmCond.sle;
                break;
            case OGT:
                armCond = ArmCond.sgt;
                break;
            case OGE:
                armCond = ArmCond.sge;
                break;
            default:
        }
        return armCond;
    }

    public ArmCond parserIrCmp(Instr cmp, ArmBlock curBlock, ArmOperand movDst) {
        // 如果传进来的是常数比较的话, 直接返回ArmCond.Any
        assert (cmp instanceof Instr.IcmpInstr || cmp instanceof Instr.FcmpInstr);
        // 解析一下cmp指令
        Value A1;
        Value A2;
        ArmCond armCond;
        if (cmp instanceof Instr.IcmpInstr) {
            A1 = ((Instr.IcmpInstr) cmp).getA1();
            A2 = ((Instr.IcmpInstr) cmp).getA2();
            Instr.IcmpInstr.IcmpOp cmpOp = ((Instr.IcmpInstr) cmp).getIcmpOp();
            armCond = iCmpCondToArmCond(cmpOp);
        } else {
            A1 = ((Instr.FcmpInstr) cmp).getA1();
            A2 = ((Instr.FcmpInstr) cmp).getA2();
            Instr.FcmpInstr.FcmpOp cmpOp = ((Instr.FcmpInstr) cmp).getFcmpOp();
            armCond = fCmpCondToArmCond(cmpOp);
        }
        boolean isConstA1 = A1 instanceof Constant.IntConst || A1 instanceof Constant.FloatConst;
        boolean isConstA2 = A2 instanceof Constant.IntConst || A2 instanceof Constant.FloatConst;
        if (isConstA1 && isConstA2) {
            boolean isSatisfyCond = false;
            if (A1 instanceof Constant.IntConst) { // 都是常整数
                int a1 = ((Constant.IntConst) A1).getIntVal();
                int a2 = ((Constant.IntConst) A2).getIntVal();
                switch (armCond) {
                    case eq:
                        isSatisfyCond = (a1 == a2);
                        break;
                    case ne:
                        isSatisfyCond = (a1 != a2);
                        break;
                    case slt:
                        isSatisfyCond = (a1 < a2);
                        break;
                    case sle:
                        isSatisfyCond = (a1 <= a2);
                        break;
                    case sgt:
                        isSatisfyCond = (a1 > a2);
                        break;
                    case sge:
                        isSatisfyCond = (a1 >= a2);
                        break;
                    default:
                }
            } else {
                float a1 = ((Constant.FloatConst) A1).getFloatVal();
                float a2 = ((Constant.FloatConst) A2).getFloatVal();
                switch (armCond) {
                    case eq:
                        isSatisfyCond = (a1 == a2);
                        break;
                    case ne:
                        isSatisfyCond = (a1 != a2);
                        break;
                    case slt:
                        isSatisfyCond = (a1 < a2);
                        break;
                    case sle:
                        isSatisfyCond = (a1 <= a2);
                        break;
                    case sgt:
                        isSatisfyCond = (a1 > a2);
                        break;
                    case sge:
                        isSatisfyCond = (a1 >= a2);
                        break;
                    default:
                }
            }
            ArmCond cc = (isSatisfyCond ? ArmCond.any : ArmCond.noway);
            if (movDst != null) {
                // mov 条件成立, 执行mov 1, 条件不成立, 执行mov 0
                if (cc == ArmCond.any)
                    new ArmMov(curBlock, movDst, new Imme(1), ArmCond.any);
                else
                    new ArmMov(curBlock, movDst, new Imme(0), ArmCond.any);
            }
            return cc;
        }
        // 有不是常数的, 这里必然有一个不是常数
        ArmCond retCond;
        if (A1 instanceof Constant.IntConst || A1 instanceof Constant.FloatConst) {
            // 交换  TODO icmp的结果有无可能用于下一个icmp的操作数????
            condIsOppo = true;
            ArmOperand op1 = getArmOperand(A2, curBlock, A2 instanceof Constant.IntConst ? 0 : 3);
            ArmOperand op2 = (A1 instanceof Constant.IntConst) ?
                    getArmOperand(A1, curBlock, 1) :
                    movFloatImme(((Constant.FloatConst) A2).getFloatVal(), curBlock, null);
            ArmCond oppoCond = getOppositeCondForCmp(armCond);
            if (A1 instanceof Constant.IntConst) {
                new ArmCompare(curBlock, ArmCompare.CmpType.cmp, oppoCond, op1, op2); // 比较结果不确定的话才需要翻译出来cmp比较指令
            } else {
                new ArmVCompare(curBlock, ArmVCompare.VCmpType.vcmp, oppoCond, op1, op2);
            }
            retCond = oppoCond;
        } else { // A1不是常数
            // 不用交换
            ArmOperand op1 = getArmOperand(A1, curBlock, (A1.getType().isFloatType()) ? 3 : 0);
            ArmOperand op2;
            if (A2.getType().isFloatType()) {
                if (A2 instanceof Constant.FloatConst) {
                    op2 = movFloatImme(((Constant.FloatConst) A2).getFloatVal(), curBlock, null);
                } else {
                    op2 = getArmOperand(A2, curBlock, 3);
                }
                new ArmVCompare(curBlock, ArmVCompare.VCmpType.vcmp, armCond, op1, op2);
            } else {
                op2 = (A2 instanceof Constant.IntConst) ? getArmOperand(A2, curBlock, 1) :
                        getArmOperand(A2, curBlock, 0);
                new ArmCompare(curBlock, ArmCompare.CmpType.cmp, armCond, op1, op2);
            }
            retCond = armCond;
        }
        if (movDst != null) {
            // mov 条件成立, 执行mov 1, 条件不成立, 执行mov 0
            new ArmMov(curBlock, movDst, new Imme(1), retCond);
            new ArmMov(curBlock, movDst, new Imme(0), getOppositeCondForBr(retCond));
        }
        return retCond;
    }

    public String getArmFromSelf() { // 生成arm
        StringBuilder arm = new StringBuilder();
        arm.append(".arch armv7ve\n.fpu vfpv3-d16\n");
        arm.append(".text\n");
        for (ArmFunction f : getArmModule().getArmFunctions()) {
            arm.append("\n.global\t").append(f).append("\n");
            arm.append(f).append(":\n");

            // 有需要保存的, 就Push进栈
            if (f.isUseLr() || f.getUsedProtectedRegs().size() > 0) {
                arm.append("\tpush\t{");
                ArrayList<PhysicalReg> regs = f.getUsedProtectedRegs();
                // 遍历所有要保存的, 进栈
                for (int i = 0; i < regs.size(); ++i) {
                    arm.append(regs.get(i)).append(i == regs.size() - 1 ? "}\n" : ",\t");
                }
                addOff(4);
            }
            // 编.. 迟
            // 译.. 知
            // 结.. 迷人目
            // 束.. 我
            ArrayList<Integer> floatRegIds = new ArrayList<>();
            for (FloatPhysicalReg fReg : f.getUsedProtectedFloatRegs()) {
                floatRegIds.add(fReg.getNum());
            } // 存起来使用的需要保护的浮点寄存器的id

            if (floatRegIds.size() > 0) {
                ArrayList<ArrayList<Integer>> connectList = new ArrayList<>();
                for (int i = 0; i < floatRegIds.size(); i++) {
                    if (i == 0) {
                        connectList.add(new ArrayList<>());
                        connectList.get(0).add(floatRegIds.get(i));
                    } else if (floatRegIds.get(i) - floatRegIds.get(i - 1) == 1) {
                        connectList.get(connectList.size() - 1).add(floatRegIds.get(i));
                    } else {
                        connectList.add(new ArrayList<>());
                        connectList.get(connectList.size() - 1).add(floatRegIds.get(i));
                    }
                }

                for (ArrayList<Integer> item : connectList) {
                    arm.append("\tvpush\t{");
                    for (int j = 0; j < item.size(); j++) {
                        arm.append("s").append(item.get(j)).append(j == item.size() - 1 ? "}\n" : ",\t");
                    }
                }
            }
            if (f.getStackSize() > 0) {
                int stackSize = f.getStackSize();


                if (isCorrectArmImme(-stackSize)) {
                    arm.append("\tadd\tsp,\tsp,\t#").append(-stackSize).append("\n");
                    addOff(1);
                } else if (isCorrectArmImme(stackSize)) {
                    arm.append("\tsub\tsp,\tsp,\t#").append(stackSize).append("\n");
                    addOff(1);
                } else {
                    // 暂时借用一下 r5
                    arm.append("\tpush\t{r5}\n");
                    arm.append(new ArmMov(new PhysicalReg(5), new Imme(stackSize)));
                    arm.append("\tsub\tsp,\tsp,\tr5\n");
                    arm.append("\tpop\t{r5}\n");
                    addOff(4);
                }
            }

            for (ArmBlock block : f.getArmBlocks()) {
                arm.append(block).append(":\n");
                for (ArmInstr instr : block.getArmInstructions()) {
                    // arm.append("\n");
                    // System.out.println("instr -> " + instr);
                    arm.append(instr);
                }
                arm.append("\n"); // 块之间换行
            }
        }
        // TODO 全局变量输出
        if (!module.globalValues.isEmpty()) {
            arm.append("\n\n.data\n");
            arm.append(".align 4\n\n");
            for (GlobalValue globalValue : module.globalValues) {
                arm.append(".global\t").append(globalValue.getNameWithoutPre()).append("\n");
                arm.append(globalValue.getNameWithoutPre()).append(":\n");

                SymType.PointerType point = (SymType.PointerType) globalValue.getType();
                SymType targetType = point.getPointToType();
                if (targetType.isIntType()) { // 如何拿到全局的值
                    arm.append("\t.word\t").
                            append(((InitSym.InitValue) (((GlobalValue.DefGlobalVal) globalValue).initValue)).getValue()).append("\n");
                } else if (targetType.isFloatType()) {
                    // if (是常浮点) {
                    float val = ((Constant.FloatConst) ((InitSym.InitValue)
                            (((GlobalValue.DefGlobalVal) globalValue).initValue)).getValue()).floatVal;
                    arm.append("\t.word\t").append(Float.floatToIntBits(val)).append("\n");
                    // } else {
                    //    这个分类是啥
                    // }
                } else if (targetType.isArrayType()) {
                    ForDefInit.ForArrayFlatten flatten = (((GlobalValue.DefGlobalVal) globalValue).initValue).flatten();
                    for (ForDefInit.ForArrayFlatten.Elements element : flatten) {
                        int val;
                        int count;
                        if (element.value instanceof Constant.FloatConst) {
                            val = Float.floatToIntBits(((Constant.FloatConst) element.value).getFloatVal());
                            count = element.eleCnt;
                        } else {
                            val = ((Constant.IntConst) element.value).getIntVal();
                            count = element.eleCnt;
                        }
                        if (val == 0) {
                            arm.append("\t.zero\t").append(count * 4).append("\n");
                        } else if (count == 1) { // .word
                            arm.append("\t.word\t").append(val).append("\n");
                        } else { // .fill
                            arm.append("\t.fill\t").append(count).append(",\t4,\t").append(val).append("\n");
                        }
                    }
                }
                arm.append("\n");
            }
        }
        return arm.toString();
    }

}
