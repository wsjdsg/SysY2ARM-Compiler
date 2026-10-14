//关键词分类

package frontend.ast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Pattern;

/*
枚举类型对语句中的词进行分类，涵盖：
1. 关键词
2. 数字（整型、浮点）
3. 标志量（自定义的常量/变量名）
4. 运算符（逻辑、关系、常规运算）
5. 括号、标点、字符串
（与以下定义对应）
*/
public enum WordType {

    INT("int", true),
    FLOAT("float", true),
    VOID("void", true),
    CONST("const", true),
    IF("if", true),
    ELSE("else", true),
    WHILE("while", true),
    BREAK("break", true),
    CONTINUE("continue", true),
    RETURN("return", true),


    DEC_INT("0|([1-9][0-9]*)", false),
    OCT_INT("0[0-7]+", false),
    HEX_INT("0(x|X)[0-9A-Fa-f]+", false),
    DEC_FLOAT("([0-9]*\\.[0-9]*((p|P|e|E)(\\+|\\-)?[0-9]+)?)|" +
            "([0-9]*[\\.]?[0-9]*(p|P|e|E)((\\+|\\-)?[0-9]+)?)", false),
    HEX_FLOAT("(0(x|X)[0-9A-Fa-f]*\\.[0-9A-Fa-f]*((p|P|e|E)(\\+|\\-)?[0-9A-Fa-f]*)?)|" +
            "(0(x|X)[0-9A-Fa-f]*[\\.]?[0-9A-Fa-f]*(p|P|e|E)((\\+|\\-)?[0-9A-Fa-f]*)?)", false),


    IDENT("[A-Za-z_][A-Za-z0-9_]*", false),


    LOGIC_AND("&&", false),
    LOGIC_OR("\\|\\|", false),
    EQ("==", false),
    NEQ("!=", false),
    LT("<", false),
    GT(">", false),
    LE("<=", false),
    GE(">=", false),
    ADD("\\+", false),
    SUB("-", false),
    MUL("\\*", false),
    DIV("/", false),
    MOD("%", false),
    NOT("!", false),
    ASSIGN("=", false),


    SEMI(";", false),
    COMMA(",", false),
    LPARENT("\\(", false),
    RPARENT("\\)", false),
    LBRACK("\\[", false),
    RBRACK("]", false),
    LBRACE("\\{", false),
    RBRACE("}", false),
    STR("", false);


    public static final ArrayList<WordType> numTypeList =
            new ArrayList<>(Arrays.asList(DEC_INT, DEC_FLOAT, OCT_INT, HEX_INT, HEX_FLOAT));
    private String pattern;
    private boolean ifKeyword;

    WordType(String pattern, boolean ifKeyword) {
        this.pattern = pattern;
        this.ifKeyword = ifKeyword;
    }

    //用于不同匹配识别不同词类型
    //(?!pattern)   非获取匹配，正向否定预查，在任何不匹配pattern的字符串开始处匹配查找字符串，该匹配不需要获取供以后使用。
    /*
    example:
    var testReg=/test(?!123)/;
    var result=testReg.exec('test123');
    console.log(result)//输出null

    var result2=testReg.exec('test12');
    console.log(result[0])//输出test
    */
    public Pattern getPattern() {
        return Pattern.compile("^(" + this.pattern + ")"
                + (this.ifKeyword ? "(?![A-Za-z0-9_])" : ""));      //防止出现 将ifNew识别为if 这类错误
    }
}
