import arm.armDS.ArmTools;
import arm.regallocator.FloatRegAllocator;
import arm.regallocator.MaintainStack;
import arm.regallocator.RegAllocator;
import compiler.MyIO;
import midend.*;
import midend.analysis.CFGBuilder;
import midend.analysis.LoopAnalysis;
import midend.analysis.LoopInductionVariableGet;
import mir.MyModule;
import tools.AllocaPre;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Compiler {
    public static void main(String[] args) throws Exception {
        if (!hasValidArguments(args)) {
            printUsage();
            return;
        }

        String targetFile = args[2];
        String sourceFile = args[3];

        MyModule myModule = MyIO.genMyModel(sourceFile);

        CFGBuilder.analyseCFG(myModule);
        AllocaPre.allocaPre(myModule);
        CFGBuilder.analyseCFG(myModule);

        GlobalValueLocalize globalValueLocalize = new GlobalValueLocalize(myModule.globalValues);
        globalValueLocalize.runLocalization(myModule.globalValues);

        UselessRetValEmit uselessRetValEmit = new UselessRetValEmit(myModule);
        uselessRetValEmit.runUselessRetValEmit();

        Mem2Reg.run(myModule);

        FuncInline funcInline = new FuncInline(myModule);
        funcInline.run();

        SimpleDCE simpleDCE = new SimpleDCE(myModule);
        simpleDCE.run();

        UselessStoreEmit uselessStoreEmit = new UselessStoreEmit(myModule);
        uselessStoreEmit.run();

        DeadCodeElimination deadCodeElimination = new DeadCodeElimination(myModule);
        deadCodeElimination.run();

        InstrCombine instrCombine = new InstrCombine(myModule);
        instrCombine.runInstrCombine();

        LoopAnalysis.runLoopAnalysis(myModule);
        LCSSA lcssa = new LCSSA(myModule);
        lcssa.runLCSSA(myModule);

        LoopAnalysis.runLoopAnalysis(myModule);
        LoopUselessDel loopUselessDel = new LoopUselessDel(myModule);
        loopUselessDel.runLoopUselessDel();

        LoopAnalysis.runLoopAnalysis(myModule);
        LoopInvariantCodeMotion loopInvariantCodeMotion = new LoopInvariantCodeMotion(myModule);
        loopInvariantCodeMotion.runLoopCodeMotion();

        LoopAnalysis.runLoopAnalysis(myModule);
        LoopInductionVariableGet loopInductionVariableGet =
                new LoopInductionVariableGet(myModule.funcs);
        loopInductionVariableGet.getLoopInductionVariable();
        LoopFold loopFold = new LoopFold(myModule);
        loopFold.runLoopFold();

        BranchOpt branchOpt = new BranchOpt(myModule);
        branchOpt.run();

        GVN gvn = new GVN(myModule);
        gvn.runGVN();

        SimpleDCE simpleDCE1 = new SimpleDCE(myModule);
        simpleDCE1.run();

        DeadCodeElimination deadCodeElimination1 = new DeadCodeElimination(myModule);
        deadCodeElimination1.run();

        GCM gcm = new GCM(myModule);
        gcm.run();

        CFGBuilder.analyseCFG(myModule);
        AllocaPre.allocaPre(myModule);
        CFGBuilder.analyseCFG(myModule);

        InstrCombine instrCombine1 = new InstrCombine(myModule);
        instrCombine1.runInstrCombine();

        AddFold addFold = new AddFold(myModule);
        addFold.run();

        DivMulMod divMulMod = new DivMulMod(myModule);
        divMulMod.run();

        BranchOpt branchOpt1 = new BranchOpt(myModule);
        branchOpt1.run();

        BlockOrderAdjust.runBlockOrderAdjust(myModule);

        LocalSameLoadEmit localSameLoadEmit = new LocalSameLoadEmit(myModule);
        localSameLoadEmit.run();

        AddFold addFold1 = new AddFold(myModule);
        addFold1.run();

        RemovePhi removePhi = new RemovePhi(myModule);
        removePhi.run();

        CFGBuilder.analyseCFG(myModule);

        ArmTools armTools = new ArmTools();
        armTools.parserIrModel(myModule);
        new FloatRegAllocator(armTools);
        new RegAllocator(armTools);
        new MaintainStack(armTools);

        Path output = Path.of(targetFile);
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        Files.writeString(output, armTools.getArmFromSelf(), StandardCharsets.UTF_8);
    }

    private static boolean hasValidArguments(String[] args) {
        return (args.length == 4 || (args.length == 5 && "-O1".equals(args[4])))
                && "-S".equals(args[0])
                && "-o".equals(args[1]);
    }

    private static void printUsage() {
        System.err.println("Usage: compiler -S -o <output.s> <input.sy> [-O1]");
    }
}
