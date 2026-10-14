package compiler;

import frontend.visitor.MyVisitor;
import frontend.ast.Builder;
import frontend.ast.Node;
import midend.GlobalValueLocalize;
import mir.Func;
import mir.GlobalValue;
import mir.MyModule;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class MyIO {

    public static MyModule genMyModel(String filename) throws Exception {
        Builder builder = new Builder();
        Node ast = builder.buildAst(filename);
        MyVisitor myVisitor = new MyVisitor();
        myVisitor.visitRoot(ast);

        return myVisitor.getMyModel();
    }

    public static void outLlvm(MyModule myModule, String filename) throws IOException {
        int index = filename.lastIndexOf('.');
        String name = filename.substring(0, index) + ".ll";
        File file = new File(name);
        FileOutputStream fos = new FileOutputStream(file);

        for (GlobalValue globalValue : myModule.globalValues) {
            fos.write(globalValue.toString().getBytes());
            System.out.println(globalValue.toString());
        }

        for (Func func : myModule.funcs) {
            if (func.getFuncBody() == null) {
                if(func.getName().equals("putf")) {
                    fos.write("declare i32 @putf(i8* %f9, ...)\n".getBytes());
                    System.out.println("declare i32 @putf(i8* %f9, ...)\n");
                } else {
                    fos.write(func.getDeclare().getBytes());
                    System.out.println(func.getDeclare());
                }
            }
        }

        for (Func func : myModule.funcs) {
            if (func.getFuncBody() != null) {
                fos.write(func.outMir().getBytes());
                System.out.println(func.outMir());
            }
        }

        fos.close();
    }


}
