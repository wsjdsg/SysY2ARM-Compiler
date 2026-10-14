//构建抽象语法树，节点类型在Node中

package frontend.ast;

import frontend.lexerparser.SysyBaseVisitor;
import frontend.lexerparser.SysyLexer;
import frontend.lexerparser.SysyParser;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.io.IOException;

public class Builder {
    public Node buildAst(String fileIn) throws IOException {
        CharStream input = CharStreams.fromFileName(fileIn);

        SysyLexer lexer = new SysyLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        SysyParser parser = new SysyParser(tokens);
        SysyParser.ParseContext tree = parser.parse();
        MySysyListener listener = new MySysyListener();
        listener.enterParse(tree);
        return listener.getRoot();
    }
}
